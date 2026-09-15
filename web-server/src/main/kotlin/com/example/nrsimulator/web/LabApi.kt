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
        // Canonical forward PHY execution. Historical V19-V110 routes remain unchanged.
        if (version == "CANONICAL" || version == "FINAL" || version == "V111") {
            NrCanonicalWebAdapter.handle(exchange)
            return
        }
        // Additive V85-V91 dispatch: preserve the existing V19-V63 LabApi path unchanged.
        if (version in setOf("V85", "V86", "V87", "V88", "V89", "V90", "V91")) {
            NrV85V91WebAdapter.handle(exchange)
            return
        }
        // Additive V92-V100 dispatch: preserve all earlier LabApi cases unchanged.
        if (version in setOf("V92", "V93", "V94", "V95", "V96", "V97", "V98", "V99", "V100")) {
            LabApiV92V100.handle(exchange)
            return
        }
        // Additive V101 exact DM-RS dispatch; all V1-V100 routes remain unchanged.
        if (version == "V101") {
            NrV101WebAdapter.handle(exchange)
            return
        }
        // Additive V102-V110 dispatch; all V1-V101 routes remain unchanged.
        if (version in setOf("V102", "V103", "V104", "V105", "V106", "V107", "V108", "V109", "V110")) {
            NrV102V110WebAdapter.handle(exchange)
            return
        }
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
                else -> ok("unsupported version")
            }
            reply(exchange, response)
        } catch (e: Exception) {
            reply(exchange, "{\"ok\":false,\"version\":\"${esc(version)}\",\"error\":\"${esc(e.message ?: e::class.simpleName.orEmpty())}\"}", 400)
        }
    }
}
