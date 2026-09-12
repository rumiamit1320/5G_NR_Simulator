package com.example.nrsimulator

object NrTimingV67Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = mutableListOf<String>()
        fun check(name: String, ok: Boolean) {
            checks += "$name: ${if (ok) "PASS" else "FAIL"}"
        }

        val mu0 = NrTimingConfigV67(15)
        val mu1 = NrTimingConfigV67(30)
        val mu2 = NrTimingConfigV67(60)
        check("15 kHz numerology", mu0.numerology == 0 && mu0.slotsPerFrame == 10 && mu0.slotDurationUs == 1000.0)
        check("30 kHz numerology", mu1.numerology == 1 && mu1.slotsPerFrame == 20 && mu1.slotDurationUs == 500.0)
        check("60 kHz numerology", mu2.numerology == 2 && mu2.slotsPerFrame == 40 && mu2.slotDurationUs == 250.0)

        val s0 = NrTimingV67.slot(mu1, 0)
        val s19 = NrTimingV67.slot(mu1, 19)
        val s20 = NrTimingV67.slot(mu1, 20)
        check("slot zero", s0.frame == 0L && s0.subframe == 0 && s0.slotInSubframe == 0 && s0.slotInFrame == 0)
        check("frame boundary", s19.frame == 0L && s19.subframe == 9 && s19.slotInSubframe == 1 && s19.endTimeUs == 10_000.0)
        check("next frame", s20.frame == 1L && s20.subframe == 0 && s20.slotInSubframe == 0 && s20.startTimeUs == 10_000.0)

        val timed = NrTimingV67.run(
            NrIntegratedNetworkConfigV66(slots = 3, ueCount = 2, cells = 2, prbs = 12, scsKHz = 30, seed = 6701)
        )
        check("V66 bridge", timed.slotResults.size == 3 && timed.radio.ueStates.size == 2)
        check("monotonic timing", timed.slotResults.zipWithNext().all { (a, b) ->
            b.timing.startTimeUs == a.timing.endTimeUs
        })
        check("timing duration", timed.slotResults.all { it.timing.durationUs == 500.0 })
        check("radio slot identity", timed.slotResults.map { it.radio.slotIndex } == listOf(0, 1, 2))

        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
