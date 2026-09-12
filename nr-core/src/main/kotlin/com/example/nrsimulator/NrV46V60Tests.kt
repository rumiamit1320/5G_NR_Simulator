package com.example.nrsimulator

data class NrV46V60Result(val lines:List<String>,val pass:Boolean,val certificationReady:Boolean,val summary:String)
object NrV46V60Tests{fun run():NrV46V60Result{val o=ArrayList<String>();fun t(id:String,b:Boolean){o+="$id: ${if(b)"PASS" else "FAIL"}"};val tr=NrLdpcV46.build(IntArray(100){it and 1},200);t("V46 LDPC transport",tr.codeBlocks.size==1&&tr.tbCrcBits==124);val x=IntArray(8){it and 1};t("V47 Polar",NrPolarV47.decode(NrPolarV47.encode(x,16),8).contentEquals(x));val p=NrPdcchV48.encode(IntArray(16){it and 1},0x1234,4);t("V48 PDCCH",p.qpsk.isNotEmpty());val g=NrResourceGridV49(24);t("V49 resource grid",g.reserve(NrChannelV49.PDSCH,0,0,2,0,4)==96);t("V50 numerology",NrNumerologyV50.timing(NrScsV50.SCS30,0).slotDurationUs==500.0);var h=NrHarqV51.start(NrHarqProcessV51(0));h=NrHarqV51.feedback(h,false);t("V51 HARQ",h.rv==2);t("V52 MAC",NrMacV52.multiplex(listOf(NrLogicalChannelV52(1,1,0,100)),listOf(NrMacCeV52.BSR),50).payloadBytes==50);val e=NrRlcV53.transmit(NrRlcEntityV53(NrRlcModeV53.AM),byteArrayOf(1,2));t("V53 RLC",e.second.sn==0);val k=ByteArray(16){it.toByte()};val pc=NrPdcpV54.protect(k,byteArrayOf(1),NrPdcpV54.count(0,0),1,0);t("V54 PDCP",NrPdcpV54.verify(k,pc,1,0)?.contentEquals(byteArrayOf(1))==true);val rr=NrRrcV55.decode(NrRrcV55.encode(NrRrcPduV55(NrRrcMessageTypeV55.SETUP,1,byteArrayOf(3))));t("V55 RRC",rr.transactionId==1);t("V56 NAS",NrNasV56.step(NrNasContextV56("001"),NrNasProcedureV56.REGISTRATION).state==NrNasStateV56.REGISTRATION_REQUESTED);val gp=NrGtpPacketV57(1,2,byteArrayOf(4),3);t("V57 GTP-U",NrGtpV57.decode(NrGtpV57.encode(gp)).teid==1L);val lb=NrLoopbackSplitV58();t("V58 split",lb.send(NrUeGnbV58.ueToGnb("u1",byteArrayOf(1))));val r=NrConformanceV59.run();t("V59 harness",r.pass);t("V60 matrix",NrComplianceV60.matrix().size>=8)
        // Additive V82-V84 execution gate. This runs the new coding modules from the existing suite;
        // it does not replace or alter the V46-V60 checks above.
        val payload=IntArray(4000){it and 1}
        val bg=NrLdpcV82.selectBaseGraph(payload.size,.5)
        val seg=NrTransportV83.segment(payload,bg)
        t("V82 LDPC geometry",NrLdpcV82.geometry(bg).rows>0&&NrLdpcV82.allowedLiftingSizes().size==51)
        t("V83 transport CRC/segmentation",seg.codeBlocks.isNotEmpty()&&seg.kPrime>0&&seg.liftingSize>0)
        val e=NrLdpcV82.encodedSize(bg,seg.liftingSize)
        val rm=NrRateMatchingV84.rateMatch(IntArray(e),NrRateMatchingV84.Config(bg,seg.liftingSize,0,minOf(e-2*seg.liftingSize,e-2*seg.liftingSize).coerceAtLeast(1)))
        t("V84 rate matching",rm.isNotEmpty())
        val cert=false;return NrV46V60Result(o,o.none{it.endsWith("FAIL")},cert,o.joinToString("  •  "))}}
