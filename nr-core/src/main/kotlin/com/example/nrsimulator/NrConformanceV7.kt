package com.example.nrsimulator

import kotlin.math.ceil

/**
 * NR v7 conformance-oriented transport/rate-matching layer.
 *
 * This is additive: v1-v6 remain untouched.  The class makes the 38.212
 * transport decisions explicit and deterministic so the exact LDPC graph
 * engine can be plugged in later without changing the Android architecture.
 */

data class NrV7Config(
    val a: Int = 4000,
    val targetRate: Double = 0.5,
    val qm: Int = 2,
    val layers: Int = 1,
    val nRe: Int = 156,
    val rv: Int = 0
)

data class NrV7Result(
    val a: Int,
    val bg: Int,
    val c: Int,
    val l: Int,
    val b: Int,
    val bPrime: Int,
    val zc: Int,
    val k: Int,
    val n: Int,
    val filler: Int,
    val ncb: Int,
    val g: Int,
    val ePerCb: IntArray,
    val rv: Int,
    val uniqueRateMatchIndices: Int,
    val duplicateRateMatchIndices: Int,
    val note: String
)

class NrConformanceV7 {
    private val zSets = arrayOf(
        intArrayOf(2,4,8,16,32,64,128,256),
        intArrayOf(3,6,12,24,48,96,192,384),
        intArrayOf(5,10,20,40,80,160,320),
        intArrayOf(7,14,28,56,112,224),
        intArrayOf(9,18,36,72,144,288),
        intArrayOf(11,22,44,88,176,352),
        intArrayOf(13,26,52,104,208),
        intArrayOf(15,30,60,120,240)
    )

    fun run(cfg: NrV7Config): NrV7Result {
        require(cfg.qm in 2..8 && cfg.layers in 1..4)
        val a = cfg.a.coerceIn(1, 382400)
        val r = cfg.targetRate.coerceIn(0.01, 0.95)
        val bg = selectBg(a, r)
        val l = 0 // CB CRC is added below only when C > 1
        val b = a + 24
        val maxKb = if (bg == 1) 8448 else 3840
        val c = if (b <= maxKb) 1 else ceil(b.toDouble() / (maxKb - 24)).toInt()
        val cbCrcBits = if (c > 1) 24 else 0
        val bPrime = b + c * cbCrcBits
        val kPrime = ceil(bPrime.toDouble() / c).toInt()
        val zc = selectZc(bg, kPrime, bPrime / c)
        val k = (if (bg == 1) 22 else 10) * zc
        val n = (if (bg == 1) 66 else 50) * zc
        val filler = c * k - bPrime
        val ncb = n
        val g = cfg.nRe * cfg.qm * cfg.layers
        val e = distributeE(g, c)
        val indices = e.flatMapIndexed { cb, ec -> rateMatchIndices(ncb, ec, cfg.rv, cb, c) }
        val unique = indices.toSet().size
        return NrV7Result(
            a, bg, c, l, b, bPrime, zc, k, n, filler, ncb, g, e, cfg.rv and 3,
            unique, indices.size - unique,
            "38.212-oriented BG/Zc/segmentation/rate-matching decisions. Exact LDPC parity-check graph/encoder remains isolated in v5; this layer is designed so an exact graph backend can replace it without altering v1-v6."
        )
    }

    private fun selectBg(a: Int, r: Double): Int =
        if (a <= 292 || (a <= 3824 && r <= 0.67) || r <= 0.25) 2 else 1

    private fun selectZc(bg: Int, kPrime: Int, cbSize: Int): Int {
        val kB = if (bg == 1) 22 else when {
            cbSize > 640 -> 10
            cbSize > 560 -> 9
            cbSize > 192 -> 8
            else -> 6
        }
        val all = zSets.asSequence().flatMap { it.asSequence() }.distinct().sorted()
        return all.firstOrNull { kB * it >= kPrime } ?: 384
    }

    private fun distributeE(g: Int, c: Int): IntArray {
        val base = g / c
        val rem = g % c
        return IntArray(c) { i -> base + if (i < rem) 1 else 0 }
    }

    /** Circular-buffer rate matching index sequence with RV-dependent start. */
    private fun rateMatchIndices(ncb: Int, e: Int, rv: Int, cb: Int, c: Int): List<Int> {
        if (e <= 0) return emptyList()
        val start = ((rv and 3) * ncb) / 4
        val out = ArrayList<Int>(e)
        var k = start
        while (out.size < e) {
            val idx = k % ncb
            // First 2*Zc positions are punctured by NR LDPC; the caller supplies
            // the shortened circular buffer, so this layer deliberately works on
            // the transmitted Ncb domain only.
            out += idx
            k++
        }
        return out
    }
}

object NrV7KnownAnswerTests {
    data class Case(val a: Int, val r: Double, val expectedBg: Int)

    fun run(): List<String> {
        val cases = listOf(
            Case(292, 0.90, 2),
            Case(293, 0.50, 2),
            Case(3824, 0.67, 2),
            Case(3824, 0.68, 1),
            Case(4000, 0.50, 1),
            Case(4000, 0.20, 2)
        )
        val engine = NrConformanceV7()
        return cases.map {
            val got = engine.run(NrV7Config(a = it.a, targetRate = it.r)).bg
            "A=${it.a},R=${it.r}: BG${got} ${if (got == it.expectedBg) "PASS" else "FAIL expected BG${it.expectedBg}"}"
        }
    }
}
