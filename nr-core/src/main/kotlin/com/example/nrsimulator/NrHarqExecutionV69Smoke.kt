package com.example.nrsimulator

object NrHarqExecutionV69Smoke {
    fun run(): Boolean {
        val timed = NrTimingV67.run(
            NrIntegratedNetworkConfigV66(slots = 12, ueCount = 4, cells = 2, prbs = 24, scsKHz = 30, seed = 6901)
        )
        val result = NrHarqExecutionV69.run(timed)
        return result.transmissions.isNotEmpty() &&
            result.ackCount + result.nackCount == result.transmissions.size &&
            result.transmissions.all { it.feedbackSlot == it.transmissionSlot + result.config.ackDelaySlots } &&
            result.transmissions.all { it.processId in 0 until result.config.processesPerUe } &&
            result.transmissions.all { it.rv in 0..3 && it.transmissionNumber in 1..result.config.maxTransmissions }
    }
}
