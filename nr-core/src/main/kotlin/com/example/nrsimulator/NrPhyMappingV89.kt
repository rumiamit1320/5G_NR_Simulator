package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** V89 additive NR data modulation and layer mapping primitives. */
object NrPhyMappingV89 {
    data class Complex(val re: Double, val im: Double)
    enum class Modulation(val bitsPerSymbol: Int) { BPSK(1), QPSK(2), QAM16(4), QAM64(6), QAM256(8) }

    fun modulate(bits: IntArray, modulation: Modulation): Array<Complex> {
        require(bits.size % modulation.bitsPerSymbol == 0)
        return Array(bits.size / modulation.bitsPerSymbol) { s ->
            val off = s * modulation.bitsPerSymbol
            when (modulation) {
                Modulation.BPSK -> Complex(if (bits[off] == 0) 1.0 else -1.0, 0.0)
                Modulation.QPSK -> {
                    val b0 = bits[off] and 1; val b1 = bits[off + 1] and 1
                    Complex((if (b0 == 0) 1 else -1) / sqrt(2.0), (if (b1 == 0) 1 else -1) / sqrt(2.0))
                }
                Modulation.QAM16 -> squareQam(bits, off, 4)
                Modulation.QAM64 -> squareQam(bits, off, 8)
                Modulation.QAM256 -> squareQam(bits, off, 16)
            }
        }
    }

    private fun squareQam(bits: IntArray, off: Int, levels: Int): Complex {
        val m = Integer.numberOfTrailingZeros(levels)
        fun axis(start: Int): Double {
            var v = 0
            repeat(m) { v = (v shl 1) or (bits[start + it] and 1) }
            val amp = 2 * v - (levels - 1)
            return amp.toDouble() / sqrt((2.0 / 3.0) * (levels * levels - 1))
        }
        return Complex(axis(off), axis(off + m))
    }

    fun mapLayers(symbols: Array<Complex>, layers: Int): Array<Array<Complex>> {
        require(layers in 1..8)
        val out = Array(layers) { ArrayList<Complex>() }
        symbols.forEachIndexed { i, s -> out[i % layers] += s }
        return Array(layers) { out[it].toTypedArray() }
    }

    fun piOverTwoBpsk(bits: IntArray): Array<Complex> = Array(bits.size) { i ->
        val phase = if (bits[i] == 0) 0.0 else PI
        val rot = (i and 3) * PI / 2.0
        Complex(cos(phase + rot), sin(phase + rot))
    }
}
