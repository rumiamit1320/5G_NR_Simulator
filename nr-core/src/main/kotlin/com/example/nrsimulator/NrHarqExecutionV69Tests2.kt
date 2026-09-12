package com.example.nrsimulator

object NrHarqExecutionV69Tests2 {
    fun run(): Boolean {
        val timed = NrTimingV67.run(NrIntegratedNetworkConfigV66(slots = 12, ueCount = 4, cells = 2, prbs = 24, scsKHz = 30, seed = 6901))
        val r = NrHarqExecutionV69.run(timed)
        return r.transmissions.isNotEmpty() &&
            r.ackCount + r.nackCount == r.transmissions.size &&
            r.transmissions.all { it.feedbackSlot == it.transmissionSlot + 4 } &&
            r.transmissions.all { it.rv in 0..3 && it.transmissionNumber in 1..4 }
    }
}
