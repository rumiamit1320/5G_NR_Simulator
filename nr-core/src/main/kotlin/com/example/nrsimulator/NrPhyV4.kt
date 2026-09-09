package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

/**
 * v4 NR conformance-oriented path. Existing v1-v3 classes are intentionally untouched.
 * Profiles the procedures defined by 3GPP TS 38.211 (physical channels/modulation),
 * TS 38.212 (transport-channel coding/rate matching) and TS 38.214 (data/MCS).
 * This is a laboratory implementation, not a conformance-test product.
 */
data class NrV4Config(
    val mcs: Int = 16,
    val layers: Int = 2,
    val snrDb: Double = 15.0,
    val prbs: Int = 52,
    val symbols: Int = 12,
    val nfft: Int = 1024,
    val scsKHz: Int = 30,
    val rv: Int = 0,
    val maxLdpcIterations: Int = 12
)

data class NrV4Result(
    val mcs: Int, val qm: Int, val targetCodeRate: Double, val tbs: Int,
    val codedBits: Int, val bitErrors: Int, val crcOk: Boolean, val bler: Double,
    val evmPercent: Double, val throughputMbps: Double, val layers: Int,
    val dmrsSymbols: Int, val nfft: Int, val sampleRateMHz: Double,
    val tx: Array<Complex>, val rx: Array<Complex>, val note: String
)

data class NrMcsEntry(val qM: Int, val r1024: Int) { val rate: Double get() = r1024 / 1024.0 }

object NrMcsTable {
    // TS 38.214 Table 5.1.3.1-1, MCS index 0..28 (qam64 table).
    val table1 = arrayOf(
        NrMcsEntry(2,120),NrMcsEntry(2,157),NrMcsEntry(2,193),NrMcsEntry(2,251),
        NrMcsEntry(2,308),NrMcsEntry(2,379),NrMcsEntry(2,449),NrMcsEntry(2,526),
        NrMcsEntry(2,602),NrMcsEntry(2,679),NrMcsEntry(4,340),NrMcsEntry(4,378),
        NrMcsEntry(4,434),NrMcsEntry(4,490),NrMcsEntry(4,553),NrMcsEntry(4,616),
        NrMcsEntry(4,658),NrMcsEntry(4,698),NrMcsEntry(6,466),NrMcsEntry(6,517),
        NrMcsEntry(6,567),NrMcsEntry(6,616),NrMcsEntry(6,666),NrMcsEntry(6,719),
        NrMcsEntry(6,772),NrMcsEntry(6,822),NrMcsEntry(6,873),NrMcsEntry(6,910),
        NrMcsEntry(6,948)
    )
}

class NrPhyV4(private val rng: Random = Random(System.nanoTime())) {
    fun run(cfg: NrV4Config): NrV4Result {
        val mcs = cfg.mcs.coerceIn(0, 28)
        val e = NrMcsTable.table1[mcs]
        val layers = cfg.layers.coerceIn(1, 4)
        val nPrb = cfg.prbs.coerceIn(1, 275)
        val nSym = cfg.symbols.coerceIn(4, 14)
        val dmrs = 2
        val dataSym = nSym - dmrs
        val nre = nPrb * 12 * dataSym
        val nrePerLayer = nre
        val nInfoApprox = (nrePerLayer * e.qM * e.rate * layers).toInt().coerceAtLeast(24)
        val tbs = nrTbs(nInfoApprox)
        val payload = IntArray(tbs) { rng.nextInt(2) }
        val tbWithCrc = payload + crc24a(payload)

        // A deterministic QC-style parity stage is retained only as an executable
        // laboratory LDPC surrogate; the exact NR BG/Z lifting tables are isolated
        // behind this function so they can be replaced without touching the UI.
        val coded = ldpcLikeEncode(tbWithCrc, e.rate)
        val g = rateMatch(coded, (nre * e.qM * layers).coerceAtLeast(1), cfg.rv)
        val txData = Dsp.qam(g, 1 shl e.qM)

        val dmrsSeq = nrDmrs(txData.size, 0)
        val tx = Array(txData.size + dmrsSeq.size) { Complex(0.0, 0.0) }
        var di = 0; var ri = 0
        for (i in tx.indices) {
            if (i % max(2, tx.size / max(1, dmrsSeq.size)) == 0 && ri < dmrsSeq.size) tx[i] = dmrsSeq[ri++]
            else if (di < txData.size) tx[i] = txData[di++]
        }
        val rx = Dsp.addAwgn(tx, cfg.snrDb, rng)
        val evm = Dsp.evm(tx, rx)
        val dataRx = ArrayList<Complex>(txData.size)
        di = 0; ri = 0
        for (i in tx.indices) {
            if (i % max(2, tx.size / max(1, dmrsSeq.size)) == 0 && ri < dmrsSeq.size) ri++
            else if (di < txData.size) { dataRx += rx[i]; di++ }
        }
        val llr = demapHardLlrs(dataRx.toTypedArray(), e.qM, cfg.snrDb)
        val recoveredCoded = rateRecover(llr, coded.size, cfg.rv)
        val decoded = hardParityDecode(recoveredCoded, tbWithCrc.size)
        val recovered = decoded.copyOf(tbs)
        val errors = payload.indices.count { payload[it] != recovered.getOrElse(it) { 1 - payload[it] } }
        val crc = crc24a(recovered).contentEquals(decoded.copyOfRange(tbs, tbs + 24))
        val ok = crc && errors == 0
        val bler = if (ok) 0.0 else 1.0
        val slotMs = 1.0 / (cfg.scsKHz / 15.0)
        val throughput = if (ok) tbs / (slotMs / 1000.0) / 1e6 else 0.0
        return NrV4Result(mcs,e.qM,e.rate,tbs,coded.size,errors,ok,bler,evm,throughput,layers,dmrs,cfg.nfft,cfg.nfft*cfg.scsKHz/1000.0,tx.take(96).toTypedArray(),rx.take(96).toTypedArray(),"38.211/38.212/38.214 profile; exact LDPC tables are isolated for replacement")
    }

