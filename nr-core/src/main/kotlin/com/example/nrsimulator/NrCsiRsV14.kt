package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * NR v14 additive CSI-RS / CSI measurement reference layer.
 *
 * V1-v13 remain intact. This layer adds a CSI acquisition path on top of the
 * existing V11 frequency-domain MIMO channel:
 *
 * NZP CSI-RS reference resources -> MIMO channel sounding -> LS channel
 * estimate -> RSRP/SINR -> rank/condition estimate -> reference PMI/CQI.
 *
 * The resource pattern is deliberately a compact reference pattern (comb-4,
 * time-orthogonal ports) rather than claiming full 38.211 CSI-RS mapping
 * conformance. The resulting measurements feed the later link-adaptation and
 * codebook work without changing the existing PHY architecture.
 */

data class NrCsiRsV14Config(
    val prbs: Int = 52,
    val txAntennas: Int = 2,
    val rxAntennas: Int = 2,
    val csiPorts: Int = 2,
    val symbols: Int = 14,
    val csiSymbols: IntArray = intArrayOf(4, 8, 12),
    val freqDensity: Int = 4,
    val snrDb: Double = 20.0,
    val channelModel: String = "FREQUENCY_SELECTIVE",
    val seed: Int = 0x1401
)

data class NrCsiRsResourceV14(
    val port: Int,
    val symbol: Int,
    val subcarrier: Int,
    val value: Complex
)

data class NrCsiRsV14Result(
    val prbs: Int,
    val txAntennas: Int,
    val rxAntennas: Int,
    val csiPorts: Int,
    val resources: Int,
    val rsrpDb: Double,
    val sinrDb: Double,
    val rank: Int,
    val conditionNumberDb: Double,
    val cqi: Int,
    val pmi: Int,
    val channelErrorPercent: Double,
    val pass: Boolean,
    val note: String
)

class NrCsiRsV14 {
    fun run(cfg: NrCsiRsV14Config): NrCsiRsV14Result {
        val prbs = cfg.prbs.coerceIn(1, 275)
        val tx = cfg.txAntennas.coerceIn(1, 4)
        val rx = cfg.rxAntennas.coerceIn(1, 4)
        val ports = minOf(cfg.csiPorts.coerceIn(1, 4), tx)
        val nSc = prbs * 12
        val density = cfg.freqDensity.coerceIn(2, 12)
        val csiSymbols = cfg.csiSymbols.filter { it in 0 until cfg.symbols.coerceIn(1, 14) }.ifEmpty { listOf(4) }
        val resources = generateResources(nSc, csiSymbols, density, ports, cfg.seed)
        val channel = NrChannelV11(NrChannelV11Config(tx, rx, prbs, cfg.snrDb, cfg.channelModel, cfg.seed xor 0x55))
        val rng = Random(cfg.seed xor 0x66)

        val estimates = Array(nSc) { Array(rx) { Array(ports) { Complex(0.0, 0.0) } } }
        val truth = Array(nSc) { channel.frequencyResponse(it, nSc) }
        var rxPower = 0.0
        var noisePower = 0.0
        var errPower = 0.0
        var truthPower = 0.0
        var count = 0

        for (r in resources) {
            val h = truth[r.subcarrier]
            for (rr in 0 until rx) {
                val y = h[rr][r.port - 3000] * r.value
                val np = 1.0 / 10.0.pow(cfg.snrDb / 10.0)
                val s = sqrt(np / 2.0)
                val noise = Complex(gaussian(rng) * s, gaussian(rng) * s)
                val yn = y + noise
                val est = yn * r.value.conj() * (1.0 / r.value.abs2().coerceAtLeast(1e-12))
                estimates[r.subcarrier][rr][r.port - 3000] = est
                rxPower += yn.abs2()
                noisePower += noise.abs2()
                errPower += (est - h[rr][r.port - 3000]).abs2()
                truthPower += h[rr][r.port - 3000].abs2()
                count++
            }
        }

        // Interpolate each measured CSI-RS subcarrier estimate across the band.
        val filled = Array(nSc) { Array(rx) { Array(ports) { Complex(0.0, 0.0) } } }
        for (k in 0 until nSc) {
            for (p in 0 until ports) {
                val portId = 3000 + p
                val nearest = nearestMeasuredSubcarrier(k, resources.filter { it.port == portId })
                for (rr in 0 until rx) filled[k][rr][p] = estimates[nearest][rr][p]
            }
        }

        var hNorm = 0.0
        var hErr = 0.0
        var minSv = Double.POSITIVE_INFINITY
        var maxSv = 0.0
        var rankVotes = 0
        for (k in 0 until nSc) {
            val h = filled[k]
            val svals = singularValues(h, rx, ports)
            if (svals.isNotEmpty()) {
                maxSv = maxOf(maxSv, svals[0])
                minSv = minOf(minSv, svals.last())
                val localRank = svals.count { it > svals[0] * 1e-2 }
                rankVotes += localRank
            }
            val ht = truth[k]
            for (rr in 0 until rx) for (p in 0 until ports) {
                hNorm += ht[rr][p].abs2()
                hErr += (h[rr][p] - ht[rr][p]).abs2()
            }
        }
        val rank = (rankVotes.toDouble() / nSc.coerceAtLeast(1)).roundToIntCompat().coerceIn(1, minOf(rx, ports))
        val condDb = 20.0 * log10(maxSv.coerceAtLeast(1e-12) / minSv.coerceAtLeast(1e-12))
        val rsrp = 10.0 * log10((truthPower / count.coerceAtLeast(1)).coerceAtLeast(1e-12))
        val sinr = 10.0 * log10((rxPower - noisePower).coerceAtLeast(1e-12) / noisePower.coerceAtLeast(1e-12))
        val cqi = referenceCqi(sinr, rank)
        val pmi = referencePmi(filled[nSc / 2], rx, ports)
        val chErr = sqrt(hErr / hNorm.coerceAtLeast(1e-12)) * 100.0
        val pass = resources.isNotEmpty() && chErr < 20.0 && sinr.isFinite() && rank >= 1

        return NrCsiRsV14Result(
            prbs, tx, rx, ports, resources.size, rsrp, sinr, rank,
            condDb, cqi, pmi, chErr, pass,
            "V14 adds an NZP CSI-RS sounding/reference pattern, LS channel estimation, RSRP/SINR, rank, reference PMI and CQI. The CSI-RS pattern is a compact comb-4/time-orthogonal reference implementation; full 38.211 CSI-RS density/row mapping and 38.214 report quantization are reserved for conformance work."
        )
    }

