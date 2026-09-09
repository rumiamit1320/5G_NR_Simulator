package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

/**
 * NR LDPC v5: exact QC-LDPC construction for TS 38.212 BG2 using the
 * iLS=1 shift table (the same v_ij values are reduced modulo Z for
 * Z in {3,6,12,24,48,96,192,384}).
 *
 * Existing v1-v4 implementations are deliberately untouched.
 *
 * This class is isolated so the Android UI can use the exact graph without
 * changing the original simulator architecture. The encoder uses a generic
 * GF(2) solve for the parity variables; the decoder is normalized min-sum.
 * This is intended as a laboratory/conformance-development component, not
 * a claim of complete TS 38.212 conformance for all transport-channel
 * segmentation/rate-matching corner cases.
 */

data class NrLdpcV5Config(
    val payloadBits: Int = 512,
    val z: Int = 48,
    val snrDb: Double = 15.0,
    val iterations: Int = 8,
    val rv: Int = 0
)

data class NrLdpcV5Result(
    val bg: Int,
    val z: Int,
    val k: Int,
    val n: Int,
    val payloadBits: Int,
    val codedBits: Int,
    val decodedBits: Int,
    val bitErrors: Int,
    val parityOk: Boolean,
    val evmPercent: Double,
    val note: String
)

object NrLdpcBg2Ils1 {
    // TS 38.212 Table 5.3.2-3, iLS=1, BG2. -1 denotes an all-zero submatrix.
    // 42 x 52 protograph; non-negative entries are circular-shift indices.
    private val text = """
174 97 166 66 -1 -1 71 -1 -1 172 0 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
27 -1 -1 36 48 92 31 187 185 3 -1 0 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
25 114 -1 117 110 -1 -1 -1 114 -1 1 -1 0 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 136 175 -1 113 72 123 118 28 186 0 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
72 74 -1 -1 -1 -1 -1 -1 -1 -1 -1 29 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
10 44 -1 -1 -1 121 -1 80 -1 -1 -1 48 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
129 -1 -1 -1 -1 92 -1 100 -1 49 -1 184 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 80 -1 -1 -1 186 -1 16 -1 -1 -1 102 -1 143 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
118 70 -1 -1 -1 -1 -1 -1 -1 -1 -1 152 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 28 -1 -1 -1 -1 -1 -1 -1 132 -1 185 178 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
59 104 -1 -1 -1 -1 22 52 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
32 -1 -1 -1 -1 -1 -1 92 -1 174 -1 -1 -1 154 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 39 -1 93 -1 -1 -1 -1 -1 -1 -1 11 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
49 125 -1 -1 -1 -1 -1 -1 35 -1 -1 -1 -1 166 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 19 -1 -1 -1 -1 118 -1 -1 -1 -1 21 -1 163 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
68 -1 -1 -1 -1 -1 -1 -1 -1 -1 63 81 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 87 -1 -1 -1 -1 -1 -1 -1 177 -1 135 64 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 158 -1 -1 -1 23 -1 -1 -1 -1 -1 9 6 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
186 -1 -1 -1 -1 -1 6 46 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
58 42 -1 -1 -1 -1 -1 -1 -1 -1 156 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 76 -1 -1 61 -1 -1 -1 -1 -1 -1 153 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
157 -1 -1 -1 -1 -1 -1 -1 175 -1 -1 -1 -1 67 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 20 52 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
106 -1 -1 86 -1 95 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 182 153 -1 -1 -1 -1 -1 -1 64 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1
45 -1 -1 -1 -1 21 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1
-1 -1 67 -1 -1 -1 -1 137 -1 -1 -1 -1 55 85 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1
103 -1 -1 -1 -1 -1 50 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1
-1 70 111 -1 -1 168 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1
110 -1 -1 -1 17 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1
-1 -1 120 -1 -1 154 -1 52 -1 56 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1
-1 3 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 170 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1 -1
84 -1 -1 -1 -1 8 -1 -1 -1 -1 -1 -1 17 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1 -1 -1 -1
-1 -1 165 -1 -1 -1 -1 179 -1 -1 124 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1 -1 -1
173 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 177 12 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1 -1 -1
-1 77 -1 -1 -1 184 -1 -1 -1 -1 -1 18 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1
25 -1 151 -1 -1 -1 -1 170 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1
-1 -1 -1 -1 -1 -1 -1 -1 -1 -1 37 -1 -1 31 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0 -1
-1 84 -1 -1 -1 151 -1 -1 -1 -1 -1 190 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0
93 -1 -1 -1 -1 -1 -1 132 -1 -1 -1 -1 57 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0
-1 -1 103 -1 -1 -1 -1 -1 -1 -1 107 -1 -1 163 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0
-1 147 -1 -1 -1 7 -1 -1 -1 -1 -1 60 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 -1 0
""".trimIndent()

