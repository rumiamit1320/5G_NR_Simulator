package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V92-V100 additive NR research primitives. Existing V1-V91 APIs are untouched.
 * These are deterministic simulator primitives, not a claim of full 3GPP certification.
 */
object NrDmrsV92 {
    data class Position(val subcarrier: Int, val symbol: Int, val layer: Int = 0)
    data class Config(val subcarriers: Int, val symbols: Int, val layers: Int = 1, val symbolPositions: IntArray = intArrayOf(2), val comb: Int = 2)
    data class Sequence(val values: Array<NrPhyMappingV89.Complex>, val positions: List<Position>)

    fun generate(config: Config, seed: Int = 0): Sequence {
        require(config.subcarriers > 0 && config.symbols > 0 && config.layers in 1..8 && config.comb in 1..2)
        val positions = ArrayList<Position>()
        val values = ArrayList<NrPhyMappingV89.Complex>()
        var n = 0
        config.symbolPositions.filter { it in 0 until config.symbols }.distinct().forEach { l ->
            for (k in 0 until config.subcarriers step config.comb) {
                for (layer in 0 until config.layers) {
                    val phase = if (((k + 7 * l + 13 * layer + seed) and 1) == 0) 0.0 else PI
                    values += NrPhyMappingV89.Complex(cos(phase) / sqrt(2.0), sin(phase) / sqrt(2.0))
                    positions += Position(k, l, layer)
                    n++
                }
            }
        }
        require(n == values.size)
        return Sequence(values.toTypedArray(), positions)
    }
}

object NrOfdmV93 {
    data class Waveform(val samples: Array<NrPhyMappingV89.Complex>, val fftSize: Int, val cyclicPrefix: Int, val symbols: Int)
    private fun dft(input: Array<NrPhyMappingV89.Complex>, inverse: Boolean): Array<NrPhyMappingV89.Complex> {
        val n = input.size
        val sign = if (inverse) 1.0 else -1.0
        return Array(n) { k ->
            var re = 0.0; var im = 0.0
            for (j in 0 until n) {
                val a = sign * 2.0 * PI * k * j / n
                val c = cos(a); val s = sin(a)
                re += input[j].re * c - input[j].im * s
                im += input[j].re * s + input[j].im * c
            }
            val scale = if (inverse) 1.0 / n else 1.0
            NrPhyMappingV89.Complex(re * scale, im * scale)
        }
    }
    fun modulate(grid: Array<Array<NrPhyMappingV89.Complex>>, fftSize: Int, cyclicPrefix: Int): Waveform {
        require(fftSize > 0 && cyclicPrefix in 0 until fftSize && grid.isNotEmpty())
        val out = ArrayList<NrPhyMappingV89.Complex>()
        grid.forEach { bins ->
            require(bins.size <= fftSize)
            val padded = Array(fftSize) { NrPhyMappingV89.Complex(0.0, 0.0) }
            bins.forEachIndexed { i, v -> padded[i] = v }
            val time = dft(padded, true)
            out.addAll(time.takeLast(cyclicPrefix)); out.addAll(time)
        }
        return Waveform(out.toTypedArray(), fftSize, cyclicPrefix, grid.size)
    }
    fun demodulate(waveform: Waveform): Array<Array<NrPhyMappingV89.Complex>> {
        val stride = waveform.fftSize + waveform.cyclicPrefix
        require(waveform.samples.size % stride == 0)
        return Array(waveform.samples.size / stride) { s ->
            val start = s * stride + waveform.cyclicPrefix
            dft(waveform.samples.copyOfRange(start, start + waveform.fftSize), false)
        }
    }
    fun evm(reference: Array<NrPhyMappingV89.Complex>, received: Array<NrPhyMappingV89.Complex>): Double {
        require(reference.size == received.size && reference.isNotEmpty())
        var e = 0.0; var p = 0.0
        reference.indices.forEach { i ->
            val dr = received[i].re - reference[i].re; val di = received[i].im - reference[i].im
            e += dr * dr + di * di; p += reference[i].re * reference[i].re + reference[i].im * reference[i].im
        }
        return sqrt(e / p.coerceAtLeast(1e-12))
    }
}

object NrMimoV94 {
    data class Matrix(val rows: Int, val cols: Int, val values: Array<Array<NrDmrsMimoV90.Complex>>)
    data class Result(val layers: Array<Array<NrDmrsMimoV90.Complex>>, val sinrDb: Double)
    private fun add(a: NrDmrsMimoV90.Complex, b: NrDmrsMimoV90.Complex) = a + b
    private fun scale(a: NrDmrsMimoV90.Complex, x: Double) = NrDmrsMimoV90.Complex(a.re * x, a.im * x)
    private fun div(a: NrDmrsMimoV90.Complex, b: NrDmrsMimoV90.Complex): NrDmrsMimoV90.Complex {
        val d = b.abs2().coerceAtLeast(1e-12); return NrDmrsMimoV90.Complex((a.re*b.re+a.im*b.im)/d, (a.im*b.re-a.re*b.im)/d)
    }
    fun identity(n: Int): Matrix = Matrix(n, n, Array(n) { r -> Array(n) { c -> if (r == c) NrDmrsMimoV90.Complex(1.0, 0.0) else NrDmrsMimoV90.Complex(0.0, 0.0) } })
    fun equalize2x2(h: Matrix, y: Array<NrDmrsMimoV90.Complex>, noiseVariance: Double = 1e-3): Result {
        require(h.rows == 2 && h.cols == 2 && y.size == 2)
        val a=h.values[0][0]; val b=h.values[0][1]; val c=h.values[1][0]; val d=h.values[1][1]
        val det = a*d - b*c
        val yy0 = div(add(d * y[0], scale(b * y[1], -1.0)), det)
        val yy1 = div(add(scale(c * y[0], -1.0), a * y[1]), det)
        val power = (yy0.abs2() + yy1.abs2()) / 2.0
        return Result(arrayOf(arrayOf(yy0), arrayOf(yy1)), 10.0*log10(power/noiseVariance.coerceAtLeast(1e-12)))
    }
}

