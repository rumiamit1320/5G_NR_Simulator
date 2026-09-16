package com.example.nrsimulator.web

import com.example.nrsimulator.NrCanonicalExecution
import com.example.nrsimulator.NrPhyMappingV89
import com.sun.net.httpserver.HttpExchange

/** Web adapter for the canonical execution path; historical LabApi routes remain unchanged. */
object NrCanonicalExecutionWebAdapter {
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
            val modulation = q(exchange, "modulation", "QPSK").uppercase().let {
                when (it) {
                    "BPSK" -> NrPhyMappingV89.Modulation.BPSK
                    "QPSK" -> NrPhyMappingV89.Modulation.QPSK
                    "16-QAM", "QAM16" -> NrPhyMappingV89.Modulation.QAM16
                    "64-QAM", "QAM64" -> NrPhyMappingV89.Modulation.QAM64
                    "256-QAM", "QAM256" -> NrPhyMappingV89.Modulation.QAM256
                    else -> throw IllegalArgumentException("unsupported modulation: $it")
                }
            }
            val layers = q(exchange, "layers", "2").toIntOrNull()?.coerceIn(1, 8) ?: 2
            val tx = q(exchange, "tx", layers.toString()).toIntOrNull()?.coerceIn(layers, 8) ?: layers
            val rx = q(exchange, "rx", layers.toString()).toIntOrNull()?.coerceIn(layers, 8) ?: layers
            val cfg = NrCanonicalExecution.Config(
                payloadBits = q(exchange, "payloadBits", "512").toIntOrNull()?.coerceIn(1, 200_000) ?: 512,
                targetCodeRate = q(exchange, "codeRate", "0.5").toDoubleOrNull()?.coerceIn(0.01, 0.99) ?: 0.5,
                modulation = modulation,
                layers = layers,
                snrDb = q(exchange, "snr", "20").toDoubleOrNull()?.coerceIn(-20.0, 80.0) ?: 20.0,
                rv = q(exchange, "rv", "0").toIntOrNull()?.coerceIn(0, 3) ?: 0,
                txAntennas = tx,
                rxAntennas = rx,
                channelSeed = q(exchange, "seed", "1107").toIntOrNull() ?: 1107
            )
            val r = NrCanonicalExecution.run(cfg)
            val stages = r.stages.entries.joinToString(",") { "\"${esc(it.key)}\":${it.value}" }
            reply(exchange, "{\"ok\":true,\"version\":\"CANONICAL\",\"passed\":${r.passed},\"layers\":${r.detectedLayers},\"txAntennas\":${cfg.txAntennas},\"rxAntennas\":${cfg.rxAntennas},\"postSinrDb\":${r.postSinrDb},\"evm\":${r.evm},\"crcPassed\":${r.crcPassed},\"ldpcPassed\":${r.ldpcPassed},\"stages\":{$stages},\"notes\":\"${esc(r.notes)}\"}")
        } catch (e: Exception) {
            reply(exchange, "{\"ok\":false,\"version\":\"CANONICAL\",\"error\":\"${esc(e.message ?: e::class.simpleName ?: "error")}\"}", 400)
        }
    }
}
