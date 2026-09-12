package com.example.nrsimulator

object NrHarqSoftPhyV73Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = mutableListOf<String>()
        fun check(name: String, ok: Boolean) {
            checks += "$name: ${if (ok) "PASS" else "FAIL"}"
        }

        val tx = arrayOf(
            Complex(1.0 / kotlin.math.sqrt(2.0), 1.0 / kotlin.math.sqrt(2.0)),
            Complex(-1.0 / kotlin.math.sqrt(2.0), 1.0 / kotlin.math.sqrt(2.0))
        )
        val llr = NrHarqSoftPhyV73.qamLlrs(tx, 4, 0.01)
        check("QPSK LLR finite", llr.all { it.isFinite() })
        check("QPSK LLR has sign", llr[0] > 0.0 && llr[2] < 0.0)

        val soft = NrHarqSoftPhyV73.rateRecoverSoft(doubleArrayOf(1.0, 2.0, 3.0, 4.0), 4, 0)
        check("soft rate recovery accumulates", soft.contentEquals(doubleArrayOf(1.0, 2.0, 3.0, 4.0)))

        val high = NrHarqSoftPhyV73.run(
            NrHarqSoftPhyConfigV73(
                payloadBits = 64,
                snrDb = 40.0,
                modulationOrder = 16,
                maxTransmissions = 4,
                processId = 2,
                seed = 7311
            )
        )
        check("high-SNR soft path terminates", high.transmissions.size == 1)
        check("high-SNR CRC passes", high.finalCrcPass)
        check("soft buffer populated", high.combinedLlrs.isNotEmpty() && high.combinedLlrs.all { it.isFinite() })
        check("process identity preserved", high.transmissions.all { it.processId == 2 })
        check("RV order", high.transmissions.map { it.rv } == listOf(0))

        val low = NrHarqSoftPhyV73.run(
            NrHarqSoftPhyConfigV73(
                payloadBits = 64,
                snrDb = 0.0,
                modulationOrder = 16,
                maxTransmissions = 4,
                processId = 2,
                seed = 7312
            )
        )
        check("low-SNR path bounded", low.transmissions.size in 1..4)
        check("LLR magnitude grows with combining", low.transmissions.zipWithNext().all { (a, b) -> b.combinedMeanAbsLlr >= a.combinedMeanAbsLlr })
        check("payload returned when decoded", !low.finalCrcPass || low.finalPayloadBits.size == 64)

        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
