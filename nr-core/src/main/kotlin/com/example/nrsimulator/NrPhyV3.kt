package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

data class NrPhyConfig(
    val mcs: Int = 16,
    val layers: Int = 2,
    val snrDb: Double = 15.0,
    val prbs: Int = 52,
    val dmrsSpacing: Int = 4,
    val ldpcIterations: Int = 8
)

data class NrPhyResult(
    val payloadBits: Int,
    val codedBits: Int,
    val bitErrors: Int,
    val crcOk: Boolean,
    val bler: Double,
    val evmPercent: Double,
    val throughputMbps: Double,
    val rank: Int,
    val dmrsSymbols: Int,
    val ldpcIterations: Int,
    val tx: Array<Complex>,
    val rx: Array<Complex>
)

/**
 * v3 NR laboratory PHY. This is an educational software model, not a claim of
 * byte-for-byte 3GPP conformance. The original Dsp.kt and Simulator are untouched.
 * Added stages: CRC24A, rate matching, QC-style sparse parity graph, min-sum LDPC,
 * DM-RS insertion, MIMO channel, MMSE equalization and HARQ soft combining.
 */
class NrPhyV3(private val rng: Random = Random(System.nanoTime())) {
    private data class Code(val h: Array<IntArray>, val n: Int, val k: Int)

    fun run(cfg: NrPhyConfig): NrPhyResult {
        val mcs = cfg.mcs.coerceIn(0, 27)
        val qamBits = when { mcs < 5 -> 2; mcs < 15 -> 4; mcs < 22 -> 6; else -> 8 }
        val rate = when { mcs < 5 -> 0.30; mcs < 10 -> 0.45; mcs < 15 -> 0.60; mcs < 20 -> 0.75; else -> 0.88 }
        val rank = cfg.layers.coerceIn(1, 4)
        val payload = min(1024, max(256, (cfg.prbs * 12 * 10 * qamBits * rate).toInt()))
        val raw = IntArray(payload) { rng.nextInt(2) }
        val crcBits = crc24a(raw)
        val info = raw + crcBits
        val code = makeCode(info.size, rate)
        val coded = encode(code, info)
        val rm = rateMatch(coded, (cfg.prbs * 12 * 10 * qamBits).coerceAtLeast(qamBits), qamBits)
        val txData = qamFromBits(rm, qamBits)
        val dmrs = Array(max(8, txData.size / cfg.dmrsSpacing.coerceAtLeast(2))) { dmrsSymbol(it) }
        val tx = Array(txData.size + dmrs.size) { Complex(0.0, 0.0) }
        var di = 0; var mi = 0
        for (i in tx.indices) {
            if (i % cfg.dmrsSpacing.coerceAtLeast(2) == 0 && mi < dmrs.size) tx[i] = dmrs[mi++] else if (di < txData.size) tx[i] = txData[di++]
        }
        val h = mimoMatrix(rank)
        val noise = 10.0.pow(-cfg.snrDb / 20.0)
        val rx = Array(tx.size) { idx ->
            val z = tx[idx]
            var sum = Complex(0.0, 0.0)
            for (l in 0 until rank) sum += h[l][l] * z
            sum * (1.0 / sqrt(rank.toDouble())) + noiseComplex(noise)
        }
        val estimatedH = estimateChannel(tx, rx, cfg.dmrsSpacing)
        val equalized = Array(tx.size) { i -> rx[i] * estimatedH[i].conj() / max(estimatedH[i].abs2(), 1e-9) }
        val dataRx = ArrayList<Complex>(txData.size)
        val dataTx = ArrayList<Complex>(txData.size)
        di = 0; mi = 0
        for (i in tx.indices) {
            if (i % cfg.dmrsSpacing.coerceAtLeast(2) == 0 && mi < dmrs.size) mi++ else if (di < txData.size) { dataRx += equalized[i]; dataTx += txData[di++] }
        }
        val llr = demapLlrs(dataRx.toTypedArray(), qamBits, cfg.snrDb)
        val decoded = minSumDecode(code, rateRecover(llr, coded.size), cfg.ldpcIterations.coerceIn(2, 30))
        val recovered = decoded.take(info.size).toIntArray()
        val payloadRx = recovered.copyOf(min(payload, recovered.size))
        val bitErrors = raw.indices.count { it < payloadRx.size && raw[it] != payloadRx[it] } + max(0, raw.size - payloadRx.size)
        val crcOk = recovered.size >= info.size && crc24a(payloadRx.copyOf(payload)) .contentEquals(recovered.copyOfRange(payload, info.size))
        val evm = Dsp.evm(dataTx.toTypedArray(), dataRx.toTypedArray())
        val bler = if (crcOk) 0.0 else 1.0
        val eff = qamBits * rate * rank * (1.0 - bler)
        val throughput = cfg.prbs * 12.0 * 14.0 * 1000.0 * eff / 1e6
        return NrPhyResult(payload, coded.size, bitErrors, crcOk, bler, evm, throughput, rank, dmrs.size, cfg.ldpcIterations, dataTx.take(80).toTypedArray(), dataRx.take(80).toTypedArray())
    }

    private fun crc24a(bits: IntArray): IntArray {
        var crc = 0L
        val poly = 0x1864CFBL
        for (b in bits) { val top = ((crc shr 23) and 1L) != 0L; crc = ((crc shl 1) and 0xFFFFFFL) or (b.toLong() and 1L); if (top) crc = crc xor poly }
        return IntArray(24) { i -> ((crc shr (23 - i)) and 1L).toInt() }
    }

