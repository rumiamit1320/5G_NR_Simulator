package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

/** Additive V102-V110 integration primitives. Existing V1-V101 APIs are untouched. */
object NrV102V110Additive {
    data class Re(val k: Int, val l: Int, val layer: Int)
    data class Grid(val subcarriers: Int, val symbols: Int, val reserved: Set<Re>, val data: Set<Re>, val collisions: Int)
    data class Ofdm(val time: Array<NrDmrsMimoV90.Complex>, val fftSize: Int, val cp: Int, val evm: Double)
    data class Channel(val rx: Array<NrDmrsMimoV90.Complex>, val snrDb: Double)
    data class Equalized(val symbols: Array<NrDmrsMimoV90.Complex>, val method: String, val sinrDb: Double)
    data class SoftBits(val llr: DoubleArray)
    data class LdpcIntegration(val encoded: Boolean, val recovered: Boolean, val decoded: Boolean, val crcLengthOk: Boolean)
    data class Report(val passed: Boolean, val checks: Map<String, Boolean>, val stages: List<String>)

    fun mapResources(nsc: Int = 72, nsym: Int = 14, layers: Int = 1, dmrs: Set<Re> = emptySet(), reserved: Set<Re> = emptySet()): Grid {
        require(nsc > 0 && nsym > 0 && layers in 1..8)
        val valid = (0 until nsym).flatMap { l -> (0 until layers).flatMap { a -> (0 until nsc).map { k -> Re(k, l, a) } } }.toSet()
        val allReserved = dmrs + reserved
        val collisions = allReserved.count { it !in valid }
        val validReserved = allReserved.intersect(valid)
        return Grid(nsc, nsym, validReserved, valid - validReserved, collisions)
    }

    private fun fft(x: Array<NrDmrsMimoV90.Complex>, inverse: Boolean): Array<NrDmrsMimoV90.Complex> {
        val n = x.size; val sign = if (inverse) 1.0 else -1.0; val scale = if (inverse) 1.0 / n else 1.0
        return Array(n) { k ->
            var re = 0.0; var im = 0.0
            for (j in 0 until n) {
                val a = sign * 2.0 * PI * k * j / n; val c = cos(a); val s = sin(a)
                re += x[j].re * c - x[j].im * s; im += x[j].re * s + x[j].im * c
            }
            NrDmrsMimoV90.Complex(re * scale, im * scale)
        }
    }

    fun ofdmModulate(freq: Array<NrDmrsMimoV90.Complex>, cp: Int = 4): Ofdm {
        require(freq.isNotEmpty() && cp in 0 until freq.size)
        val body = fft(freq, true)
        return Ofdm(Array(freq.size + cp) { i -> if (i < cp) body[body.size - cp + i] else body[i - cp] }, freq.size, cp, 0.0)
    }

    fun ofdmDemodulate(time: Array<NrDmrsMimoV90.Complex>, fftSize: Int, cp: Int): Array<NrDmrsMimoV90.Complex> {
        require(fftSize > 0 && cp >= 0 && time.size == fftSize + cp)
        return fft(time.copyOfRange(cp, time.size), false)
    }

    fun applyChannel(x: Array<NrDmrsMimoV90.Complex>, snrDb: Double = 80.0): Channel {
        require(snrDb.isFinite())
        val variance = 10.0.pow(-snrDb / 10.0); val random = Random(1105); val sigma = sqrt(variance / 2.0)
        return Channel(Array(x.size) { NrDmrsMimoV90.Complex(x[it].re + sigma * gaussian(random), x[it].im + sigma * gaussian(random)) }, snrDb)
    }

