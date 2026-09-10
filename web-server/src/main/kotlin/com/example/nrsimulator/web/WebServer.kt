package com.example.nrsimulator.web

import com.example.nrsimulator.*
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors

private fun q(exchange: HttpExchange, key: String, fallback: String): String {
    val raw = exchange.requestURI.rawQuery ?: return fallback
    return raw.split('&').asSequence().mapNotNull {
        val p = it.split('=', limit = 2)
        if (p.size == 2 && URLDecoder.decode(p[0], "UTF-8") == key) URLDecoder.decode(p[1], "UTF-8") else null
    }.firstOrNull() ?: fallback
}
private fun esc(v: String): String = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
private fun json(exchange: HttpExchange, body: String, code: Int = 200) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
    exchange.responseHeaders.add("Cache-Control", "no-store")
    exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
    exchange.sendResponseHeaders(code, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
}

private fun simulation(exchange: HttpExchange) {
    val snr = q(exchange, "snr", "15").toDoubleOrNull()?.coerceIn(-10.0, 40.0) ?: 15.0
    val prbs = q(exchange, "prbs", "52").toIntOrNull()?.coerceIn(1, 106) ?: 52
    val mod = q(exchange, "mod", "64-QAM")
    val order = when (mod) { "QPSK" -> 4; "16-QAM" -> 16; "256-QAM" -> 256; else -> 64 }
    val scs = q(exchange, "scs", "30").toIntOrNull()?.let { if (it == 15 || it == 30 || it == 60) it else 30 } ?: 30
    val ue = q(exchange, "ue", "4").toIntOrNull()?.coerceIn(1, 16) ?: 4
    val tx = q(exchange, "tx", "4").toIntOrNull()?.coerceIn(1, 4) ?: 4
    val rx = q(exchange, "rx", "4").toIntOrNull()?.coerceIn(1, 4) ?: 4
    val mcs = q(exchange, "mcs", "16").toIntOrNull()?.coerceIn(0, 27) ?: 16
    val layers = q(exchange, "layers", "2").toIntOrNull()?.coerceIn(1, 4) ?: 2
    val coding = q(exchange, "coding", "0.75").toDoubleOrNull()?.coerceIn(0.1, 0.99) ?: 0.75
    val harq = q(exchange, "harq", "true").toBoolean()
    val tick = q(exchange, "tick", "0").toLongOrNull() ?: 0L
    val qm = when (order) { 4 -> 2; 16 -> 4; 64 -> 6; else -> 8 }

    val base = Simulator().run(scs, prbs, order, snr, 20.0, 1.0)
    val sys = AdvancedSimulator().run(ue, prbs, order, snr, ChannelModel.RAYLEIGH, Scheduler.PROPORTIONAL_FAIR, tx, rx, coding, harq, tick)
    val phy3 = NrPhyV3().run(NrPhyConfig(mcs = mcs, layers = layers, snrDb = snr, prbs = prbs))
    val phy4 = NrPhyV4().run(NrV4Config(mcs = mcs, layers = layers, snrDb = snr, prbs = prbs, scsKHz = scs))
    val ldpc5 = NrLdpcV5().run(NrLdpcV5Config(payloadBits = 480, z = 48, snrDb = snr, iterations = 8))
    val conf7 = NrConformanceV7().run(NrV7Config(a = 4000, targetRate = coding, rv = tick.toInt() and 3, qm = qm, layers = layers, nRe = maxOf(1, prbs * 12 * 12)))
    val transport6 = NrTransportV6().run(NrTransportV6Config(payloadBits = 4000, targetCodeRate = 0.5, rv = tick.toInt() and 3, snrDb = snr, zHint = 48))
    val transport9 = NrTransportV9().run(NrTransportV9Config(payloadBits = 300, targetCodeRate = 0.5, rv = tick.toInt() and 3, qm = qm, layers = layers, nRe = maxOf(600, prbs * 12 * 10), snrDb = snr))
    val ofdm10 = NrOfdmV10().run(NrOfdmV10Config(scsKHz = scs, prbs = prbs, snrDb = snr))
    val ldpc8 = NrLdpcV8().run(NrLdpcV8Config(payloadBits = 480, z = 48, snrDb = snr, iterations = 12, normalization = 0.8, runNoisyTest = true))
    val a = minOf(tx, 4); val r = minOf(rx, 4); val l = minOf(a, r)
    val phy11 = NrPhyV11().run(NrPhyV11Config(scsKHz = scs, prbs = prbs.coerceAtMost(52), dmrsSymbol = 2, txAntennas = a, rxAntennas = r, dmrsPorts = l, snrDb = snr, channelModel = "FREQUENCY_SELECTIVE", equalizer = "MMSE", seed = 0x1101 + tick.toInt()))
    val phy12 = NrPhyV12().run(NrPhyV12Config(scsKHz = scs, prbs = prbs.coerceAtMost(52), dmrsSymbol = 2, payloadBits = 300, targetCodeRate = 0.5, rv = tick.toInt() and 3, qm = qm, txAntennas = minOf(a, 2), rxAntennas = minOf(r, 2), layers = minOf(l, 2), snrDb = snr, channelModel = "FREQUENCY_SELECTIVE", equalizer = "MMSE", seed = 0x1201 + tick.toInt()))
    val phy13 = NrPhyV13().run(NrPhyV13Config(scsKHz = scs, prbs = prbs.coerceAtMost(52), dmrsSymbol = 2, payloadBits = 300, targetCodeRate = 0.5, rv = tick.toInt() and 3, qm = qm, txAntennas = minOf(a, 2), rxAntennas = minOf(r, 2), layers = minOf(l, 2), snrDb = snr, channelModel = "FREQUENCY_SELECTIVE", equalizer = "MMSE", ptRsEnabled = true, cfoHz = 250.0, sfoPpm = 2.0, phaseNoiseStdRad = 0.015, seed = 0x1301 + tick.toInt()))
    val csi = NrCsiRsV14().run(NrCsiRsV14Config(prbs = prbs.coerceAtMost(52), txAntennas = a, rxAntennas = r, csiPorts = minOf(a, r, 4), snrDb = snr, channelModel = "FREQUENCY_SELECTIVE", seed = 0x1401 + tick.toInt()))
    val mimo = NrMimoV15().run(NrMimoV15Config(txAntennas = a, rxAntennas = r, layers = minOf(l, a, r), prbs = prbs.coerceAtMost(52), snrDb = snr, channelModel = "FREQUENCY_SELECTIVE", seed = 0x1501 + tick.toInt()))
    val channel = NrChannelV16(NrChannelV16Config(model = "TDL-C", txAntennas = a, rxAntennas = r, scsKHz = scs, prbs = prbs.coerceAtMost(52), carrierGHz = 3.5, velocityKmh = 30.0, rmsDelayNs = 100.0, spatialCorrelation = 0.35, losKDb = 9.0, snrDb = snr, timeIndex = tick.toInt(), seed = 0x1601 + tick.toInt())).summary()
    val adaptation = NrLinkAdaptationV17().run(NrLinkAdaptationV17Config(sinrDb = csi.sinrDb, cqi = csi.cqi, rank = csi.rank, layers = layers.coerceAtMost(csi.rank), prbs = prbs, linkMarginDb = 1.5, maxHarqTx = if (harq) 4 else 1, seed = 0x1701 + tick.toInt()))
    val pdcch = NrPdcchV18().run(rnti = 0x1234, bwpPrbs = prbs, slot = tick.toInt(), dci = NrDciV18(frequencyDomainAssignment = (prbs / 4).coerceAtLeast(1), mcs = adaptation.selectedMcs, rv = adaptation.rvHistory.firstOrNull() ?: 0, harqProcess = tick.toInt() and 15, layers = adaptation.rank.coerceIn(1, 4)), aggregationLevel = 4, candidateIndex = 0)
    val e2e = NrEndToEndV30().run(NrEndToEndConfigV30(ueCount = ue.coerceIn(1, 4), prbs = prbs.coerceAtMost(52), scsKHz = scs, snrDb = snr, layers = layers.coerceIn(1, 2), frames = 1, slotsPerFrame = 2, payloadBytesPerUe = 1024, harqEnabled = harq))
    val stack = NrSystemStackV23V30().run()
    val pts = base.constellation.take(96).joinToString(",", prefix = "[", postfix = "]") { "[${it.re},${it.im}]" }
    val rxs = base.rx.take(96).joinToString(",", prefix = "[", postfix = "]") { "[${it.re},${it.im}]" }
    val body = """
        {"ok":true,"config":{"snr":$snr,"prbs":$prbs,"scs":$scs,"mod":"${esc(mod)}","ue":$ue,"tx":$tx,"rx":$rx,"mcs":$mcs,"layers":$layers,"codingRate":$coding,"harq":$harq},
        "primary":{"bits":${base.bits},"symbols":${base.symbols},"snr":${base.snr},"evm":${base.evm},"throughputMbps":${base.throughputMbps},"ber":${base.ber},"constellation":$pts,"rx":$rxs},
        "phy":{"v3":"${esc(phy3.toString())}","v4":"${esc(phy4.toString())}","v5":"${esc(ldpc5.toString())}","v6":"${esc(transport6.toString())}","v7":"${esc(conf7.toString())}","v8":"${esc(ldpc8.toString())}","v9":"${esc(transport9.toString())}","v10":"${esc(ofdm10.toString())}","v11":"${esc(phy11.toString())}","v12":"${esc(phy12.toString())}","v13":"${esc(phy13.toString())}"},
        "advanced":{"system":"${esc(sys.toString())}","csi":"${esc(csi.toString())}","mimo":"${esc(mimo.toString())}","channel":"${esc(channel.toString())}","linkAdaptation":"${esc(adaptation.toString())}","pdcch":"${esc(pdcch.toString())}","stack":"${esc(stack.toString())}","e2e":"${esc(e2e.toString())}"}}
    """.trimIndent()
    json(exchange, body)
}

