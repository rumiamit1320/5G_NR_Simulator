package com.example.nrsimulator

/** Acceptance tests for the V116 DM-RS-aided canonical MIMO boundary. */
object NrCanonicalDmrsMimoV116Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>, val report: NrCanonicalDmrsMimoV116.Report)

    fun run(): Result {
        val single = NrCanonicalDmrsMimoV116.run(
            NrCanonicalDmrsMimoV116.Config(layers = 1, txAntennas = 1, rxAntennas = 1, snrDb = 60.0)
        )
        val multi = NrCanonicalDmrsMimoV116.run(
            NrCanonicalDmrsMimoV116.Config(layers = 2, txAntennas = 2, rxAntennas = 2, snrDb = 45.0)
        )
        val checks = linkedMapOf(
            "single DM-RS mapped" to single.dmrsMapped,
            "single channel estimated" to single.channelEstimated,
            "single equalized" to single.equalized,
            "single finite EVM" to single.evm.isFinite(),
            "single finite channel MSE" to single.channelMse.isFinite(),
            "multi DM-RS mapped" to multi.dmrsMapped,
            "multi channel estimated" to multi.channelEstimated,
            "multi equalized" to multi.equalized,
            "multi detected layers" to (multi.detectedLayers == 2),
            "multi finite post-SINR" to multi.postSinrDb.isFinite(),
            "multi finite EVM" to multi.evm.isFinite(),
            "multi pilot coverage" to (multi.pilotCount >= 48)
        )
        return Result(checks.values.all { it }, checks, multi)
    }
}
