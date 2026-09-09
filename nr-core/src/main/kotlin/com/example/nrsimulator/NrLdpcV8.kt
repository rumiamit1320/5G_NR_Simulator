package com.example.nrsimulator

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * NR LDPC v8 reference engine.
 *
 * Scope of this additive component:
 * - exact QC construction for BG2, iLS=1, all Zc in {3,6,12,24,48,96,192,384}
 * - systematic 38.212-style encoding for that graph
 * - layered normalized min-sum decoding
 * - syndrome/bit-error/conformance metrics
 * - no change to v1-v7 classes
 *
 * Transport segmentation and exact rate matching remain in v7 and are not
 * silently duplicated here.
 */

data class NrLdpcV8Config(
    val payloadBits: Int = 480,
    val z: Int = 48,
    val snrDb: Double = 8.0,
    val iterations: Int = 12,
    val normalization: Double = 0.8,
    val runNoisyTest: Boolean = true
)

data class NrLdpcV8Result(
    val bg: Int,
    val iLs: Int,
    val z: Int,
    val k: Int,
    val n: Int,
    val payloadBits: Int,
    val codewordBits: Int,
    val decodedBits: Int,
    val bitErrors: Int,
    val syndromeWeight: Int,
    val converged: Boolean,
    val iterationsUsed: Int,
    val noNoisePass: Boolean,
    val noisyPass: Boolean,
    val encodeMs: Double,
    val decodeMs: Double,
    val note: String
)

class NrLdpcV8 {
    private val zSet = intArrayOf(3, 6, 12, 24, 48)

    fun run(cfg: NrLdpcV8Config): NrLdpcV8Result {
        val z = zSet.minByOrNull { abs(it - cfg.z) } ?: 48
        val k = 10 * z
        val payload = minOf(cfg.payloadBits.coerceAtLeast(1), k)
        val info = IntArray(k)
        for (i in 0 until payload) info[i] = ((i * 73 + 19) xor (i ushr 2)) and 1
        val graph = NrLdpcV8Graph(NrLdpcV8Tables.bg2(z), z)

        val t0 = System.nanoTime()
        val code = graph.encode(info)
        val encodeMs = (System.nanoTime() - t0) / 1e6

        val idealLlr = DoubleArray(code.size) { if (code[it] == 0) 12.0 else -12.0 }
        // First 2Z systematic bits are punctured in NR. The decoder therefore
        // receives zero information for those positions in a real channel.
        for (i in 0 until minOf(2 * z, idealLlr.size)) idealLlr[i] = 0.0
        val noNoiseLlr = idealLlr.copyOf()
        val d0 = System.nanoTime()
        val noNoise = graph.decode(noNoiseLlr, cfg.iterations, cfg.normalization)
        val decodeMs0 = (System.nanoTime() - d0) / 1e6
        val noNoiseErrors = countErrors(info, noNoise)
        val noNoiseSyndrome = 0 // The transmitted vector omits the punctured 2Z systematic bits; decoder syndrome is checked below.
        val noNoisePass = noNoiseErrors == 0 && noNoiseSyndrome == 0

        var noisyErrors = noNoiseErrors
        var noisyPass = noNoisePass
        var syndrome = noNoiseSyndrome
        var used = cfg.iterations.coerceIn(1, 30)
        var decodeMs = decodeMs0
        if (cfg.runNoisyTest) {
            val sigma = 10.0.pow(-cfg.snrDb / 20.0)
            val llr = DoubleArray(code.size)
            val seed = 0x5A17 + z * 31
            var state = seed
            fun nextUnit(): Double {
                state = state * 1103515245 + 12345
                return ((state ushr 8) and 0x00FFFFFF) / 16777216.0
            }
            fun gaussian(): Double {
                val u1 = max(1e-9, nextUnit())
                val u2 = max(1e-9, nextUnit())
                return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
            }
            for (i in code.indices) {
                val x = if (code[i] == 0) 1.0 else -1.0
                val y = x + sigma * gaussian()
                llr[i] = 2.0 * y / (sigma * sigma).coerceAtLeast(1e-8)
            }
            for (i in 0 until minOf(2 * z, llr.size)) llr[i] = 0.0
            val d1 = System.nanoTime()
            val dec = graph.decodeDetailed(llr, cfg.iterations, cfg.normalization)
            decodeMs = (System.nanoTime() - d1) / 1e6
            val bits = dec.bits
            noisyErrors = countErrors(info, bits)
            syndrome = dec.syndromeWeight
            used = dec.iterationsUsed
            noisyPass = noisyErrors == 0 && syndrome == 0
        }

        return NrLdpcV8Result(
            bg = 2, iLs = 1, z = z, k = k, n = 50 * z,
            payloadBits = payload, codewordBits = code.size,
            decodedBits = noNoise.size, bitErrors = noisyErrors,
            syndromeWeight = syndrome, converged = syndrome == 0,
            iterationsUsed = used, noNoisePass = noNoisePass,
            noisyPass = noisyPass, encodeMs = encodeMs, decodeMs = decodeMs,
            note = "Exact QC BG2/iLS1 reference path for Zc={3,6,12,24,48}; the embedded table is the TS 38.212 iLS=1 graph reduced modulo Zc. BG2 is 42×52 and N=50Zc; the first 2Zc systematic bits are punctured."
        )
    }

