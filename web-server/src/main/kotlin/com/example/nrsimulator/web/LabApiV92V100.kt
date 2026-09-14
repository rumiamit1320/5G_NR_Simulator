package com.example.nrsimulator.web

import com.example.nrsimulator.NrCanonicalPhy
import com.example.nrsimulator.NrResearchGradeV100
import com.example.nrsimulator.NrV92V100Tests
import com.sun.net.httpserver.HttpExchange

/** Additive V92-V100 API surface. Existing LabApi V19-V91 remains untouched. */
object LabApiV92V100 {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2 && java.net.URLDecoder.decode(p[0], "UTF-8") == key) {
                java.net.URLDecoder.decode(p[1], "UTF-8")
            } else null
        }.firstOrNull() ?: fallback
    }

    private fun reply(exchange: HttpExchange, body: String, code: Int = 200) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "application/json; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.responseHeaders.add("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(code, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    fun handle(exchange: HttpExchange) {
        val version = q(exchange, "version", "V100").uppercase()
        try {
            when (version) {
                "V92" -> reply(exchange, "{\"ok\":true,\"version\":\"V92\",\"stage\":\"DMRS\",\"components\":${jsonComponents()}}")
                "V93" -> reply(exchange, "{\"ok\":true,\"version\":\"V93\",\"stage\":\"OFDM\",\"fft\":\"DFT/IFFT\"}")
                "V94" -> reply(exchange, "{\"ok\":true,\"version\":\"V94\",\"stage\":\"MIMO\",\"equalizer\":\"2x2\"}")
                "V95" -> reply(exchange, "{\"ok\":true,\"version\":\"V95\",\"stage\":\"CHANNEL\",\"model\":\"TDL-A-like/AWGN\"}")
                "V96" -> reply(exchange, "{\"ok\":true,\"version\":\"V96\",\"stage\":\"LINK_ADAPTATION\",\"sinrDb\":${q(exchange, "snr", "20")}}")
                "V97" -> reply(exchange, "{\"ok\":true,\"version\":\"V97\",\"stage\":\"HARQ_CSI\",\"process\":${q(exchange, "process", "0")}}")
                "V98" -> reply(exchange, "{\"ok\":true,\"version\":\"V98\",\"stage\":\"PRACH\",\"root\":${q(exchange, "root", "1")}}")
                "V99" -> reply(exchange, "{\"ok\":true,\"version\":\"V99\",\"stage\":\"REFERENCE_VECTORS\",\"deterministic\":true}")
                "V100" -> {
                    val result = NrV92V100Tests.run()
                    reply(exchange, "{\"ok\":${result.passed},\"version\":\"V100\",\"stage\":\"REGRESSION\",\"passed\":${result.passed},\"checks\":${result.checks.entries.joinToString(",", "{", "}") { "\"${it.key}\":${it.value}" }},\"components\":${jsonComponents()}}", if (result.passed) 200 else 500)
                }
                else -> reply(exchange, "{\"ok\":false,\"error\":\"unsupported V92-V100 version\"}", 400)
            }
        } catch (t: Throwable) {
            reply(exchange, "{\"ok\":false,\"version\":\"$version\",\"error\":\"${escape(t.message ?: t::class.simpleName ?: "error")}\"}", 500)
        }
    }

    private fun jsonComponents(): String = NrCanonicalPhy.components().joinToString(",", "[", "]") { "\"${escape(it)}\"" }
    private fun escape(v: String): String = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
}
