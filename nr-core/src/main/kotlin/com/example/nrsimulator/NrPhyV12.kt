package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * NR v12 additive end-to-end PDSCH reference link.
 *
 * V1-v11 remain intact. This layer closes the transport-to-air-to-transport
 * loop for a single code block using the embedded BG2/iLS1 LDPC reference:
 *
 * TB -> CRC -> LDPC -> circular-buffer rate matching -> QAM -> layer mapping
 * -> DM-RS/resource grid -> OFDM IFFT/CP -> frequency-selective MIMO channel
 * -> FFT/CP removal -> DM-RS LS estimation -> MMSE/ZF -> QAM LLRs
 * -> rate recovery -> LDPC -> TB CRC.
 *
 * Scope deliberately remains reference-level: BG2/iLS1, one code block,
 * single-symbol PDSCH DM-RS type-1, no DCI/PT-RS/codebook precoding yet.
 */
data class NrPhyV12Config(
    val scsKHz: Int = 30,
    val prbs: Int = 24,
    val symbolsPerSlot: Int = 14,
    val dmrsSymbol: Int = 2,
    val payloadBits: Int = 300,
    val targetCodeRate: Double = 0.5,
    val rv: Int = 0,
    val qm: Int = 2,
    val txAntennas: Int = 1,
    val rxAntennas: Int = 1,
    val layers: Int = 1,
    val snrDb: Double = 20.0,
    val channelModel: String = "FREQUENCY_SELECTIVE",
    val equalizer: String = "MMSE",
    val seed: Int = 0x1201
)

data class NrPhyV12Result(
    val payloadBits: Int,
    val bg: Int,
    val zc: Int,
    val codewordBits: Int,
    val rateMatchedBits: Int,
    val dataRe: Int,
    val qamSymbols: Int,
    val fftSize: Int,
    val cpSamples: Int,
    val txAntennas: Int,
    val rxAntennas: Int,
    val layers: Int,
    val dmrsResources: Int,
    val channelErrorPercent: Double,
    val equalizedEvmPercent: Double,
    val bitErrors: Int,
    val tbCrcOk: Boolean,
    val ldpcPass: Boolean,
    val ofdmEvmPercent: Double,
    val throughputMbps: Double,
    val bler: Double,
    val pass: Boolean,
    val note: String
)

