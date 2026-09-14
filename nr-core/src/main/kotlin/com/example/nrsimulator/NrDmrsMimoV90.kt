package com.example.nrsimulator

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** V90 additive DMRS, channel-estimation and small MIMO equalization primitives. */
object NrDmrsMimoV90 {
    data class Complex(val re: Double, val im: Double) {
        operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
        operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
        fun conj() = Complex(re, -im)
        fun abs2() = re * re + im * im
    }

    data class MimoResult(val symbols: Array<Complex>, val postEqSinrDb: Double)

    fun dmrs(length: Int, seed: Int = 0): Array<Complex> {
        require(length >= 0)
        var x = (seed xor 0x5A5A5A5A).toLong() and 0x7fffffffL
        return Array(length) {
            x = (1103515245L * x + 12345L) and 0x7fffffffL
            val bit = (x ushr 16) and 1L
            val phase = if (bit == 0L) 0.0 else Math.PI
            Complex(cos(phase) / sqrt(2.0), sin(phase) / sqrt(2.0))
        }
    }

    fun estimate(tx: Array<Complex>, rx: Array<Complex>): Array<Complex> {
        require(tx.size == rx.size && tx.isNotEmpty())
        return Array(tx.size) { i ->
            val den = tx[i].abs2().coerceAtLeast(1e-12)
            rx[i] * tx[i].conj() * Complex(1.0 / den, 0.0)
        }
    }

    /** Scalar ZF/MMSE equalization; V77 remains untouched. */
    fun equalize(rx: Array<Complex>, h: Array<Complex>, noiseVariance: Double = 0.0): MimoResult {
        require(rx.size == h.size && rx.isNotEmpty())
        val out = Array(rx.size) { i ->
            val den = h[i].abs2() + noiseVariance.coerceAtLeast(0.0)
            rx[i] * h[i].conj() * Complex(1.0 / den.coerceAtLeast(1e-12), 0.0)
        }
        val signal = out.map { it.abs2() }.average()
        val sinr = 10.0 * kotlin.math.log10((signal / noiseVariance.coerceAtLeast(1e-12)).coerceAtLeast(1e-12))
        return MimoResult(out, sinr)
    }
}
