package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * V121 additive time-varying TDL layer.
 *
 * Keeps V118/V120 public APIs unchanged and generates successive channel
 * realizations using a deterministic sum-of-sinusoids approximation of the
 * classical Jakes Doppler process. TDL-D/E retain a Ricean first tap with the
 * TR 38.901 LOS spectral component at 0.7*fD.
 *
 * This is a reproducible link-level Jakes approximation, not a claim of full
 * stochastic 3GPP channel-model conformance.
 */
object NrCanonicalTdlV121 {
    data class Config(
        val tdl: NrCanonicalTdlV118.Config = NrCanonicalTdlV118.Config(),
        val oscillators: Int = 32,
        val timeStepSeconds: Double = 1.0e-4
    )

    data class SequenceResult(
        val realizations: List<NrCanonicalTdlV118.Result>,
        val timeStepSeconds: Double
    )

    fun buildAtTime(
        config: Config = Config(),
        timeSeconds: Double
    ): NrCanonicalTdlV118.Result {
        validate(config, timeSeconds)

        val base = config.tdl.copy(timeSeconds = timeSeconds)
        val paths = NrCanonicalTdlV118.profilePaths(base.profile)
        val fading = if (base.profile == NrCanonicalTdlV118.Profile.TDL_D ||
            base.profile == NrCanonicalTdlV118.Profile.TDL_E) {
            NrCanonicalTdlV118.Fading.RICEAN_FIRST_TAP
        } else {
            NrCanonicalTdlV118.Fading.RAYLEIGH
        }
        val kDb = if (fading == NrCanonicalTdlV118.Fading.RICEAN_FIRST_TAP) {
            base.kFactorDb ?: NrCanonicalTdlV118.defaultKFactorDb(base.profile)
        } else {
            null
        }
        val random = Random(base.seed)
        val taps = paths.mapIndexed { pathIndex, path ->
            val amplitude = sqrt(path.powerLinear)
            val h = Array(base.rxAntennas) { rx ->
                Array(base.txAntennas) { tx ->
                    val spatial = if (rx == tx) 1.0 else 0.15
                    val linkSeed = random.nextInt()
                    val diffuse = jakesComplex(
                        dopplerHz = base.dopplerHz,
                        timeSeconds = timeSeconds,
                        oscillators = config.oscillators,
                        seed = mixSeed(linkSeed, pathIndex, rx, tx)
                    )
                    val fadingSample = if (pathIndex == 0 && fading == NrCanonicalTdlV118.Fading.RICEAN_FIRST_TAP) {
                        val k = dbToLinear(kDb ?: 0.0)
                        val losPhase = deterministicPhase(mixSeed(linkSeed, 0x4f5343, pathIndex, rx * 31 + tx))
                        val los = NrCanonicalSpatialEngine.C(
                            cos(2.0 * PI * 0.7 * base.dopplerHz * timeSeconds + losPhase),
                            kotlin.math.sin(2.0 * PI * 0.7 * base.dopplerHz * timeSeconds + losPhase)
                        )
                        val losScale = sqrt(k / (k + 1.0))
                        val diffuseScale = sqrt(1.0 / (k + 1.0))
                        NrCanonicalSpatialEngine.C(
                            los.re * losScale + diffuse.re * diffuseScale,
                            los.im * losScale + diffuse.im * diffuseScale
                        )
                    } else {
                        diffuse
                    }
                    NrCanonicalSpatialEngine.C(
                        amplitude * spatial * fadingSample.re,
                        amplitude * spatial * fadingSample.im
                    )
                }
            }
            NrCanonicalSpatialEngine.Tap(path.delaySamples, h)
        }

        return NrCanonicalTdlV118.Result(
            taps = taps,
            paths = paths,
            normalizedPower = paths.sumOf { it.powerLinear },
            maxDelaySamples = paths.maxOf { it.delaySamples },
            frequencySelective = paths.map { it.delaySamples }.distinct().size > 1,
            dopplerHz = base.dopplerHz,
            profile = base.profile,
            fading = fading,
            kFactorDb = kDb
        )
    }

    /** Generate deterministic successive channel realizations without mutable channel state. */
    fun buildSequence(
        config: Config = Config(),
        sampleCount: Int,
        startTimeSeconds: Double = config.tdl.timeSeconds
    ): SequenceResult {
        require(sampleCount >= 0) { "sampleCount must be >= 0" }
        validate(config, startTimeSeconds)
        val realizations = (0 until sampleCount).map { index ->
            buildAtTime(config, startTimeSeconds + index * config.timeStepSeconds)
        }
        return SequenceResult(realizations, config.timeStepSeconds)
    }

    fun frequencyResponseAtTime(
        config: Config = Config(),
        fftSize: Int,
        timeSeconds: Double
    ): Array<Array<Array<NrCanonicalSpatialEngine.C>>> {
        require(fftSize > 0) { "fftSize must be > 0" }
        return NrCanonicalSpatialEngine.frequencyResponse(buildAtTime(config, timeSeconds).taps, fftSize)
    }

    private fun jakesComplex(
        dopplerHz: Double,
        timeSeconds: Double,
        oscillators: Int,
        seed: Int
    ): NrCanonicalSpatialEngine.C {
        if (dopplerHz == 0.0) {
            val phase = deterministicPhase(seed)
            return NrCanonicalSpatialEngine.C(cos(phase), kotlin.math.sin(phase))
        }

        val random = Random(seed)
        var re = 0.0
        var im = 0.0
        val n = oscillators
        val scale = 1.0 / sqrt(n.toDouble())
        for (i in 0 until n) {
            // Deterministic half-bin angular sampling approximates the
            // classical Jakes U-shaped Doppler spectrum with f = fD*cos(theta).
            val theta = PI * (i + 0.5) / n.toDouble()
            val frequency = dopplerHz * cos(theta)
            val phase0 = 2.0 * PI * random.nextDouble()
            val phase = 2.0 * PI * frequency * timeSeconds + phase0
            re += cos(phase)
            im += kotlin.math.sin(phase)
        }
        return NrCanonicalSpatialEngine.C(re * scale, im * scale)
    }

    private fun validate(config: Config, timeSeconds: Double) {
        require(config.oscillators >= 8) { "oscillators must be >= 8" }
        require(config.timeStepSeconds >= 0.0 && config.timeStepSeconds.isFinite())
        require(timeSeconds.isFinite())
        require(config.tdl.dopplerHz >= 0.0 && config.tdl.dopplerHz.isFinite())
    }

    private fun deterministicPhase(seed: Int): Double =
        2.0 * PI * Random(seed).nextDouble()

    private fun mixSeed(seed: Int, a: Int, b: Int, c: Int): Int {
        var x = seed xor (a * 0x9E3779B9.toInt())
        x = x xor (b * 0x85EBCA6B.toInt())
        x = x xor (c * 0xC2B2AE35.toInt())
        x = x xor (x ushr 16)
        x *= 0x7FEB352D
        x = x xor (x ushr 15)
        x *= 0x846CA68B.toInt()
        return x xor (x ushr 16)
    }

    private fun dbToLinear(db: Double): Double = Math.pow(10.0, db / 10.0)
}