class NrPhyV12 {
    fun run(cfg: NrPhyV12Config): NrPhyV12Result {
        require(cfg.scsKHz == 15 || cfg.scsKHz == 30 || cfg.scsKHz == 60)
        val prbs = cfg.prbs.coerceIn(1, 106)
        val symbols = cfg.symbolsPerSlot.coerceIn(4, 14)
        val dmrsL = cfg.dmrsSymbol.coerceIn(0, symbols - 1)
        val tx = cfg.txAntennas.coerceIn(1, 4)
        val rx = cfg.rxAntennas.coerceIn(1, 4)
        val layers = minOf(cfg.layers.coerceIn(1, 4), tx, rx)
        val qm = cfg.qm.coerceIn(2, 8)

        // Current exact embedded LDPC path: BG2/iLS1, one CB, Zc selected from
        // the exact table family currently embedded by v8/v9.
        val payload = deterministicBits(cfg.payloadBits.coerceIn(40, 3000), cfg.seed)
        val crcBits = if (payload.size > 3824) 24 else 16
        val tb = appendCrc(payload, crcBits)
        val bPrime = tb.size
        val kb = when {
            bPrime > 640 -> 10
            bPrime > 560 -> 9
            bPrime > 192 -> 8
            else -> 6
        }
        val zc = intArrayOf(3, 6, 12, 24, 48).firstOrNull { kb * it >= bPrime }
            ?: return fail(cfg, payload.size, "Payload exceeds the currently embedded exact BG2/iLS1 lifting-size range.")
        val k = 10 * zc
        val n = 50 * zc
        val filler = k - bPrime
        val info = IntArray(k)
        tb.copyInto(info, filler)
        val graph = NrLdpcV8Graph(NrLdpcV8Tables.bg2(zc), zc)
        val code = graph.encode(info)

        val nSc = prbs * 12
        val nfft = nextPow2(maxOf(128, nSc + 2))
        val cp = (nfft * 72.0 / 1024.0 * 15.0 / cfg.scsKHz).roundToIntCompat()
        val firstCp = (nfft * 80.0 / 1024.0 * 15.0 / cfg.scsKHz).roundToIntCompat()
        val offset = (nfft - nSc) / 2

        val dmrs = NrDmrsV11().generate(NrDmrsV11Config(
            nId = 1, slotNumber = 0, symbol = dmrsL, prbs = prbs,
            ports = layers, amplitude = 1.0
        ))
        val dmrsSet = dmrs.map { it.subcarrier to it.symbol }.toHashSet()
        val dataPositions = ArrayList<Pair<Int, Int>>()
        for (l in 0 until symbols) for (k0 in 0 until nSc) {
            if (!dmrsSet.contains(k0 to l)) dataPositions += k0 to l
        }
        val dataRe = dataPositions.size * layers
        val e = dataRe * qm
        val rmIndices = selectionIndices(n, e, cfg.rv and 3, zc, filler)
        val txBits = IntArray(e) { code[rmIndices[it]] }
        val txGrid = Array(tx) { Array(symbols) { Array(nfft) { Complex(0.0, 0.0) } } }
        val txDataSymbols = Array(layers) { ArrayList<Complex>() }
        var bitPos = 0
        for (layer in 0 until layers) {
            for ((k0, l) in dataPositions) {
                val bits = IntArray(qm) { txBits[bitPos++] }
                val s = Dsp.qam(bits, 1 shl qm).first()
                txGrid[layer][l][offset + k0] = s
                txDataSymbols[layer] += s
            }
        }
        for (r in dmrs) txGrid[r.port - 1000][r.symbol][offset + r.subcarrier] = r.value

        // Explicit OFDM TX/RX round trip around the frequency-domain channel.
        // The channel itself is applied on subcarriers (equivalent to circular
        // convolution for the short reference channel); CP sizing is still
        // exercised explicitly here.
        val txGridOccupied = Array(tx) { Array(symbols) { Array(nSc) { Complex(0.0, 0.0) } } }
        for (t in 0 until tx) for (l in 0 until symbols) for (k0 in 0 until nSc)
            txGridOccupied[t][l][k0] = txGrid[t][l][offset + k0]

        val ofdmFdTx = Array(tx) { Array(symbols) { Array(nSc) { Complex(0.0, 0.0) } } }
        var ofdmErr = 0.0
        var ofdmPow = 0.0
        for (t in 0 until tx) for (l in 0 until symbols) {
            val full = Array(nfft) { Complex(0.0, 0.0) }
            for (k0 in 0 until nSc) full[offset + k0] = txGridOccupied[t][l][k0]
            val td = Dsp.fft(full, true)
            val cpLen = if (l == 0) firstCp else cp
            val withCp = Array(nfft + cpLen) { i -> if (i < cpLen) td[nfft - cpLen + i] else td[i - cpLen] }
            val noCp = withCp.copyOfRange(cpLen, cpLen + nfft)
            val fd = Dsp.fft(noCp)
            for (k0 in 0 until nSc) {
                ofdmFdTx[t][l][k0] = fd[offset + k0]
                ofdmErr += (fd[offset + k0] - txGridOccupied[t][l][k0]).abs2()
                ofdmPow += txGridOccupied[t][l][k0].abs2()
            }
        }
        val ofdmEvm = sqrt(ofdmErr / ofdmPow.coerceAtLeast(1e-12)) * 100.0

        val channel = NrChannelV11(NrChannelV11Config(tx, rx, prbs, cfg.snrDb, cfg.channelModel, cfg.seed xor 0x44))
        val received = channel.apply(ofdmFdTx, cfg.snrDb, 0x33)

        val hEst = estimateChannel(received, dmrs, layers, rx, nSc, offset)
        val hTruth = Array(nSc) { channel.frequencyResponse(it, nSc) }
        var hErr = 0.0
        var hPow = 0.0
        for (k0 in 0 until nSc) for (r in 0 until rx) for (t in 0 until layers) {
            val e0 = hTruth[k0][r][t] - hEst[k0][r * layers + t]
            hErr += e0.abs2(); hPow += hTruth[k0][r][t].abs2()
        }
        val hError = sqrt(hErr / hPow.coerceAtLeast(1e-12)) * 100.0

        val rxSymbols = Array(layers) { ArrayList<Complex>() }
        val refSymbols = Array(layers) { ArrayList<Complex>() }
        for ((k0, l) in dataPositions) {
            val h = hEst[k0]
            val y = Array(rx) { received[it][l][k0] }
            val x = if (cfg.equalizer.uppercase() == "ZF") zf(h, y, layers) else mmse(h, y, layers, cfg.snrDb)
            for (layer in 0 until layers) {
                rxSymbols[layer] += x[layer]
                refSymbols[layer] += txGrid[layer][l][offset + k0]
            }
        }

        val refAll = refSymbols.flatMap { it }.toTypedArray()
        val rxAll = rxSymbols.flatMap { it }.toTypedArray()
        val evm = Dsp.evm(refAll, rxAll)
        val llr = demapLlrs(rxAll, qm, cfg.snrDb)
        val recovered = DoubleArray(n)
        for (i in rmIndices.indices) recovered[rmIndices[i]] += llr[i]
        for (i in 0 until 2 * zc) recovered[i] = 0.0
        val dec = graph.decode(recovered, 20, 0.8)
        val decodedTb = dec.copyOfRange(filler, filler + tb.size)
        val bitErrors = countErrors(tb, decodedTb)
        val crcOk = checkCrc(decodedTb, crcBits)
        val ldpcPass = bitErrors == 0 && crcOk
        val slotSeconds = 1e-3 / (2.0.pow(kotlin.math.log2(cfg.scsKHz / 15.0)))
        val throughput = if (crcOk) payload.size / slotSeconds / 1e6 else 0.0
        val bler = if (crcOk) 0.0 else 1.0
        val pass = crcOk && graph.syndromeWeight(code) == 0 && ofdmEvm < 1e-6
        return NrPhyV12Result(
            payload.size, 2, zc, n, e, dataRe, e / qm, nfft, cp, tx, rx, layers,
            dmrs.size, hError, evm, bitErrors, crcOk, ldpcPass, ofdmEvm,
            throughput, bler, pass,
            "V12 closes the reference PDSCH loop: CRC/LDPC/rate matching → QAM/layers → type-1 DM-RS resource grid → IFFT/CP/FFT → frequency-selective MIMO → LS estimation → MMSE/ZF → QAM LLRs → rate recovery/LDPC → TB CRC. BG2/iLS1 single-CB scope is deliberate; V13 can add PT-RS and phase impairments."
        )
    }