    fun generateResources(nSc: Int, symbols: List<Int>, density: Int, ports: Int, seed: Int): List<NrCsiRsResourceV14> {
        val out = ArrayList<NrCsiRsResourceV14>()
        for (p in 0 until ports) {
            val l = symbols[p % symbols.size]
            val seq = goldComplex(((nSc + density - 1) / density) + 8, seed + p * 17)
            var q = 0
            for (k in p % density until nSc step density) {
                out += NrCsiRsResourceV14(3000 + p, l, k, seq[q++ % seq.size])
            }
        }
        return out
    }

    private fun nearestMeasuredSubcarrier(k: Int, resources: List<NrCsiRsResourceV14>): Int {
        var best = resources.firstOrNull()?.subcarrier ?: 0
        var d = kotlin.math.abs(best - k)
        for (r in resources) {
            val nd = kotlin.math.abs(r.subcarrier - k)
            if (nd < d) { d = nd; best = r.subcarrier }
        }
        return best
    }

    private fun singularValues(h: Array<Array<Complex>>, rows: Int, cols: Int): DoubleArray {
        val gram = Array(cols) { Array(cols) { Complex(0.0, 0.0) } }
        for (i in 0 until cols) for (j in 0 until cols) {
            var s = Complex(0.0, 0.0)
            for (r in 0 until rows) s += h[r][i].conj() * h[r][j]
            gram[i][j] = s
        }
        val vals = hermitianEigenvalues(gram)
        return vals.map { sqrt(it.coerceAtLeast(0.0)) }.sortedDescending().toDoubleArray()
    }

