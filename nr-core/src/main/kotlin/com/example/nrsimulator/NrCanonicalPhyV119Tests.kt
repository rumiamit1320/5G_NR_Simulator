package com.example.nrsimulator

/** Regression coverage for the V119 frequency-selective canonical coded PHY path. */
object NrCanonicalPhyV119Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val a = NrCanonicalPhyV117.run(
            NrCanonicalPhyV117.Config(
                payloadBits = 512,
                layers = 1,
                txAntennas = 1,
                rxAntennas = 1,
                snrDb = 60.0,
                tdlProfile = NrCanonicalTdlV118.Profile.TDL_A
            )
        )
        val e = NrCanonicalPhyV117.run(
            NrCanonicalPhyV117.Config(
                payloadBits = 512,
                layers = 2,
                txAntennas = 2,
                rxAntennas = 2,
                snrDb = 60.0,
                tdlProfile = NrCanonicalTdlV118.Profile.TDL_E
            )
        )
        val checks = linkedMapOf(
            "TDL-A 1x1 coded PHY passed" to a.passed,
            "TDL-A 1x1 CRC" to a.transportCrcPassed,
            "TDL-A 1x1 LDPC" to a.ldpcPassed,
            "TDL-A 1x1 DM-RS estimate" to (a.dmrsMapped && a.channelEstimated),
            "TDL-A 1x1 finite metrics" to (a.evm.isFinite() && a.postSinrDb.isFinite() && a.channelMse.isFinite()),
            "TDL-E 2x2 coded PHY passed" to e.passed,
            "TDL-E 2x2 CRC" to e.transportCrcPassed,
            "TDL-E 2x2 LDPC" to e.ldpcPassed,
            "TDL-E 2x2 DM-RS estimate" to (e.dmrsMapped && e.channelEstimated),
            "TDL-E 2x2 equalization" to e.equalized,
            "TDL-E 2x2 finite metrics" to (e.evm.isFinite() && e.postSinrDb.isFinite() && e.channelMse.isFinite())
        )
        return Result(checks.values.all { it }, checks)
    }
}