    private fun estimateChannel(
        rxGrid: Array<Array<Array<Complex>>>, dmrs: List<NrDmrsResource>, layers: Int,
        rx: Int, nSc: Int, offset: Int
    ): Array<Array<Complex>> {
        val h = Array(nSc) { Array(rx * layers) { Complex(0.0, 0.0) } }
        val byPort = dmrs.groupBy { it.port }.mapValues { (_, v) -> v.associateBy { it.subcarrier } }
        if (layers == 1) {
            val p = byPort[1000].orEmpty()
            for (k0 in p.keys) for (r in 0 until rx) {
                val x = p.getValue(k0).value
                h[k0][r] = divide(rxGrid[r][p.getValue(k0).symbol][k0], x)
            }
        } else {
            // Type-1 ports are orthogonal over adjacent REs in the simplified
            // single-symbol reference mapping used by V11. Solve the 2x2 pair
            // directly wherever both ports are present, then interpolate.
            var k0 = 0
            while (k0 + 2 < nSc) {
                val a = byPort[1000]?.get(k0)
                val b = byPort[1000]?.get(k0 + 2)
                val c = byPort[1001]?.get(k0)
                val d = byPort[1001]?.get(k0 + 2)
                if (a != null && b != null && c != null && d != null) {
                    val det = a.value * d.value - b.value * c.value
                    for (r in 0 until rx) {
                        val ya = rxGrid[r][a.symbol][k0]
                        val yb = rxGrid[r][b.symbol][k0 + 2]
                        h[k0][r * layers] = divide(ya * d.value - yb * c.value, det)
                        h[k0][r * layers + 1] = divide(a.value * yb - b.value * ya, det)
                        h[k0 + 2][r * layers] = h[k0][r * layers]
                        h[k0 + 2][r * layers + 1] = h[k0][r * layers + 1]
                    }
                }
                k0 += 4
            }
            // Additional layers use their own port estimate where possible.
            for (layer in 2 until layers) {
                val p = byPort[1000 + layer].orEmpty()
                for (k in p.keys) for (r in 0 until rx) h[k][r * layers + layer] = divide(rxGrid[r][p.getValue(k).symbol][k], p.getValue(k).value)
            }
        }
        for (k in 0 until nSc) for (r in 0 until rx) for (t in 0 until layers) {
            val idx = r * layers + t
            if (h[k][idx].abs2() > 0.0) continue
            var left = k - 1
            while (left >= 0 && h[left][idx].abs2() == 0.0) left--
            var right = k + 1
            while (right < nSc && h[right][idx].abs2() == 0.0) right++
            h[k][idx] = when {
                left >= 0 && right < nSc -> (h[left][idx] + h[right][idx]) * 0.5
                left >= 0 -> h[left][idx]
                right < nSc -> h[right][idx]
                else -> Complex(0.0, 0.0)
            }
        }
        return h
    }

