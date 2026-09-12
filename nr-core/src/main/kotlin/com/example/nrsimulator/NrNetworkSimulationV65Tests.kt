package com.example.nrsimulator

object NrNetworkSimulationV65Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val r = NrNetworkSimulationV65.run(
            NrNetworkSimulationConfigV65(
                slots = 4,
                ueCount = 6,
                cells = 3,
                prbs = 24,
                velocityKmh = 60.0,
                trafficDemandMbps = 25.0
            )
        )
        val checks = listOf(
            "cell count" to (r.cells.size == 3),
            "UE count" to (r.ueStates.size == 6),
            "slot count" to (r.slotResults.size == 4),
            "serving cell valid" to (r.ueStates.all { it.servingCellId in 1..3 }),
            "throughput finite" to (r.totalThroughputMbps.isFinite() && r.meanThroughputMbps.isFinite()),
            "demand satisfaction bounded" to (r.demandSatisfiedPercent in 0.0..100.0),
            "fairness bounded" to (r.fairness in 0.0..1.0),
            "handover count valid" to (r.handovers >= 0),
            "slot UE counts" to (r.slotResults.all { it.ueStates.size == 6 }),
            "cell load finite" to (r.cellLoadPercent.values.all { it.isFinite() && it >= 0.0 })
        ).map { "${it.first}: ${if (it.second) "PASS" else "FAIL"}" }
        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
