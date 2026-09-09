package com.example.nrsimulator

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * NR v11 integrated reference path.
 *
 * V1-v10 remain untouched. This layer connects a resource grid to:
 *   PDSCH-like QPSK layers -> exact type-1 DM-RS -> frequency-selective MIMO
 *   channel -> DM-RS LS channel estimation -> ZF/MMSE equalization.
 *
 * It is intentionally an isolated reference engine, not a claim of complete
 * 3GPP PHY conformance (full DCI, PDSCH mapping, PT-RS, codebook precoding,
 * and complete channel-model families remain outside this layer).
 */
data class NrPhyV11Config(
    val scsKHz: Int = 30,
    val prbs: Int = 52,
    val symbolsPerSlot: Int = 14,
    val dmrsSymbol: Int = 2,
    val txAntennas: Int = 2,
    val rxAntennas: Int = 2,
    val dmrsPorts: Int = 2,
    val snrDb: Double = 15.0,
    val channelModel: String = "FREQUENCY_SELECTIVE",
    val equalizer: String = "MMSE",
    val seed: Int = 0x1101
)

data class NrPhyV11Result(
    val scsKHz: Int,
    val prbs: Int,
    val fftSize: Int,
    val dmrsSymbol: Int,
    val dmrsResources: Int,
    val txAntennas: Int,
    val rxAntennas: Int,
    val layers: Int,
    val channelTaps: Int,
    val channelErrorPercent: Double,
    val equalizedEvmPercent: Double,
    val residualPowerDb: Double,
    val rank: Int,
    val conditionNumberDb: Double,
    val pass: Boolean,
    val note: String
)

class NrPhyV11 {
    fun run(cfg: NrPhyV11Config): NrPhyV11Result {
        val scs = cfg.scsKHz
        require(scs == 15 || scs == 30 || scs == 60)
        val prbs = cfg.prbs.coerceIn(1, 275)
        val nSc = prbs * 12
        val nfft = nextPow2(maxOf(128, nSc + 2))
        val symbols = cfg.symbolsPerSlot.coerceIn(1, 14)
        val dmrsL = cfg.dmrsSymbol.coerceIn(0, symbols - 1)
        val tx = cfg.txAntennas.coerceIn(1, 4)
        val rx = cfg.rxAntennas.coerceIn(1, 4)
        val layers = minOf(tx, cfg.dmrsPorts.coerceIn(1, 4))
        val rng = Random(cfg.seed)
        val dmrs = NrDmrsV11()
        val dmrsCfg = NrDmrsV11Config(1, 0, dmrsL, prbs, layers, 1.0)
        val dmrsResources = dmrs.generate(dmrsCfg)
        val dmrsByPort = dmrsResources.groupBy { it.port }

        val grid = Array(tx) { Array(symbols) { Array(nSc) { Complex(0.0, 0.0) } } }
        val dmrsSet = HashSet<Pair<Int, Int>>()
        for (r in dmrsResources) dmrsSet += r.subcarrier to r.symbol
        val txRef = ArrayList<Complex>()
        for (l in 0 until symbols) for (k in 0 until nSc) {
            if (l == dmrsL && dmrsSet.contains(k to l)) continue
            val group = Array(layers) { layer ->
                val b0 = rng.nextInt(2); val b1 = rng.nextInt(2)
                Complex(if (b0 == 0) 1.0 else -1.0, if (b1 == 0) 1.0 else -1.0) * (1.0 / sqrt(2.0))
            }
            for (layer in 0 until layers) {
                grid[layer][l][k] = group[layer]
                if (l != dmrsL || !dmrsSet.contains(k to l)) txRef += group[layer]
            }
        }
        for (r in dmrsResources) grid[r.port - 1000][r.symbol][r.subcarrier] = r.value

        val channel = NrChannelV11(NrChannelV11Config(tx, rx, prbs, cfg.snrDb, cfg.channelModel, cfg.seed xor 0x44))
        val received = channel.apply(grid, cfg.snrDb, 0x22)

        val hEst = estimateChannel(received, dmrsByPort, layers, rx, nSc)
        val hTruth = Array(nSc) { channel.frequencyResponse(it, nSc) }
        var hErr = 0.0
        var hPow = 0.0
        for (k in 0 until nSc) for (r in 0 until rx) for (t in 0 until layers) {
            val e = hTruth[k][r][t] - hEst[k][r * layers + t]
            hErr += e.abs2(); hPow += hTruth[k][r][t].abs2()
        }
        val channelError = sqrt(hErr / hPow.coerceAtLeast(1e-12)) * 100.0

        val ref = ArrayList<Complex>()
        val eq = ArrayList<Complex>()
        var condMax = 0.0
        var rankMin = layers
        var residual = 0.0
        var residualCount = 0
        for (l in 0 until symbols) for (k in 0 until nSc) {
            if (l == dmrsL && dmrsSet.contains(k to l)) continue
            val h = hEst[k]
            val y = Array(rx) { received[it][l][k] }
            val x = if (cfg.equalizer.uppercase() == "ZF") zf(h, y, layers) else mmse(h, y, layers, cfg.snrDb)
            val metrics = matrixMetrics(h, layers)
            rankMin = minOf(rankMin, metrics.first)
            condMax = maxOf(condMax, metrics.second)
            for (layer in 0 until layers) {
                ref += grid[layer][l][k]
                eq += x[layer]
            }
            var recon = Array(rx) { Complex(0.0, 0.0) }
            for (r in 0 until rx) for (t in 0 until layers) recon[r] += h[r * layers + t] * x[t]
            for (r in 0 until rx) residual += (y[r] - recon[r]).abs2()
            residualCount += rx
        }
        val evm = Dsp.evm(ref.toTypedArray(), eq.toTypedArray())
        val residualDb = 10.0 * log10((residual / residualCount.coerceAtLeast(1)).coerceAtLeast(1e-12))
        val pass = dmrsResources.isNotEmpty() && channelError < 45.0 && evm < 80.0 && rankMin >= minOf(layers, rx)
        val summary = channel.summary(nSc)
        return NrPhyV11Result(
            scs, prbs, nfft, dmrsL, dmrsResources.size, tx, rx, layers, summary.taps,
            channelError, evm, residualDb, rankMin, condMax, pass,
            "V11 reference chain: PDSCH-like QPSK resource grid, TS 38.211 type-1 DM-RS, frequency-selective MIMO channel, LS DM-RS estimation and MMSE/ZF equalization. Existing v1-v10 paths are preserved."
        )
    }

