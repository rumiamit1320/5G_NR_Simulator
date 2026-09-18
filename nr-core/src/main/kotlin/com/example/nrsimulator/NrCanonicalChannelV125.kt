package com.example.nrsimulator

import kotlin.math.sqrt
import kotlin.random.Random

/** Additive channel-processing boundary: fractional-delay TDL convolution + deterministic AWGN. */
object NrCanonicalChannelV125 {
    data class Config(
        val sampleRateHz: Double = 30.72e6,
        val noiseVariance: Double = 0.0,
        val seed: Int = 12501,
        val fractionalDelay: Boolean = true
    )
    data class Result(
        val output: Array<Array<NrCanonicalSpatialEngine.C>>,
        val noiseVariance: Double,
        val appliedDelays: List<Double>
    )

    fun apply(
        input: Array<Array<NrCanonicalSpatialEngine.C>>,
        taps: List<NrCanonicalSpatialEngine.Tap>,
        config: Config = Config()
    ): Result {
        require(input.isNotEmpty() && input.all { it.isNotEmpty() })
        require(taps.isNotEmpty())
        require(config.sampleRateHz > 0.0 && config.noiseVariance >= 0.0)
        val tx = input.size
        val n = input[0].size
        val rx = taps.first().h.size
        val out = Array(rx) { Array(n) { NrCanonicalSpatialEngine.C(0.0, 0.0) } }
        val rng = Random(config.seed)
        for (tap in taps) {
            require(tap.h.size == rx && tap.h.all { it.size == tx })
            val d = tap.delay.toDouble()
            for (r in 0 until rx) for (t in 0 until tx) {
                val h = tap.h[r][t]
                for (i in 0 until n) {
                    val x = if (!config.fractionalDelay) {
                        val j = i - tap.delay
                        if (j in 0 until n) input[t][j] else NrCanonicalSpatialEngine.C(0.0, 0.0)
                    } else {
                        val j0 = kotlin.math.floor(i - d).toInt()
                        val f = (i - d) - j0
                        val x0 = if (j0 in 0 until n) input[t][j0] else NrCanonicalSpatialEngine.C(0.0, 0.0)
                        val x1 = if (j0 + 1 in 0 until n) input[t][j0 + 1] else NrCanonicalSpatialEngine.C(0.0, 0.0)
                        x0 * (1.0 - f) + x1 * f
                    }
                    out[r][i] = out[r][i] + NrCanonicalSpatialEngine.C(
                        x.re * h.re - x.im * h.im, x.re * h.im + x.im * h.re
                    )
                }
            }
        }
        if (config.noiseVariance > 0.0) {
            val sigma = sqrt(config.noiseVariance / 2.0)
            for (r in out.indices) for (i in out[r].indices) {
                out[r][i] = out[r][i] + NrCanonicalSpatialEngine.C(
                    gaussian(rng) * sigma, gaussian(rng) * sigma
                )
            }
        }
        return Result(out, config.noiseVariance, taps.map { it.delay.toDouble() })
    }

    private fun gaussian(rng: Random): Double {
        var u: Double
        var v: Double
        do { u = rng.nextDouble() } while (u <= 1e-15)
        do { v = rng.nextDouble() } while (v <= 1e-15)
        return sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2.0 * Math.PI * v)
    }
}
