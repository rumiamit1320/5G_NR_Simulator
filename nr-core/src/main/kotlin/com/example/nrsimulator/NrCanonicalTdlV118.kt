package com.example.nrsimulator

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * V120-upgraded canonical TDL channel implementation.
 *
 * The profile tables are the TDL-A..E tables in 3GPP TR 38.901 v19.3.0,
 * Section 7.7.2. Delays are scaled using Section 7.7.3 and represented on
 * the simulator sample grid. TDL-D/E implement the specified Ricean first tap.
 * The existing canonical spatial engine remains the waveform/MIMO backend.
 */
object NrCanonicalTdlV118 {
    enum class Profile { TDL_A, TDL_B, TDL_C, TDL_D, TDL_E }
    enum class Fading { RAYLEIGH, RICEAN_FIRST_TAP }

    data class Path(
        val normalizedDelay: Double,
        val delayNs: Double,
        val delaySamplesExact: Double,
        val delaySamples: Int,
        val powerLinear: Double
    )

    data class Config(
        val profile: Profile = Profile.TDL_A,
        val txAntennas: Int = 1,
        val rxAntennas: Int = 1,
        val sampleRateHz: Double = 30.72e6,
        val rmsDelayNs: Double = 30.0,
        val dopplerHz: Double = 0.0,
        val seed: Int = 11801,
        val kFactorDb: Double? = null,
        val timeSeconds: Double = 0.0
    )

    data class Result(
        val taps: List<NrCanonicalSpatialEngine.Tap>,
        val paths: List<Path>,
        val normalizedPower: Double,
        val maxDelaySamples: Int,
        val frequencySelective: Boolean,
        val dopplerHz: Double,
        val profile: Profile,
        val fading: Fading,
        val kFactorDb: Double?
    )

    private data class RawPath(val normalizedDelay: Double, val powerDb: Double)

    /** Exact Section 7.7.2 tables. The D/E zero-delay diffuse term is represented
     * by the Ricean first-tap model rather than as a separate discrete tap. */
    private fun raw(profile: Profile): List<RawPath> = when (profile) {
        Profile.TDL_A -> listOf(0.0000 to -13.4, 0.3819 to 0.0, 0.4025 to -2.2, 0.5868 to -4.0, 0.4610 to -6.0, 0.5375 to -8.2, 0.6708 to -9.9, 0.5750 to -10.5, 0.7618 to -7.5, 1.5375 to -15.9, 1.8978 to -6.6, 2.2242 to -16.7, 2.1718 to -12.4, 2.4942 to -15.2, 2.5119 to -10.8, 3.0582 to -11.3, 4.0810 to -12.7, 4.4579 to -16.2, 4.5695 to -18.3, 4.7966 to -18.9, 5.0066 to -16.6, 5.3043 to -19.9, 9.6586 to -29.7).map { RawPath(it.first, it.second) }
        Profile.TDL_B -> listOf(0.0000 to 0.0, 0.1072 to -2.2, 0.2155 to -4.0, 0.2095 to -3.2, 0.2870 to -9.8, 0.2986 to -1.2, 0.3752 to -3.4, 0.5055 to -5.2, 0.3681 to -7.6, 0.3697 to -3.0, 0.5700 to -8.9, 0.5283 to -9.0, 1.1021 to -4.8, 1.2756 to -5.7, 1.5474 to -7.5, 1.7842 to -1.9, 2.0169 to -7.6, 2.8294 to -12.2, 3.0219 to -9.8, 3.6187 to -11.4, 4.1067 to -14.9, 4.2790 to -9.2, 4.7834 to -11.3).map { RawPath(it.first, it.second) }
        Profile.TDL_C -> listOf(0.0000 to -4.4, 0.2099 to -1.2, 0.2219 to -3.5, 0.2329 to -5.2, 0.2176 to -2.5, 0.6366 to 0.0, 0.6448 to -2.2, 0.6560 to -3.9, 0.6584 to -7.4, 0.7935 to -7.1, 0.8213 to -10.7, 0.9336 to -11.1, 1.2285 to -5.1, 1.3083 to -6.8, 2.1704 to -8.7, 2.7105 to -13.2, 4.2589 to -13.9, 4.6003 to -13.9, 5.4902 to -15.8, 5.6077 to -17.1, 6.3065 to -16.0, 6.6374 to -15.7, 7.0427 to -21.6, 8.6523 to -22.8).map { RawPath(it.first, it.second) }
        Profile.TDL_D -> listOf(0.0000 to -0.2, 0.0350 to -18.8, 0.6120 to -21.0, 1.3630 to -22.8, 1.4050 to -17.9, 1.8040 to -20.1, 2.5960 to -21.9, 1.7750 to -22.9, 4.0420 to -27.8, 7.9370 to -23.6, 9.4240 to -24.8, 9.7080 to -30.0, 12.5250 to -27.7).map { RawPath(it.first, it.second) }
        Profile.TDL_E -> listOf(0.0000 to -0.03, 0.5133 to -15.8, 0.5440 to -18.1, 0.5630 to -19.8, 0.5440 to -22.9, 0.7112 to -22.4, 1.9092 to -18.6, 1.9293 to -20.8, 1.9589 to -22.6, 2.6426 to -22.3, 3.7136 to -25.6, 5.4524 to -20.2, 12.0034 to -29.8, 20.6519 to -29.2).map { RawPath(it.first, it.second) }
    }

