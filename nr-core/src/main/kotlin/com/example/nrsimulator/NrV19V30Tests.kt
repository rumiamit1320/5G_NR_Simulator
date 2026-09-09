package com.example.nrsimulator

/** Additive regression tests for V19-V30. V1-V18 are not modified by these tests. */
object NrV19V30Tests {
    fun runAll(): List<String> {
        val out = ArrayList<String>()
        val uci = NrUciV19(booleanArrayOf(true,false,true), true, intArrayOf(9,12))
        check(NrPucchV19().run(uci).pass); out += "V19 PUCCH/UCI: PASS"
        check(NrPuschV20().run(16000).pass); out += "V20 PUSCH/UL-SCH: PASS"
        check(NrSrsV21().run(15.0).rank >= 1); out += "V21 SRS sounding: PASS"
        val sched=NrSchedulerV22().run(listOf(NrUeV22(1,12,15.0),NrUeV22(2,8,9.0),NrUeV22(3,6,7.0)),106)
        check(sched.grants.size==3 && sched.usedPrbs<=106); out += "V22 multi-UE scheduler: PASS"
        check(NrSlotEngineV23().run(2,20).executed==40); out += "V23 frame/slot engine: PASS"
        val bytes=ByteArray(1400){(it and 255).toByte()}; check(NrRlcV24().segment(bytes,300).pass); out += "V24 RLC segmentation/reassembly: PASS"
        check(NrPdcpV25().run(bytes).pass); out += "V25 PDCP sequence/reordering: PASS"
        check(Nr5gCoreV26().register(listOf(1,2)).sessions.size==2); out += "V26 5G Core session model: PASS"
        val cells=listOf(NrCellV27(1,0.0,0.0),NrCellV27(2,100.0,0.0)); check(NrMobilityV27().evaluate(20.0,0.0,cells).source in 1..2); out += "V27 mobility/handover: PASS"
        check(NrBeamV28().sweep(10.0).beamsScanned==16); out += "V28 beam management: PASS"
        check(NrTddV29().run().dlSymbols>0 && NrTddV29().run().ulSymbols>0); out += "V29 TDD configuration: PASS"
        val a=NrAnalyticsV30().run(NrAnalyticsV30Input(100.0,90.0,15.0,.02,5.0,80,100,3)); check(a.score in 0.0..100.0); out += "V30 analytics dashboard metrics: PASS"
        return out
    }
}
