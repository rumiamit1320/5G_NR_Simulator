package com.example.nrsimulator

object NrV32V45Tests {
    fun run():List<String>{
        val out=ArrayList<String>()
        fun t(id:String,b:Boolean){out += "$id: ${if(b)"PASS" else "FAIL"}"}
        val info=intArrayOf(1,0,1,1,0,1,0,1); val cw=NrPolarV32.encode(info,32); t("V32 Polar",NrPolarV32.decode(cw.bits,info.size).contentEquals(info))
        val m=NrPdcchV33.map(NrCoresetV33(0,0,24,2),4,1); t("V33 PDCCH mapping",m.cce.size==4&&m.reg.size==24)
        t("V34 LDPC multi-CB",NrLdpcV34.encode(IntArray(1500){it and 1}).blocks.size==2)
        t("V35 resource mapping",NrPhyMappingV35.allocate(NrPhyAllocationV35(NrChannelV35.PDSCH,0,4,0,2,1)).isNotEmpty())
        t("V36 MAC timer",NrMacV36.tick(NrMacStateV36(timers=mapOf(NrMacTimerV36.T300 to 2))).timers[NrMacTimerV36.T300]==1)
        val (e,p)=NrRlcV37.enqueue(NrRlcEntityV37(NrRlcConfigV37(NrRlcModeV37.AM)),byteArrayOf(1));t("V37 RLC",p.payload.contentEquals(byteArrayOf(1))&&e.txSn==1)
        val pd=NrPdcpV38();val k=ByteArray(16){it.toByte()};val pp=pd.protect(k,byteArrayOf(1,2),1,0,0);t("V38 PDCP",pd.verify(k,pp,1,0,0)?.contentEquals(byteArrayOf(1,2))==true)
        val rm=NrRrcMessageV39(NrRrcProcedureV31.SETUP,1,byteArrayOf(1,2));t("V39 PER",NrRrcV39.decode(NrRrcV39.encode(rm)).payload.contentEquals(rm.payload))
        val ne=NrNasV40.protect(k,NrNasEnvelopeV40(NrNasMessageV40.REGISTRATION_REQUEST,0,0,byteArrayOf(1)),1,0,0);t("V40 NAS",ne.mac!=null)
        t("V41 NGAP",NrInterfaceV41.wrap(NrRanInterfaceV41.N2_NGAP,1,byteArrayOf(1)).iface==NrRanInterfaceV41.N2_NGAP)
        t("V42 5GC",Nr5gcV42.establish("001010",10).state=="ESTABLISHED")
        t("V43 RF",NrRfV43.measure(listOf(Complex(1.0,0.0))).sinrDb.isFinite())
        val rep=NrConformanceV44.run(listOf(NrConformanceCaseV44("V32","38.212",{true})));t("V44 harness",rep.pass)
        t("V45 gate",!NrCommercialGateV45.evaluate(rep).certificationReady)
        return out
    }
}