    fun profilePaths(profile: Profile): List<Path> = buildPaths(profile, 30.0, 30.72e6)

    private fun buildPaths(profile: Profile, rmsDelayNs: Double, sampleRateHz: Double): List<Path> {
        require(rmsDelayNs >= 0.0 && rmsDelayNs.isFinite())
        val samplePeriodNs = 1.0e9 / sampleRateHz
        val base = raw(profile)
        val norm = base.sumOf { dbToLinear(it.powerDb) }.coerceAtLeast(1e-18)
        return base.map { p ->
            val delayNs = p.normalizedDelay * rmsDelayNs
            val exact = delayNs / samplePeriodNs
            Path(p.normalizedDelay, delayNs, exact, kotlin.math.round(exact).toInt().coerceAtLeast(0), dbToLinear(p.powerDb) / norm)
        }
    }

    fun defaultKFactorDb(profile: Profile): Double = when (profile) {
        Profile.TDL_D -> 13.3
        Profile.TDL_E -> 22.0
        else -> 0.0
    }

    fun build(config: Config = Config()): Result {
        require(config.txAntennas > 0 && config.rxAntennas > 0)
        require(config.sampleRateHz > 0.0 && config.sampleRateHz.isFinite())
        require(config.rmsDelayNs >= 0.0 && config.rmsDelayNs.isFinite())
        require(config.dopplerHz >= 0.0 && config.dopplerHz.isFinite())
        require(config.timeSeconds.isFinite())
        val paths = buildPaths(config.profile, config.rmsDelayNs, config.sampleRateHz)
        val fading = if (config.profile == Profile.TDL_D || config.profile == Profile.TDL_E) Fading.RICEAN_FIRST_TAP else Fading.RAYLEIGH
        val kDb = if (fading == Fading.RICEAN_FIRST_TAP) (config.kFactorDb ?: defaultKFactorDb(config.profile)) else null
        require(kDb == null || (kDb.isFinite() && kDb >= 0.0))
        val random = Random(config.seed)
        val fdPhase = 2.0 * Math.PI * config.dopplerHz * config.timeSeconds
        val taps = paths.mapIndexed { index, p ->
            val amp = sqrt(p.powerLinear)
            val h = Array(config.rxAntennas) { r -> Array(config.txAntennas) { t ->
                val phase = 2.0 * Math.PI * random.nextDouble() + fdPhase * (0.7 + 0.3 * index.toDouble() / paths.size.coerceAtLeast(1))
                val spatial = if (r == t) 1.0 else 0.15
                if (index == 0 && fading == Fading.RICEAN_FIRST_TAP) {
                    val k = 10.0.pow(kDb!! / 10.0)
                    val los = sqrt(k / (k + 1.0))
                    val diffuse = sqrt(1.0 / (k + 1.0))
                    NrCanonicalSpatialEngine.C(amp * spatial * (los + diffuse * cos(phase)), amp * spatial * diffuse * sin(phase))
                } else {
                    NrCanonicalSpatialEngine.C(amp * spatial * cos(phase), amp * spatial * sin(phase))
                }
            } }
            NrCanonicalSpatialEngine.Tap(p.delaySamples, h)
        }
        return Result(taps, paths, paths.sumOf { it.powerLinear }, paths.maxOf { it.delaySamples }, paths.map { it.delaySamples }.distinct().size > 1, config.dopplerHz, config.profile, fading, kDb)
    }

    fun frequencyResponse(config: Config, fftSize: Int): Array<Array<Array<NrCanonicalSpatialEngine.C>>> {
        require(fftSize > 0)
        return NrCanonicalSpatialEngine.frequencyResponse(build(config).taps, fftSize)
    }

    private fun dbToLinear(db: Double): Double = 10.0.pow(db / 10.0)
    private fun Double.pow(x: Double): Double = Math.pow(this, x)
}
