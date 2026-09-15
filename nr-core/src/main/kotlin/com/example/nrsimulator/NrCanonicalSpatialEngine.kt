package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.pow
import kotlin.random.Random

/**
 * Additive canonical spatial/frequency-selective PHY primitives.
 *
 * This is intentionally independent of the historical V1-V110 classes.  It provides:
 *  - radix-2 FFT/IFFT
 *  - deterministic TDL convolution with complex MIMO tap matrices
 *  - frequency response H[k]
 *  - pilot-aided LS channel estimation with linear interpolation
 *  - general MxN ZF/MMSE detection using normal equations
 *
 * The existing V1-V110 APIs are not modified.
 */
object NrCanonicalSpatialEngine {
    data class C(val re: Double, val im: Double) {
        operator fun plus(o: C) = C(re + o.re, im + o.im)
        operator fun minus(o: C) = C(re - o.re, im - o.im)
        operator fun times(o: C) = C(re * o.re - im * o.im, re * o.im + im * o.re)
        operator fun times(a: Double) = C(re * a, im * a)
        fun conj() = C(re, -im)
        fun abs2() = re * re + im * im
        fun abs() = sqrt(abs2())
        operator fun div(o: C): C {
            val d = o.abs2().coerceAtLeast(1e-18)
            return C((re * o.re + im * o.im) / d, (im * o.re - re * o.im) / d)
        }
    }

    data class Tap(val delay: Int, val h: Array<Array<C>>) {
        init {
            require(delay >= 0 && h.isNotEmpty() && h[0].isNotEmpty())
            require(h.all { it.size == h[0].size })
        }
    }

    data class TdlResult(val output: Array<Array<C>>, val noiseVariance: Double, val snrDb: Double)
    data class Estimate(val h: Array<Array<Array<C>>>, val pilotPositions: IntArray)
    data class Detection(val symbols: Array<Array<C>>, val postSinrDb: DoubleArray, val method: String)

