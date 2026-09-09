package com.example.nrsimulator

/** Deterministic tests for the additive V14 CSI-RS/CSI measurement layer. */
object NrV14Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val one = NrCsiRsV14().run(NrCsiRsV14Config(
            prbs = 24, txAntennas = 1, rxAntennas = 1, csiPorts = 1,
            snrDb = 35.0, channelModel = "FLAT", seed = 0x1402
        ))
        check(one.resources > 0 && one.rank == 1 && one.cqi >= 0) { "1x1 CSI" }
        out += "1x1 CSI-RS / RSRP / SINR / CQI: PASS"

        val mimo = NrCsiRsV14().run(NrCsiRsV14Config(
            prbs = 52, txAntennas = 2, rxAntennas = 2, csiPorts = 2,
            snrDb = 30.0, channelModel = "FREQUENCY_SELECTIVE", seed = 0x1403
        ))
        check(mimo.resources > 0 && mimo.rank in 1..2 && mimo.pmi in 0..3 && mimo.channelErrorPercent < 25.0) { "2x2 CSI" }
        out += "2x2 CSI-RS channel estimate / RI / PMI: PASS"

        val four = NrCsiRsV14().run(NrCsiRsV14Config(
            prbs = 52, txAntennas = 4, rxAntennas = 4, csiPorts = 4,
            snrDb = 30.0, channelModel = "FREQUENCY_SELECTIVE", seed = 0x1404
        ))
        check(four.resources > 0 && four.rank in 1..4 && four.cqi in 0..15) { "4x4 CSI" }
        out += "4x4 CSI-RS / rank / CQI: PASS"
        return out
    }
}
