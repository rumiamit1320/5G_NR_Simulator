package com.example.nrsimulator.web

import com.example.nrsimulator.NrChannelV95
import com.example.nrsimulator.NrDmrsV92
import com.example.nrsimulator.NrHarqCsiV97
import com.example.nrsimulator.NrLinkAdaptationV96
import com.example.nrsimulator.NrMimoV94
import com.example.nrsimulator.NrOfdmV93
import com.example.nrsimulator.NrRachV98
import com.example.nrsimulator.NrRefVectorsV99
import com.example.nrsimulator.NrResearchGradeV100
import com.example.nrsimulator.NrPhyMappingV89
import com.sun.net.httpserver.HttpExchange

/** Web-only additive adapter for V92-V100; V19-V91 routes remain unchanged. */
object NrV92V100WebAdapter {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull { item ->
            val p = item.split('=', limit = 2)
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
    fun handle(exchange: HttpExchange) {
        try {
            val version = q(exchange, "version", "V100").uppercase()
            if (version == "V100") {
                val r = NrResearchGradeV100.run()
                val checks = r.stages.entries.joinToString(",") { "\"${it.key}\":${it.value}" }
                reply(exchange, "{\"ok\":${r.passed},\"version\":\"V92-V100\",\"checks\":{$checks},\"notes\":\"${esc(r.notes)}\"}", if (r.passed) 200 else 500)
                return
            }
            val snr = q(exchange, "snrDb", "10").toDoubleOrNull()?.coerceIn(-30.0, 60.0) ?: 10.0
            when (version) {
                "V92" -> {
                    val sub = q(exchange, "subcarriers", "24").toIntOrNull()?.coerceIn(1, 4096) ?: 24
                    val symbols = q(exchange, "symbols", "14").toIntOrNull()?.coerceIn(1, 14) ?: 14
                    val layers = q(exchange, "layers", "2").toIntOrNull()?.coerceIn(1, 8) ?: 2
                    val r = NrDmrsV92.generate(NrDmrsV92.Config(sub, symbols, layers))
                    reply(exchange, "{\"ok\":true,\"version\":\"V92\",\"dmrsSymbols\":${r.values.size},\"positions\":${r.positions.size},\"comb\":2}")
                }
                "V93" -> {
                    val n = q(exchange, "fftSize", "16").toIntOrNull()?.coerceIn(2, 128) ?: 16
                    val cp = q(exchange, "cp", "4").toIntOrNull()?.coerceIn(0, n - 1) ?: 4
                    val grid = Array(2) { Array(n) { i -> NrPhyMappingV89.Complex(if (i and 1 == 0) 1.0 else -1.0, 0.0) } }
                    val w = NrOfdmV93.modulate(grid, n, cp); val back = NrOfdmV93.demodulate(w)
                    reply(exchange, "{\"ok\":true,\"version\":\"V93\",\"fftSize\":$n,\"cyclicPrefix\":$cp,\"samples\":${w.samples.size},\"evm\":${NrOfdmV93.evm(grid[0], back[0])}}")
                }
                "V94" -> {
                    val h = NrMimoV94.identity(2)
                    val r = NrMimoV94.equalize2x2(h, arrayOf(com.example.nrsimulator.NrDmrsMimoV90.Complex(1.0,0.0),com.example.nrsimulator.NrDmrsMimoV90.Complex(-1.0,0.0)), 1e-3)
                    reply(exchange, "{\"ok\":true,\"version\":\"V94\",\"layers\":${r.layers.size},\"postEqSinrDb\":${r.sinrDb}}")
                }
                "V95" -> {
                    val input = Array(16) { com.example.nrsimulator.NrDmrsMimoV90.Complex(1.0, 0.0) }
                    val r = NrChannelV95.apply(input, NrChannelV95.tdlA(snr), snr)
                    reply(exchange, "{\"ok\":true,\"version\":\"V95\",\"samples\":${r.samples.size},\"snrDb\":${r.snrDb},\"noiseVariance\":${r.noiseVariance}}")
                }
                "V96" -> {
                    val r = NrLinkAdaptationV96.select(snr)
                    reply(exchange, "{\"ok\":true,\"version\":\"V96\",\"sinrDb\":${r.sinrDb},\"modulation\":\"${r.modulation}\",\"layers\":${r.layers},\"targetCodeRate\":${r.targetCodeRate},\"cqi\":${r.cqi}}")
                }
                "V97" -> {
                    val p = NrHarqCsiV97.newProcess(0, doubleArrayOf(1.0, -0.5)); val c = NrHarqCsiV97.combine(p, doubleArrayOf(1.0, 1.0), 1); val csi = NrHarqCsiV97.csi(snr, 2)
                    reply(exchange, "{\"ok\":true,\"version\":\"V97\",\"rounds\":${c.rounds},\"ack\":${NrHarqCsiV97.ack(c)},\"cqi\":${csi.cqi},\"ri\":${csi.ri},\"pmi\":${csi.pmi}}")
                }
                "V98" -> {
                    val p = NrRachV98.generate(); val d = NrRachV98.detect(p, p.sequence)
                    reply(exchange, "{\"ok\":true,\"version\":\"V98\",\"preambleLength\":${p.length},\"detected\":${d.detected},\"metric\":${d.metric},\"timingOffset\":${d.timingOffset}}")
                }
                "V99" -> reply(exchange, "{\"ok\":true,\"version\":\"V99\",\"referenceVectors\":${NrRefVectorsV99.smokeVectors().size},\"passed\":${NrRefVectorsV99.check().all { it }}}")
                else -> reply(exchange, "{\"ok\":false,\"version\":\"V92-V100\",\"error\":\"unsupported version\"}", 400)
            }
        } catch (e: Exception) {
            reply(exchange, "{\"ok\":false,\"version\":\"V92-V100\",\"error\":\"${esc(e.message ?: e::class.simpleName ?: "error")}\"}", 400)
        }
    }
}
