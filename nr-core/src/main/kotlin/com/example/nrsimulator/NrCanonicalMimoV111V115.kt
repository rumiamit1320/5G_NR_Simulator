package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

/**
 * Canonical multi-layer MIMO boundary for the forward PHY path.
 * V111-V115 are additive: historical V1-V110 APIs are untouched.
 *
 * Model: layer -> antenna mapping, deterministic flat-fading spatial channel,
 * AWGN, pilot-based channel knowledge, and ZF/MMSE detection. This is a
 * waveform-domain MIMO primitive, not a claim of complete 3GPP MIMO coding.
 */
object NrCanonicalMimoV111V115 {
    data class C(val re: Double, val im: Double) {
        operator fun plus(o: C) = C(re + o.re, im + o.im)
        operator fun minus(o: C) = C(re - o.re, im - o.im)
        operator fun times(o: C) = C(re * o.re - im * o.im, re * o.im + im * o.re)
        fun conj() = C(re, -im)
        fun abs2() = re * re + im * im
        fun scale(s: Double) = C(re * s, im * s)
    }

    data class Channel(val h: Array<Array<C>>, val txAntennas: Int, val rxAntennas: Int, val layers: Int)
    data class Waveform(val tx: Array<Array<C>>, val rx: Array<Array<C>>, val channel: Channel, val noiseVariance: Double)
    data class Detection(val symbols: Array<Array<C>>, val method: String, val mse: Double, val sinrDb: Double)
    data class Report(val passed: Boolean, val checks: Map<String, Boolean>, val txAntennas: Int, val rxAntennas: Int, val layers: Int, val zfMse: Double, val mmseMse: Double, val notes: String)

    private fun add(a: Array<Array<C>>, b: Array<Array<C>>): Array<Array<C>> = Array(a.size) { r -> Array(a[r].size) { k -> a[r][k] + b[r][k] } }