    private fun mmse(h: Array<Complex>, y: Array<Complex>, layers: Int, snrDb: Double): Array<Complex> = solveMimo(h, y, layers, 10.0.pow(-snrDb / 10.0).coerceAtLeast(1e-8))
    private fun zf(h: Array<Complex>, y: Array<Complex>, layers: Int): Array<Complex> = solveMimo(h, y, layers, 1e-9)

    private fun solveMimo(h: Array<Complex>, y: Array<Complex>, layers: Int, reg: Double): Array<Complex> {
        val a = Array(layers) { Array(layers) { Complex(0.0, 0.0) } }
        val b = Array(layers) { Complex(0.0, 0.0) }
        for (i in 0 until layers) for (j in 0 until layers) {
            var s = Complex(0.0, 0.0)
            for (r in y.indices) s += h[r * layers + i].conj() * h[r * layers + j]
            a[i][j] = s + if (i == j) Complex(reg, 0.0) else Complex(0.0, 0.0)
        }
        for (i in 0 until layers) {
            var s = Complex(0.0, 0.0)
            for (r in y.indices) s += h[r * layers + i].conj() * y[r]
            b[i] = s
        }
        return solve(a, b)
    }

    private fun solve(a0: Array<Array<Complex>>, b0: Array<Complex>): Array<Complex> {
        val n = b0.size
        val a = Array(n) { i -> Array(n + 1) { j -> if (j < n) a0[i][j] else b0[i] } }
        for (c in 0 until n) {
            var p = c
            for (r in c + 1 until n) if (a[r][c].abs2() > a[p][c].abs2()) p = r
            if (p != c) { val tmp = a[c]; a[c] = a[p]; a[p] = tmp }
            val pivot = a[c][c]
            for (j in c..n) a[c][j] = divide(a[c][j], pivot)
            for (r in 0 until n) if (r != c) {
                val f = a[r][c]
                for (j in c..n) a[r][j] = a[r][j] - f * a[c][j]
            }
        }
        return Array(n) { a[it][n] }
    }