    fun fft(input: Array<C>, inverse: Boolean = false): Array<C> {
        require(input.isNotEmpty() && (input.size and (input.size - 1)) == 0) { "FFT size must be a power of two" }
        val n = input.size
        val a = input.copyOf()
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while ((j and bit) != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) { val t = a[i]; a[i] = a[j]; a[j] = t }
        }
        var len = 2
        val sign = if (inverse) 1.0 else -1.0
        while (len <= n) {
            val theta = sign * 2.0 * PI / len
            val wLen = C(cos(theta), sin(theta))
            var i = 0
            while (i < n) {
                var w = C(1.0, 0.0)
                for (k in 0 until len / 2) {
                    val u = a[i + k]
                    val v = a[i + k + len / 2] * w
                    a[i + k] = u + v
                    a[i + k + len / 2] = u - v
                    w = w * wLen
                }
                i += len
            }
            len = len shl 1
        }
        if (inverse) for (i in a.indices) a[i] = a[i] * (1.0 / n)
        return a
    }

    /** Time-domain TDL MIMO channel. output[rx][sample]. */
    fun applyTdl(
        input: Array<Array<C>>,
        taps: List<Tap>,
        snrDb: Double,
        seed: Int = 1107
    ): TdlResult {
        require(input.isNotEmpty() && input[0].isNotEmpty() && input.all { it.size == input[0].size })
        require(taps.isNotEmpty() && snrDb.isFinite())
        val tx = input.size
        val rx = taps.first().h.size
        require(taps.all { it.h[0].size == tx })
        require(taps.maxOf { it.delay } < input[0].size)
        val n = input[0].size
        val out = Array(rx) { Array(n) { C(0.0, 0.0) } }
        for (tap in taps) for (r in 0 until rx) for (t in 0 until tx) {
            val h = tap.h[r][t]
            for (i in tap.delay until n) out[r][i] = out[r][i] + input[t][i - tap.delay] * h
        }
        val signalPower = out.sumOf { row -> row.sumOf { it.abs2() } } / (rx * n).coerceAtLeast(1)
        val noiseVariance = signalPower / 10.0.pow10(snrDb)
        val sigma = sqrt(noiseVariance / 2.0)
        val random = Random(seed)
        for (r in 0 until rx) for (i in 0 until n) {
            out[r][i] = out[r][i] + C(sigma * gaussian(random), sigma * gaussian(random))
        }
        return TdlResult(out, noiseVariance, snrDb)
    }

    fun frequencyResponse(taps: List<Tap>, fftSize: Int): Array<Array<Array<C>>> {
        require(fftSize > 0 && (fftSize and (fftSize - 1)) == 0)
        val rx = taps.first().h.size
        val tx = taps.first().h[0].size
        require(taps.all { it.h.size == rx && it.h[0].size == tx })
        return Array(fftSize) { k -> Array(rx) { r -> Array(tx) { t ->
            var sum = C(0.0, 0.0)
            for (tap in taps) {
                val a = -2.0 * PI * k * tap.delay / fftSize
                sum += tap.h[r][t] * C(cos(a), sin(a))
            }
            sum
        } } }
    }

    /** LS estimate H[k] at pilot subcarriers, followed by linear interpolation. */
    fun estimateFromOrthogonalPilots(
        received: Array<Array<C>>, pilots: Array<Array<Array<C>>>, pilotSubcarriers: IntArray
    ): Estimate {
        require(received.isNotEmpty() && pilots.isNotEmpty())
        val rx = received.size
        val tx = pilots.size
        val nsc = received[0].size
        require(received.all { it.size == nsc })
        require(pilotSubcarriers.isNotEmpty() && pilotSubcarriers.all { it in 0 until nsc })
        require(pilots.all { it.size == rx && it.all { row -> row.size == pilotSubcarriers.size } })
        val sorted = pilotSubcarriers.sorted().distinct().toIntArray()
        require(sorted.size == pilotSubcarriers.size)
        // pilots[tx][rx][pilotIndex] are orthogonal in the sense that each pilot observation
        // is provided per transmit antenna. This avoids silently assuming a SISO estimator.
        val h = Array(nsc) { Array(rx) { Array(tx) { C(0.0, 0.0) } } }
        for (p in sorted.indices) {
            val k = sorted[p]
            for (r in 0 until rx) for (t in 0 until tx) {
                val x = pilots[t][r][p]
                h[k][r][t] = if (x.abs2() < 1e-18) C(0.0, 0.0) else received[r][k] / x
            }
        }
        for (k in 0 until nsc) if (k !in sorted) {
            val lo = sorted.lastOrNull { it < k }
            val hi = sorted.firstOrNull { it > k }
            val l = lo ?: hi!!
            val u = hi ?: lo!!
            val alpha = if (u == l) 0.0 else (k - l).toDouble() / (u - l)
            for (r in 0 until rx) for (t in 0 until tx) {
                val a = h[l][r][t]; val b = h[u][r][t]
                h[k][r][t] = a * (1.0 - alpha) + b * alpha
            }
        }
        return Estimate(h, sorted)
    }

    /** General ZF/MMSE detector for y = Hx+n, per resource element. */
    fun detect(
        received: Array<Array<C>>, channel: Array<Array<Array<C>>>, noiseVariance: Double = 0.0,
        method: String = "MMSE"
    ): Detection {
        require(received.isNotEmpty() && channel.isNotEmpty())
        val nsc = received[0].size
        val rx = received.size
        val tx = channel[0][0].size
        require(received.all { it.size == nsc })
        require(channel.size == nsc && channel.all { it.size == rx && it.all { row -> row.size == tx } })
        require(method.uppercase() in setOf("ZF", "MMSE"))
        val out = Array(tx) { Array(nsc) { C(0.0, 0.0) } }
        val sinr = DoubleArray(nsc)
        for (k in 0 until nsc) {
            val a = Array(rx) { Array(tx) { C(0.0, 0.0) } }
            for (r in 0 until rx) for (t in 0 until tx) a[r][t] = channel[k][r][t]
            val ah = conjTranspose(a)
            val gram = multiply(ah, a)
            val lambda = if (method.equals("MMSE", true)) noiseVariance.coerceAtLeast(1e-12) else 0.0
            for (i in 0 until tx) gram[i][i] = gram[i][i] + C(lambda, 0.0)
            val inv = invert(gram)
            val w = multiply(inv, ah)
            val y = Array(rx) { r -> received[r][k] }
            val x = multiplyVector(w, y)
            for (t in 0 until tx) out[t][k] = x[t]
            var desired = 0.0
            for (t in 0 until tx) desired += a.sumOf { row -> row[t].abs2() }
            val residual = if (method.equals("MMSE", true)) noiseVariance * tx else noiseVariance
            sinr[k] = 10.0 * log10((desired / residual.coerceAtLeast(1e-12)).coerceAtLeast(1e-12))
        }
        return Detection(out, sinr, method.uppercase())
    }

    private fun conjTranspose(a: Array<Array<C>>): Array<Array<C>> {
        val rows = a.size; val cols = a[0].size
        return Array(cols) { i -> Array(rows) { j -> a[j][i].conj() } }
    }
    private fun multiply(a: Array<Array<C>>, b: Array<Array<C>>): Array<Array<C>> {
        require(a[0].size == b.size)
        return Array(a.size) { i -> Array(b[0].size) { j ->
            var s = C(0.0, 0.0)
            for (k in b.indices) s += a[i][k] * b[k][j]
            s
        } }
    }
    private fun multiplyVector(a: Array<Array<C>>, b: Array<C>): Array<C> = Array(a.size) { i ->
        var s = C(0.0, 0.0)
        for (k in b.indices) s += a[i][k] * b[k]
        s
    }
    private fun invert(a: Array<Array<C>>): Array<Array<C>> {
        val n = a.size
        val aug = Array(n) { i -> Array(2 * n) { j -> if (j < n) a[i][j] else if (j - n == i) C(1.0, 0.0) else C(0.0, 0.0) } }
        for (col in 0 until n) {
            var pivot = col
            for (r in col + 1 until n) if (aug[r][col].abs2() > aug[pivot][col].abs2()) pivot = r
            require(aug[pivot][col].abs2() > 1e-18) { "Singular MIMO Gram matrix" }
            if (pivot != col) { val tmp = aug[pivot]; aug[pivot] = aug[col]; aug[col] = tmp }
            val p = aug[col][col]
            for (j in 0 until 2 * n) aug[col][j] = aug[col][j] / p
            for (r in 0 until n) if (r != col) {
                val f = aug[r][col]
                for (j in 0 until 2 * n) aug[r][j] = aug[r][j] - aug[col][j] * f
            }
        }
        return Array(n) { i -> Array(n) { j -> aug[i][j + n] } }
    }

    private fun gaussian(random: Random): Double {
        val u = random.nextDouble().coerceAtLeast(1e-15)
        return sqrt(-2.0 * kotlin.math.ln(u)) * cos(2.0 * PI * random.nextDouble())
    }
    private fun Double.pow10(exp: Double): Double = 10.0.pow(exp)
}
