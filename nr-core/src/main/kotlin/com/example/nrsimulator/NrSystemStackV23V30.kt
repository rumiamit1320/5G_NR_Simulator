package com.example.nrsimulator

/**
 * Optional orchestration facade. Existing version-specific APIs remain intact;
 * this class merely demonstrates a top-down V18-V30 execution path.
 */
data class NrSystemStackV23V30Result(
    val pdcch: NrPdcchV18Result,
    val uci: NrPucchV19Result,
    val pusch: NrPuschV20Result,
    val srs: NrSrsV21Result,
    val scheduler: NrSchedulerV22Result,
    val slots: NrSlotEngineV23Result,
    val rlc: NrRlcV24Result,
    val pdcp: NrPdcpV25Result,
    val core: Nr5gCoreV26Result,
    val mobility: NrHandoverV27Result,
    val beam: NrBeamV28Result,
    val tdd: NrTddV29Result,
    val analytics: NrAnalyticsV30Result
)

class NrSystemStackV23V30 {
    fun run(): NrSystemStackV23V30Result {
        val dci=NrDciV18(frequencyDomainAssignment=10,mcs=20,rv=0,harqProcess=0,layers=2)
        val pdcch=NrPdcchV18().run(dci=dci,aggregationLevel=4)
        val uci=NrPucchV19().run(NrUciV19(booleanArrayOf(pdcch.ackNotUsed()),false,intArrayOf(12)))
        val pusch=NrPuschV20().run()
        val srs=NrSrsV21().run(15.0)
        val scheduler=NrSchedulerV22().run(listOf(NrUeV22(1,12,15.0),NrUeV22(2,9,11.0)),52)
        val slots=NrSlotEngineV23().run()
        val payload=ByteArray(1024){(it and 255).toByte()}
        val rlc=NrRlcV24().segment(payload)
        val pdcp=NrPdcpV25().run(payload)
        val core=Nr5gCoreV26().register(listOf(1,2))
        val mobility=NrMobilityV27().evaluate(20.0,0.0,listOf(NrCellV27(1,0.0,0.0),NrCellV27(2,100.0,0.0)))
        val beam=NrBeamV28().sweep(10.0)
        val tdd=NrTddV29().run()
        val analytics=NrAnalyticsV30().run(NrAnalyticsV30Input(scheduler.throughputMbps,pusch.tbBits/1000.0,15.0,.02,5.0,scheduler.usedPrbs,52,1))
        return NrSystemStackV23V30Result(pdcch,uci,pusch,srs,scheduler,slots,rlc,pdcp,core,mobility,beam,tdd,analytics)
    }
}
private fun NrPdcchV18Result.ackNotUsed():Boolean = decodeOk