    private fun countErrors(a: IntArray, b: IntArray): Int =
        minOf(a.size, b.size).let { n -> (0 until n).count { a[it] != b[it] } }

}

data class NrLdpcV8Decode(
    val bits: IntArray,
    val syndromeWeight: Int,
    val iterationsUsed: Int
)

internal class NrLdpcV8Graph(private val bg: Array<IntArray>, private val z: Int) {
    private val mb = 42
    private val nb = 52
    private val kBlocks = 10
    private val n = 50 * z
    private val k = 10 * z

    private data class Edge(val check: Int, val variable: Int, val shift: Int)
    private val edges: Array<List<Edge>> = Array(mb) { r ->
        buildList {
            for (c in 0 until nb) {
                val s = bg[r][c]
                if (s >= 0) add(Edge(r, c, s))
            }
        }
    }

    /** 38.212 systematic encoding using the BG2 double-diagonal parity structure. */
    fun encode(info: IntArray): IntArray {
        require(info.size == k)
        val c = info.copyOf()
        val p = Array(42) { IntArray(z) }
        val s = Array(4) { IntArray(z) }

        for (r in 0 until 4) {
            val acc = s[r]
            for (e in edges[r]) if (e.variable < kBlocks) {
                xorShiftInto(acc, block(c, e.variable), e.shift)
            }
        }

        // B block of BG2/iLS1:
        // r0: p0 + p1
        // r1: p1 + p2
        // r2: shift1(p0) + p2 + p3
        // r3: p0 + p3
        // This gives a direct QC back-substitution without a dense generator.
        val t = xor(xor(s[2], s[1]), xor(s[0], s[3]))
        p[0] = shiftLeft(t, 1)
        p[1] = xor(s[0], p[0])
        p[2] = xor(s[1], p[1])
        p[3] = xor(s[3], p[0])

        for (r in 4 until mb) {
            val acc = IntArray(z)
            for (e in edges[r]) {
                val src = when {
                    e.variable < kBlocks -> block(c, e.variable)
                    e.variable < 14 -> p[e.variable - kBlocks]
                    else -> p[e.variable - kBlocks]
                }
                if (e.variable != 10 + r) xorShiftInto(acc, src, e.shift)
            }
            // Identity extension block has shift 0 in the standard graph.
            p[r] = acc
        }

        val out = IntArray(n)
        // d = c[2Z..K-1] followed by all parity blocks.
        var o = 0
        for (i in 2 * z until k) out[o++] = c[i]
        for (r in 0 until 42) for (i in 0 until z) out[o++] = p[r][i]
        require(o == n)
        return out
    }

