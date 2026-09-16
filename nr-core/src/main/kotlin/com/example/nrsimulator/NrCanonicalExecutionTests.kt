package com.example.nrsimulator

object NrCanonicalExecutionTests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val single = NrCanonicalExecution.run(
            NrCanonicalExecution.Config(payloadBits = 512, targetCodeRate = 0.5, modulation = NrPhyMappingV89.Modulation.QPSK, layers = 1, snrDb = 80.0, rv = 0)
        )
        val multi = NrCanonicalExecution.run(
            NrCanonicalExecution.Config(payloadBits = 512, targetCodeRate = 0.5, modulation = NrPhyMappingV89.Modulation.QPSK, layers = 2, txAntennas = 2, rxAntennas = 2, snrDb = 35.0, rv = 0)
        )
        val checks = linkedMapOf(
            "single-layer canonical passed" to single.passed,
            "single-layer TB CRC" to single.crcPassed,
            "single-layer LDPC" to single.ldpcPassed,
            "single-layer payload recovered" to (single.recoveredBits >= single.payloadBits + 16),
            "single-layer finite EVM" to single.evm.isFinite(),
            "multi-layer canonical passed" to multi.passed,
            "multi-layer spatial stage" to multi.spatialPassed,
            "multi-layer detected layer count" to (multi.detectedLayers == 2),
            "multi-layer finite post-SINR" to multi.postSinrDb.isFinite(),
            "multi-layer stage map" to (multi.stages["channel+MIMO-waveform"] == true && multi.stages["MMSE-equalization+layer-recovery"] == true)
        )
        return Result(checks.values.all { it }, checks)
    }
}
