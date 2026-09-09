package com.example.nrsimulator

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.random.Random

/**
 * NR v9 additive transport/LDPC integration.
 *
 * Existing v1-v8 components are preserved.  This layer connects the v7
 * transport decisions to the v8 exact BG2/iLS1 QC engine and adds a reference
 * circular-buffer rate matcher/rate-recovery path with the NR RV k0 fractions.
 * It intentionally reports when the requested transport case is outside the
 * currently embedded exact BG2/iLS1 table family.
 */

data class NrTransportV9Config(
    val payloadBits: Int = 300,
    val targetCodeRate: Double = 0.5,
    val rv: Int = 0,
    val qm: Int = 2,
    val layers: Int = 1,
    val nRe: Int = 600,
    val snrDb: Double = 15.0,
    val seed: Int = 0x51A9
)

data class NrTransportV9Result(
    val a: Int,
    val tbCrcBits: Int,
    val bg: Int,
    val c: Int,
    val zc: Int,
    val k: Int,
    val n: Int,
    val fillerBits: Int,
    val g: Int,
    val ePerCb: IntArray,
    val rv: Int,
    val k0: Int,
    val selectedBits: Int,
    val recoveredBits: Int,
    val decodeErrors: Int,
    val syndromeWeight: Int,
    val ldpcPass: Boolean,
    val tbCrcOk: Boolean,
    val encodeMs: Double,
    val decodeMs: Double,
    val note: String
)

class NrTransportV9 {
    private val supportedZ = intArrayOf(3, 6, 12, 24, 48, 96, 192, 384)

    fun run(cfg: NrTransportV9Config): NrTransportV9Result {
        val a = cfg.payloadBits.coerceIn(40, 382400)
        val r = cfg.targetCodeRate.coerceIn(0.05, 0.95)
        val bg = selectBg(a, r)
        if (bg != 2) return unsupported(cfg, a, bg, "BG1 is selected; v9 exact integration currently embeds BG2/iLS1 only.")

        val crcBits = if (a > 3824) 24 else 16
        val b = a + crcBits
        val maxKb = 3840
        val c = if (b <= maxKb) 1 else ceil(b.toDouble() / (maxKb - 24)).toInt()
        val cbCrcBits = if (c > 1) 24 else 0
        val bPrime = b + c * cbCrcBits
        val kB = when {
            bPrime > 640 -> 10
            bPrime > 560 -> 9
            bPrime > 192 -> 8
            else -> 6
        }
        val z = supportedZ.firstOrNull { kB * it >= ceil(bPrime.toDouble() / c).toInt() }
            ?: return unsupported(cfg, a, bg, "No embedded BG2/iLS1 lifting size can carry the requested code block.")
        val k = 10 * z
        val n = 50 * z
        val filler = (c * k - bPrime).coerceAtLeast(0)
        val g = (cfg.nRe * cfg.qm * cfg.layers).coerceAtLeast(1)
        val e = distributeE(g, c)
        val rv = cfg.rv and 3
        val k0 = k0(n, rv)

        // v9 reference path is deliberately a one-code-block exact LDPC test.
        // For C>1, transport metadata is still exposed but encoding is deferred.
        if (c != 1) return unsupported(cfg, a, bg, "C=$c requires multi-code-block segmentation; v9 keeps the integration test single-CB.")

        val payload = deterministicBits(a, cfg.seed)
        val tb = appendTbCrc(payload, crcBits)
        val info = IntArray(k) // first F bits are filler/null
        for (i in tb.indices) info[filler + i] = tb[i]

        val graph = NrLdpcV8Graph(NrLdpcV8Tables.bg2(z), z)
        val t0 = System.nanoTime()
        val code = graph.encode(info)
        val encodeMs = (System.nanoTime() - t0) / 1e6

        val selected = rateMatch(code, n, e[0], rv, z, filler)
        val llr = DoubleArray(n) // transmitted-domain Ncb recovery
        val selectedIndices = selectionIndices(n, e[0], rv, z, filler)
        val sigma = 10.0.pow(-cfg.snrDb / 20.0)
        val rng = Random(cfg.seed xor (rv shl 8) xor z)
        for (i in selected.indices) {
            val bit = selected[i]
            val x = if (bit == 0) 1.0 else -1.0
            val y = x + gaussian(rng) * sigma
            llr[selectedIndices[i]] += 2.0 * y / (sigma * sigma).coerceAtLeast(1e-8)
        }

        val recovered = rateRecover(llr, n, e[0], rv, z, filler)
        val t1 = System.nanoTime()
        val dec = graph.decode(recovered, 20, 0.8)
        val decodeMs = (System.nanoTime() - t1) / 1e6
        val decodedTb = dec.copyOfRange(filler, filler + tb.size)
        val errors = countErrors(tb, decodedTb)
        val tbOk = checkTbCrc(decodedTb, crcBits)
        val syndrome = graph.syndromeWeight(code)

        return NrTransportV9Result(
            a, crcBits, bg, c, z, k, n, filler, g, e, rv, k0,
            selected.size, recovered.count { it != 0.0 }, errors, syndrome,
            errors == 0 && tbOk, tbOk, encodeMs, decodeMs,
            "V9 integrates v7 transport metadata with the v8 BG2/iLS1 QC engine. " +
                "Rate matching uses NR RV k0 fractions for BG2 (0, 13/50, 25/50, 43/50) and skips punctured/filler positions. " +
                "BG1 and multi-CB exact integration remain isolated for the next conformance stage."
        )
    }

