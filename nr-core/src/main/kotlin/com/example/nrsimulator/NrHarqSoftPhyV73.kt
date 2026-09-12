package com.example.nrsimulator

import kotlin.math.*

/**
 * V73 additive soft-PHY stage.
 *
 * This is deliberately built on V61 rather than replacing it. V61 remains the
 * authoritative waveform/link execution path; V73 consumes its equalized QAM
 * symbols, produces modulation-aware max-log LLRs, performs RV-aware soft rate
 * recovery, combines HARQ observations, and runs a soft-input Polar SC decoder.
 *
 * This is a research-grade PHY improvement, not a claim of full 3GPP NR LDPC
 * compliance. The Polar/rate-matching primitives remain the project's V47
 * reference implementation and are explicitly isolated for later replacement
 * with normative NR channel coding.
 */
data class NrHarqSoftPhyConfigV73(
    val payloadBits: Int = 128,
    val snrDb: Double = 8.0,
    val modulationOrder: Int = 16,
    val maxTransmissions: Int = 4,
    val processId: Int = 0,
    val seed: Int = 7301
) {
    init {
        require(payloadBits in 32..200)
        require(maxTransmissions in 1..4)
        require(processId >= 0)
        require(modulationOrder in setOf(4, 16, 64, 256))
    }
}

data class NrHarqSoftPhyTransmissionV73(
    val processId: Int,
    val transmissionNumber: Int,
    val rv: Int,
    val crcPass: Boolean,
    val meanAbsLlr: Double,
    val combinedMeanAbsLlr: Double
)

data class NrHarqSoftPhyResultV73(
    val config: NrHarqSoftPhyConfigV73,
    val transmissions: List<NrHarqSoftPhyTransmissionV73>,
    val finalCrcPass: Boolean,
    val finalPayloadBits: IntArray,
    val combinedLlrs: DoubleArray,
    val retransmissionCount: Int
)

object NrHarqSoftPhyV73 {
    private fun levels(m: Int): DoubleArray {
        if (m == 4) return doubleArrayOf(-1.0 / sqrt(2.0), 1.0 / sqrt(2.0))
        val l = sqrt(m.toDouble()).roundToInt()
        val scale = sqrt((2.0 / 3.0) * (m - 1))
        return DoubleArray(l) { i -> (2.0 * i - l + 1.0) / scale }
    }

    /** Max-log LLR, positive means bit 0 is more likely. */
    fun qamLlrs(symbols: Array<Complex>, modulationOrder: Int, noiseVariance: Double): DoubleArray {
        require(modulationOrder in setOf(4, 16, 64, 256))
        require(noiseVariance.isFinite() && noiseVariance > 0.0)
        val bps = log2(modulationOrder.toDouble()).roundToInt()
        val constellation = Array(modulationOrder) { index ->
            if (modulationOrder == 4) {
                when (index) {
                    0 -> Complex(1.0, 1.0) * (1.0 / sqrt(2.0))
                    1 -> Complex(-1.0, 1.0) * (1.0 / sqrt(2.0))
                    2 -> Complex(1.0, -1.0) * (1.0 / sqrt(2.0))
                    else -> Complex(-1.0, -1.0) * (1.0 / sqrt(2.0))
                }
            } else {
                val l = sqrt(modulationOrder.toDouble()).roundToInt()
                val lv = levels(modulationOrder)
                Complex(lv[index % l], lv[index / l])
            }
        }
        val out = DoubleArray(symbols.size * bps)
        for (s in symbols.indices) {
            for (bit in 0 until bps) {
                var d0 = Double.POSITIVE_INFINITY
                var d1 = Double.POSITIVE_INFINITY
                for (index in constellation.indices) {
                    val d = (symbols[s] - constellation[index]).abs2()
                    val b = (index ushr (bps - 1 - bit)) and 1
                    if (b == 0) d0 = min(d0, d) else d1 = min(d1, d)
                }
                out[s * bps + bit] = ((d1 - d0) / noiseVariance).coerceIn(-80.0, 80.0)
            }
        }
        return out
    }

    /** Soft counterpart of V47.rateRecover: repeated observations accumulate. */
    fun rateRecoverSoft(llr: DoubleArray, n: Int, rv: Int): DoubleArray {
        require(n > 0)
        val out = DoubleArray(n)
        if (llr.isEmpty()) return out
        val off = ((rv and 3) * n) / 4
        for (i in llr.indices) out[(off + i) % n] += llr[i]
        return out
    }

    private fun f(a: Double, b: Double): Double =
        sign(a) * sign(b) * min(abs(a), abs(b))

    private fun g(a: Double, b: Double, u: Int): Double =
        b + (if (u == 0) a else -a)

    private fun sign(x: Double): Double = when {
        x > 0.0 -> 1.0
        x < 0.0 -> -1.0
        else -> 0.0
    }

