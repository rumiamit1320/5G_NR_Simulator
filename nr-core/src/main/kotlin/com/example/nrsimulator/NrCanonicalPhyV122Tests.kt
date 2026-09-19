package com.example.nrsimulator

/** Regression coverage for V122 time-sequence canonical coded PHY execution. */
object NrCanonicalPhyV122Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val checks = linkedMapOf<String, Boolean>()

        val oneByOne = NrCanonicalPhyV122.run(
            NrCanonicalPhyV122.Config(
                phy = NrCanonicalPhyV117.Config(
                    snrDb = 80.0,
                    layers = 1,
                    txAntennas = 1,
                    rxAntennas = 1,
                    tdlProfile = NrCanonicalTdlV118.Profile.TDL_A,
                    timeVaryingTdl = true,
                    tdlDopplerHz = 70.0,
                    tdlJakesOscillators = 32,
                    seed = 12201
                ),
                sampleCount = 3,
                timeStepSeconds = 1.0e-3
            )
        )
        checks["1x1 sequence executes"] = oneByOne.realizations.size == 3
        checks["1x1 coded PHY passes"] = oneByOne.allPhyChecksPassed
        checks["nonzero Doppler evolves channel"] = oneByOne.channelChanged
        checks["sequence report passes"] = oneByOne.passed

        val twoByTwo = NrCanonicalPhyV122.run(
            NrCanonicalPhyV122.Config(
                phy = NrCanonicalPhyV117.Config(
                    snrDb = 80.0,
                    layers = 2,
                    txAntennas = 2,
                    rxAntennas = 2,
                    tdlProfile = NrCanonicalTdlV118.Profile.TDL_B,
                    timeVaryingTdl = true,
                    tdlDopplerHz = 120.0,
                    tdlJakesOscillators = 32,
                    seed = 12202
                ),
                sampleCount = 2,
                timeStepSeconds = 5.0e-4
            )
        )
        checks["2x2 sequence executes"] = twoByTwo.realizations.size == 2
        checks["2x2 coded PHY passes"] = twoByTwo.allPhyChecksPassed

        val riceanD = NrCanonicalTdlV121.buildAtTime(
            NrCanonicalTdlV121.Config(
                tdl = NrCanonicalTdlV118.Config(
                    profile = NrCanonicalTdlV118.Profile.TDL_D,
                    txAntennas = 2,
                    rxAntennas = 2,
                    dopplerHz = 90.0,
                    seed = 12203
                )
            ),
            0.002
        )
        val riceanE = NrCanonicalTdlV121.buildAtTime(
            NrCanonicalTdlV121.Config(
                tdl = NrCanonicalTdlV118.Config(
                    profile = NrCanonicalTdlV118.Profile.TDL_E,
                    txAntennas = 2,
                    rxAntennas = 2,
                    dopplerHz = 90.0,
                    seed = 12204
                )
            ),
            0.002
        )
        checks["TDL-D Ricean first tap"] = riceanD.fading == NrCanonicalTdlV118.Fading.RICEAN_FIRST_TAP
        checks["TDL-E Ricean first tap"] = riceanE.fading == NrCanonicalTdlV118.Fading.RICEAN_FIRST_TAP
        checks["2x2 D/E channel finite"] = listOf(riceanD, riceanE).all { result ->
            result.taps.all { tap -> tap.h.flatten().all { it.re.isFinite() && it.im.isFinite() } }
        }

        val zeroA = NrCanonicalTdlV121.buildAtTime(
            NrCanonicalTdlV121.Config(
                tdl = NrCanonicalTdlV118.Config(profile = NrCanonicalTdlV118.Profile.TDL_C, dopplerHz = 0.0, seed = 12205)
            ),
            0.0
        )
        val zeroB = NrCanonicalTdlV121.buildAtTime(
            NrCanonicalTdlV121.Config(
                tdl = NrCanonicalTdlV118.Config(profile = NrCanonicalTdlV118.Profile.TDL_C, dopplerHz = 0.0, seed = 12205)
            ),
            1.0
        )
        checks["zero Doppler is static"] = tapsEquivalent(zeroA.taps, zeroB.taps)

        return Result(checks.values.all { it }, checks)
    }

    private fun tapsEquivalent(a: List<NrCanonicalSpatialEngine.Tap>, b: List<NrCanonicalSpatialEngine.Tap>): Boolean {
        if (a.size != b.size) return false
        for (i in a.indices) {
            if (a[i].delay != b[i].delay) return false
            val ac = a[i].h
            val bc = b[i].h
            if (ac.size != bc.size) return false
            for (r in ac.indices) for (t in ac[r].indices) {
                if (ac[r][t].re != bc[r][t].re || ac[r][t].im != bc[r][t].im) return false
            }
        }
        return true
    }
}
