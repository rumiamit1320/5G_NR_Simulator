package com.example.nrsimulator

object NrHarqTimingV68Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = mutableListOf<String>()
        fun check(name: String, ok: Boolean) { checks += "$name: ${if (ok) "PASS" else "FAIL"}" }

        val timed = NrTimingV67.run(
            NrIntegratedNetworkConfigV66(
                slots = 8, ueCount = 4, cells = 2, prbs = 24,
                scsKHz = 30, velocityKmh = 60.0, seed = 6801
            )
        )
        val r = NrHarqTimingV68.run(timed, NrHarqTimingConfigV68(processesPerUe = 8, downlinkAckDelaySlots = 4))

        check("events generated", r.events.isNotEmpty())
        check("feedback delay", r.events.all { it.feedbackSlot == it.transmissionSlot + 4 })
        check("process range", r.events.all { it.processId in 0..7 })
        check("RV sequence", r.events.all { it.rv in setOf(0, 1, 2, 3) })
        check("ACK/NACK accounting", r.ackCount + r.nackCount == r.events.size)
        check("pending feedback", r.pendingFeedbackBySlot.values.flatten().size == r.events.size)
        check("NACK retransmission planning", r.events.filter { !it.ack }.all { it.nextTransmissionSlot == it.feedbackSlot + 1 })
        check("no fabricated retransmission", r.events.all { it.transmissionNumber == 1 })

        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
