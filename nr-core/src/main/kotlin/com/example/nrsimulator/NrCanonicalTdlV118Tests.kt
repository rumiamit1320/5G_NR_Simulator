package com.example.nrsimulator

/** Regression coverage for the additive V118 TDL channel profile layer. */
object NrCanonicalTdlV118Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val checks = linkedMapOf<String, Boolean>()
        for (p in NrCanonicalTdlV118.Profile.values()) {
            val r = NrCanonicalTdlV118.build(
                NrCanonicalTdlV118.Config(profile = p, txAntennas = 2, rxAntennas = 2, dopplerHz = 300.0)
            )
            checks["${p.name} has multiple paths"] = r.paths.size > 1
            checks["${p.name} normalized power"] = kotlin.math.abs(r.normalizedPower - 1.0) < 1e-9
            checks["${p.name} has MIMO taps"] = r.taps.size == r.paths.size && r.taps.all { it.h.size == 2 && it.h[0].size == 2 }
            checks["${p.name} frequency selective"] = r.frequencySelective
            checks["${p.name} finite delays"] = r.maxDelaySamples >= 1
            val h = NrCanonicalTdlV118.frequencyResponse(r.tapsConfig(), 64)
            checks["${p.name} finite H"] = h.all { k -> k.all { row -> row.all { c -> c.re.isFinite() && c.im.isFinite() } } }
        }
        return Result(checks.values.all { it }, checks)
    }

    private fun NrCanonicalTdlV118.Result.tapsConfig(): NrCanonicalTdlV118.Config =
        NrCanonicalTdlV118.Config(profile = profile, txAntennas = taps.first().h[0].size, rxAntennas = taps.size.let { taps.first().h.size }, dopplerHz = dopplerHz)
}
