package com.example.nrsimulator.web

import com.example.nrsimulator.NrDmrsV101
import com.example.nrsimulator.NrDmrsV101Tests
import com.sun.net.httpserver.HttpExchange

/** Web-only V101 adapter; existing V1-V100 LabApi behavior is unchanged. */
object NrV101WebAdapter {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2 && java.net.URLDecoder.decode(p[0], "UTF-8") == key) java.net.URLDecoder.decode(p[1], "UTF-8") else null
        }.firstOrNull() ?: fallback
    }

    private fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")

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
            val kind = q(exchange, "kind", "PDSCH").uppercase()
            val mapping = when (q(exchange, "mapping", "A").uppercase()) {
                "B" -> NrDmrsV101.MappingType.B
                else -> NrDmrsV101.MappingType.A
            }
            val type = when (q(exchange, "type", "1").uppercase()) {
                "2", "TYPE2" -> NrDmrsV101.ConfigurationType.TYPE2
                else -> NrDmrsV101.ConfigurationType.TYPE1
            }
            val maxLength = when (q(exchange, "maxLength", "1").uppercase()) {
                "2", "LEN2" -> NrDmrsV101.MaxLength.LEN2
                else -> NrDmrsV101.MaxLength.LEN1
            }
            val additional = when (q(exchange, "additionalPosition", "pos2").lowercase()) {
                "pos0" -> NrDmrsV101.AdditionalPosition.POS0
                "pos1" -> NrDmrsV101.AdditionalPosition.POS1
                "pos3" -> NrDmrsV101.AdditionalPosition.POS3
                else -> NrDmrsV101.AdditionalPosition.POS2
            }
            val ports = q(exchange, "ports", "1000").split(',').map { it.trim().toInt() }.toIntArray()
            val cfg = NrDmrsV101.Config(
                mappingType = mapping,
                configurationType = type,
                maxLength = maxLength,
                additionalPosition = additional,
                typeAPosition3 = q(exchange, "typeAPosition", "pos2").lowercase() == "pos3",
                allocationStartSymbol = q(exchange, "startSymbol", "0").toInt(),
                allocationSymbols = q(exchange, "symbolCount", "14").toInt(),
                slot = q(exchange, "slot", "0").toInt(),
                nId = q(exchange, "nId", "0").toInt(),
                nSCID = q(exchange, "nSCID", "0").toInt(),
                symbolsPerSlot = q(exchange, "symbolsPerSlot", "14").toInt(),
                startSubcarrier = q(exchange, "startSubcarrier", "0").toInt(),
                resourceBlocks = q(exchange, "resourceBlocks", "1").toInt(),
                ports = ports
            )
            if (kind == "TEST") {
                val r = NrDmrsV101Tests.run()
                reply(exchange, "{\"ok\":${r.passed},\"version\":\"V101\",\"checks\":${r.checks.entries.joinToString(prefix = "{", postfix = "}") { "\"${esc(it.key)}\":${it.value}" }}}", if (r.passed) 200 else 500)
                return
            }
            val r = if (kind == "PUSCH") NrDmrsV101.pusch(cfg) else NrDmrsV101.pdsch(cfg)
            val resources = r.resources.take(256).joinToString(prefix = "[", postfix = "]") {
                "{\"port\":${it.port},\"k\":${it.subcarrier},\"l\":${it.symbol},\"re\":${it.value.re},\"im\":${it.value.im},\"cInit\":${it.cInit},\"m\":${it.sequenceIndex},\"cdmGroup\":${it.cdmGroup},\"kPrime\":${it.kPrime},\"lPrime\":${it.lPrime}}"
            }
            val body = "{\"ok\":true,\"version\":\"V101\",\"kind\":\"${esc(kind)}\",\"mapping\":\"${mapping.name}\",\"configurationType\":\"${type.name}\",\"maxLength\":\"${maxLength.name}\",\"dmrsSymbols\":${r.dmrsSymbols.joinToString(prefix = "[", postfix = "]")},\"resourceCount\":${r.resources.size},\"returnedResources\":${r.resources.take(256).size},\"resources\":$resources}"
            reply(exchange, body)
        } catch (t: Throwable) {
            reply(exchange, "{\"ok\":false,\"version\":\"V101\",\"error\":\"${esc(t.message ?: t::class.simpleName ?: "error")}\"}", 400)
        }
    }
}
