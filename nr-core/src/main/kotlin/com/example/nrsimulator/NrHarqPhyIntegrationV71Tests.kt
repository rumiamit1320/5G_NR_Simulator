package com.example.nrsimulator

object NrHarqPhyIntegrationV71Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = mutableListOf<String>()
        fun check(name: String, ok: Boolean) {
            checks += "$name: ${if (ok) "PASS" else "FAIL"}"
        }

        val r = NrHarqPhyIntegrationV71.run(
            processId = 3,
            maxTransmissions = 4,
            config = NrHarqPhyConfigV71(payloadBits = 128, snrDb = 0.0, seed = 7109)
        )
        check("transmission exists", r.transmissions.isNotEmpty())
        check("transmission bound", r.transmissions.size in 1..4)
        check("RV sequence", r.transmissions.map { it.rv } == r.transmissions.indices.map { intArrayOf(0, 2, 3, 1)[it] })
        check("process identity stable", r.transmissions.all { it.processId == 3 })
        check("soft metric finite", r.transmissions.all { it.softMetric.isFinite() && it.combinedSoftMetric.isFinite() })
        check("combined metric monotonic", r.transmissions.zipWithNext().all { (a, b) -> b.combinedSoftMetric >= a.combinedSoftMetric })
        check("retransmission accounting", r.retransmissionCount == r.transmissions.size - 1)
        check("final CRC is actual CRC", r.finalCrcPass == r.transmissions.last().crcPass)

        val high = NrHarqPhyIntegrationV71.run(
            processId = 1,
            maxTransmissions = 4,
            config = NrHarqPhyConfigV71(payloadBits = 64, snrDb = 40.0, seed = 7110)
        )
        check("clean link ACK terminates", high.transmissions.size == 1 && high.finalCrcPass)

        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
