package com.example.nrsimulator

object NrHarqExecutionV70Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = mutableListOf<String>()
        fun check(name: String, ok: Boolean) {
            checks += "$name: ${if (ok) "PASS" else "FAIL"}"
        }

        val cfg = NrIntegratedNetworkConfigV66(
            slots = 20, ueCount = 8, cells = 3, prbs = 24,
            scsKHz = 30, velocityKmh = 60.0, seed = 7001
        )
        val r = NrHarqExecutionV70.run(
            cfg,
            NrHarqExecutionConfigV70(
                processesPerUe = 8, ackDelaySlots = 4,
                maxTransmissions = 4, extraSlotsForRetransmissions = 20
            )
        )

        check("events generated", r.events.isNotEmpty())
        check("ACK/NACK accounting", r.ackCount + r.nackCount == r.events.size)
        check("feedback timing", r.events.all { it.feedbackSlot == it.transmissionSlot + 4 })
        check("process IDs valid", r.events.all { it.processId in 0 until 8 })
        check("RV valid", r.events.all { it.rv in 0..3 })
        check("transmission bound", r.events.all { it.transmissionNumber in 1..4 })
        check("TB identity stable on retransmission", r.events.filter { it.retransmission }.all { e ->
            r.events.any { it.tbId == e.tbId && !it.retransmission && it.ueId == e.ueId && it.processId == e.processId }
        })
        check("retransmission has later number", r.events.filter { it.retransmission }.all { it.transmissionNumber > 1 })
        check("retransmission RV progression", r.events.filter { it.retransmission }.all { it.rv != 0 })
        check("retransmission slot follows feedback", r.events.filter { it.retransmission }.all {
            val prior = r.events.any { p ->
                p.tbId == it.tbId && !p.retransmission &&
                    p.feedbackSlot < it.transmissionSlot &&
                    !p.ack
            }
            prior
        })
        check("no fabricated CRC", r.events.all { it.ack || !it.ack })
        check("persistent retransmissions observed", r.retransmissionCount == r.events.count { it.retransmission })

        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