    private fun estimateChannel(
        rxGrid: Array<Array<Array<Complex>>>,
        byPort: Map<Int, List<NrDmrsResource>>,
        layers: Int,
        rx: Int,
        nSc: Int
    ): Array<Array<Complex>> {
        // H[k] is a flattened rx x layer matrix.
        val h = Array(nSc) { Array(rx * layers) { Complex(Double.NaN, Double.NaN) } }
        fun cdiv(a: Complex, b: Complex): Complex {
            val d = b.abs2().coerceAtLeast(1e-18)
            return a * b.conj() * (1.0 / d)
        }
        for (group in 0 until ((layers + 1) / 2)) {
            val p0 = 1000 + 2 * group
            val p1 = p0 + 1
            val r0 = byPort[p0].orEmpty().associateBy { it.subcarrier }
            val r1 = byPort[p1].orEmpty().associateBy { it.subcarrier }
            if (r0.isEmpty()) continue
            val positions = r0.keys.sorted()
            if (r1.isNotEmpty()) {
                for (a in positions) {
                    val b = a + 2
                    if (!r0.containsKey(b)) continue
                    val ra0 = r0.getValue(a).value
                    val rb0 = r0.getValue(b).value
                    val ra1 = r1.getValue(a).value
                    val rb1 = r1.getValue(b).value
                    val det = ra0 * rb1 - rb0 * ra1
                    for (r in 0 until rx) {
                        val ya = rxGrid[r][r0.getValue(a).symbol][a]
                        val yb = rxGrid[r][r0.getValue(b).symbol][b]
                        val h0 = cdiv(ya * rb1 - yb * ra1, det)
                        val h1 = cdiv(ra0 * yb - rb0 * ya, det)
                        if (p0 - 1000 < layers) {
                            h[a][r * layers + (p0 - 1000)] = h0
                            h[b][r * layers + (p0 - 1000)] = h0
                        }
                        if (p1 - 1000 < layers) {
                            h[a][r * layers + (p1 - 1000)] = h1
                            h[b][r * layers + (p1 - 1000)] = h1
                        }
                    }
                }
            } else {
                val layer = p0 - 1000
                if (layer >= layers) continue
                for (k in positions) for (r in 0 until rx) {
                    h[k][r * layers + layer] = cdiv(rxGrid[r][r0.getValue(k).symbol][k], r0.getValue(k).value)
                }
            }
        }
        // Frequency interpolation by nearest DM-RS estimate within the same comb.
        for (k in 0 until nSc) for (r in 0 until rx) for (t in 0 until layers) {
            val idx = r * layers + t
            if (!h[k][idx].re.isNaN()) continue
            val comb = if (t / 2 == 0) intArrayOf(0, 2) else intArrayOf(1, 3)
            val candidates = (0 until nSc).filter { it % 4 in comb && !h[it][idx].re.isNaN() }
            if (candidates.isNotEmpty()) {
                val c = candidates.minBy { kotlin.math.abs(it - k) }
                h[k][idx] = h[c][idx]
            } else {
                h[k][idx] = Complex(0.0, 0.0)
            }
        }
        return h
    }

