package com.example.nrsimulator

object NrRadioEnvironmentV62Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val a = NrRadioEnvironmentV62.run(NrRadioEnvironmentConfigV62(ueCount = 8, cells = 1, prbs = 52, slotIndex = 0))
        val b = NrRadioEnvironmentV62.run(NrRadioEnvironmentConfigV62(ueCount = 8, cells = 3, prbs = 52, velocityKmh = 120.0, slotIndex = 25))
        val checks = listOf(
            "UE count" to (a.ueStates.size == 8),
            "PRB conservation" to (a.ueStates.sumOf { it.allocatedPrbs } == 52),
            "CQI range" to a.ueStates.all { it.cqi in 0..12 },
            "MCS range" to a.ueStates.all { it.mcs in 0..27 },
            "Rank range" to a.ueStates.all { it.rank in 1..4 },
            "Finite KPIs" to listOf(a.totalThroughputMbps, a.meanSinrDb, a.meanCqi, a.spectralEfficiencyBpsHz, a.fairness).all { it.isFinite() },
            "Multi-cell interference" to (b.meanSinrDb < a.meanSinrDb),
            "Mobility changes geometry" to (b.ueStates.first().xM != a.ueStates.first().xM || b.ueStates.first().yM != a.ueStates.first().yM),
            "Fairness range" to (a.fairness in 0.0..1.0)
        ).map { "${it.first}: ${if (it.second) "PASS" else "FAIL"}" }
        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