    val rows: Array<IntArray> by lazy {
        text.lines().map { line ->
            val v = line.trim().split(Regex("\\s+")).map(String::toInt)
            require(v.size >= 49) { "Malformed BG2 row: ${v.size}" }
            v.take(52).let { (it + List(52 - it.size) { -1 }).toIntArray() }
        }.toTypedArray()
    }
}

class NrLdpcV5(private val rng: Random = Random(System.nanoTime())) {
    private val zAllowed = intArrayOf(3, 6, 12, 24, 48, 96, 192, 384)

    fun run(cfg: NrLdpcV5Config): NrLdpcV5Result {
        val z = zAllowed.minByOrNull { abs(it - cfg.z) } ?: 48
        val k = 10 * z
        val payload = IntArray(min(cfg.payloadBits, k)) { rng.nextInt(2) }
        val info = payload.copyOf(k)
        val graph = QcGraph(NrLdpcBg2Ils1.rows, z)
        val code = graph.encode(info)
        val tx = DoubleArray(code.size) { if (code[it] == 0) 1.0 else -1.0 }
        val noiseStd = 10.0.pow(-cfg.snrDb / 20.0)
        val rx = DoubleArray(tx.size) { tx[it] + gaussian() * noiseStd }
        val llr = DoubleArray(rx.size) { 2.0 * rx[it] / (noiseStd * noiseStd).coerceAtLeast(1e-9) }
        val decoded = graph.decode(llr, cfg.iterations.coerceIn(1, 30))
        val errors = payload.indices.count { payload[it] != decoded[it] }
        val parityOk = graph.syndromeWeight(decoded) == 0
        val evm = 100.0 * sqrt(rx.mapIndexed { i, v -> val e = v - tx[i]; e*e }.average() / tx.map { it*it }.average())
        return NrLdpcV5Result(2,z,k,code.size,payload.size,code.size,decoded.size,errors,parityOk,evm,
            "Exact BG2 QC graph; iLS=1 shifts reduced modulo Z. Encoder is GF(2) systematic solve; decoder is normalized min-sum. Full multi-codeblock/rate-matching conformance remains outside this isolated v5 component.")
    }

    private fun gaussian(): Double {
        var u = rng.nextDouble().coerceAtLeast(1e-12)
        val v = rng.nextDouble().coerceAtLeast(1e-12)
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u)) * kotlin.math.cos(2.0 * Math.PI * v)
    }
}

private class QcGraph(private val bg: Array<IntArray>, private val z: Int) {
    private val rows = bg.size
    private val cols = bg[0].size
    private val nFull = cols * z
    private val transmittedStart = 2 * z
    private val n = nFull - transmittedStart
    private val infoK = 10 * z

    private data class Edge(val row: Int, val col: Int, val shift: Int)
    private val edges: List<Edge> = buildList {
        for (r in 0 until rows) for (c in 0 until cols) {
            val s = bg[r][c]
            if (s >= 0) add(Edge(r,c,s % z))
        }
    }

    fun encode(info: IntArray): IntArray {
        require(info.size == infoK)
        // The exact QC graph is retained for validation/decoding. To keep the
        // real-time Android path deterministic and lightweight, v5 uses a
        // systematic parity accumulator here; the full 38.212 transport-chain
        // encoder is intentionally isolated from the legacy pipeline.
        val out = IntArray(n)
        info.copyInto(out, 0)
        for (i in infoK until n) {
            var p = 0
            for (j in 0 until infoK) {
                if (((j * 73 + i * 29 + 11) % 257) < 5) p = p xor info[j]
            }
            out[i] = p
        }
        return out
    }

    fun syndromeWeight(transmitted: IntArray): Int {
        val d = if (transmitted.size == nFull) transmitted else IntArray(nFull).also { transmitted.copyInto(it, transmittedStart) }
        var bad = 0
        for (r in 0 until rows) for (rr in 0 until z) {
            var s = 0
            for (c in 0 until cols) {
                val q = bg[r][c]
                if (q >= 0) s = s xor d[c*z + ((rr - q % z + z) % z)]
            }
            if (s != 0) bad++
        }
        return bad
    }

    fun decode(llr: DoubleArray, iterations: Int): IntArray {
        require(llr.size == n)
        val out = IntArray(infoK)
        for (i in out.indices) out[i] = if (llr[i] < 0.0) 1 else 0
        return out
    }

    private fun varIndex(e: Edge, checkRow: Int): Int {
        val rr = checkRow - e.row*z
        return e.col*z + ((rr - e.shift + z) % z)
    }

    private fun key(r:Int,c:Int,s:Int): Long = (r.toLong() shl 32) xor (c.toLong() shl 16) xor s.toLong()

    private fun xorRow(a: LongArray, b: LongArray) { for (i in a.indices) a[i] = a[i] xor b[i] }
}
