package com.example.nrsimulator

/** Deterministic smoke/conformance-oriented tests for the isolated v11 layer. */
object NrV11Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val d = NrDmrsV11().run(NrDmrsV11Config(prbs = 24, ports = 4))
        check(d.resourceCount == 24 * 12 / 2 * 4) { "DM-RS resource count" }
        check(d.pass) { "DM-RS OCC orthogonality" }
        out += "DM-RS type-1 ports 1000..1003: PASS"

        val flat = NrPhyV11().run(NrPhyV11Config(prbs = 24, txAntennas = 2, rxAntennas = 2, dmrsPorts = 2, snrDb = 35.0, channelModel = "FLAT", seed = 0x1102))
        check(flat.pass && flat.channelErrorPercent < 10.0 && flat.equalizedEvmPercent < 30.0) { "flat 2x2" }
        out += "Flat 2x2 channel + LS/ZF: PASS"

        val freq = NrPhyV11().run(NrPhyV11Config(prbs = 24, txAntennas = 4, rxAntennas = 4, dmrsPorts = 4, snrDb = 30.0, channelModel = "FREQUENCY_SELECTIVE", seed = 0x1104))
        check(freq.pass && freq.rank == 4) { "frequency-selective 4x4" }
        out += "Frequency-selective 4x4 MIMO: PASS"
        return out
    }
}
