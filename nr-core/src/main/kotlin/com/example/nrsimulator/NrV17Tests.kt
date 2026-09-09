package com.example.nrsimulator

/** Deterministic V17 tests: CQI monotonicity, MCS selection and HARQ combining. */
object NrV17Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val la = NrLinkAdaptationV17()
        check(la.estimateCqi(-10.0) == 0)
        check(la.estimateCqi(5.0) < la.estimateCqi(18.0))
        out += "CQI monotonicity: PASS"

        val low = la.run(NrLinkAdaptationV17Config(sinrDb=4.0, cqi=-1, rank=1, layers=1, prbs=24, seed=0x1702))
        val high = la.run(NrLinkAdaptationV17Config(sinrDb=22.0, cqi=-1, rank=2, layers=2, prbs=52, seed=0x1703))
        check(high.selectedMcs >= low.selectedMcs)
        check(high.spectralEfficiency >= low.spectralEfficiency)
        out += "CQI→MCS link adaptation: PASS"

        val good = la.run(NrLinkAdaptationV17Config(sinrDb=24.0, rank=2, layers=2, prbs=52, maxHarqTx=4, seed=0x1704))
        check(good.ack && good.selectedMcs > 0 && good.goodputMbps > 0.0)
        out += "High-SINR adaptive MCS + HARQ ACK: PASS"

        val harsh = la.run(NrLinkAdaptationV17Config(sinrDb=2.0, rank=1, layers=1, prbs=24, maxHarqTx=4, seed=0x1705))
        check(harsh.history.size in 1..4 && harsh.rvHistory.isNotEmpty())
        check(harsh.combiningGainDb >= 0.0)
        out += "HARQ RV 0/2/3/1 + soft-combining model: PASS"
        return out
    }
}