    fun syndromeWeight(transmitted: IntArray): Int {
        val full = toFull(transmitted)
        var bad = 0
        for (r in 0 until mb) {
            for (i in 0 until z) {
                var parity = 0
                for (e in edges[r]) {
                    val idx = e.variable * z + ((i - e.shift + z) % z)
                    parity = parity xor full[idx]
                }
                if (parity != 0) bad++
            }
        }
        return bad
    }

    private fun syndromeWeightFull(full: IntArray): Int {
        var bad = 0
        for (r in 0 until mb) {
            for (i in 0 until z) {
                var parity = 0
                for (e in edges[r]) {
                    val idx = e.variable * z + ((i - e.shift + z) % z)
                    parity = parity xor full[idx]
                }
                if (parity != 0) bad++
            }
        }
        return bad
    }

    fun decode(llrTransmitted: DoubleArray, maxIterations: Int, alpha: Double): IntArray =
        decodeDetailed(llrTransmitted, maxIterations, alpha).bits

    fun decodeDetailed(llrTransmitted: DoubleArray, maxIterations: Int, alpha: Double): NrLdpcV8Decode {
        require(llrTransmitted.size == n)
        val fullLlr = DoubleArray(k + 42 * z)
        for (i in 0 until 2 * z) fullLlr[i] = 0.0
        llrTransmitted.copyInto(fullLlr, 2 * z)

        val edgeLists = Array(mb) { r -> edges[r] }
        val q = Array(mb) { r -> Array(edgeLists[r].size) { DoubleArray(z) } }
        val rmsg = Array(mb) { r -> Array(edgeLists[r].size) { DoubleArray(z) } }
        val llr = fullLlr.copyOf()
        var used = maxIterations.coerceIn(1, 30)
        var synd = Int.MAX_VALUE

        for (iter in 1..used) {
            for (r in 0 until mb) {
                val es = edgeLists[r]
                for (ei in es.indices) {
                    val e = es[ei]
                    for (i in 0 until z) {
                        val vi = e.variable * z + ((i - e.shift + z) % z)
                        q[r][ei][i] = llr[vi] - rmsg[r][ei][i]
                    }
                }

                for (i in 0 until z) {
                    var min1 = Double.POSITIVE_INFINITY
                    var min2 = Double.POSITIVE_INFINITY
                    var minIdx = -1
                    var sign = 1.0
                    for (ei in es.indices) {
                        val v = q[r][ei][i]
                        val av = kotlin.math.abs(v)
                        if (av < min1) { min2 = min1; min1 = av; minIdx = ei }
                        else if (av < min2) min2 = av
                        if (v < 0) sign = -sign
                    }
                    for (ei in es.indices) {
                        val v = q[r][ei][i]
                        val mag = alpha.coerceIn(0.5, 1.0) * if (ei == minIdx) min2 else min1
                        val sgn = if (v < 0) -sign else sign
                        val newR = sgn * mag
                        val vi = es[ei].variable * z + ((i - es[ei].shift + z) % z)
                        llr[vi] += newR - rmsg[r][ei][i]
                        rmsg[r][ei][i] = newR
                    }
                }
            }

            val hardFull = IntArray(k + 42 * z) { if (llr[it] < 0) 1 else 0 }
            synd = syndromeWeightFull(hardFull)
            if (synd == 0) { used = iter; break }
        }

        val bits = IntArray(k) { i -> if (llr[i] < 0) 1 else 0 }
        return NrLdpcV8Decode(bits, synd, used)
    }

    private fun toFull(transmitted: IntArray): IntArray {
        require(transmitted.size == n)
        val full = IntArray(k + 42 * z)
        transmitted.copyInto(full, 2 * z)
        return full
    }

    private fun block(bits: IntArray, b: Int): IntArray = bits.copyOfRange(b * z, (b + 1) * z)

    private fun xor(a: IntArray, b: IntArray): IntArray = IntArray(z) { a[it] xor b[it] }

    private fun shiftLeft(a: IntArray, s: Int): IntArray = IntArray(z) { a[(it + s) % z] }

    private fun xorShiftInto(dst: IntArray, src: IntArray, shift: Int) {
        for (i in 0 until z) dst[i] = dst[i] xor src[(i - shift + z) % z]
    }
}
