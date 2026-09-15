package com.example.nrsimulator.web

import com.example.nrsimulator.NrV102V110Additive
import com.sun.net.httpserver.HttpExchange

/** Web-only adapter for the additive V102-V110 pipeline. */
object NrV102V110WebAdapter {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull { part ->
            val p = part.split('=', limit = 2)
            if (p.size == 2 && java.net.URLDecoder.decode(p[0], "UTF-8") == key) java.net.URLDecoder.decode(p[1], "UTF-8") else null
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
        try {
            val version = q(exchange, "version", "V102").uppercase()
            val report = NrV102V110Additive.report()
            val stage = report.checks[version] ?: report.passed
            val checks = report.checks.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
            val stages = report.stages.joinToString(",") { "\"${it.replace("\"", "\\\"")}\"" }
            reply(exchange, "{\"ok\":${report.passed},\"version\":\"$version\",\"stagePass\":$stage,\"checks\":{$checks},\"stages\":[$stages]}")
        } catch (e: Exception) {
            reply(exchange, "{\"ok\":false,\"error\":\"${e.message?.replace("\\", "\\\\")?.replace("\"", "\\\"") ?: "unknown"}\"}", 400)
        }
    }
}
