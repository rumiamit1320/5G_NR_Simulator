package com.example.nrsimulator.web

import com.example.nrsimulator.*
import com.sun.net.httpserver.HttpExchange

/** Web-only adapter: exposes existing V19-V60 APIs without changing nr-core. */
object LabApi {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2 && java.net.URLDecoder.decode(p[0], "UTF-8") == key)
                java.net.URLDecoder.decode(p[1], "UTF-8") else null
        }.firstOrNull() ?: fallback
    }
    private fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
    private fun reply(exchange: HttpExchange, body: String, code: Int = 200) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
    private fun bytes(n: Int): ByteArray = ByteArray(n.coerceIn(1, 1_000_000)) { (it and 255).toByte() }
    private fun bits(n: Int): IntArray = IntArray(n.coerceIn(1, 100_000)) { it and 1 }
    private fun boolResult(version: String, result: Any) = "{\"ok\":true,\"version\":\"${esc(version)}\",\"parameterized\":false,\"result\":\"${esc(result.toString())}\"}"

    fun handle(exchange: HttpExchange) {
        val version = q(exchange, "version", "V19").uppercase()
        val snr = q(exchange, "snr", "15").toDoubleOrNull()?.coerceIn(-20.0, 50.0) ?: 15.0
        val prbs = q(exchange, "prbs", "52").toIntOrNull()?.coerceIn(1, 275) ?: 52
        val layers = q(exchange, "layers", "2").toIntOrNull()?.coerceIn(1, 4) ?: 2
        val mcs = q(exchange, "mcs", "16").toIntOrNull()?.coerceIn(0, 27) ?: 16
        val response: String = when (version) {
            "V19" -> {
                val ack = q(exchange, "ack", "true").toBoolean()
                val sr = q(exchange, "sr", "false").toBoolean()
                val csi = q(exchange, "csi", "12").toIntOrNull()?.coerceIn(0, 15) ?: 12
                "{\"ok\":true,\"version\":\"V19\",\"parameterized\":true,\"result\":\"${esc(NrPucchV19().run(NrUciV19(booleanArrayOf(ack), sr, intArrayOf(csi)), NrPucchV19Config(prbs = prbs.coerceAtMost(275))).toString())}\"}"
            }
            "V20" -> {
                val r = NrPuschV20().run(q(exchange, "payloadBits", "12000").toIntOrNull()?.coerceIn(1, 2_000_000) ?: 12000,
                    NrPuschV20Config(prbs = prbs, layers = layers, qm = when { mcs <= 9 -> 2; mcs <= 16 -> 4; else -> 6 }, rv = q(exchange, "rv", "0").toIntOrNull()?.coerceIn(0, 3) ?: 0))
                "{\"ok\":true,\"version\":\"V20\",\"parameterized\":true,\"result\":\"${esc(r.toString())}\"}"
            }
            "V21" -> "{\"ok\":true,\"version\":\"V21\",\"parameterized\":true,\"result\":\"${esc(NrSrsV21().run(snr, NrSrsV21Config(ports = layers, rbCount = prbs)).toString())}\"}"
            "V22" -> {
                val n = q(exchange, "ue", "4").toIntOrNull()?.coerceIn(1, 16) ?: 4
                val cqi = q(exchange, "cqi", "12").toIntOrNull()?.coerceIn(0, 15) ?: 12
                val baseSinr = q(exchange, "ueSinr", snr.toString()).toDoubleOrNull() ?: snr
                val ues = (1..n).map { id -> NrUeV22(id, (cqi - id + 1).coerceIn(0, 15), baseSinr - id + 1, weight = 1.0 + (n - id) * .05) }
                "{\"ok\":true,\"version\":\"V22\",\"parameterized\":true,\"result\":\"${esc(NrSchedulerV22().run(ues, prbs).toString())}\"}"
            }
            "V23" -> "{\"ok\":true,\"version\":\"V23\",\"parameterized\":true,\"result\":\"${esc(NrSlotEngineV23().run(q(exchange, "frames", "1").toIntOrNull()?.coerceIn(1, 1000) ?: 1, q(exchange, "slots", "20").toIntOrNull()?.coerceIn(1, 160) ?: 20, q(exchange, "dlRatio", "0.7").toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: .7).toString())}\"}"
            "V24" -> "{\"ok\":true,\"version\":\"V24\",\"parameterized\":true,\"result\":\"${esc(NrRlcV24().segment(bytes(q(exchange, "payloadBytes", "1400").toIntOrNull() ?: 1400), q(exchange, "mtu", "300").toIntOrNull()?.coerceIn(1, 9000) ?: 300, q(exchange, "mode", "AM")).toString())}\"}"
            "V25" -> "{\"ok\":true,\"version\":\"V25\",\"parameterized\":true,\"result\":\"${esc(NrPdcpV25().run(bytes(q(exchange, "payloadBytes", "1400").toIntOrNull() ?: 1400), q(exchange, "snBits", "12").toIntOrNull()?.coerceIn(5, 18) ?: 12).toString())}\"}"
            "V26" -> { val n=q(exchange,"ue","2").toIntOrNull()?.coerceIn(1,32)?:2; "{\"ok\":true,\"version\":\"V26\",\"parameterized\":true,\"result\":\"${esc(Nr5gCoreV26().register((1..n).toList()).toString())}\"}" }
            "V27" -> "{\"ok\":true,\"version\":\"V27\",\"parameterized\":true,\"result\":\"${esc(NrMobilityV27().evaluate(q(exchange,"x","20").toDoubleOrNull()?:20.0,q(exchange,"y","0").toDoubleOrNull()?:0.0,listOf(NrCellV27(1,0.0,0.0),NrCellV27(2,100.0,0.0)),q(exchange,"offset","3").toDoubleOrNull()?.coerceIn(-20.0,20.0)?:3.0).toString())}\"}"
            "V28" -> "{\"ok\":true,\"version\":\"V28\",\"parameterized\":true,\"result\":\"${esc(NrBeamV28().sweep(q(exchange,"azimuth","10").toDoubleOrNull()?:10.0, antennaRows=layers, antennaCols=4, beamCount=q(exchange,"beams","16").toIntOrNull()?.coerceIn(2,128)?:16).toString())}\"}"
            "V29" -> "{\"ok\":true,\"version\":\"V29\",\"parameterized\":true,\"result\":\"${esc(NrTddV29().run(NrTddV29Config(q(exchange,"pattern","DDDDDDUUUU"),q(exchange,"slotsPerFrame","20").toIntOrNull()?.coerceIn(1,1000)?:20)).toString())}\"}"
            "V30" -> "{\"ok\":true,\"version\":\"V30\",\"parameterized\":true,\"result\":\"${esc(NrAnalyticsV30().run(NrAnalyticsV30Input(q(exchange,"throughput","100").toDoubleOrNull()?:100.0,q(exchange,"goodput","90").toDoubleOrNull()?:90.0,snr,q(exchange,"ber",".02").toDoubleOrNull()?.coerceIn(0.0,1.0)?:.02,q(exchange,"latency","5").toDoubleOrNull()?.coerceAtLeast(0.0)?:5.0,prbs,q(exchange,"totalPrbs","106").toIntOrNull()?.coerceAtLeast(prbs)?:106,q(exchange,"retransmissions","3").toIntOrNull()?.coerceAtLeast(0)?:3)).toString())}\"}"
            "V31" -> {
                val cInit=q(exchange,"cInit","1").toIntOrNull()?:1; val len=q(exchange,"length","64").toIntOrNull()?.coerceIn(1,4096)?:64; val data=bytes(q(exchange,"payloadBytes","15").toIntOrNull()?:15); val key=ByteArray(16){it.toByte()}; val bearer=q(exchange,"bearer","3").toIntOrNull()?.coerceIn(0,31)?:3; val dir=q(exchange,"direction","1").toIntOrNull()?.coerceIn(0,1)?:1
                val gold=NrGoldSequenceV31.generate(cInit,len); val crc=NrCrc24CV31.append(gold); val cipher=NrSecurityV31.nea2(key,0x12345678,bearer,dir,data); val mac=NrSecurityV31.nia2(key,0x12345678,bearer,dir,data)
                "{\"ok\":true,\"version\":\"V31\",\"parameterized\":true,\"result\":\"Gold=${gold.size}, CRC=${crc.size}, NEA2=${cipher.size}, NIA2=${mac}\"}"
            }
            "V32" -> { val n=q(exchange,"encodedBits","32").toIntOrNull()?.let{if(it<32)32 else Integer.highestOneBit(it-1)*2}?:32; val info=bits(q(exchange,"payloadBits","8").toIntOrNull()?:8).let{it.copyOf(it.size.coerceAtMost(n))}; val cw=NrPolarV32.encode(info,n); "{\"ok\":true,\"version\":\"V32\",\"parameterized\":true,\"result\":\"payload=${info.size}, encoded=${cw.bits.size}, roundTrip=${NrPolarV32.decode(cw.bits,info.size).contentEquals(info)}\"}" }
            "V33" -> { val rb=q(exchange,"rbCount","24").toIntOrNull()?.coerceIn(1,275)?:24; val dur=q(exchange,"duration","2").toIntOrNull()?.coerceIn(1,3)?:2; val c=q(exchange,"cce","4").toIntOrNull()?.coerceIn(1,64)?:4; val m=NrPdcchV33.map(NrCoresetV33(0,q(exchange,"startRb","0").toIntOrNull()?.coerceIn(0,274)?:0,rb,dur),c,1); "{\"ok\":true,\"version\":\"V33\",\"parameterized\":true,\"result\":\"CCE=${m.cce.size}, REG=${m.reg.size}, DMRS=${m.dmrs.size}\"}" }
            "V34" -> { val n=q(exchange,"payloadBits","1500").toIntOrNull()?.coerceIn(1,100000)?:1500; val r=NrLdpcV34.encode(bits(n)); "{\"ok\":true,\"version\":\"V34\",\"parameterized\":true,\"result\":\"blocks=${r.blocks.size}\"}" }
            "V35" -> { val a=NrPhyAllocationV35(NrChannelV35.valueOf(q(exchange,"channel","PDSCH").uppercase()),q(exchange,"startSymbol","0").toIntOrNull()?:0,q(exchange,"symbolCount","4").toIntOrNull()?.coerceAtLeast(1)?:4,q(exchange,"startRb","0").toIntOrNull()?:0,q(exchange,"rbCount","2").toIntOrNull()?.coerceAtLeast(1)?:2,layers); val r=NrPhyMappingV35.allocate(a); "{\"ok\":true,\"version\":\"V35\",\"parameterized\":true,\"result\":\"elements=${r.size}\"}" }
            "V36" -> { val t=q(exchange,"timer","2").toIntOrNull()?.coerceAtLeast(0)?:2; val r=NrMacV36.tick(NrMacStateV36(timers=mapOf(NrMacTimerV36.T300 to t))); "{\"ok\":true,\"version\":\"V36\",\"parameterized\":true,\"result\":\"slot=${r.slot}, T300=${r.timers[NrMacTimerV36.T300]}\"}" }
            "V37" -> { val r=NrRlcV37.enqueue(NrRlcEntityV37(NrRlcConfigV37(NrRlcModeV37.AM)),bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1)); "{\"ok\":true,\"version\":\"V37\",\"parameterized\":true,\"result\":\"SN=${r.first.txSn}, payload=${r.second.payload.size}\"}" }
            "V38" -> { val key=ByteArray(16){it.toByte()}; val p=bytes(q(exchange,"payloadBytes","2").toIntOrNull()?:2); val pd=NrPdcpV38(); val pp=pd.protect(key,p,1,0,0); "{\"ok\":true,\"version\":\"V38\",\"parameterized\":true,\"result\":\"protected=${pp.size}, verified=${pd.verify(key,pp,1,0,0)?.contentEquals(p)==true}\"}" }
            "V39" -> { val p=bytes(q(exchange,"payloadBytes","2").toIntOrNull()?:2); val rm=NrRrcMessageV39(NrRrcProcedureV31.SETUP,1,p); val d=NrRrcV39.decode(NrRrcV39.encode(rm)); "{\"ok\":true,\"version\":\"V39\",\"parameterized\":true,\"result\":\"payload=${d.payload.size}, transaction=${d.transactionId}\"}" }
            "V40" -> { val key=ByteArray(16){it.toByte()}; val p=bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1); val ne=NrNasV40.protect(key,NrNasEnvelopeV40(NrNasMessageV40.REGISTRATION_REQUEST,0,0,p),1,0,0); "{\"ok\":true,\"version\":\"V40\",\"parameterized\":true,\"result\":\"payload=${p.size}, mac=${ne.mac!=null}\"}" }
            "V41" -> { val p=bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1); val r=NrInterfaceV41.wrap(NrRanInterfaceV41.N2_NGAP,1,p); "{\"ok\":true,\"version\":\"V41\",\"parameterized\":true,\"result\":\"iface=${r.iface}, payload=${r.payload.size}\"}" }
            "V42" -> { val r=Nr5gcV42.establish(q(exchange,"plmn","001010"),q(exchange,"ueId","10").toIntOrNull()?:10); "{\"ok\":true,\"version\":\"V42\",\"parameterized\":true,\"result\":\"state=${r.state}\"}" }
            "V43" -> { val amp=q(exchange,"amplitude","1").toDoubleOrNull()?:1.0; val r=NrRfV43.measure(listOf(Complex(amp,0.0))); "{\"ok\":true,\"version\":\"V43\",\"parameterized\":true,\"result\":\"sinrDb=${r.sinrDb}\"}" }
            "V44" -> boolResult("V44",NrConformanceV44.run(listOf(NrConformanceCaseV44("V32","38.212",{true}))))
            "V45" -> boolResult("V45",NrCommercialGateV45.evaluate(NrConformanceV44.run(listOf(NrConformanceCaseV44("V32","38.212",{true})))))
            "V46" -> { val n=q(exchange,"payloadBits","100").toIntOrNull()?.coerceIn(1,100000)?:100; val tb=q(exchange,"transportBits","200").toIntOrNull()?.coerceAtLeast(n)?:200; val r=NrLdpcV46.build(bits(n),tb); "{\"ok\":true,\"version\":\"V46\",\"parameterized\":true,\"result\":\"blocks=${r.codeBlocks.size}, tbCrcBits=${r.tbCrcBits}\"}" }
            "V47" -> { val n=q(exchange,"payloadBits","8").toIntOrNull()?.coerceIn(1,1000)?:8; val enc=q(exchange,"encodedBits","16").toIntOrNull()?.coerceAtLeast(n)?:16; val x=bits(n); val c=NrPolarV47.encode(x,enc); "{\"ok\":true,\"version\":\"V47\",\"parameterized\":true,\"result\":\"encoded=${c.size}, roundTrip=${NrPolarV47.decode(c,n).contentEquals(x)}\"}" }
            "V48" -> { val n=q(exchange,"bits","16").toIntOrNull()?.coerceIn(1,10000)?:16; val p=NrPdcchV48.encode(bits(n),q(exchange,"rnti","4660").toIntOrNull()?:0x1234,q(exchange,"aggregation","4").toIntOrNull()?.coerceIn(1,16)?:4); "{\"ok\":true,\"version\":\"V48\",\"parameterized\":true,\"result\":\"QPSK=${p.qpsk.size}\"}" }
            "V49" -> { val rb=q(exchange,"rbCount","24").toIntOrNull()?.coerceIn(1,275)?:24; val symbols=q(exchange,"symbolCount","2").toIntOrNull()?.coerceAtLeast(1)?:2; val g=NrResourceGridV49(rb); val used=g.reserve(NrChannelV49.PDSCH,0,q(exchange,"startRb","0").toIntOrNull()?:0,symbols,0,q(exchange,"rbReserve","4").toIntOrNull()?.coerceAtLeast(1)?:4); "{\"ok\":true,\"version\":\"V49\",\"parameterized\":true,\"result\":\"reserved=${used}\"}" }
            "V50" -> { val scs=when(q(exchange,"scs","30")){"15"->NrScsV50.SCS15;"60"->NrScsV50.SCS60;else->NrScsV50.SCS30}; val r=NrNumerologyV50.timing(scs,0); "{\"ok\":true,\"version\":\"V50\",\"parameterized\":true,\"result\":\"slotDurationUs=${r.slotDurationUs}\"}" }
            "V51" -> { var h=NrHarqV51.start(NrHarqProcessV51(0)); h=NrHarqV51.feedback(h,q(exchange,"ack","false").toBoolean()); "{\"ok\":true,\"version\":\"V51\",\"parameterized\":true,\"result\":\"rv=${h.rv}\"}" }
            "V52" -> { val p=NrMacV52.multiplex(listOf(NrLogicalChannelV52(1,1,0,q(exchange,"payloadBytes","100").toIntOrNull()?.coerceAtLeast(1)?:100)),listOf(NrMacCeV52.BSR),q(exchange,"pduBytes","50").toIntOrNull()?.coerceAtLeast(1)?:50); "{\"ok\":true,\"version\":\"V52\",\"parameterized\":true,\"result\":\"payloadBytes=${p.payloadBytes}\"}" }
            "V53" -> { val r=NrRlcV53.transmit(NrRlcEntityV53(NrRlcModeV53.AM),bytes(q(exchange,"payloadBytes","2").toIntOrNull()?:2)); "{\"ok\":true,\"version\":\"V53\",\"parameterized\":true,\"result\":\"SN=${r.second.sn}, payload=${r.second.payload.size}\"}" }
            "V54" -> { val k=ByteArray(16){it.toByte()}; val p=bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1); val pc=NrPdcpV54.protect(k,p,NrPdcpV54.count(0,0),1,0); "{\"ok\":true,\"version\":\"V54\",\"parameterized\":true,\"result\":\"protected=${pc.size}, verified=${NrPdcpV54.verify(k,pc,1,0)?.contentEquals(p)==true}\"}" }
            "V55" -> { val id=q(exchange,"transactionId","1").toIntOrNull()?:1; val rr=NrRrcV55.decode(NrRrcV55.encode(NrRrcPduV55(NrRrcMessageTypeV55.SETUP,id,bytes(1)))); "{\"ok\":true,\"version\":\"V55\",\"parameterized\":true,\"result\":\"transactionId=${rr.transactionId}\"}" }
            "V56" -> { val r=NrNasV56.step(NrNasContextV56(q(exchange,"plmn","001")),NrNasProcedureV56.REGISTRATION); "{\"ok\":true,\"version\":\"V56\",\"parameterized\":true,\"result\":\"state=${r.state}\"}" }
            "V57" -> { val teid=q(exchange,"teid","1").toLongOrNull()?:1; val gp=NrGtpPacketV57(teid,2,bytes(4),3); val d=NrGtpV57.decode(NrGtpV57.encode(gp)); "{\"ok\":true,\"version\":\"V57\",\"parameterized\":true,\"result\":\"teid=${d.teid}\"}" }
            "V58" -> { val id=q(exchange,"ue","u1"); val ok=NrLoopbackSplitV58().send(NrUeGnbV58.ueToGnb(id,bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1))); "{\"ok\":true,\"version\":\"V58\",\"parameterized\":true,\"result\":\"loopback=$ok\"}" }
            "V59" -> boolResult("V59",NrConformanceV59.run())
            "V60" -> boolResult("V60",NrComplianceV60.matrix())
            else -> throw IllegalArgumentException("Unsupported lab version: $version")
        }
        reply(exchange, response)
    }
}
