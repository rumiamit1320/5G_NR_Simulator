package com.example.nrsimulator

/** Deterministic end-to-end tests for the additive v12 PDSCH reference chain. */
object NrV12Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val clean = NrPhyV12().run(NrPhyV12Config(prbs = 24, payloadBits = 300, qm = 2, txAntennas = 1, rxAntennas = 1, layers = 1, snrDb = 35.0, channelModel = "FLAT", seed = 0x1202))
        check(clean.pass && clean.tbCrcOk && clean.bitErrors == 0) { "1x1 QPSK end-to-end" }
        out += "1x1 QPSK: TB→LDPC→OFDM→RX→CRC PASS"

        val mimo = NrPhyV12().run(NrPhyV12Config(prbs = 24, payloadBits = 300, qm = 4, txAntennas = 2, rxAntennas = 2, layers = 2, snrDb = 40.0, channelModel = "FLAT", equalizer = "MMSE", seed = 0x1203))
        check(mimo.tbCrcOk && mimo.bitErrors == 0) { "2x2 16-QAM end-to-end" }
        out += "2x2 16-QAM MMSE: end-to-end CRC PASS"

        for (rvId in 0..3) {
            val rv = NrPhyV12().run(NrPhyV12Config(prbs = 24, payloadBits = 300, qm = 2, txAntennas = 1, rxAntennas = 1, layers = 1, snrDb = 35.0, rv = rvId, channelModel = "FLAT", seed = 0x1204 + rvId))
            check(rv.tbCrcOk && rv.bitErrors == 0) { "RV$rvId" }
        }
        out += "RV0..RV3 circular-buffer recovery: PASS"
        return out
    }
}
