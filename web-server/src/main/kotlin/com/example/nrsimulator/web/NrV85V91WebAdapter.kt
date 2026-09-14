package com.example.nrsimulator.web

import com.example.nrsimulator.NrCodingChainV87
import com.example.nrsimulator.NrDmrsMimoV90
import com.example.nrsimulator.NrLdpcV85
import com.example.nrsimulator.NrPhyMappingV89
import com.example.nrsimulator.NrPdschPuschV91
import com.sun.net.httpserver.HttpExchange

/**
 * Web-only adapter for the additive V85-V91 PHY primitives.
 * It does not replace or modify the existing web execution path; callers can
 * expose it from the existing LabApi route without changing nr-core.
 */
object NrV85V91WebAdapter {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull {
            val p = it.split('=', limit = 2)
            if (p.size == 2 && java.net.URLDecoder.decode(p[0], "UTF-8") == key) {
                java.net.URLDecoder.decode(p[1], "UTF-8")
            } else null
        }.firstOrNull() ?: fallback
    }

    private fun esc(v: String) = v.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r")

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
            val payloadBits = q(exchange, "payloadBits", "1000").toIntOrNull()?.coerceIn(8, 100_000) ?: 1000
            val targetRate = q(exchange, "coding", "0.5").toDoubleOrNull()?.coerceIn(0.1, 0.95) ?: 0.5
            val outputBits = q(exchange, "outputBits", "512").toIntOrNull()?.coerceAtLeast(8) ?: 512
            val rv = q(exchange, "rv", "0").toIntOrNull()?.coerceIn(0, 3) ?: 0
            val subcarriers = q(exchange, "subcarriers", "12").toIntOrNull()?.coerceAtLeast(1) ?: 12
            val ofdmSymbols = q(exchange, "symbols", "14").toIntOrNull()?.coerceAtLeast(1) ?: 14
            val layers = q(exchange, "layers", "2").toIntOrNull()?.coerceIn(1, 8) ?: 2
            val modulation = when (q(exchange, "mod", "QPSK").uppercase()) {
                "BPSK" -> NrPhyMappingV89.Modulation.BPSK
                "16-QAM", "QAM16" -> NrPhyMappingV89.Modulation.QAM16
                "64-QAM", "QAM64" -> NrPhyMappingV89.Modulation.QAM64
                "256-QAM", "QAM256" -> NrPhyMappingV89.Modulation.QAM256
                else -> NrPhyMappingV89.Modulation.QPSK
            }
            val bits = IntArray((payloadBits / modulation.bitsPerSymbol) * modulation.bitsPerSymbol) { it and 1 }
            val table = NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG1)
            val codingResult = NrCodingChainV87.encode(bits, targetRate, table, outputBits, rv)
            val qam = NrPhyMappingV89.modulate(bits.copyOf(bits.size - (bits.size % modulation.bitsPerSymbol)), modulation)
            val mapped = NrPhyMappingV89.mapLayers(qam, layers)
            val grid = NrPdschPuschV91.mapData(
                NrPdschPuschV91.Channel.PDSCH,
                qam,
                subcarriers,
                ofdmSymbols,
                layers
            )
            val dmrs = NrDmrsMimoV90.dmrs(minOf(qam.size, subcarriers * ofdmSymbols))
            val estimate = NrDmrsMimoV90.estimate(dmrs, dmrs)
            val equalized = NrDmrsMimoV90.equalize(dmrs, estimate, 1e-3)
            val rateMatchedBits = codingResult.rateMatched.sumOf { it.size }
            reply(exchange, """{"ok":true,"version":"V85-V91","config":{"payloadBits":${bits.size},"codingRate":$targetRate,"outputBitsPerCodeBlock":$outputBits,"rv":$rv,"modulation":"${modulation.name}","layers":$layers,"subcarriers":$subcarriers,"ofdmSymbols":$ofdmSymbols},"coding":{"baseGraph":"${codingResult.baseGraph}","liftingSize":${codingResult.liftingSize},"liftingSet":${codingResult.liftingSet},"codeBlocks":${codingResult.codeBlocks.size},"codewords":${codingResult.codewords.size},"rateMatchedBits":$rateMatchedBits},"phy":{"qamSymbols":${qam.size},"layerSymbols":${mapped.sumOf { it.size }},"gridResources":${grid.resources.size},"gridOccupancy":${NrPdschPuschV91.occupancy(grid)},"dmrsSymbols":${dmrs.size},"channelEstimate":${estimate.size},"equalizedSymbols":${equalized.symbols.size},"postEqSinrDb":${equalized.postEqSinrDb}}}""")
        } catch (e: Exception) {
            reply(exchange, "{\"ok\":false,\"version\":\"V85-V91\",\"error\":\"${esc(e.message ?: e::class.simpleName ?: "error")}\"}", 400)
        }
    }
}
