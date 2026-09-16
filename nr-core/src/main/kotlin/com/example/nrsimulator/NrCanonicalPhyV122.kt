package com.example.nrsimulator

/**
 * V122 additive time-sequence execution layer.
 *
 * V117 remains the canonical single-realization coded PHY. V122 executes that
 * path at deterministic time instants, using the existing opt-in V121 Jakes
 * TDL channel, and reports both per-realization PHY results and sequence-level
 * channel evolution metrics. Existing V1-V121 APIs are unchanged.
 */
object NrCanonicalPhyV122 {
    data class Config(
        val phy: NrCanonicalPhyV117.Config = NrCanonicalPhyV117.Config(
            tdlProfile = NrCanonicalTdlV118.Profile.TDL_A,
            timeVaryingTdl = true,
            tdlDopplerHz = 70.0,
            tdlJakesOscillators = 32
        ),
        val sampleCount: Int = 4,
        val timeStepSeconds: Double = 1.0e-3,
        val startTimeSeconds: Double = phy.tdlTimeSeconds
    )

    data class Report(
        val passed: Boolean,
        val realizations: List<NrCanonicalPhyV117.Report>,
        val timesSeconds: List<Double>,
        val channelChanged: Boolean,
        val maxChannelDelta: Double,
        val allPhyChecksPassed: Boolean,
        val zeroDopplerStaticCheckPassed: Boolean,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.sampleCount > 0) { "sampleCount must be > 0" }
        require(config.timeStepSeconds >= 0.0 && config.timeStepSeconds.isFinite())
        require(config.startTimeSeconds.isFinite())
        require(config.phy.tdlProfile != null) { "V122 requires an explicit TDL profile" }
        require(config.phy.timeVaryingTdl) { "V122 requires V121 time-varying TDL to be enabled" }

        val times = (0 until config.sampleCount).map { config.startTimeSeconds + it * config.timeStepSeconds }
        val reports = times.map { time ->
            NrCanonicalPhyV117.run(config.phy.copy(tdlTimeSeconds = time))
        }
        val allPhy = reports.all { it.passed }

        val channelSamples = times.map { time ->
            NrCanonicalTdlV121.frequencyResponseAtTime(
                NrCanonicalTdlV121.Config(
                    tdl = NrCanonicalTdlV118.Config(
                        profile = config.phy.tdlProfile!!,
                        txAntennas = config.phy.txAntennas,
                        rxAntennas = config.phy.rxAntennas,
                        sampleRateHz = 30.72e6,
                        rmsDelayNs = 30.0,
                        dopplerHz = config.phy.tdlDopplerHz,
                        seed = config.phy.seed + 118
                    ),
                    oscillators = config.phy.tdlJakesOscillators,
                    timeStepSeconds = config.timeStepSeconds
                ),
                config.phy.fftSize,
                time
            )
        }
        var maxDelta = 0.0
        for (i in 1 until channelSamples.size) {
            val previous = channelSamples[i - 1]
            val current = channelSamples[i]
            for (k in current.indices) for (r in current[k].indices) for (t in current[k][r].indices) {
                val dr = current[k][r][t].re - previous[k][r][t].re
                val di = current[k][r][t].im - previous[k][r][t].im
                maxDelta = maxOf(maxDelta, kotlin.math.sqrt(dr * dr + di * di))
            }
        }
        val channelChanged = maxDelta > 1.0e-10

        val zeroDopplerBase = config.phy.copy(tdlDopplerHz = 0.0, tdlTimeSeconds = config.startTimeSeconds)
        val zeroA = NrCanonicalTdlV121.buildAtTime(
            NrCanonicalTdlV121.Config(
                tdl = NrCanonicalTdlV118.Config(
                    profile = zeroDopplerBase.tdlProfile!!,
                    txAntennas = zeroDopplerBase.txAntennas,
                    rxAntennas = zeroDopplerBase.rxAntennas,
                    sampleRateHz = 30.72e6,
                    rmsDelayNs = 30.0,
                    dopplerHz = 0.0,
                    seed = zeroDopplerBase.seed + 118
                ),
                oscillators = zeroDopplerBase.tdlJakesOscillators
            ),
            config.startTimeSeconds
        )
        val zeroB = NrCanonicalTdlV121.buildAtTime(
            NrCanonicalTdlV121.Config(
                tdl = NrCanonicalTdlV118.Config(
                    profile = zeroDopplerBase.tdlProfile!!,
                    txAntennas = zeroDopplerBase.txAntennas,
                    rxAntennas = zeroDopplerBase.rxAntennas,
                    sampleRateHz = 30.72e6,
                    rmsDelayNs = 30.0,
                    dopplerHz = 0.0,
                    seed = zeroDopplerBase.seed + 118
                ),
                oscillators = zeroDopplerBase.tdlJakesOscillators
            ),
            config.startTimeSeconds + 10.0
        )
        val zeroStatic = zeroA.taps == zeroB.taps

        val passed = allPhy && (config.sampleCount == 1 || channelChanged) && zeroStatic
        return Report(
            passed = passed,
            realizations = reports,
            timesSeconds = times,
            channelChanged = channelChanged,
            maxChannelDelta = maxDelta,
            allPhyChecksPassed = allPhy,
            zeroDopplerStaticCheckPassed = zeroStatic,
            notes = "V122 executes V117 coded PHY repeatedly at deterministic time instants with V121 Jakes TDL; default APIs remain unchanged."
        )
    }
}
