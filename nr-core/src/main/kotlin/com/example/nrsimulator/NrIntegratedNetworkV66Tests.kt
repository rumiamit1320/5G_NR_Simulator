package com.example.nrsimulator

object NrIntegratedNetworkV66Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = ArrayList<String>()
        fun check(name: String, ok: Boolean) { checks += "$name:${if (ok) "PASS" else "FAIL"}" }

        val cfg = NrIntegratedNetworkConfigV66(
            slots = 3, ueCount = 4, cells = 2, prbs = 24,
            velocityKmh = 60.0, trafficDemandMbps = 20.0, seed = 6607
        )
        val a = NrIntegratedNetworkV66.run(cfg)
        val b = NrIntegratedNetworkV66.run(cfg)

        check("slot-count", a.slotResults.size == 3)
        check("ue-count", a.ueStates.size == 4)
        check("cell-count", a.cells.size == 2)
        check("finite-throughput", a.totalThroughputMbps.isFinite())
        check("fairness-range", a.fairness in 0.0..1.0)
        check("demand-range", a.demandSatisfiedPercent >= 0.0 && a.demandSatisfiedPercent.isFinite())
        check("beam-samples", a.meanBeamGainDb.isFinite())
        check("channel-samples", a.meanChannelFrequencySelectivityDb.isFinite())
        check("mimo-samples", a.meanMimoEffectiveSinrDb.isFinite())
        check("deterministic", a.totalThroughputMbps == b.totalThroughputMbps && a.ueStates == b.ueStates)
        check("prb-conservation", a.slotResults.all { slot ->
            val total = slot.ueStates.sumOf { it.allocatedPrbs }
            total == cfg.prbs
        })
        check("bounded-offsets", a.ueStates.all { it.integratedSinrOffsetDb in -12.0..12.0 })
        check("valid-beams", a.ueStates.all { it.beam in 0 until cfg.beamCount })

        // V64 default API must remain behaviorally usable with no external offsets.
        val legacy = NrClosedLoopV64.run(
            NrClosedLoopConfigV64(slots = 2, ueCount = 3, cells = 2, prbs = 18, seed = 6607)
        )
        check("v64-legacy", legacy.slotResults.size == 2 && legacy.ueStates.size == 3 && legacy.totalThroughputMbps.isFinite())

        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