    // Jacobi diagonalization of the real symmetric representation of a small
    // Hermitian matrix. This avoids pulling in a matrix library and supports
    // the 1..4 antenna configurations used by the existing simulator.
    private fun hermitianEigenvalues(a: Array<Array<Complex>>): DoubleArray {
        val n = a.size
        val m = Array(2 * n) { DoubleArray(2 * n) }
        for (i in 0 until n) for (j in 0 until n) {
            m[i][j] = a[i][j].re
            m[i][j + n] = -a[i][j].im
            m[i + n][j] = a[i][j].im
            m[i + n][j + n] = a[i][j].re
        }
        repeat(60) {
            var p = 0; var q = 1; var max = 0.0
            for (i in 0 until 2 * n) for (j in i + 1 until 2 * n) if (kotlin.math.abs(m[i][j]) > max) { max = kotlin.math.abs(m[i][j]); p = i; q = j }
            if (max < 1e-10) return@repeat
            val phi = 0.5 * kotlin.math.atan2(2.0 * m[p][q], m[q][q] - m[p][p])
            val c = cos(phi); val s = sin(phi)
            for (k in 0 until 2 * n) {
                val mpk = m[p][k]; val mqk = m[q][k]
                m[p][k] = c * mpk - s * mqk
                m[q][k] = s * mpk + c * mqk
            }
            for (k in 0 until 2 * n) {
                val mkp = m[k][p]; val mkq = m[k][q]
                m[k][p] = c * mkp - s * mkq
                m[k][q] = s * mkp + c * mkq
            }
        }
        val all = DoubleArray(2 * n) { m[it][it] }.sortedArrayDescending()
        // The real representation duplicates every eigenvalue twice.
        return DoubleArray(n) { i -> all[2 * i] }
    }

    private fun referenceCqi(sinrDb: Double, rank: Int): Int {
        val thresholds = doubleArrayOf(-10.0, -7.0, -4.0, -1.0, 2.0, 5.0, 8.0, 11.0, 14.0, 17.0, 20.0, 23.0, 26.0, 29.0, 32.0, 35.0)
        var q = 0
        for (i in thresholds.indices) if (sinrDb >= thresholds[i]) q = i
        return (q + (rank - 1).coerceIn(0, 2)).coerceIn(0, 15)
    }

    private fun referencePmi(h: Array<Array<Complex>>, rows: Int, cols: Int): Int {
        if (cols < 2 || rows < 1) return 0
        val phases = doubleArrayOf(0.0, PI / 2.0, PI, 3.0 * PI / 2.0)
        var best = 0; var bestP = Double.NEGATIVE_INFINITY
        for (i in phases.indices) {
            val w = Array(cols) { p -> if (p == 0) Complex(1.0 / sqrt(2.0), 0.0) else Complex(cos(phases[i]) / sqrt(2.0), sin(phases[i]) / sqrt(2.0)) }
            var pwr = 0.0
            for (r in 0 until rows) {
                var y = Complex(0.0, 0.0)
                for (c in 0 until minOf(cols, 2)) y += h[r][c] * w[c]
                pwr += y.abs2()
            }
            if (pwr > bestP) { bestP = pwr; best = i }
        }
        return best
    }

    private fun goldComplex(count: Int, seed: Int): Array<Complex> {
        val n = 1600 + count * 2 + 31
        val x1 = IntArray(n)
        val x2 = IntArray(n)
        x1[0] = 1
        val init = (seed.toLong() and 0x7fffffffL) or 1L
        for (i in 0 until 31) x2[i] = ((init ushr i) and 1L).toInt()
        for (i in 31 until n) {
            x1[i] = x1[i - 28] xor x1[i - 31]
            x2[i] = x2[i - 28] xor x2[i - 29] xor x2[i - 30] xor x2[i - 31]
        }
        return Array(count) { i ->
            val b0 = x1[1600 + 2 * i] xor x2[1600 + 2 * i]
            val b1 = x1[1600 + 2 * i + 1] xor x2[1600 + 2 * i + 1]
            Complex((1 - 2 * b0) / sqrt(2.0), (1 - 2 * b1) / sqrt(2.0))
        }
    }

    private fun gaussian(r: Random): Double {
        var u = 0.0
        while (u == 0.0) u = r.nextDouble()
        val v = r.nextDouble()
        return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * v)
    }

    private fun Int.roundToIntCompat(): Int = kotlin.math.round(toDouble()).toInt()
    private fun Double.roundToIntCompat(): Int = kotlin.math.round(this).toInt()
}