object NrChannelV95 {
    data class Tap(val delay: Int, val gain: NrDmrsMimoV90.Complex)
    data class Result(val samples: Array<NrDmrsMimoV90.Complex>, val noiseVariance: Double, val snrDb: Double)
    fun apply(input: Array<NrDmrsMimoV90.Complex>, taps: List<Tap>, snrDb: Double): Result {
        require(input.isNotEmpty() && taps.isNotEmpty())
        val out = Array(input.size) { NrDmrsMimoV90.Complex(0.0, 0.0) }
        input.indices.forEach { i -> taps.forEach { t -> if (i >= t.delay) out[i] = out[i] + input[i-t.delay] * t.gain } }
        val signal = out.map { it.abs2() }.average().coerceAtLeast(1e-12)
        val noise = signal / 10.0.pow10(snrDb / 10.0)
        var state = 0x13579BDFL
        out.indices.forEach { i ->
            state = (1664525L * state + 1013904223L) and 0xffffffffL
            val u1 = ((state ushr 8) and 0xffff).toDouble() / 65536.0
            state = (1664525L * state + 1013904223L) and 0xffffffffL
            val u2 = ((state ushr 8) and 0xffff).toDouble() / 65536.0
            val g = sqrt(-2.0 * kotlin.math.ln(u1.coerceAtLeast(1e-12))) * cos(2.0*PI*u2)
            val g2 = sqrt(-2.0 * kotlin.math.ln(u1.coerceAtLeast(1e-12))) * sin(2.0*PI*u2)
            out[i] = NrDmrsMimoV90.Complex(out[i].re + sqrt(noise/2.0)*g, out[i].im + sqrt(noise/2.0)*g2)
        }
        return Result(out, noise, 10.0*log10(signal/noise))
    }
    private fun Double.pow10(x: Double) = kotlin.math.exp(x * kotlin.math.ln(10.0))
    fun tdlA(snrDb: Double): List<Tap> = listOf(Tap(0, NrDmrsMimoV90.Complex(1.0,0.0)), Tap(2, NrDmrsMimoV90.Complex(0.32,0.08)), Tap(5, NrDmrsMimoV90.Complex(0.18,-0.05))).map { it.copy(gain = NrDmrsMimoV90.Complex(it.gain.re/sqrt(1.13), it.gain.im/sqrt(1.13))) }
}

object NrLinkAdaptationV96 {
    data class Decision(val sinrDb: Double, val modulation: NrPhyMappingV89.Modulation, val layers: Int, val targetCodeRate: Double, val cqi: Int)
    fun select(sinrDb: Double, maxLayers: Int = 2): Decision {
        val (m, r, cqi) = when {
            sinrDb < -5 -> Triple(NrPhyMappingV89.Modulation.BPSK, .20, 1)
            sinrDb < 0 -> Triple(NrPhyMappingV89.Modulation.QPSK, .30, 2)
            sinrDb < 5 -> Triple(NrPhyMappingV89.Modulation.QPSK, .50, 4)
            sinrDb < 10 -> Triple(NrPhyMappingV89.Modulation.QAM16, .50, 7)
            sinrDb < 16 -> Triple(NrPhyMappingV89.Modulation.QAM64, .60, 11)
            else -> Triple(NrPhyMappingV89.Modulation.QAM256, .75, 15)
        }
        val layers = if (sinrDb >= 8) maxLayers.coerceIn(1, 8) else 1
        return Decision(sinrDb, m, layers, r, cqi)
    }
}

object NrHarqCsiV97 {
    data class Process(val id: Int, val rv: Int, val rounds: Int, val combinedLlr: DoubleArray)
    data class Csi(val cqi: Int, val ri: Int, val pmi: Int, val sinrDb: Double)
    fun newProcess(id: Int, llr: DoubleArray, rv: Int = 0): Process { require(id >= 0 && rv in 0..3); return Process(id, rv, 1, llr.copyOf()) }
    fun combine(p: Process, llr: DoubleArray, rv: Int): Process { require(llr.size == p.combinedLlr.size && rv in 0..3); return p.copy(rv=rv, rounds=p.rounds+1, combinedLlr=p.combinedLlr.indices.map { p.combinedLlr[it]+llr[it] }.toDoubleArray()) }
    fun ack(p: Process, threshold: Double = 0.0): Boolean = p.combinedLlr.sum() > threshold
    fun csi(sinrDb: Double, rank: Int = 1): Csi = NrLinkAdaptationV96.select(sinrDb, rank).let { Csi(it.cqi, rank.coerceIn(1,8), if (rank > 1) 1 else 0, sinrDb) }
}