    private fun demapLlrs(symbols: Array<Complex>, qm: Int, snrDb: Double): DoubleArray {
        val order = 1 shl qm
        val bitLabels = Array(order) { v -> IntArray(qm) { i -> (v ushr (qm - 1 - i)) and 1 } }
        val constellation = Array(order) { v -> Dsp.qam(bitLabels[v], order).first() }
        val sigma2 = 10.0.pow(-snrDb / 10.0).coerceAtLeast(1e-7)
        val out = DoubleArray(symbols.size * qm)
        var p = 0
        for (y in symbols) {
            for (b in 0 until qm) {
                var d0 = Double.POSITIVE_INFINITY
                var d1 = Double.POSITIVE_INFINITY
                for (v in 0 until order) {
                    val d = (y - constellation[v]).abs2()
                    if (bitLabels[v][b] == 0) d0 = minOf(d0, d) else d1 = minOf(d1, d)
                }
                out[p++] = (d1 - d0) / sigma2
            }
        }
        return out
    }

    private fun selectionIndices(n: Int, e: Int, rv: Int, z: Int, filler: Int): IntArray {
        val out = IntArray(e)
        val start = when (rv and 3) { 0 -> 0; 1 -> n * 13 / 50; 2 -> n / 2; else -> n * 43 / 50 }
        val fillerTransmitted = (filler - 2 * z).coerceAtLeast(0)
        var j = 0; var count = 0
        while (count < e) {
            val idx = (start + j) % n
            if (idx >= fillerTransmitted) out[count++] = idx
            j++
        }
        return out
    }

    private fun deterministicBits(n: Int, seed: Int): IntArray {
        var s = seed
        return IntArray(n) { s = s * 1664525 + 1013904223; (s ushr 31) and 1 }
    }

    private fun appendCrc(payload: IntArray, bits: Int): IntArray {
        val crc = if (bits == 24) crc24A(payload) else crc16(payload)
        return payload.copyOf(payload.size + bits).also { out -> for (i in 0 until bits) out[payload.size + i] = (crc ushr (bits - 1 - i)) and 1 }
    }

    private fun checkCrc(bits: IntArray, crcBits: Int): Boolean {
        val payload = bits.copyOf(bits.size - crcBits)
        var got = 0
        for (i in payload.size until bits.size) got = (got shl 1) or bits[i]
        return got == if (crcBits == 24) crc24A(payload) else crc16(payload)
    }

    private fun crc16(bits: IntArray): Int { var c = 0; for (b in bits) { val top = ((c ushr 15) and 1) xor b; c = (c shl 1) and 0xFFFF; if (top != 0) c = c xor 0x1021 }; return c and 0xFFFF }
    private fun crc24A(bits: IntArray): Int { var c = 0; for (b in bits) { val top = ((c ushr 23) and 1) xor b; c = (c shl 1) and 0xFFFFFF; if (top != 0) c = c xor 0x864CFB }; return c and 0xFFFFFF }
    private fun countErrors(a: IntArray, b: IntArray): Int = a.indices.count { a[it] != b[it] }
    private fun divide(a: Complex, b: Complex): Complex { val d = b.abs2().coerceAtLeast(1e-18); return a * b.conj() * (1.0 / d) }
    private fun nextPow2(x: Int): Int { var n = 1; while (n < x) n = n shl 1; return n }
    private fun Int.roundToIntCompat(): Int = this
    private fun Double.roundToIntCompat(): Int = kotlin.math.round(this).toInt()

    private fun fail(cfg: NrPhyV12Config, payload: Int, why: String) = NrPhyV12Result(payload, 2, 0, 0, 0, 0, 0, 0, 0, cfg.txAntennas, cfg.rxAntennas, cfg.layers, 0, 0.0, 0.0, 0, false, false, 0.0, 0.0, 1.0, false, why)
}
