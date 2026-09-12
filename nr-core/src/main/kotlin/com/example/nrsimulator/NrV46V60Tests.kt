package com.example.nrsimulator

data class NrV46V60Result(val lines:List<String>,val pass:Boolean,val certificationReady:Boolean,val summary:String)
object NrV46V60Tests{fun run():NrV46V60Result{val o=ArrayList<String>();fun t(id:String,b:Boolean){o+="$id: ${if(b)"PASS" else "FAIL"}"};fun safe(id:String,block:()->Boolean){try{t(id,block())}catch(e:Throwable){o+="$id: ERROR ${e.message?:e::class.simpleName?:"exception"}"}}
        safe("V46 LDPC transport"){val tr=NrLdpcV46.build(IntArray(100){it and 1},200);tr.codeBlocks.size==1&&tr.tbCrcBits==124}
        safe("V47 Polar"){val x=IntArray(8){it and 1};NrPolarV47.decode(NrPolarV47.encode(x,16),8).contentEquals(x)}
        safe("V48 PDCCH"){NrPdcchV48.encode(IntArray(16){it and 1},0x1234,4).qpsk.isNotEmpty()}
        safe("V49 resource grid"){val g=NrResourceGridV49(24);g.reserve(NrChannelV49.PDSCH,0,0,2,0,4)==96}
        safe("V50 numerology"){NrNumerologyV50.timing(NrScsV50.SCS30,0).slotDurationUs==500.0}
        safe("V51 HARQ"){var h=NrHarqV51.start(NrHarqProcessV51(0));h=NrHarqV51.feedback(h,false);h.rv==2}
        safe("V52 MAC"){NrMacV52.multiplex(listOf(NrLogicalChannelV52(1,1,0,100)),listOf(NrMacCeV52.BSR),50).payloadBytes==50}
        safe("V53 RLC"){val rlcTx=NrRlcV53.transmit(NrRlcEntityV53(NrRlcModeV53.AM),byteArrayOf(1,2));rlcTx.second.sn==0}
        safe("V54 PDCP"){val k=ByteArray(16){it.toByte()};val pc=NrPdcpV54.protect(k,byteArrayOf(1),NrPdcpV54.count(0,0),1,0);NrPdcpV54.verify(k,pc,1,0)?.contentEquals(byteArrayOf(1))==true}
        safe("V55 RRC"){val rr=NrRrcV55.decode(NrRrcV55.encode(NrRrcPduV55(NrRrcMessageTypeV55.SETUP,1,byteArrayOf(3))));rr.transactionId==1}
        safe("V56 NAS"){NrNasV56.step(NrNasContextV56("001"),NrNasProcedureV56.REGISTRATION).state==NrNasStateV56.REGISTRATION_REQUESTED}
        safe("V57 GTP-U"){val gp=NrGtpPacketV57(1,2,byteArrayOf(4),3);NrGtpV57.decode(NrGtpV57.encode(gp)).teid==1L}
        safe("V58 split"){NrLoopbackSplitV58().send(NrUeGnbV58.ueToGnb("u1",byteArrayOf(1)))}
        safe("V59 harness"){NrConformanceV59.run().pass}
        safe("V60 matrix"){NrComplianceV60.matrix().size>=8}
        // Additive V82-V84 execution gate. This runs the new coding modules from the existing suite;
        // it does not replace or alter the V46-V60 checks above.
        safe("V82 LDPC geometry"){val payload=IntArray(4000){it and 1};val bg=NrLdpcV82.selectBaseGraph(payload.size,.5);NrLdpcV82.geometry(bg).rows>0&&NrLdpcV82.allowedLiftingSizes().size==51}
        safe("V83 transport CRC/segmentation"){val payload=IntArray(4000){it and 1};val bg=NrLdpcV82.selectBaseGraph(payload.size,.5);val seg=NrTransportV83.segment(payload,bg);seg.codeBlocks.isNotEmpty()&&seg.kPrime>0&&seg.liftingSize>0}
        safe("V84 rate matching"){val payload=IntArray(4000){it and 1};val bg=NrLdpcV82.selectBaseGraph(payload.size,.5);val seg=NrTransportV83.segment(payload,bg);val encodedBits=NrLdpcV82.encodedSize(bg,seg.liftingSize);val outputBits=(encodedBits-2*seg.liftingSize).coerceAtLeast(1);NrRateMatchingV84.rateMatch(IntArray(encodedBits),NrRateMatchingV84.Config(bg,seg.liftingSize,0,outputBits)).isNotEmpty()}
        val cert=false;return NrV46V60Result(o,o.none{it.contains("FAIL")||it.contains("ERROR")},cert,o.joinToString("  •  "))}}
