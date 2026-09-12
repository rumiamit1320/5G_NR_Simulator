package com.example.nrsimulator

object NrHarqExecutionV69Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = mutableListOf<String>()
        fun check(name: String, ok: Boolean) { checks += "$name: ${if (ok) "PASS" else "FAIL"}" }

        val timed = NrTimingV67.run(
            NrIntegratedNetworkConfigV66(
                slots = 12, ueCount = 4, cells = 2, prbs = 24,
                scsKHz = 30, velocityKmh = 60.0, seed = 6901
            )
        )
        val r = NrHarqExecutionV69.run(
            timed,
            NrHarqExecutionConfigV69(processesPerUe = 16, ackDelaySlots = 4, maxTransmissions = 4)
        )

        check("transmissions generated", r.transmissions.isNotEmpty())
        check("ACK/NACK accounting", r.ackCount + r.nackCount == r.transmissions.size)
        check("feedback timing", r.transmissions.all { it.feedbackSlot == it.transmissionSlot + 4 })
        check("RV range", r.transmissions.all { it.rv in 0..3 })
        check("valid process IDs", r.transmissions.all { it.processId in 0..15 })
        check("terminal state valid", r.transmissions.all { it.state in setOf(NrHarqStateV51.ACKED, NrHarqStateV51.RETX, NrHarqStateV51.NACKED) })
        check("max transmission bound", r.transmissions.all { it.transmissionNumber in 1..4 })
        check("retransmission accounting", r.retransmissionCount == r.transmissions.count { it.transmissionNumber > 1 })
        check("exhaustion accounting", r.exhaustedCount == r.transmissions.count { !it.ack && it.transmissionNumber >= 4 })
        check("no fabricated PHY result", r.transmissions.all { it.ack == timed.slotResults.flatMap { s -> s.radio.ueStates }.any { u -> u.ueId == it.ueId && u.crcPass } || !it.ack })

        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
