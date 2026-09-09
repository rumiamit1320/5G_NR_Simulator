package com.example.nrsimulator

import kotlin.math.*

/**
 * V15 additive MIMO precoding / beam-selection layer.
 *
 * V1-v14 are unchanged. This module consumes the frequency-domain MIMO channel
 * already exposed by V11/V14 and selects a normalized precoder from a compact
 * NR-oriented codebook. The 2-port codebook is the Type-I single-panel family
 * for ports 3000/3001 described in TS 38.214. For 4 ports, the module uses a
 * deterministic unitary/DFT reference family so that the simulator can exercise
 * multi-antenna beam selection without pretending to implement every 38.214
 * Type-I table. The selected W is then applied as y = H W x.
 */

data class NrMimoV15Config(
    val txAntennas: Int = 2,
    val rxAntennas: Int = 2,
    val layers: Int = 2,
    val prbs: Int = 52,
    val snrDb: Double = 20.0,
    val channelModel: String = "FREQUENCY_SELECTIVE",
    val seed: Int = 0x1501,
    val requestedPmi: Int = -1
)

data class NrMimoV15Result(
    val txAntennas: Int,
    val rxAntennas: Int,
    val layers: Int,
    val pmi: Int,
    val codebookName: String,
    val beamGainDb: Double,
    val effectiveRank: Int,
    val conditionNumberDb: Double,
    val avgSingularValue: Double,
    val effectiveSinrDb: Double,
    val precoderErrorPercent: Double,
    val pass: Boolean,
    val note: String
)

class NrMimoV15 {
    fun run(cfg: NrMimoV15Config): NrMimoV15Result {
        val tx = cfg.txAntennas.coerceIn(1, 4)
        val rx = cfg.rxAntennas.coerceIn(1, 4)
        val v = cfg.layers.coerceIn(1, minOf(tx, rx))
        val nSc = cfg.prbs.coerceIn(1, 275) * 12
        val channel = NrChannelV11(NrChannelV11Config(tx, rx, cfg.prbs.coerceIn(1, 275), cfg.snrDb, cfg.channelModel, cfg.seed xor 0x5A))

        val candidates = codebook(tx, v)
        require(candidates.isNotEmpty())
        val selected = if (cfg.requestedPmi >= 0) candidates[cfg.requestedPmi.mod(candidates.size)] else {
            candidates.maxBy { averageEffectiveGain(channel, it.w, nSc) }
        }

        var bestGain = 0.0
        var baseline = 0.0
        var minRank = v
        var maxCond = 0.0
        var svSum = 0.0
        var svCount = 0
        var effectiveSinrPower = 0.0
        for (k in 0 until nSc) {
            val h = channel.frequencyResponse(k, nSc)
            val hw = multiply(h, selected.w)
            val sv = singularValues(hw, rx, v)
            val svMax = sv.firstOrNull() ?: 0.0
            val svMin = sv.lastOrNull() ?: 0.0
            val localRank = sv.count { it > svMax * 1e-2 }
            minRank = minOf(minRank, localRank)
            if (svMax > 0.0 && svMin > 1e-12) maxCond = maxOf(maxCond, 20.0 * log10(svMax / svMin))
            for (s in sv) { svSum += s; svCount++ }
            bestGain += hw.sumOf { row -> row.sumOf { it.abs2() } }
            baseline += h.sumOf { row -> row.sumOf { it.abs2() } }
            effectiveSinrPower += sv.sumOf { s -> s * s }
        }
        bestGain /= nSc.toDouble()
        baseline /= nSc.toDouble()
        val gainDb = 10.0 * log10((bestGain / (baseline / v).coerceAtLeast(1e-12)).coerceAtLeast(1e-12))
        val avgSv = svSum / svCount.coerceAtLeast(1)
        val noiseScale = 10.0.pow(-cfg.snrDb / 10.0)
        val effSinr = 10.0 * log10((effectiveSinrPower / nSc / v).coerceAtLeast(1e-12) / noiseScale.coerceAtLeast(1e-12))
        val orthoErr = orthogonalityError(selected.w, tx, v) * 100.0
        val pass = selected.w.size == tx && selected.w.firstOrNull()?.size == v &&
            orthoErr < 1e-6 && minRank >= 1 && effSinr.isFinite()

        return NrMimoV15Result(
            tx, rx, v, selected.index, selected.name, gainDb, minRank, maxCond,
            avgSv, effSinr, orthoErr, pass,
            "V15 selects a normalized precoder from an NR-oriented codebook and applies y = H·W·x. The 2-port family is the Type-I single-panel 2TX family; 4-port candidates are a deterministic unitary/DFT reference family for multi-antenna beam selection."
        )
    }