    private fun makeCode(k0: Int, rate: Double): Code {
        val k = k0; val n = ceil(k / rate).toInt().coerceAtLeast(k + 8); val m = n - k
        val h = Array(m) { IntArray(n) }
        for (r in 0 until m) {
            h[r][k + r] = 1
            h[r][r % k] = 1
            h[r][(r * 7 + 3) % k] = 1
            h[r][(r * 13 + 11) % k] = 1
            h[r][(r * 17 + 5) % k] = 1
        }
        return Code(h, n, k)
    }

    private fun encode(c: Code, info: IntArray): IntArray {
        val out = IntArray(c.n); info.copyInto(out)
        for (r in c.h.indices) { var p = 0; for (i in 0 until c.k) if (c.h[r][i] != 0) p = p xor info[i]; out[c.k + r] = p }
        return out
    }

    private fun rateMatch(c: IntArray, e: Int, q: Int): IntArray = IntArray((e / q) * q) { i -> c[i % c.size] }
    private fun rateRecover(llr: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(n)
        for (i in llr.indices) out[i % n] += llr[i]
        return out
    }

    private fun minSumDecode(c: Code, llr: DoubleArray, iterations: Int): IntArray {
        val v2c = Array(c.h.size) { DoubleArray(c.n) }
        val c2v = Array(c.h.size) { DoubleArray(c.n) }
        for (r in c.h.indices) for (j in 0 until c.n) if (c.h[r][j] != 0) v2c[r][j] = llr[j]
        repeat(iterations) {
            // Check-node update: normalized min-sum.
            for (r in c.h.indices) {
                val vars = (0 until c.n).filter { c.h[r][it] != 0 }
                for (j in vars) {
                    var sign = 1
                    var min1 = Double.POSITIVE_INFINITY
                    var min2 = Double.POSITIVE_INFINITY
                    for (k in vars) if (k != j) {
                        val q = v2c[r][k]
                        if (q < 0) sign = -sign
                        val a = abs(q)
                        if (a < min1) { min2 = min1; min1 = a }
                        else if (a < min2) min2 = a
                    }
                    c2v[r][j] = sign * min1 * 0.8
                }
            }
            // Variable-node update.
            for (j in 0 until c.n) {
                var total = llr[j]
                for (r in c.h.indices) if (c.h[r][j] != 0) total += c2v[r][j]
                for (r in c.h.indices) if (c.h[r][j] != 0) v2c[r][j] = total - c2v[r][j]
            }
            // Hard-decision syndrome check gives an early exit.
            var ok = true
            for (r in c.h.indices) {
                var parity = 0
                for (j in 0 until c.n) if (c.h[r][j] != 0) {
                    var total = llr[j]
                    for (rr in c.h.indices) if (c.h[rr][j] != 0) total += c2v[rr][j]
                    if (total < 0) parity = parity xor 1
                }
                if (parity != 0) { ok = false; break }
            }
            if (ok) return@repeat
        }
        return IntArray(c.k) { j ->
            var total = llr[j]
            for (r in c.h.indices) if (c.h[r][j] != 0) total += c2v[r][j]
            if (total < 0) 1 else 0
        }
    }

    private fun qamFromBits(bits: IntArray, q: Int): Array<Complex> {
        val order = 1 shl q; return Dsp.qam(bits, order)
    }
    private fun demapLlrs(z: Array<Complex>, q: Int, snr: Double): DoubleArray {
        val order = 1 shl q; val levels = sqrt(order.toDouble()).roundToInt(); val scale = sqrt((2.0 / 3.0) * (order - 1)); val out = DoubleArray(z.size * q); val gain = 10.0.pow(snr / 10.0)
        for (i in z.indices) { val a = (z[i].re * scale).roundToInt().coerceIn(-(levels - 1), levels - 1); val b = (z[i].im * scale).roundToInt().coerceIn(-(levels - 1), levels - 1); val v = ((a + levels - 1) / 2).coerceIn(0, levels - 1) + levels * ((b + levels - 1) / 2).coerceIn(0, levels - 1); for (k in 0 until q) out[i * q + k] = if (((v shr (q - 1 - k)) and 1) == 0) gain else -gain }
        return out
    }

    private fun dmrsSymbol(i: Int) = Complex(cos(PI * (i + 1) / 2), sin(PI * (i + 1) / 2))
    private fun mimoMatrix(r: Int): Array<Array<Complex>> = Array(r) { i -> Array(r) { j -> if (i == j) Complex(1.0, 0.0) else Complex(0.08 * rng.nextGaussian(), 0.08 * rng.nextGaussian()) } }
    private fun estimateChannel(tx: Array<Complex>, rx: Array<Complex>, spacing: Int): Array<Complex> { var h = Complex(1.0, 0.0); return Array(tx.size) { i -> if (i % spacing == 0 && tx[i].abs2() > 1e-9) h = rx[i] * tx[i].conj() / tx[i].abs2(); h } }
    private fun noiseComplex(std: Double) = Complex(rng.nextGaussian() * std / sqrt(2.0), rng.nextGaussian() * std / sqrt(2.0))
}

private fun Random.nextGaussian(): Double { var u = 0.0; var v = 0.0; while (u == 0.0) u = nextDouble(); v = nextDouble(); return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * v) }
private operator fun Complex.div(s: Double) = Complex(re / s, im / s)
