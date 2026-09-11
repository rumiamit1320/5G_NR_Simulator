package com.example.nrsimulator

object NrClosedLoopV64Tests {
    data class Report(val pass: Boolean, val checks: List<String>)

    fun run(): Report {
        val r = NrClosedLoopV64.run(
            NrClosedLoopConfigV64(
                slots = 4,
                ueCount = 4,
                cells = 2,
                prbs = 24,
                velocityKmh = 60.0,
                payloadBitsPerUe = 64,
                seed = 6407
            )
        )
        val checks = listOf(
            "slot-count=${r.slotResults.size == 4}",
            "ue-count=${r.ueStates.size == 4}",
            "prb-conservation=${r.slotResults.all { slot -> slot.ueStates.sumOf { it.allocatedPrbs } == 24 }}",
            "finite=${listOf(r.totalThroughputMbps, r.fairness, r.crcPassRate, r.ber).all { it.isFinite() }}",
            "fairness=${r.fairness in 0.0..1.0}",
            "feedback=${r.slotResults.all { it.schedulerFeedback.contains("next-slot") }}",
            "coordinates=${r.ueStates.all { it.xM.isFinite() && it.yM.isFinite() }}",
            "phy-feedback-observed=${r.slotResults.flatMap { it.ueStates }.any { it.pfAverageMbps.isFinite() }}"
        )
        return Report(checks.all { it.endsWith("=true") }, checks)
    }
}
