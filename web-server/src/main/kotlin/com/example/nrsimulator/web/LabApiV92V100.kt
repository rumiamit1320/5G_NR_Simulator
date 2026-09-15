package com.example.nrsimulator.web

import com.example.nrsimulator.NrCanonicalPhy
import com.example.nrsimulator.NrEndToEndV100
import com.example.nrsimulator.NrV92V100Tests
import com.sun.net.httpserver.HttpExchange

/** Additive V92-V100 API surface. Existing LabApi V19-V91 remains untouched. */
object LabApiV92V100 {
    private fun q(exchange: HttpExchange, key: String, fallback: String): String {
        val raw = exchange.requestURI.rawQuery ?: return fallback
        return raw.split('&').asSequence().mapNotNull {
            val p = it.split('=', limit = 2)
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

    private fun esc(v: String): String = v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")

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
                    val regression = NrV92V100Tests.run()
                    val e2e = NrEndToEndV100.run(
                        NrEndToEndV100.Config(
                            payloadBits = q(exchange, "payloadBits", "512").toIntOrNull()?.coerceIn(64, 4096) ?: 512,
                            targetCodeRate = q(exchange, "codeRate", "0.5").toDoubleOrNull()?.coerceIn(0.1, 0.95) ?: 0.5,
                            modulation = when (q(exchange, "mod", "QPSK").uppercase()) {
                                "BPSK" -> com.example.nrsimulator.NrPhyMappingV89.Modulation.BPSK
                                "16-QAM", "QAM16" -> com.example.nrsimulator.NrPhyMappingV89.Modulation.QAM16
                                "64-QAM", "QAM64" -> com.example.nrsimulator.NrPhyMappingV89.Modulation.QAM64
                                "256-QAM", "QAM256" -> com.example.nrsimulator.NrPhyMappingV89.Modulation.QAM256
                                else -> com.example.nrsimulator.NrPhyMappingV89.Modulation.QPSK
                            },
                            layers = q(exchange, "layers", "1").toIntOrNull()?.coerceIn(1, 8) ?: 1,
                            snrDb = q(exchange, "snr", "80").toDoubleOrNull()?.coerceIn(20.0, 100.0) ?: 80.0,
                            rv = q(exchange, "rv", "0").toIntOrNull()?.coerceIn(0, 3) ?: 0
                        )
                    )
                    val ok = regression.passed && e2e.passed
                    reply(exchange, "{\"ok\":$ok,\"version\":\"V100\",\"stage\":\"END_TO_END_PHY\",\"regressionPassed\":${regression.passed},\"e2ePassed\":${e2e.passed},\"payloadBits\":${e2e.payloadBits},\"recoveredBits\":${e2e.recoveredBits},\"baseGraph\":\"${esc(e2e.baseGraph)}\",\"liftingSize\":${e2e.liftingSize},\"codeBlocks\":${e2e.codeBlocks},\"rateMatchedBits\":${e2e.rateMatchedBits},\"qamSymbols\":${e2e.qamSymbols},\"ofdmSymbols\":${e2e.ofdmSymbols},\"fftSize\":${e2e.fftSize},\"evm\":${e2e.evm},\"snrDb\":${e2e.snrDb},\"ldpcPassed\":${e2e.ldpcPassed},\"crcPassed\":${e2e.crcPassed},\"components\":${jsonComponents()}}", if (ok) 200 else 500)
                }
                else -> reply(exchange, "{\"ok\":false,\"error\":\"unsupported V92-V100 version\"}", 400)
            }
        } catch (t: Throwable) {
            reply(exchange, "{\"ok\":false,\"version\":\"${esc(version)}\",\"error\":\"${esc(t.message ?: t::class.simpleName ?: "error")}\"}", 500)
        }
    }

    private fun jsonComponents(): String = NrCanonicalPhy.components().joinToString(",", "[", "]") { "\"${esc(it)}\"" }
}