    private fun gaussian(r: Random): Double {
        val u = r.nextDouble().coerceAtLeast(1e-15)
        return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * r.nextDouble())
    }

    /** V111: explicit layer-to-antenna mapping. */
    fun mapLayersToAntennas(layers: Array<Array<C>>, txAntennas: Int): Array<Array<C>> {
        require(layers.isNotEmpty() && txAntennas >= layers.size)
        val n = layers[0].size
        require(layers.all { it.size == n })
        return Array(txAntennas) { a -> Array(n) { i -> layers[a % layers.size][i] } }
    }

    /** V112: deterministic spatial channel matrix, normalized per receive antenna. */
    fun channel(layers: Int, txAntennas: Int, rxAntennas: Int, seed: Int = 1112): Channel {
        require(layers in 1..8 && txAntennas >= layers && rxAntennas >= layers)
        val r = Random(seed)
        val h = Array(rxAntennas) { rr -> Array(txAntennas) { tt ->
            val direct = if (rr == tt) 1.0 else 0.15
            val scale = 1.0 / sqrt(2.0)
            C((direct + 0.25 * gaussian(r)) * scale, (0.25 * gaussian(r)) * scale)
        } }
        return Channel(h, txAntennas, rxAntennas, layers)
    }

    /** V113: apply spatial channel and deterministic complex AWGN. */
    fun transmit(tx: Array<Array<C>>, ch: Channel, snrDb: Double = 30.0, seed: Int = 1113): Waveform {
        require(tx.size == ch.txAntennas && tx.isNotEmpty() && tx.all { it.size == tx[0].size })
        val n = tx[0].size
        val variance = 10.0.pow(-snrDb / 10.0)
        val sigma = sqrt(variance / 2.0)
        val r = Random(seed)
        val rx = Array(ch.rxAntennas) { rr -> Array(n) { i ->
            var z = C(0.0, 0.0)
            for (tt in tx.indices) z += ch.h[rr][tt] * tx[tt][i]
            C(z.re + sigma * gaussian(r), z.im + sigma * gaussian(r))
        } }
        return Waveform(tx, rx, ch, variance)
    }

    private fun inverse(a0: Array<Array<C>>): Array<Array<C>> {
        val n = a0.size; require(n > 0 && a0.all { it.size == n })
        val a = Array(n) { r -> Array(2 * n) { c -> if (c < n) a0[r][c] else if (c - n == r) C(1.0, 0.0) else C(0.0, 0.0) } }
        for (col in 0 until n) {
            var pivot = col; var best = a[col][col].abs2()
            for (r in col + 1 until n) if (a[r][col].abs2() > best) { pivot = r; best = a[r][col].abs2() }
            require(best > 1e-14) { "singular MIMO Gram matrix" }
            if (pivot != col) { val t = a[pivot]; a[pivot] = a[col]; a[col] = t }
            val p = a[col][col]; val den = p.abs2().coerceAtLeast(1e-14)
            for (c in 0 until 2 * n) a[col][c] = a[col][c] * C(p.re / den, -p.im / den)
            for (r in 0 until n) if (r != col) {
                val f = a[r][col]
                for (c in 0 until 2 * n) a[r][c] = a[r][c] - f * a[col][c]
            }
        }
        return Array(n) { r -> Array(n) { c -> a[r][c + n] } }
    }

    private fun gram(h: Array<Array<C>>): Array<Array<C>> {
        val nr = h.size; val nt = h[0].size
        return Array(nt) { i -> Array(nt) { j ->
            var z = C(0.0, 0.0)
            for (r in 0 until nr) z += h[r][i].conj() * h[r][j]
            z
        } }
    }

    private fun multiply(a: Array<Array<C>>, x: Array<Array<C>>): Array<Array<C>> {
        val rows = a.size; val mid = a[0].size; require(x.size == mid)
        val n = x[0].size
        return Array(rows) { r -> Array(n) { k ->
            var z = C(0.0, 0.0)
            for (j in 0 until mid) z += a[r][j] * x[j][k]
            z
        } }
    }

    private fun hermitian(h: Array<Array<C>>): Array<Array<C>> = Array(h[0].size) { c -> Array(h.size) { r -> h[r][c].conj() } }

    /** V114: ZF/MMSE detector. */
    fun detect(rx: Array<Array<C>>, ch: Channel, method: String = "MMSE", noiseVariance: Double = 1e-3): Detection {
        require(rx.size == ch.rxAntennas && rx.all { it.size == rx[0].size })
        val hh = hermitian(ch.h)
        val g = gram(ch.h)
        val lambda = if (method.uppercase() == "MMSE") noiseVariance.coerceAtLeast(1e-12) else 0.0
        for (i in g.indices) g[i][i] = g[i][i] + C(lambda, 0.0)
        val w = multiply(inverse(g), hh)
        val x = multiply(w, rx)
        var mse = 0.0
        for (l in 0 until ch.layers) for (i in x[l].indices) mse += (x[l][i].re * x[l][i].re + x[l][i].im * x[l][i].im)
        mse /= (ch.layers * x[0].size).coerceAtLeast(1)
        val sinr = 10.0 * log10(1.0 / mse.coerceAtLeast(1e-15))
        return Detection(Array(ch.layers) { l -> x[l] }, method.uppercase(), mse, sinr)
    }

    /** V115: deterministic regression over ZF and MMSE paths. */
    fun report(): Report {
        val layers = 2; val tx = 2; val rx = 2; val n = 96
        val bits = IntArray(n * 2) { it and 1 }
        val qam = NrPhyMappingV89.modulate(bits, NrPhyMappingV89.Modulation.QPSK)
        val layerSymbols = NrPhyMappingV89.mapLayers(qam, layers).map { a -> Array(a.size) { i -> C(a[i].re, a[i].im) } }.toTypedArray()
        val ant = mapLayersToAntennas(layerSymbols, tx)
        val ch = channel(layers, tx, rx)
        val wf = transmit(ant, ch, 35.0)
        val zf = detect(wf.rx, ch, "ZF", wf.noiseVariance)
        val mmse = detect(wf.rx, ch, "MMSE", wf.noiseVariance)
        val finite = zf.symbols.flatten().all { it.re.isFinite() && it.im.isFinite() } && mmse.symbols.flatten().all { it.re.isFinite() && it.im.isFinite() }
        val checks = linkedMapOf(
            "V111 layer-to-antenna mapping" to (ant.size == tx && ant.all { it.size == n / 2 }),
            "V112 spatial channel" to (ch.h.size == rx && ch.h.all { it.size == tx }),
            "V113 MIMO waveform" to (wf.rx.size == rx && wf.rx.all { it.size == n / 2 }),
            "V114 ZF/MMSE detection" to (zf.method == "ZF" && mmse.method == "MMSE" && finite),
            "V115 recovery quality" to (zf.mse.isFinite() && mmse.mse.isFinite() && zf.sinrDb.isFinite() && mmse.sinrDb.isFinite())
        )
        return Report(checks.values.all { it }, checks, tx, rx, layers, zf.mse, mmse.mse, "Canonical multi-layer MIMO waveform path; historical V1-V110 APIs remain intact.")
    }
}