    private data class Candidate(val index: Int, val name: String, val w: Array<Array<Complex>>)

    private fun codebook(tx: Int, layers: Int): List<Candidate> {
        if (tx == 1) return listOf(Candidate(0, "Identity", arrayOf(arrayOf(Complex(1.0, 0.0)))))
        if (tx == 2) return typeI2Port(layers)
        return reference4Port(tx, layers)
    }

    // TS 38.214 Type-I single-panel, 2 antenna-port codebook:
    // v=1: [1, 1]^T/sqrt2, [1,j]^T/sqrt2, [1,-1]^T/sqrt2, [1,-j]^T/sqrt2
    // v=2: [ [1,1], [1,-1] ]/2 and [ [1,1], [j,-j] ]/2
    private fun typeI2Port(layers: Int): List<Candidate> {
        val s = 1.0 / sqrt(2.0)
        if (layers == 1) {
            return listOf(
                Candidate(0, "Type-I 2TX PMI0", arrayOf(arrayOf(Complex(s,0.0)), arrayOf(Complex(s,0.0)))),
                Candidate(1, "Type-I 2TX PMI1", arrayOf(arrayOf(Complex(s,0.0)), arrayOf(Complex(0.0,s)))),
                Candidate(2, "Type-I 2TX PMI2", arrayOf(arrayOf(Complex(s,0.0)), arrayOf(Complex(-s,0.0)))),
                Candidate(3, "Type-I 2TX PMI3", arrayOf(arrayOf(Complex(s,0.0)), arrayOf(Complex(0.0,-s))))
            )
        }
        val h = 0.5
        return listOf(
            Candidate(0, "Type-I 2TX 2-layer PMI0", arrayOf(arrayOf(Complex(h,0.0), Complex(h,0.0)), arrayOf(Complex(h,0.0), Complex(-h,0.0)))),
            Candidate(1, "Type-I 2TX 2-layer PMI1", arrayOf(arrayOf(Complex(h,0.0), Complex(h,0.0)), arrayOf(Complex(0.0,h), Complex(0.0,-h))))
        )
    }

    // Reference 4TX family: normalized DFT beams for one layer; disjoint
    // orthogonal beam pairs for two layers; normalized identity for four layers.
    private fun reference4Port(tx: Int, layers: Int): List<Candidate> {
        val out = ArrayList<Candidate>()
        if (layers == 1) {
            for (m in 0 until tx) {
                val col = Array(tx) { n ->
                    val a = 2.0 * PI * m * n / tx
                    Complex(cos(a) / sqrt(tx.toDouble()), sin(a) / sqrt(tx.toDouble()))
                }
                out += Candidate(m, "4TX DFT beam $m", Array(tx) { r -> arrayOf(col[r]) })
            }
        } else if (layers == 2) {
            val beams = Array(tx) { m -> Array(tx) { n ->
                val a = 2.0 * PI * m * n / tx
                Complex(cos(a) / sqrt(tx.toDouble()), sin(a) / sqrt(tx.toDouble()))
            } }
            for (a in 0 until tx) for (b in a + 1 until tx) {
                out += Candidate(out.size, "4TX DFT pair $a/$b", Array(tx) { r -> arrayOf(beams[a][r], beams[b][r]) })
            }
        } else {
            val q = Array(tx) { r -> Array(tx) { c -> if (r == c) Complex(1.0,0.0) else Complex(0.0,0.0) } }
            out += Candidate(0, "4TX identity", q.map { it.copyOf() }.toTypedArray())
            val h = 0.5
            out += Candidate(1, "4TX Hadamard", arrayOf(
                arrayOf(Complex(h,0.0), Complex(h,0.0), Complex(h,0.0), Complex(h,0.0)),
                arrayOf(Complex(h,0.0), Complex(-h,0.0), Complex(h,0.0), Complex(-h,0.0)),
                arrayOf(Complex(h,0.0), Complex(h,0.0), Complex(-h,0.0), Complex(-h,0.0)),
                arrayOf(Complex(h,0.0), Complex(-h,0.0), Complex(-h,0.0), Complex(h,0.0))
            ))
        }
        return out
    }