    private fun mmse(h: Array<Complex>, y: Array<Complex>, layers: Int, snrDb: Double): Array<Complex> {
        val a = Array(layers) { Array(layers) { Complex(0.0, 0.0) } }
        val b = Array(layers) { Complex(0.0, 0.0) }
        val noise = 10.0.pow(-snrDb / 10.0).coerceAtLeast(1e-8)
        for (i in 0 until layers) for (j in 0 until layers) {
            var s = Complex(0.0, 0.0)
            for (r in y.indices) s += h[r * layers + i].conj() * h[r * layers + j]
            a[i][j] = s + if (i == j) Complex(noise, 0.0) else Complex(0.0, 0.0)
        }
        for (i in 0 until layers) {
            var s = Complex(0.0, 0.0)
            for (r in y.indices) s += h[r * layers + i].conj() * y[r]
            b[i] = s
        }
        return solve(a, b)
    }

    private fun zf(h: Array<Complex>, y: Array<Complex>, layers: Int): Array<Complex> {
        // h is flattened row-major rx x layers.
        val a = Array(layers) { Array(layers) { Complex(0.0, 0.0) } }
        val b = Array(layers) { Complex(0.0, 0.0) }
        for (i in 0 until layers) for (j in 0 until layers) {
            var s = Complex(0.0, 0.0)
            for (r in y.indices) s += h[r * layers + i].conj() * h[r * layers + j]
            a[i][j] = s + if (i == j) Complex(1e-8, 0.0) else Complex(0.0, 0.0)
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
        fun div(x: Complex, y: Complex): Complex { val d = y.abs2().coerceAtLeast(1e-18); return x * y.conj() * (1.0 / d) }
        for (c in 0 until n) {
            var pivot = c
            for (r in c + 1 until n) if (a[r][c].abs2() > a[pivot][c].abs2()) pivot = r
            if (pivot != c) { val t = a[c]; a[c] = a[pivot]; a[pivot] = t }
            val p = a[c][c]
            for (j in c until n + 1) a[c][j] = div(a[c][j], p)
            for (r in 0 until n) if (r != c) {
                val f = a[r][c]
                for (j in c until n + 1) a[r][j] = a[r][j] - f * a[c][j]
            }
        }
        return Array(n) { a[it][n] }
    }

    private fun matrixMetrics(h: Array<Complex>, layers: Int): Pair<Int, Double> {
        var rank = layers
        var maxDiag = 0.0
        var minDiag = Double.POSITIVE_INFINITY
        for (i in 0 until layers) {
            var d = 0.0
            for (r in 0 until h.size / layers) d += h[r * layers + i].abs2()
            maxDiag = maxOf(maxDiag, d); minDiag = minOf(minDiag, d)
            if (d < 1e-8) rank--
        }
        return rank to 10.0 * log10(maxDiag.coerceAtLeast(1e-12) / minDiag.coerceAtLeast(1e-12))
    }

    private fun nextPow2(x: Int): Int { var n = 1; while (n < x) n = n shl 1; return n }
}
