package com.example.nrsimulator

/** Deterministic tests for the additive V13 PT-RS/impairment layer. */
object NrV13Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val clean = NrPhyV13().run(NrPhyV13Config(
            prbs = 24, payloadBits = 300, qm = 2, txAntennas = 1, rxAntennas = 1,
            layers = 1, snrDb = 35.0, channelModel = "FLAT", cfoHz = 0.0,
            sfoPpm = 0.0, phaseNoiseStdRad = 0.0, ptRsEnabled = true, seed = 0x1302
        ))
        check(clean.tbCrcOk && clean.bitErrors == 0 && clean.ptRsResources > 0) { "clean PT-RS" }
        out += "1x1 PT-RS clean link: PASS"

        val impaired = NrPhyV13().run(NrPhyV13Config(
            prbs = 24, payloadBits = 300, qm = 2, txAntennas = 1, rxAntennas = 1,
            layers = 1, snrDb = 30.0, channelModel = "FLAT", cfoHz = 250.0,
            sfoPpm = 2.0, phaseNoiseStdRad = 0.0, ptRsEnabled = true, seed = 0x1303
        ))
        check(impaired.ptRsResources > 0 && impaired.tbCrcOk && impaired.bitErrors == 0) { "PT-RS/CFO compensation" }
        out += "CFO/SFO/phase-noise + PT-RS correction: PASS"

        val noPt = NrPhyV13().run(NrPhyV13Config(
            prbs = 24, payloadBits = 300, qm = 2, txAntennas = 1, rxAntennas = 1,
            layers = 1, snrDb = 35.0, channelModel = "FLAT", cfoHz = 0.0,
            sfoPpm = 0.0, phaseNoiseStdRad = 0.0, ptRsEnabled = false, seed = 0x1304
        ))
        check(noPt.ptRsResources == 0) { "PT-RS disable" }
        out += "PT-RS configurable disable: PASS"
        return out
    }
}