    private fun averageEffectiveGain(channel: NrChannelV11, w: Array<Array<Complex>>, nSc: Int): Double {
        var p = 0.0
        for (k in 0 until nSc) {
            val hw = multiply(channel.frequencyResponse(k, nSc), w)
            p += hw.sumOf { row -> row.sumOf { it.abs2() } }
        }
        return p / nSc.coerceAtLeast(1)
    }

    private fun multiply(a: Array<Array<Complex>>, b: Array<Array<Complex>>): Array<Array<Complex>> {
        val rows = a.size; val inner = a.firstOrNull()?.size ?: 0; val cols = b.firstOrNull()?.size ?: 0
        return Array(rows) { r -> Array(cols) { c ->
            var s = Complex(0.0,0.0)
            for (i in 0 until minOf(inner, b.size)) s += a[r][i] * b[i][c]
            s
        } }
    }

    private fun singularValues(a: Array<Array<Complex>>, rows: Int, cols: Int): DoubleArray {
        val gram = Array(cols) { Array(cols) { Complex(0.0,0.0) } }
        for (i in 0 until cols) for (j in 0 until cols) {
            var s = Complex(0.0,0.0)
            for (r in 0 until rows) s += a[r][i].conj() * a[r][j]
            gram[i][j] = s
        }
        val vals = jacobiHermitian(gram)
        return vals.map { sqrt(it.coerceAtLeast(0.0)) }.sortedDescending().toDoubleArray()
    }

    private fun jacobiHermitian(a: Array<Array<Complex>>): DoubleArray {
        val n = a.size
        if (n == 0) return doubleArrayOf()
        val m = Array(2*n) { DoubleArray(2*n) }
        for (i in 0 until n) for (j in 0 until n) {
            m[i][j] = a[i][j].re; m[i][j+n] = -a[i][j].im
            m[i+n][j] = a[i][j].im; m[i+n][j+n] = a[i][j].re
        }
        repeat(80) {
            var p=0; var q=1; var max=0.0
            for (i in 0 until 2*n) for (j in i+1 until 2*n) if (abs(m[i][j]) > max) { max=abs(m[i][j]); p=i; q=j }
            if (max < 1e-11) return@repeat
            val phi=0.5*atan2(2*m[p][q],m[q][q]-m[p][p]); val c=cos(phi); val s=sin(phi)
            for (k in 0 until 2*n) { val x=m[p][k]; val y=m[q][k]; m[p][k]=c*x-s*y; m[q][k]=s*x+c*y }
            for (k in 0 until 2*n) { val x=m[k][p]; val y=m[k][q]; m[k][p]=c*x-s*y; m[k][q]=s*x+c*y }
        }
        val all=DoubleArray(2*n){m[it][it]}.sortedArrayDescending()
        return DoubleArray(n){all[2*it]}
    }

    private fun orthogonalityError(w: Array<Array<Complex>>, tx: Int, layers: Int): Double {
        // NR codebooks can use a common scalar normalization for a multi-layer
        // precoder (for example the 2TX/2-layer Type-I matrix has 1/2 entries,
        // so WᴴW = 0.5 I). Compare against the matrix's own per-layer power
        // rather than incorrectly requiring every table to have unit-norm columns.
        var total = 0.0
        for (r in 0 until tx) for (c in 0 until layers) total += w[r][c].abs2()
        val targetDiag = total / layers.coerceAtLeast(1)
        var e=0.0
        for (i in 0 until layers) for (j in 0 until layers) {
            var s=Complex(0.0,0.0)
            for (r in 0 until tx) s += w[r][i].conj()*w[r][j]
            val target=if(i==j) Complex(targetDiag,0.0) else Complex(0.0,0.0)
            e += (s-target).abs2()
        }
        return sqrt(e)
    }
}