    /** Min-sum successive-cancellation decoder for the project's V47 code construction. */
    fun polarSoftDecode(llr: DoubleArray, k: Int): IntArray {
        require(llr.size > 0 && (llr.size and (llr.size - 1)) == 0)
        require(k in 1..llr.size)
        val frozen = BooleanArray(llr.size) { true }
        NrPolarV47.reliability(llr.size).take(k).forEach { frozen[it] = false }
        val u = decodeNode(llr, frozen)
        val positions = NrPolarV47.reliability(llr.size).take(k).sorted()
        return IntArray(k) { u[positions[it]] }
    }

    private fun decodeNode(alpha: DoubleArray, frozen: BooleanArray): IntArray {
        if (alpha.size == 1) return intArrayOf(if (frozen[0]) 0 else if (alpha[0] >= 0.0) 0 else 1)
        val half = alpha.size / 2
        val leftLlr = DoubleArray(half) { i -> f(alpha[i], alpha[i + half]) }
        val leftFrozen = frozen.copyOfRange(0, half)
        val left = decodeNode(leftLlr, leftFrozen)
        val rightLlr = DoubleArray(half) { i -> g(alpha[i], alpha[i + half], left[i]) }
        val rightFrozen = frozen.copyOfRange(half, frozen.size)
        val right = decodeNode(rightLlr, rightFrozen)
        val out = IntArray(alpha.size)
        for (i in 0 until half) {
            out[i] = left[i] xor right[i]
            out[i + half] = right[i]
        }
        return out
    }

    private fun crc24c(x: IntArray): IntArray {
        var c = 0
        val p = 0x1864CFB
        for (b in x) {
            val t = ((c ushr 23) and 1) xor (b and 1)
            c = (c shl 1) and 0xFFFFFF
            if (t != 0) c = c xor p
        }
        return IntArray(24) { i -> (c ushr (23 - i)) and 1 }
    }

    private fun crcOk(x: IntArray): Boolean {
        if (x.size < 24) return false
        val n = x.size - 24
        return crc24c(x.copyOf(n)).contentEquals(x.copyOfRange(n, x.size))
    }

    private fun scramble(x: IntArray, seed: Int): IntArray = IntArray(x.size) { i ->
        var z = seed xor (i * 0x9E3779B9.toInt())
        z = z xor (z shl 13)
        z = z xor (z ushr 17)
        z = z xor (z shl 5)
        x[i] xor (z and 1)
    }

    fun run(config: NrHarqSoftPhyConfigV73 = NrHarqSoftPhyConfigV73()): NrHarqSoftPhyResultV73 {
        var combined = DoubleArray(0)
        val transmissions = ArrayList<NrHarqSoftPhyTransmissionV73>()
        var finalPayload = IntArray(0)
        var finalCrc = false

        for (number in 1..config.maxTransmissions) {
            val rv = intArrayOf(0, 2, 3, 1)[number - 1]
            val link = NrIntegratedLinkV61.run(
                NrIntegratedLinkConfigV61(
                    payloadBits = config.payloadBits,
                    snrDb = config.snrDb,
                    modulationOrder = config.modulationOrder,
                    rv = rv,
                    seed = config.seed
                )
            )
            val k = config.payloadBits + 24
            val n = generateSequence(32) { it * 2 }.first { it >= k }
            val symbolPower = levels(config.modulationOrder).let { lv -> lv.map { it * it }.average() }
            val noiseVariance = symbolPower / 10.0.pow(config.snrDb / 10.0)
            val symbols = link.rxConstellationFull
            val rawLlr = qamLlrs(symbols, config.modulationOrder, noiseVariance)
                .copyOf(link.transmittedBits)
            val recovered = rateRecoverSoft(rawLlr, n, rv)
            if (combined.isEmpty()) combined = recovered else {
                require(combined.size == recovered.size)
                for (i in combined.indices) combined[i] += recovered[i]
            }

            val decoded = polarSoftDecode(combined, k)
            val descrambled = scramble(decoded, config.seed xor 0x61)
            val tb = if (descrambled.size >= k) descrambled.copyOf(k) else IntArray(0)
            finalCrc = tb.size == k && crcOk(tb)
            finalPayload = if (tb.size == k) tb.copyOf(config.payloadBits) else IntArray(0)
            val mean = if (recovered.isEmpty()) 0.0 else recovered.map { abs(it) }.average()
            val combinedMean = if (combined.isEmpty()) 0.0 else combined.map { abs(it) }.average()
            transmissions += NrHarqSoftPhyTransmissionV73(
                processId = config.processId,
                transmissionNumber = number,
                rv = rv,
                crcPass = finalCrc,
                meanAbsLlr = mean,
                combinedMeanAbsLlr = combinedMean
            )
            if (finalCrc) break
        }

        return NrHarqSoftPhyResultV73(
            config = config,
            transmissions = transmissions,
            finalCrcPass = finalCrc,
            finalPayloadBits = finalPayload,
            combinedLlrs = combined,
            retransmissionCount = (transmissions.size - 1).coerceAtLeast(0)
        )
    }
}
