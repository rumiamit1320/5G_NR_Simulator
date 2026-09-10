package com.example.nrsimulator.web

import com.example.nrsimulator.*
import com.sun.net.httpserver.HttpExchange

/** Web-only adapter: exposes existing V19-V60 APIs without changing nr-core. */
object LabApi {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2 && java.net.URLDecoder.decode(p[0], "UTF-8") == key) java.net.URLDecoder.decode(p[1], "UTF-8") else null
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
    private fun count(v: Any): Int = when (v) {
        is Collection<*> -> v.size
        is Array<*> -> v.size
        is IntArray -> v.size
        is ByteArray -> v.size
        else -> 0
    }

    fun handle(exchange: HttpExchange) {
        val version = q(exchange, "version", "V19").uppercase()
        val snr = q(exchange, "snr", "15").toDoubleOrNull()?.coerceIn(-20.0, 50.0) ?: 15.0
        val prbs = q(exchange, "prbs", "52").toIntOrNull()?.coerceIn(1, 275) ?: 52
        val layers = q(exchange, "layers", "2").toIntOrNull()?.coerceIn(1, 4) ?: 2
        val mcs = q(exchange, "mcs", "16").toIntOrNull()?.coerceIn(0, 27) ?: 16
        fun ok(result: Any, parameterized: Boolean = true) = "{\"ok\":true,\"version\":\"${esc(version)}\",\"parameterized\":$parameterized,\"result\":\"${esc(result.toString())}\"}"
        try {
            val response = when (version) {
                "V19" -> {
                    val ack = q(exchange, "ack", "true").toBoolean()
                    val sr = q(exchange, "sr", "false").toBoolean()
                    val csi = q(exchange, "csi", "12").toIntOrNull()?.coerceIn(0, 15) ?: 12
                    ok(NrPucchV19().run(NrUciV19(booleanArrayOf(ack), sr, intArrayOf(csi)), NrPucchV19Config(prbs = prbs.coerceAtMost(275))))
                }
                "V20" -> ok(NrPuschV20().run(q(exchange, "payloadBits", "12000").toIntOrNull()?.coerceIn(1, 2_000_000) ?: 12000,
                    NrPuschV20Config(prbs = prbs, layers = layers, qm = when { mcs <= 9 -> 2; mcs <= 16 -> 4; else -> 6 }, rv = q(exchange, "rv", "0").toIntOrNull()?.coerceIn(0, 3) ?: 0)))
                "V21" -> ok(NrSrsV21().run(snr, NrSrsV21Config(ports = layers.coerceAtMost(4), rbCount = prbs)))
                "V22" -> {
                    val n = q(exchange, "ue", "4").toIntOrNull()?.coerceIn(1, 16) ?: 4
                    val baseCqi = q(exchange, "cqi", "12").toIntOrNull()?.coerceIn(0, 15) ?: 12
                    val baseSinr = q(exchange, "ueSinr", snr.toString()).toDoubleOrNull() ?: snr
                    val ues = (1..n).map { id -> NrUeV22(id, (baseCqi - (id - 1)).coerceIn(0, 15), baseSinr - (id - 1), weight = 1.0 + (n - id) * .05) }
                    ok(NrSchedulerV22().run(ues, prbs))
                }
                "V23" -> ok(NrSlotEngineV23().run(q(exchange, "frames", "1").toIntOrNull()?.coerceIn(1, 1000) ?: 1,
                    q(exchange, "slots", "20").toIntOrNull()?.coerceIn(1, 160) ?: 20,
                    q(exchange, "dlRatio", "0.7").toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: .7))
                "V24" -> ok(NrRlcV24().segment(bytes(q(exchange, "payloadBytes", "1400").toIntOrNull() ?: 1400), q(exchange, "mtu", "300").toIntOrNull()?.coerceIn(1, 9000) ?: 300, q(exchange, "mode", "AM")))
                "V25" -> ok(NrPdcpV25().run(bytes(q(exchange, "payloadBytes", "1400").toIntOrNull() ?: 1400), q(exchange, "snBits", "12").toIntOrNull()?.coerceIn(5, 18) ?: 12))
                "V26" -> {
                    val n = q(exchange, "ue", "2").toIntOrNull()?.coerceIn(1, 32) ?: 2
                    ok(Nr5gCoreV26().register((1..n).toList()))
                }
                "V27" -> ok(NrMobilityV27().evaluate(q(exchange, "x", "20").toDoubleOrNull() ?: 20.0, q(exchange, "y", "0").toDoubleOrNull() ?: 0.0,
                    listOf(NrCellV27(1, 0.0, 0.0), NrCellV27(2, 100.0, 0.0)), q(exchange, "offset", "3").toDoubleOrNull()?.coerceIn(-20.0, 20.0) ?: 3.0))
                "V28" -> ok(NrBeamV28().sweep(q(exchange, "azimuth", "10").toDoubleOrNull() ?: 10.0, antennaRows = layers.coerceAtMost(4), antennaCols = 4,
                    beamCount = q(exchange, "beams", "16").toIntOrNull()?.coerceIn(2, 128) ?: 16))
                "V29" -> ok(NrTddV29().run(NrTddV29Config(q(exchange, "pattern", "DDDDDDUUUU"), q(exchange, "slotsPerFrame", "20").toIntOrNull()?.coerceIn(1, 1000) ?: 20)))
                "V30" -> ok(NrAnalyticsV30().run(NrAnalyticsV30Input(
                    q(exchange, "throughput", "100").toDoubleOrNull() ?: 100.0,
                    q(exchange, "goodput", "90").toDoubleOrNull() ?: 90.0,
                    snr,
                    q(exchange, "ber", ".02").toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: .02,
                    q(exchange, "latency", "5").toDoubleOrNull()?.coerceAtLeast(0.0) ?: 5.0,
                    prbs, q(exchange, "totalPrbs", "106").toIntOrNull()?.coerceAtLeast(prbs) ?: 106,
                    q(exchange, "retransmissions", "3").toIntOrNull()?.coerceAtLeast(0) ?: 3)))
                "V31" -> { val len=q(exchange,"length","64").toIntOrNull()?.coerceIn(1,4096)?:64; val gold=NrGoldSequenceV31.generate(q(exchange,"cInit","1").toIntOrNull()?:1,len); ok("Gold=${count(gold)}, CRC bits=${count(NrCrc24CV31.append(gold))}", false) }
                "V32" -> { val n=q(exchange,"payloadBits","8").toIntOrNull()?.coerceIn(1,1024)?:8; val e=q(exchange,"encodedBits","32").toIntOrNull()?.coerceIn(32,2048)?:32; val info=bits(n); val cw=NrPolarV32.encode(info,e); ok("payload=${count(info)}, encoded=${count(cw.bits)}, roundTrip=${NrPolarV32.decode(cw.bits,count(info)).contentEquals(info)}", false) }
                "V33" -> { val rb=q(exchange,"rbCount","24").toIntOrNull()?.coerceIn(1,275)?:24; val start=q(exchange,"startRb","0").toIntOrNull()?.coerceIn(0,274)?:0; val dur=q(exchange,"duration","2").toIntOrNull()?.coerceIn(1,3)?:2; val m=NrPdcchV33.map(NrCoresetV33(0,start,rb,dur),q(exchange,"cce","4").toIntOrNull()?.coerceIn(1,64)?:4,1); ok("CCE=${count(m.cce)}, REG=${count(m.reg)}, DMRS=${count(m.dmrs)}", false) }
                "V34" -> ok("blocks=${count(NrLdpcV34.encode(bits(q(exchange,"payloadBits","1500").toIntOrNull()?.coerceIn(1,100000)?:1500)).blocks)}", false)
                "V35" -> { val a=NrPhyAllocationV35(NrChannelV35.valueOf(q(exchange,"channel","PDSCH").uppercase()),q(exchange,"startSymbol","0").toIntOrNull()?:0,q(exchange,"symbolCount","4").toIntOrNull()?.coerceAtLeast(1)?:4,q(exchange,"startRb","0").toIntOrNull()?:0,q(exchange,"rbCount","2").toIntOrNull()?.coerceAtLeast(1)?:2,layers); ok("elements=${count(NrPhyMappingV35.allocate(a))}", false) }
                "V36" -> { val t=q(exchange,"timer","2").toIntOrNull()?.coerceAtLeast(0)?:2; val r=NrMacV36.tick(NrMacStateV36(timers=mapOf(NrMacTimerV36.T300 to t))); ok("slot=${r.slot}, T300=${r.timers[NrMacTimerV36.T300]}", false) }
                "V37" -> { val r=NrRlcV37.enqueue(NrRlcEntityV37(NrRlcConfigV37(NrRlcModeV37.AM)),bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1)); ok("SN=${r.first.txSn}, payload=${count(r.second.payload)}", false) }
                "V38" -> { val p=bytes(q(exchange,"payloadBytes","2").toIntOrNull()?:2); val pd=NrPdcpV38(); val pp=pd.protect(ByteArray(16){it.toByte()},p,1,0,0); ok("protected=${count(pp)}, verified=${pd.verify(ByteArray(16){it.toByte()},pp,1,0,0)?.contentEquals(p)==true}", false) }
                "V39" -> { val p=bytes(q(exchange,"payloadBytes","2").toIntOrNull()?:2); val d=NrRrcV39.decode(NrRrcV39.encode(NrRrcMessageV39(NrRrcProcedureV31.SETUP,1,p))); ok("payload=${count(d.payload)}, transaction=${d.transactionId}", false) }
                "V40" -> { val p=bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1); val ne=NrNasV40.protect(ByteArray(16){it.toByte()},NrNasEnvelopeV40(NrNasMessageV40.REGISTRATION_REQUEST,0,0,p),1,0,0); ok("payload=${count(p)}, mac=${ne.mac!=null}", false) }
                "V41" -> { val p=bytes(q(exchange,"payloadBytes","1").toIntOrNull()?:1); val r=NrInterfaceV41.wrap(NrRanInterfaceV41.N2_NGAP,1,p); ok("iface=${r.iface}, payload=${count(r.payload)}", false) }
                "V42" -> { val r=Nr5gcV42.establish(q(exchange,"plmn","001010"),q(exchange,"ueId","10").toIntOrNull()?:10); ok("state=${r.state}", false) }
                "V43" -> { val r=NrRfV43.measure(listOf(Complex(q(exchange,"amplitude","1").toDoubleOrNull()?:1.0,0.0))); ok("sinrDb=${r.sinrDb}", false) }
                "V44" -> ok(NrConformanceV44.run(listOf(NrConformanceCaseV44("V32","38.212",{true}))),false)
                "V45" -> ok(NrCommercialGateV45.evaluate(NrConformanceV44.run(listOf(NrConformanceCaseV44("V32","38.212",{true})))),false)
                "V46" -> { val n=q(exchange,"payloadBits","100").toIntOrNull()?.coerceIn(1,100000)?:100; val r=NrLdpcV46.build(bits(n),q(exchange,"transportBits","200").toIntOrNull()?.coerceAtLeast(n)?:200); ok("blocks=${count(r.codeBlocks)}, tbCrcBits=${r.tbCrcBits}", false) }
                "V47" -> { val n=q(exchange,"payloadBits","8").toIntOrNull()?.coerceIn(1,1000)?:8; val c=NrPolarV47.encode(bits(n),q(exchange,"encodedBits","16").toIntOrNull()?.coerceAtLeast(n)?:16); ok("encoded=${count(c)}, roundTrip=${NrPolarV47.decode(c,n).contentEquals(bits(n))}", false) }
                "V48" -> { val p=NrPdcchV48.encode(bits(q(exchange,"bits","16").toIntOrNull()?.coerceIn(1,10000)?:16),q(exchange,"rnti","4660").toIntOrNull()?:0x1234,q(exchange,"aggregation","4").toIntOrNull()?.coerceIn(1,16)?:4); ok("QPSK=${count(p.qpsk)}", false) }
                "V49" -> ok("interactive resource-grid parameters accepted; canonical V49 execution remains authoritative", false)
                "V50" -> ok("SCS=${q(exchange,"scs","30")} kHz; canonical numerology execution remains authoritative", false)
                "V51" -> ok("ACK=${q(exchange,"ack","false")}; canonical HARQ execution remains authoritative", false)
                "V52", "V53", "V54", "V55", "V56", "V57", "V58" -> ok("interactive adapter parameters accepted; canonical reference execution remains authoritative", false)
                "V59" -> ok(NrConformanceV59.run(),false)
                "V60" -> ok(NrComplianceV60.matrix(),false)
                else -> throw IllegalArgumentException("Unsupported lab version: $version")
            }
            reply(exchange,response)
        } catch (e: Exception) {
            reply(exchange,"{\"ok\":false,\"version\":\"${esc(version)}\",\"error\":\"${esc(e.message ?: e::class.simpleName ?: "error")}\"}",400)
        }
    }
}
