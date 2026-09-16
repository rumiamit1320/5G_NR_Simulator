package com.example.nrsimulator

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * V118 additive canonical frequency-selective TDL channel profiles.
 *
 * Provides deterministic research-grade TDL-A..E profile plumbing while reusing
 * NrCanonicalSpatialEngine for the actual MIMO convolution and frequency response.
 * This is not a claim of complete 3GPP TR 38.901 conformance; the profiles are
 * explicitly versioned so normative refinements can be added without changing
 * historical APIs.
 */
object NrCanonicalTdlV118 {
    enum class Profile { TDL_A, TDL_B, TDL_C, TDL_D, TDL_E }

    data class Path(val delaySamples: Int, val powerLinear: Double)
    data class Config(
        val profile: Profile = Profile.TDL_A,
        val txAntennas: Int = 1,
        val rxAntennas: Int = 1,
        val sampleRateHz: Double = 30.72e6,
        val rmsDelayNs: Double = 30.0,
        val dopplerHz: Double = 0.0,
        val seed: Int = 11801
    )
    data class Result(
        val taps: List<NrCanonicalSpatialEngine.Tap>,
        val paths: List<Path>,
        val normalizedPower: Double,
        val maxDelaySamples: Int,
        val frequencySelective: Boolean,
        val dopplerHz: Double,
        val profile: Profile
    )

    fun profilePaths(profile: Profile): List<Path> = when (profile) {
        Profile.TDL_A -> listOf(0 to 0.0, 1 to -2.2, 2 to -4.0, 4 to -7.0, 7 to -10.0)
        Profile.TDL_B -> listOf(0 to 0.0, 1 to -0.5, 2 to -1.8, 4 to -4.5, 7 to -7.8, 11 to -11.0)
        Profile.TDL_C -> listOf(0 to -0.2, 1 to -1.0, 3 to -2.5, 6 to -4.5, 10 to -7.0, 15 to -10.5)
        Profile.TDL_D -> listOf(0 to 0.0, 1 to -1.0, 2 to -2.0, 5 to -4.0, 9 to -7.0, 14 to -11.0)
        Profile.TDL_E -> listOf(0 to 0.0, 1 to -0.7, 3 to -2.0, 6 to -3.5, 10 to -6.0, 16 to -9.0, 24 to -13.0)
    }.map { (d, db) -> Path(d, 10.0.pow(db / 10.0)) }

    fun build(config: Config = Config()): Result {
        require(config.txAntennas > 0 && config.rxAntennas > 0)
        require(config.sampleRateHz > 0.0 && config.sampleRateHz.isFinite())
        require(config.rmsDelayNs >= 0.0 && config.rmsDelayNs.isFinite())
        require(config.dopplerHz >= 0.0 && config.dopplerHz.isFinite())
        val base = profilePaths(config.profile)
        val norm = base.sumOf { it.powerLinear }.coerceAtLeast(1e-18)
        val paths = base.map { it.copy(powerLinear = it.powerLinear / norm) }
        val random = Random(config.seed)
        val taps = paths.mapIndexed { index, p ->
            val amp = sqrt(p.powerLinear)
            val h = Array(config.rxAntennas) { r -> Array(config.txAntennas) { t ->
                val phase = 2.0 * Math.PI * random.nextDouble() +
                    2.0 * Math.PI * config.dopplerHz * index.toDouble() / config.sampleRateHz
                val spatial = if (r == t) 1.0 else 0.15
                NrCanonicalSpatialEngine.C(
                    amp * spatial * cos(phase),
                    amp * spatial * sin(phase)
                )
            } }
            NrCanonicalSpatialEngine.Tap(p.delaySamples, h)
        }
        val maxDelay = paths.maxOf { it.delaySamples }
        return Result(taps, paths, paths.sumOf { it.powerLinear }, maxDelay, paths.size > 1, config.dopplerHz, config.profile)
    }

    fun frequencyResponse(config: Config, fftSize: Int): Array<Array<Array<NrCanonicalSpatialEngine.C>>> {
        require(fftSize > 0)
        val r = build(config)
        return NrCanonicalSpatialEngine.frequencyResponse(r.taps, fftSize)
    }

    private fun Double.pow(x: Double): Double = Math.pow(this, x)
}
