package com.example.nrsimulator.web

import com.example.nrsimulator.NrCanonicalExecution
import com.example.nrsimulator.NrPhyMappingV89
import com.sun.net.httpserver.HttpExchange
import java.net.URLDecoder

/** Web adapter for the canonical forward PHY execution path. */
object NrCanonicalWebAdapter {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2 && URLDecoder.decode(p[0], "UTF-8") == key) URLDecoder.decode(p[1], "UTF-8") else null
        }.firstOrNull() ?: fallback
    }

    private fun esc(v: String): String = v.replace("\\", "\\\\").replace("\"", "\\\"")

    private fun modulation(value: String): NrPhyMappingV89.Modulation = when (value.uppercase()) {
        "BPSK" -> NrPhyMappingV89.Modulation.BPSK
        "QPSK" -> NrPhyMappingV89.Modulation.QPSK
        "16-QAM", "QAM16" -> NrPhyMappingV89.Modulation.QAM16
        "64-QAM", "QAM64" -> NrPhyMappingV89.Modulation.QAM64
        "256-QAM", "QAM256" -> NrPhyMappingV89.Modulation.QAM256
        else -> throw IllegalArgumentException("unsupported modulation")
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
            val result = NrCanonicalExecution.run(
                NrCanonicalExecution.Config(
                    payloadBits = q(exchange, "payloadBits", "512").toIntOrNull()?.coerceIn(32, 10000) ?: 512,
                    targetCodeRate = q(exchange, "codeRate", "0.5").toDoubleOrNull()?.coerceIn(0.01, 0.99) ?: 0.5,
                    modulation = modulation(q(exchange, "modulation", "QPSK")),
                    layers = q(exchange, "layers", "1").toIntOrNull()?.coerceIn(1, 8) ?: 1,
                    snrDb = q(exchange, "snr", "20").toDoubleOrNull() ?: 20.0,
                    rv = q(exchange, "rv", "0").toIntOrNull()?.coerceIn(0, 3) ?: 0
                )
            )
            val stages = result.stages.entries.joinToString(",") { "\"${esc(it.key)}\":${it.value}" }
            reply(exchange, "{\"ok\":true,\"version\":\"CANONICAL\",\"passed\":${result.passed},\"payloadBits\":${result.payloadBits},\"recoveredBits\":${result.recoveredBits},\"crcPassed\":${result.crcPassed},\"ldpcPassed\":${result.ldpcPassed},\"evm\":${result.evm},\"stages\":{$stages},\"notes\":\"${esc(result.notes)}\"}")
        } catch (e: Exception) {
            reply(exchange, "{\"ok\":false,\"version\":\"CANONICAL\",\"error\":\"${esc(e.message ?: e::class.simpleName.orEmpty())}\"}", 400)
        }
    }
}
