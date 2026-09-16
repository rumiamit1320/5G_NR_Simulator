package com.example.nrsimulator

/** Regression coverage for the additive V121 time-varying Jakes TDL layer. */
object NrCanonicalTdlV121Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val checks = linkedMapOf<String, Boolean>()
        val base = NrCanonicalTdlV118.Config(
            profile = NrCanonicalTdlV118.Profile.TDL_A,
            txAntennas = 1,
            rxAntennas = 1,
            rmsDelayNs = 30.0,
            dopplerHz = 700.0,
            seed = 12101
        )
        val config = NrCanonicalTdlV121.Config(base, oscillators = 32, timeStepSeconds = 1.0e-4)

        val a = NrCanonicalTdlV121.buildAtTime(config, 0.0)
        val b = NrCanonicalTdlV121.buildAtTime(config, 0.0)
        val c = NrCanonicalTdlV121.buildAtTime(config, 1.0e-3)
        checks["same seed/time is reproducible"] = tapsEqual(a, b)
        checks["non-zero Doppler changes realization"] = !tapsEqual(a, c)
        checks["V121 finite taps"] = finite(a) && finite(c)
        checks["V121 normalized path power"] = kotlin.math.abs(a.normalizedPower - 1.0) < 1e-9

        val staticConfig = config.copy(tdl = base.copy(dopplerHz = 0.0))
        val s0 = NrCanonicalTdlV121.buildAtTime(staticConfig, 0.0)
        val s1 = NrCanonicalTdlV121.buildAtTime(staticConfig, 1.0)
        checks["zero Doppler is static"] = tapsEqual(s0, s1)

        val sequence = NrCanonicalTdlV121.buildSequence(config, sampleCount = 6)
        checks["sequence length"] = sequence.realizations.size == 6
        checks["sequence finite"] = sequence.realizations.all(::finite)
        checks["sequence evolves"] = sequence.realizations.zipWithNext().any { (x, y) -> !tapsEqual(x, y) }
        checks["sequence timestep preserved"] = sequence.timeStepSeconds == config.timeStepSeconds

        for (profile in listOf(NrCanonicalTdlV118.Profile.TDL_D, NrCanonicalTdlV118.Profile.TDL_E)) {
            val losConfig = config.copy(tdl = base.copy(profile = profile, dopplerHz = 300.0))
            val los = NrCanonicalTdlV121.buildAtTime(losConfig, 2.0e-3)
            checks["${profile.name} retains Ricean first tap"] = los.fading == NrCanonicalTdlV118.Fading.RICEAN_FIRST_TAP
            checks["${profile.name} finite realization"] = finite(los)
            checks["${profile.name} default K retained"] = los.kFactorDb == NrCanonicalTdlV118.defaultKFactorDb(profile)
        }

        val twoByTwo = NrCanonicalTdlV121.buildAtTime(
            config.copy(tdl = base.copy(txAntennas = 2, rxAntennas = 2)),
            3.0e-3
        )
        checks["2x2 MIMO tap dimensions"] = twoByTwo.taps.all { it.h.size == 2 && it.h.all { row -> row.size == 2 } }

        val phy = NrCanonicalPhyV117.run(
            NrCanonicalPhyV117.Config(
                payloadBits = 512,
                targetCodeRate = 0.5,
                snrDb = 80.0,
                txAntennas = 1,
                rxAntennas = 1,
                layers = 1,
                tdlProfile = NrCanonicalTdlV118.Profile.TDL_A,
                timeVaryingTdl = true,
                tdlDopplerHz = 500.0,
                tdlTimeSeconds = 2.0e-3,
                tdlJakesOscillators = 32,
                seed = 12117
            )
        )
        checks["V121 canonical PHY channel estimated"] = phy.channelEstimated
        checks["V121 canonical PHY equalized"] = phy.equalized
        checks["V121 canonical PHY transport recovered"] = phy.transportCrcPassed

        return Result(checks.values.all { it }, checks)
    }

    private fun finite(result: NrCanonicalTdlV118.Result): Boolean =
        result.taps.all { tap -> tap.h.all { row -> row.all { c -> c.re.isFinite() && c.im.isFinite() } } }

    private fun tapsEqual(a: NrCanonicalTdlV118.Result, b: NrCanonicalTdlV118.Result): Boolean {
        if (a.taps.size != b.taps.size) return false
        return a.taps.indices.all { i ->
            val ah = a.taps[i].h
            val bh = b.taps[i].h
            ah.indices.all { r ->
                ah[r].indices.all { t ->
                    ah[r][t].re == bh[r][t].re && ah[r][t].im == bh[r][t].im
                }
            }
        }
    }
}
