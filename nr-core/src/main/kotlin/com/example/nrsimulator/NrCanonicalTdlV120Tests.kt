package com.example.nrsimulator

/** Regression coverage for the normative TR 38.901 TDL-A..E profile tables. */
object NrCanonicalTdlV120Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val checks = linkedMapOf<String, Boolean>()
        val expectedCounts = mapOf(
            NrCanonicalTdlV118.Profile.TDL_A to 23,
            NrCanonicalTdlV118.Profile.TDL_B to 23,
            NrCanonicalTdlV118.Profile.TDL_C to 24,
            NrCanonicalTdlV118.Profile.TDL_D to 13,
            NrCanonicalTdlV118.Profile.TDL_E to 14
        )
        for (p in NrCanonicalTdlV118.Profile.values()) {
            val paths = NrCanonicalTdlV118.profilePaths(p)
            checks["${p.name} normative tap count"] = paths.size == expectedCounts.getValue(p)
            checks["${p.name} exact first delay"] = paths.first().normalizedDelay == 0.0
            checks["${p.name} finite powers"] = paths.all { it.powerLinear.isFinite() && it.powerLinear > 0.0 }
            checks["${p.name} finite scaled delays"] = paths.all { it.delayNs.isFinite() && it.delaySamplesExact.isFinite() && it.delaySamples >= 0 }
            val r = NrCanonicalTdlV118.build(
                NrCanonicalTdlV118.Config(profile = p, txAntennas = 2, rxAntennas = 2, rmsDelayNs = 30.0, dopplerHz = 300.0)
            )
            checks["${p.name} normalized power"] = kotlin.math.abs(r.normalizedPower - 1.0) < 1e-9
            checks["${p.name} Ricean mode"] = r.fading == if (p == NrCanonicalTdlV118.Profile.TDL_D || p == NrCanonicalTdlV118.Profile.TDL_E) NrCanonicalTdlV118.Fading.RICEAN_FIRST_TAP else NrCanonicalTdlV118.Fading.RAYLEIGH
            checks["${p.name} finite MIMO taps"] = r.taps.all { it.h.all { row -> row.all { c -> c.re.isFinite() && c.im.isFinite() } } }
        }
        checks["TDL-D default K factor"] = kotlin.math.abs(NrCanonicalTdlV118.defaultKFactorDb(NrCanonicalTdlV118.Profile.TDL_D) - 13.3) < 1e-9
        checks["TDL-E default K factor"] = kotlin.math.abs(NrCanonicalTdlV118.defaultKFactorDb(NrCanonicalTdlV118.Profile.TDL_E) - 22.0) < 1e-9
        return Result(checks.values.all { it }, checks)
    }
}