private fun fullSuite(exchange: HttpExchange) {
    val results = listOf(
        "V7 conformance" to runCatching { NrConformanceV7().run(NrV7Config()) },
        "V8 LDPC" to runCatching { NrLdpcV8().run(NrLdpcV8Config()) },
        "V9 transport" to runCatching { NrTransportV9().run(NrTransportV9Config()) },
        "V10 OFDM" to runCatching { NrOfdmV10().run(NrOfdmV10Config()) },
        "V11 PHY" to runCatching { NrPhyV11().run(NrPhyV11Config()) },
        "V12 PHY" to runCatching { NrPhyV12().run(NrPhyV12Config()) },
        "V13 PHY" to runCatching { NrPhyV13().run(NrPhyV13Config()) },
        "V14 CSI-RS" to runCatching { NrCsiRsV14().run(NrCsiRsV14Config()) },
        "V15 MIMO" to runCatching { NrMimoV15().run(NrMimoV15Config()) },
        "V17 link adaptation" to runCatching { NrLinkAdaptationV17().run(NrLinkAdaptationV17Config()) },
        "V18 PDCCH" to runCatching { NrPdcchV18().run() },
        "V19-V30 regression" to runCatching { NrV19V30Tests.runAll() },
        "V23-V30 system stack" to runCatching { NrSystemStackV23V30().run() },
        "V30 end-to-end" to runCatching { NrEndToEndV30().run() },
        "V31 conformance" to runCatching { NrV31ConformanceTests.run() },
        "V32-V45 tests" to runCatching { NrV32V45Tests.run() },
        "V46-V60 tests" to runCatching { NrV46V60Tests.run() }
    )
    val body = results.joinToString(",", prefix = "{\"ok\":true,\"suite\":[", postfix = "]}") { (name, r) ->
        val value = r.fold({ esc(it.toString()) }, { "ERROR: ${esc(it.message ?: it.javaClass.simpleName)}" })
        val passed = when (val result = r.getOrNull()) {
            is NrV31ConformanceResult -> result.pass
            is NrV46V60Result -> result.pass
            is List<*> -> result.isNotEmpty() && result.all { !it.toString().contains("FAIL") }
            else -> !value.startsWith("ERROR:")
        }
        "{\"name\":\"${esc(name)}\",\"result\":\"$value\",\"pass\":$passed}"
    }
    json(exchange, body)
}

