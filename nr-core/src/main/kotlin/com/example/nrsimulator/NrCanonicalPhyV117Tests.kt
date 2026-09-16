package com.example.nrsimulator

object NrCanonicalPhyV117Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val one = NrCanonicalPhyV117.run(NrCanonicalPhyV117.Config(payloadBits = 512, layers = 1, txAntennas = 1, rxAntennas = 1, snrDb = 60.0))
        val two = NrCanonicalPhyV117.run(NrCanonicalPhyV117.Config(payloadBits = 512, layers = 2, txAntennas = 2, rxAntennas = 2, snrDb = 55.0))
        val checks = linkedMapOf(
            "1x1 coded PHY passed" to one.passed,
            "1x1 CRC" to one.transportCrcPassed,
            "1x1 LDPC" to one.ldpcPassed,
            "1x1 DM-RS" to one.dmrsMapped,
            "1x1 channel estimate" to one.channelEstimated,
            "1x1 equalization" to one.equalized,
            "1x1 recovered payload" to (one.recoveredBits >= one.payloadBits + 16),
            "2x2 coded PHY passed" to two.passed,
            "2x2 CRC" to two.transportCrcPassed,
            "2x2 LDPC" to two.ldpcPassed,
            "2x2 DM-RS" to two.dmrsMapped,
            "2x2 channel estimate" to two.channelEstimated,
            "2x2 equalization" to two.equalized,
            "2x2 recovered payload" to (two.recoveredBits >= two.payloadBits + 16),
            "2x2 finite EVM" to two.evm.isFinite(),
            "2x2 finite SINR" to two.postSinrDb.isFinite(),
            "2x2 finite channel MSE" to two.channelMse.isFinite()
        )
        return Result(checks.values.all { it }, checks)
    }
}