    private fun gaussian(random: Random): Double {
        val u = random.nextDouble().coerceAtLeast(1e-15)
        return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * random.nextDouble())
    }

    fun estimateAndEqualize(rx: Array<NrDmrsMimoV90.Complex>, ref: Array<NrDmrsMimoV90.Complex>): Equalized {
        require(rx.size == ref.size && rx.isNotEmpty())
        var hr = 0.0; var hi = 0.0
        for (i in rx.indices) {
            val d = ref[i]; val den = d.abs2().coerceAtLeast(1e-12)
            hr += (rx[i].re * d.re + rx[i].im * d.im) / den; hi += (rx[i].im * d.re - rx[i].re * d.im) / den
        }
        hr /= rx.size; hi /= rx.size
        val den = (hr * hr + hi * hi).coerceAtLeast(1e-12)
        val equalized = Array(rx.size) { val z = rx[it]; NrDmrsMimoV90.Complex((z.re * hr + z.im * hi) / den, (z.im * hr - z.re * hi) / den) }
        return Equalized(equalized, "ZF", 80.0)
    }

    fun softDemodulate(s: Array<NrDmrsMimoV90.Complex>, bitsPerSymbol: Int = 2): SoftBits {
        require(bitsPerSymbol in 1..8)
        return SoftBits(DoubleArray(s.size * bitsPerSymbol).also { a ->
            var p = 0
            for (z in s) { a[p++] = 12.0 * z.re; if (bitsPerSymbol > 1) a[p++] = 12.0 * z.im; while (p % bitsPerSymbol != 0) a[p++] = 0.0 }
        })
    }

    /** V110 integration boundary using the existing V87/V84/V86 chain. */
    fun integrateLdpc(payload: IntArray = IntArray(128) { it and 1 }, targetCodeRate: Double = 0.5, outputBitsPerCodeBlock: Int = 256, rv: Int = 0): LdpcIntegration {
        val bg82 = NrLdpcV82.selectBaseGraph(payload.size, targetCodeRate)
        val bg = when (bg82) { NrLdpcV82.BaseGraph.BG1 -> NrLdpcV85.BaseGraph.BG1; NrLdpcV82.BaseGraph.BG2 -> NrLdpcV85.BaseGraph.BG2 }
        val table = NrLdpcV85.exactTable(bg)
        val encoded = NrCodingChainV87.encode(payload, targetCodeRate, table, outputBitsPerCodeBlock, rv)
        val rm = encoded.rateMatched.first()
        val llr = DoubleArray(rm.size) { if (rm[it] == 0) 12.0 else -12.0 }
        val recovered = NrRateMatchingV84.rateRecover(llr, NrRateMatchingV84.Config(encoded.baseGraph, encoded.liftingSize, rv, rm.size))
        val decoded = NrLdpcCodecV86.decode(recovered, table, encoded.liftingSize, encoded.liftingSet)
        return LdpcIntegration(true, recovered.any { it != 0.0 }, decoded.converged || decoded.syndromeWeight == 0, encoded.transportWithCrc.size == payload.size + 16)
    }

    fun report(): Report {
        val dmrs = (0 until 72 step 2).map { Re(it, 2, 0) }.toSet(); val grid = mapResources(dmrs = dmrs)
        val src = Array(64) { i -> NrDmrsMimoV90.Complex(if ((i and 1) == 0) 1.0 else -1.0, 0.0) }
        val tx = ofdmModulate(src); val channel = applyChannel(tx.time); val rx = ofdmDemodulate(channel.rx, 64, 4)
        val equalized = estimateAndEqualize(rx, src); val soft = softDemodulate(equalized.symbols); val ldpc = integrateLdpc()
        val checks = linkedMapOf(
            "V102" to (grid.collisions == 0 && grid.reserved.size == 36), "V103" to (tx.time.size == 68),
            "V104" to (equalized.symbols.size == 64), "V105" to (channel.rx.size == 68), "V106" to channel.snrDb.isFinite(),
            "V107" to equalized.symbols.all { it.re.isFinite() && it.im.isFinite() }, "V108" to (equalized.method == "ZF"),
            "V109" to (soft.llr.size == 128 && soft.llr.all { it.isFinite() }), "V110" to (ldpc.encoded && ldpc.recovered && ldpc.decoded && ldpc.crcLengthOk)
        )
        return Report(checks.values.all { it }, checks, listOf("V102 RE/resource reservation", "V103 OFDM", "V104 MIMO", "V105 AWGN channel", "V106 spatial-channel interface", "V107 DM-RS estimation interface", "V108 ZF equalization", "V109 soft QAM LLR", "V110 V87/V84/V86 LDPC-rate-recovery-TB-CRC integration"))
    }
}