    private fun nrTbs(nInfo: Int): Int {
        // TBS rounding follows the NR decision structure for the small/moderate payload region.
        if (nInfo <= 3824) {
            val n = 2.0.pow(floor(log2(nInfo.toDouble()) - 6.0)).toInt().coerceAtLeast(1)
            val nInfoPrime = max(24.0, floor(nInfo.toDouble() / n) * n)
            return max(24, nInfoPrime.toInt())
        }
        val c = ceil((nInfo + 24) / 8448.0).toInt()
        return (c * 8448 - 24).coerceAtLeast(24)
    }

    private fun crc24a(bits: IntArray): IntArray {
        var crc = 0L
        val poly = 0x1864CFBL
        for (b in bits) {
            val top = (crc and 0x800000L) != 0L
            crc = ((crc shl 1) and 0xFFFFFFL) xor (if (top) poly else 0L) xor (b.toLong() and 1L)
        }
        return IntArray(24) { i -> ((crc ushr (23 - i)) and 1L).toInt() }
    }

    private fun ldpcLikeEncode(info: IntArray, rate: Double): IntArray {
        val n = ceil(info.size / rate).toInt().coerceAtLeast(info.size + 24)
        val out = IntArray(n); info.copyInto(out)
        for (p in info.size until n) {
            var v = 0
            for (j in 0 until info.size) if (((j * 31 + p * 17 + 7) % 97) < 4) v = v xor info[j]
            out[p] = v
        }
        return out
    }

    private fun rateMatch(c: IntArray, e: Int, rv: Int): IntArray {
        val out = IntArray(e)
        val start = ((rv and 3) * c.size / 4) % c.size
        for (i in out.indices) out[i] = c[(start + i) % c.size]
        return out
    }

    private fun rateRecover(llr: DoubleArray, n: Int, rv: Int): DoubleArray {
        val out = DoubleArray(n)
        val start = ((rv and 3) * n / 4) % n
        for (i in llr.indices) out[(start + i) % n] += llr[i]
        return out
    }

    private fun hardParityDecode(llr: DoubleArray, k: Int): IntArray {
        // Hard decision plus a few local parity repair passes.
        val b = IntArray(llr.size) { if (llr[it] < 0) 1 else 0 }
        for (pass in 0 until 4) for (j in k until b.size) {
            var p = 0
            for (i in 0 until k) if (((i * 31 + j * 17 + 7) % 97) < 4) p = p xor b[i]
            if (p != b[j]) b[j] = p
        }
        return b.copyOf(k)
    }

    private fun demapHardLlrs(z: Array<Complex>, q: Int, snr: Double): DoubleArray {
        val out = DoubleArray(z.size * q)
        val gain = 10.0.pow(snr / 10.0).coerceAtLeast(0.01)
        val levels = sqrt((1 shl q).toDouble()).roundToInt()
        for (i in z.indices) {
            val re = ((z[i].re * sqrt((2.0/3.0)*((1 shl q)-1))).roundToInt() + levels - 1) / 2
            val im = ((z[i].im * sqrt((2.0/3.0)*((1 shl q)-1))).roundToInt() + levels - 1) / 2
            val v = (re.coerceIn(0,levels-1) + levels * im.coerceIn(0,levels-1))
            for (k in 0 until q) out[i*q+k] = if (((v ushr (q-1-k)) and 1) == 0) gain else -gain
        }
        return out
    }

    private fun nrDmrs(n: Int, slot: Int): Array<Complex> {
        // Gold-sequence based QPSK reference sequence; initialization follows the NR-style c(n) structure.
        val len = 2*n
        var x1 = 1
        var x2 = ((slot + 1) * 0x1F1F1 + 0x13579) and 0x7FFFFFFF
        val c = IntArray(len)
        repeat(1600) { val nx = ((x1 ushr 3) xor x1) and 1; val ny = ((x2 ushr 3) xor (x2 ushr 2) xor (x2 ushr 1) xor x2) and 1; x1 = (x1 ushr 1) or (nx shl 30); x2 = (x2 ushr 1) or (ny shl 30) }
        for (i in 0 until len) { val nx=((x1 ushr 3) xor x1) and 1; val ny=((x2 ushr 3) xor (x2 ushr 2) xor (x2 ushr 1) xor x2) and 1; x1=(x1 ushr 1) or (nx shl 30); x2=(x2 ushr 1) or (ny shl 30); c[i]=nx xor ny }
        return Array(n) { i -> Complex((1-2*c[2*i])/sqrt(2.0), (1-2*c[2*i+1])/sqrt(2.0)) }
    }
}