    private fun unsupported(cfg: NrTransportV9Config, a: Int, bg: Int, why: String): NrTransportV9Result =
        NrTransportV9Result(a, if (a > 3824) 24 else 16, bg, 0, 0, 0, 0, 0, (cfg.nRe * cfg.qm * cfg.layers), intArrayOf(), cfg.rv and 3, 0, 0, 0, 0, 0, false, false, 0.0, 0.0, why)

    private fun selectBg(a: Int, r: Double): Int = if (a <= 292 || (a <= 3824 && r <= 0.67) || r <= 0.25) 2 else 1

    private fun distributeE(g: Int, c: Int): IntArray {
        val base = g / c
        val rem = g % c
        return IntArray(c) { i -> base + if (i < rem) 1 else 0 }
    }

    /** k0 for normal LDPC rate matching, before null/filler skipping. */
    private fun k0(n: Int, rv: Int): Int = when (rv and 3) {
        0 -> 0
        1 -> (n * 13) / 50
        2 -> (n * 25) / 50
        else -> (n * 43) / 50
    }

    private fun selectionIndices(n: Int, e: Int, rv: Int, z: Int, filler: Int): IntArray {
        val out = IntArray(e)
        val fillerTransmitted = max(0, filler - 2 * z)
        val start = k0(n, rv)
        var count = 0
        var j = 0
        while (count < e) {
            val idx = (start + j) % n
            if (idx >= fillerTransmitted) out[count++] = idx
            j++
        }
        return out
    }

    private fun rateMatch(code: IntArray, n: Int, e: Int, rv: Int, z: Int, filler: Int): IntArray {
        val indices = selectionIndices(n, e, rv, z, filler)
        return IntArray(e) { code[indices[it]] }
    }

    private fun rateRecover(llr: DoubleArray, n: Int, e: Int, rv: Int, z: Int, filler: Int): DoubleArray {
        val out = DoubleArray(n)
        val indices = selectionIndices(n, e, rv, z, filler)
        for (i in indices.indices) out[indices[i]] += llr[indices[i]]
        return out
    }

    private fun deterministicBits(n: Int, seed: Int): IntArray {
        var s = seed
        return IntArray(n) {
            s = s * 1664525 + 1013904223
            (s ushr 31) and 1
        }
    }

    private fun appendTbCrc(payload: IntArray, bits: Int): IntArray {
        val crc = if (bits == 24) crc24A(payload) else crc16(payload)
        val out = payload.copyOf(payload.size + bits)
        for (i in 0 until bits) out[payload.size + i] = (crc ushr (bits - 1 - i)) and 1
        return out
    }

    private fun checkTbCrc(bits: IntArray, crcBits: Int): Boolean {
        if (bits.size < crcBits) return false
        val a = bits.copyOf(bits.size - crcBits)
        var got = 0
        for (i in bits.size - crcBits until bits.size) got = (got shl 1) or bits[i]
        val expected = if (crcBits == 24) crc24A(a) else crc16(a)
        return got == expected
    }

    private fun crc16(bits: IntArray): Int {
        var crc = 0
        for (b in bits) {
            val top = ((crc ushr 15) and 1) xor b
            crc = (crc shl 1) and 0xFFFF
            if (top != 0) crc = crc xor 0x1021
        }
        return crc and 0xFFFF
    }

    private fun crc24A(bits: IntArray): Int {
        var crc = 0
        for (b in bits) {
            val top = ((crc ushr 23) and 1) xor b
            crc = (crc shl 1) and 0xFFFFFF
            if (top != 0) crc = crc xor 0x864CFB
        }
        return crc and 0xFFFFFF
    }

    private fun countErrors(a: IntArray, b: IntArray): Int = min(a.size, b.size).let { n -> (0 until n).count { a[it] != b[it] } }

    private fun gaussian(rng: Random): Double {
        val u1 = rng.nextDouble().coerceAtLeast(1e-12)
        val u2 = rng.nextDouble()
        return kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * Math.PI * u2)
    }

}
