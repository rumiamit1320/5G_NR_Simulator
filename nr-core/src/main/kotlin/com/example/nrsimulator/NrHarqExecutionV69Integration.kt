package com.example.nrsimulator

/** Additive smoke checks for the V69 persistent HARQ execution layer. */
object NrHarqExecutionV69Integration {
    fun run(): Boolean {
        val timed = NrTimingV67.run(
            NrIntegratedNetworkConfigV66(
                slots = 12, ueCount = 4, cells = 2, prbs = 24,
                scsKHz = 30, velocityKmh = 60.0, seed = 6901
            )
        )
        val result = NrHarqExecutionV69.run(timed)
        return result.transmissions.isNotEmpty() &&
            result.ackCount + result.nackCount == result.transmissions.size &&
            result.transmissions.all { it.feedbackSlot == it.transmissionSlot + 4 } &&
            result.transmissions.all { it.processId in 0..15 && it.rv in 0..3 } &&
            result.transmissions.all { it.transmissionNumber in 1..4 }
    }
}
