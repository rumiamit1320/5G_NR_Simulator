package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Frequency-domain NR MIMO channel used by v11. */
data class NrChannelV11Config(
    val txAntennas: Int = 2,
    val rxAntennas: Int = 2,
    val prbs: Int = 52,
    val snrDb: Double = 15.0,
    val model: String = "FREQUENCY_SELECTIVE",
    val seed: Int = 0x5511
)

data class NrChannelV11Result(
    val txAntennas: Int,
    val rxAntennas: Int,
    val taps: Int,
    val rmsDelaySamples: Double,
    val meanPower: Double,
    val frequencySelectivityDb: Double,
    val note: String
)

class NrChannelV11(private val cfg: NrChannelV11Config) {
    private val tx = cfg.txAntennas.coerceIn(1, 4)
    private val rx = cfg.rxAntennas.coerceIn(1, 4)
    private val rng = Random(cfg.seed)
    private val delays = if (cfg.model.uppercase() == "FLAT") intArrayOf(0) else intArrayOf(0, 1, 3, 5)
    private val powersDb = if (cfg.model.uppercase() == "FLAT") doubleArrayOf(0.0) else doubleArrayOf(0.0, -2.5, -6.0, -9.0)
    private val taps = Array(rx) { Array(tx) { Array(delays.size) { Complex(0.0, 0.0) } } }

    init {
        for (r in 0 until rx) for (t in 0 until tx) {
            var p = 0.0
            for (i in delays.indices) {
                val lin = 10.0.pow10(powersDb[i] / 10.0)
                val s = sqrt(lin / 2.0)
                taps[r][t][i] = Complex(gaussian() * s, gaussian() * s)
                p += lin
            }
            val norm = sqrt(p.coerceAtLeast(1e-12))
            for (i in delays.indices) taps[r][t][i] = taps[r][t][i] * (1.0 / norm)
        }
    }

    fun frequencyResponse(subcarrier: Int, nSubcarriers: Int): Array<Array<Complex>> {
        val out = Array(rx) { Array(tx) { Complex(0.0, 0.0) } }
        val centered = subcarrier - nSubcarriers / 2.0
        for (r in 0 until rx) for (t in 0 until tx) {
            var h = Complex(0.0, 0.0)
            for (i in delays.indices) {
                val phase = -2.0 * PI * centered * delays[i] / nSubcarriers
                h += taps[r][t][i] * Complex(cos(phase), sin(phase))
            }
            out[r][t] = h
        }
        return out
    }

    fun apply(
        txGrid: Array<Array<Array<Complex>>>,
        snrDb: Double,
        seedOffset: Int = 0
    ): Array<Array<Array<Complex>>> {
        val symbols = txGrid[0].size
        val nSc = txGrid[0][0].size
        val random = Random(cfg.seed xor seedOffset)
        val out = Array(rx) { Array(symbols) { Array(nSc) { Complex(0.0, 0.0) } } }
        for (r in 0 until rx) for (l in 0 until symbols) for (k in 0 until nSc) {
            val h = frequencyResponse(k, nSc)
            var y = Complex(0.0, 0.0)
            for (t in 0 until tx) y += h[r][t] * txGrid[t][l][k]
            out[r][l][k] = addNoise(y, snrDb, random, txGrid, l, k)
        }
        return out
    }

    fun summary(nSc: Int): NrChannelV11Result {
        var p = 0.0
        var maxP = 0.0
        var minP = Double.POSITIVE_INFINITY
        var n = 0
        for (k in 0 until nSc) {
            val h = frequencyResponse(k, nSc)
            for (r in 0 until rx) for (t in 0 until tx) {
                val q = h[r][t].abs2(); p += q; maxP = maxOf(maxP, q); minP = minOf(minP, q); n++
            }
        }
        val mean = p / n.coerceAtLeast(1)
        return NrChannelV11Result(tx, rx, delays.size,
            sqrt(delays.map { it.toDouble() * it }.average()), mean,
            10.0 * kotlin.math.log10(maxP.coerceAtLeast(1e-12) / minP.coerceAtLeast(1e-12)),
            "Frequency-selective tapped-delay-line channel with configurable MIMO dimensions. Channel response is evaluated per occupied subcarrier so V10 OFDM can attach to this layer."
        )
    }

    private fun gaussian(): Double {
        var u = 0.0
        var v = 0.0
        while (u == 0.0) u = rng.nextDouble()
        v = rng.nextDouble()
        return sqrt(-2.0 * kotlin.math.ln(u)) * cos(2.0 * PI * v)
    }

    private fun addNoise(y: Complex, snrDb: Double, random: Random, txGrid: Array<Array<Array<Complex>>>, l: Int, k: Int): Complex {
        var p = 0.0
        var count = 0
        for (t in txGrid.indices) { p += txGrid[t][l][k].abs2(); count++ }
        val np = p / count.coerceAtLeast(1) / 10.0.pow10(snrDb / 10.0)
        val s = sqrt(np / 2.0)
        fun g(): Double { var u = 0.0; while (u == 0.0) u = random.nextDouble(); val v = random.nextDouble(); return sqrt(-2.0 * kotlin.math.ln(u)) * cos(2.0 * PI * v) }
        return y + Complex(g() * s, g() * s)
    }

    private fun Double.pow10(x: Double): Double = 10.0.pow(x)
}
