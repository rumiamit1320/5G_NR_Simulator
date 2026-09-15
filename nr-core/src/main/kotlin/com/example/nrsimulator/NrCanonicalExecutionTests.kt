package com.example.nrsimulator

/** Regression gate for the canonical end-to-end PHY entry point. */
object NrCanonicalExecutionTests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val r = NrCanonicalExecution.run(
            NrCanonicalExecution.Config(
                payloadBits = 512,
                targetCodeRate = 0.5,
                modulation = NrPhyMappingV89.Modulation.QPSK,
                layers = 1,
                snrDb = 80.0,
                rv = 0
            )
        )
        val checks = linkedMapOf(
            "canonical passed" to r.passed,
            "TB CRC" to r.crcPassed,
            "LDPC" to r.ldpcPassed,
            "all stages" to r.stages.values.all { it },
            "payload recovered" to (r.recoveredBits >= r.payloadBits + 16),
            "finite EVM" to r.evm.isFinite()
        )
        return Result(checks.values.all { it }, checks)
    }
}
