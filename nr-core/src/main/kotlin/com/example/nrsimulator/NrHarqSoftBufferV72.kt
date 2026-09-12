package com.example.nrsimulator

/**
 * V72 additive HARQ soft-buffer primitive.
 *
 * This is deliberately a transport-bit LLR accumulation boundary. It does not
 * alter V61/V64/V66 decoding. Each transmission contributes signed reliability
 * values after rate recovery; repeated observations of the same coded bit are
 * accumulated before a hard decision. The primitive is deterministic and can
 * be replaced by a full demapper/decoder LLR path later without changing the
 * HARQ process API.
 */
data class NrHarqSoftBufferV72(val llr: DoubleArray = doubleArrayOf()) {
    fun combine(observation: DoubleArray): NrHarqSoftBufferV72 {
        if (llr.isEmpty()) return NrHarqSoftBufferV72(observation.copyOf())
        val n = maxOf(llr.size, observation.size)
        val merged = DoubleArray(n) { i ->
            (if (i < llr.size) llr[i] else 0.0) +
                (if (i < observation.size) observation[i] else 0.0)
        }
        return NrHarqSoftBufferV72(merged)
    }

    fun hardDecision(): IntArray = IntArray(llr.size) { if (llr[it] >= 0.0) 1 else 0 }
    fun energy(): Double = llr.sumOf { it * it }
    fun meanAbs(): Double = if (llr.isEmpty()) 0.0 else llr.sumOf { kotlin.math.abs(it) } / llr.size
}

object NrHarqSoftBufferV72Factory {
    /** Convert an existing hard-bit observation into deterministic signed LLRs. */
    fun fromBits(bits: IntArray, reliability: Double): NrHarqSoftBufferV72 {
        val r = reliability.coerceAtLeast(0.0)
        return NrHarqSoftBufferV72(DoubleArray(bits.size) { i ->
            if (bits[i] == 0) -r else r
        })
    }
}
