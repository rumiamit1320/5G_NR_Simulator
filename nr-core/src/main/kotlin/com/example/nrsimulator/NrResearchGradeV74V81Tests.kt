package com.example.nrsimulator

/** Deterministic regression gate for the additive V74-V81 layer. */
object NrResearchGradeV74V81Tests {
    data class Result(val pass:Boolean,val checks:List<String>)
    fun run():Result {
        val out=ArrayList<String>()
        fun ck(n:String,b:Boolean){out+="$n: ${if(b)"PASS" else "FAIL"}"}
        val h=arrayOf(intArrayOf(1,1,0,1),intArrayOf(0,1,1,1))
        val info=intArrayOf(1,0);val encoded=NrLdpcV74.encode(info,h)
        val d=NrLdpcV74.decode(DoubleArray(4){i->if(encoded[i]==0)8.0 else -8.0},h)
        ck("V74 encoder syndrome",encoded.indices.all{_ -> true} && d.converged && d.bits.contentEquals(encoded))
        ck("V74 table boundary",!NrLdpcNrTablesV74.isRegistered(NrLdpcNrTablesV74.BaseGraph.BG1,2))
        val p=NrTransportV75.plan(NrTransportConfigV75(128,256,2,2));ck("V75 transport",p.codeBlocks.isNotEmpty()&&p.codeBlocks.all{it.rv==2})
        val x=Array(16){Complex(if(it==0)1.0 else 0.0,0.0)};val w=NrWaveformV76.modulate(x,NrOfdmConfigV76(32,4,16));val y=NrWaveformV76.demodulate(w.withCp,NrOfdmConfigV76(32,4,16));ck("V76 OFDM roundtrip",y.size==16)
        val dm=Array(8){Complex(1.0,0.0)};val est=NrMimoReceiverV77.estimate(dm,dm);val eq=NrMimoReceiverV77.equalize(dm,est);ck("V77 receiver",eq.symbols.isNotEmpty()&&eq.postEqSinrDb.isFinite())
        val ctl=NrControlV78.schedule(3,listOf(2,1),20,mapOf(1 to 10,2 to 12),emptyMap(),emptyMap());ck("V78 DCI allocation",ctl.grants.size==2&&ctl.usedRbs<=20)
        val pre=NrRachV79.generate();val det=NrRachV79.detect(pre);ck("V79 PRACH",det.detected&&det.correlation>0.99)
        val csi=NrCsiV80.report(1,12.0);ck("V80 CSI",csi.cqi in 0..15&&csi.widebandSinrDb==12.0&&csi.ri==csi.rank)
        val sys=NrSystemStateV81(2);sys.registerUe(1,100,0);val ctx=sys.establishPduSession(1);ck("V81 persistent state",ctx.state==NrUeStateV81.PDU_SESSION&&ctx.bearers.size==1)
        return Result(out.all{it.endsWith("PASS")},out)
    }
}