private fun static(exchange: HttpExchange) {
    val requested = exchange.requestURI.path.removePrefix("/").ifBlank { "index.html" }
    if (requested.contains("..")) { json(exchange, "{\"ok\":false,\"error\":\"invalid path\"}", 400); return }
    val path = "static/$requested"
    val data = Thread.currentThread().contextClassLoader.getResourceAsStream(path)?.use { it.readBytes() }
        ?: Thread.currentThread().contextClassLoader.getResourceAsStream("static/index.html")?.use { it.readBytes() }
        ?: run { json(exchange, "{\"ok\":false,\"error\":\"not found\"}", 404); return }
    val type = when {
        requested.endsWith(".css") -> "text/css; charset=utf-8"
        requested.endsWith(".js") -> "application/javascript; charset=utf-8"
        requested.endsWith(".html") -> "text/html; charset=utf-8"
        else -> "application/octet-stream"
    }
    exchange.responseHeaders.add("Content-Type", type)
    exchange.sendResponseHeaders(200, data.size.toLong())
    exchange.responseBody.use { it.write(data) }
}

fun main() {
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val server = HttpServer.create(InetSocketAddress("0.0.0.0", port), 0)
    server.createContext("/api/simulate") { ex -> runCatching { simulation(ex) }.onFailure { json(ex, "{\"ok\":false,\"error\":\"${esc(it.message ?: "simulation failed")}\"}", 500) } }
    server.createContext("/api/full-suite") { ex -> runCatching { fullSuite(ex) }.onFailure { json(ex, "{\"ok\":false,\"error\":\"${esc(it.message ?: "suite failed")}\"}", 500) } }
    server.createContext("/api/health") { ex -> json(ex, "{\"ok\":true,\"engine\":\"Kotlin NR reference engine\"}") }
    server.createContext("/") { ex -> runCatching { static(ex) }.onFailure { json(ex, "{\"ok\":false,\"error\":\"${esc(it.message ?: "static failed")}\"}", 500) } }
    server.executor = Executors.newFixedThreadPool(8)
    server.start()
    println("5G NR Web Simulator listening on http://0.0.0.0:$port")
}