object NrRachV98 {
    data class Preamble(val root: Int, val length: Int, val cyclicShift: Int, val sequence: Array<NrDmrsMimoV90.Complex>)
    data class Detection(val detected: Boolean, val metric: Double, val timingOffset: Int)
    fun generate(root: Int = 1, length: Int = 839, cyclicShift: Int = 0): Preamble {
        require(root in 1 until length && length > 0 && cyclicShift >= 0)
        val seq = Array(length) { n -> val phase = -PI * root * n * (n + 1) / length + 2.0*PI*cyclicShift*n/length; NrDmrsMimoV90.Complex(cos(phase), sin(phase)) }
        return Preamble(root, length, cyclicShift, seq)
    }
    fun detect(reference: Preamble, received: Array<NrDmrsMimoV90.Complex>, threshold: Double = 0.70): Detection {
        if (received.size != reference.sequence.size) return Detection(false, 0.0, -1)
        var corr = NrDmrsMimoV90.Complex(0.0,0.0)
        received.indices.forEach { i -> corr = corr + received[i] * reference.sequence[i].conj() }
        val metric = sqrt(corr.abs2()) / reference.sequence.size
        return Detection(metric >= threshold, metric, 0)
    }
}

object NrRefVectorsV99 {
    data class Vector(val name: String, val input: IntArray, val expected: DoubleArray)
    fun smokeVectors(): List<Vector> = listOf(
        Vector("BPSK-0101", intArrayOf(0,1,0,1), doubleArrayOf(1.0,0.0,-1.0,0.0)),
        Vector("QPSK-00", intArrayOf(0,0), doubleArrayOf(1.0/sqrt(2.0),1.0/sqrt(2.0)))
    )
    fun check(): List<Boolean> = smokeVectors().map { v ->
        when (v.name) {
            "BPSK-0101" -> NrPhyMappingV89.modulate(v.input, NrPhyMappingV89.Modulation.BPSK).flatMap { listOf(it.re,it.im) }.toDoubleArray().contentEquals(v.expected)
            "QPSK-00" -> NrPhyMappingV89.modulate(v.input, NrPhyMappingV89.Modulation.QPSK).flatMap { listOf(it.re,it.im) }.toDoubleArray().contentEquals(v.expected)
            else -> false
        }
    }
}

object NrResearchGradeV100 {
    data class Report(val passed: Boolean, val stages: Map<String, Boolean>, val notes: String)
    fun run(): Report {
        val stages = linkedMapOf<String, Boolean>()
        val dmrs = NrDmrsV92.generate(NrDmrsV92.Config(24, 14, 2))
        stages["V92-DMRS"] = dmrs.values.isNotEmpty() && dmrs.values.size == dmrs.positions.size
        val grid = Array(2) { s -> Array<NrPhyMappingV89.Complex>(8) { i -> NrPhyMappingV89.Complex(if ((i+s)%2==0) 1.0 else -1.0, 0.0) } }
        val wf = NrOfdmV93.modulate(grid, 8, 2); val recovered = NrOfdmV93.demodulate(wf)
        stages["V93-OFDM"] = recovered.size == grid.size && NrOfdmV93.evm(grid[0], recovered[0]) < 1e-9
        val h = NrMimoV94.identity(2); val mimo = NrMimoV94.equalize2x2(h, arrayOf(NrDmrsMimoV90.Complex(1.0,0.0),NrDmrsMimoV90.Complex(-1.0,0.0)))
        stages["V94-MIMO"] = mimo.layers.size == 2 && mimo.sinrDb.isFinite()
        val ch = NrChannelV95.apply(arrayOf(NrDmrsMimoV90.Complex(1.0,0.0)), NrChannelV95.tdlA(20.0), 20.0)
        stages["V95-CHANNEL"] = ch.samples.size == 1 && ch.snrDb.isFinite()
        stages["V96-LINK-ADAPT"] = NrLinkAdaptationV96.select(10.0).modulation == NrPhyMappingV89.Modulation.QAM16
        val hp = NrHarqCsiV97.newProcess(0, doubleArrayOf(1.0,-0.5)); stages["V97-HARQ-CSI"] = NrHarqCsiV97.ack(NrHarqCsiV97.combine(hp,doubleArrayOf(1.0,1.0),1))
        val pre = NrRachV98.generate(); stages["V98-RACH"] = NrRachV98.detect(pre, pre.sequence).detected
        stages["V99-REFERENCE"] = NrRefVectorsV99.check().all { it }
        stages["V100-REGRESSION"] = stages.values.all { it }
        return Report(stages.values.all { it }, stages, "Additive V92-V100 research-grade regression primitives; existing V1-V91 APIs remain unchanged.")
    }
}
