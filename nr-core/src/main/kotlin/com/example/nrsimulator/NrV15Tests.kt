package com.example.nrsimulator

/** Deterministic tests for V15 codebook precoding and beam selection. */
object NrV15Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val one = NrMimoV15().run(NrMimoV15Config(txAntennas=2, rxAntennas=2, layers=1, prbs=24, snrDb=30.0, channelModel="FLAT", seed=0x1502))
        check(one.pass && one.pmi in 0..3 && one.effectiveRank >= 1 && one.precoderErrorPercent < 1e-5) { "2x2 1-layer codebook" }
        out += "2x2 Type-I 1-layer PMI/codebook: PASS"

        val two = NrMimoV15().run(NrMimoV15Config(txAntennas=2, rxAntennas=2, layers=2, prbs=24, snrDb=30.0, channelModel="FREQUENCY_SELECTIVE", seed=0x1503))
        check(two.pass && two.pmi in 0..1 && two.precoderErrorPercent < 1e-5) { "2x2 2-layer codebook" }
        out += "2x2 Type-I 2-layer precoding: PASS"

        val four = NrMimoV15().run(NrMimoV15Config(txAntennas=4, rxAntennas=4, layers=2, prbs=52, snrDb=25.0, channelModel="FREQUENCY_SELECTIVE", seed=0x1504))
        check(four.pass && four.pmi >= 0 && four.precoderErrorPercent < 1e-5 && four.effectiveSinrDb.isFinite()) { "4x4 beam selection" }
        out += "4x4 2-layer DFT beam/codebook selection: PASS"

        val fourRank = NrMimoV15().run(NrMimoV15Config(txAntennas=4, rxAntennas=4, layers=4, prbs=24, snrDb=25.0, channelModel="FLAT", seed=0x1505))
        check(fourRank.pass && fourRank.layers == 4 && fourRank.precoderErrorPercent < 1e-5) { "4x4 rank-4 precoding" }
        out += "4x4 rank-4 normalized precoding: PASS"
        return out
    }
}
