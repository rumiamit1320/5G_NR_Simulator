package com.example.nrsimulator

/** Additive NR LDPC rate matching / rate recovery boundary for TS 38.212. */
object NrRateMatchingV84 {
    data class Config(
        val baseGraph: NrLdpcV82.BaseGraph,
        val liftingSize: Int,
        val redundancyVersion: Int,
        val outputBits: Int
    ) {
        init {
            require(redundancyVersion in 0..3)
            require(outputBits > 0)
        }
    }

    private fun k0(bg: NrLdpcV82.BaseGraph, z: Int, rv: Int): Int = when (bg) {
        NrLdpcV82.BaseGraph.BG1 -> intArrayOf(0, 17 * z, 33 * z, 56 * z)[rv]
        NrLdpcV82.BaseGraph.BG2 -> intArrayOf(0, 13 * z, 25 * z, 43 * z)[rv]
    }

    /**
     * Circular-buffer bit selection. The LDPC encoder's first 2Z systematic
     * positions are punctured and are therefore skipped during selection.
     */
    fun rateMatch(codeword: IntArray, config: Config): IntArray {
        val n = NrLdpcV82.encodedSize(config.baseGraph, config.liftingSize)
        require(codeword.size == n) { "Expected encoded LDPC size N=$n, got ${codeword.size}" }
        val z = config.liftingSize
        val start = k0(config.baseGraph, z, config.redundancyVersion)
        val out = IntArray(config.outputBits)
        var selected = 0
        var k = 0
        val maxSteps = n * 2 + config.outputBits * 2
        var steps = 0
        while (selected < out.size) {
            require(steps++ < maxSteps) { "Rate matching could not select E bits" }
            val j = (start + k) % n
            k++
            if (j < 2 * z) continue
            out[selected++] = codeword[j]
        }
        return out
    }

    /**
     * Soft-input inverse of rate matching. Multiple observations combine in
     * the circular buffer by LLR addition; unobserved positions remain zero.
     */
    fun rateRecover(llr: DoubleArray, config: Config): DoubleArray {
        require(llr.size == config.outputBits)
        val n = NrLdpcV82.encodedSize(config.baseGraph, config.liftingSize)
        val z = config.liftingSize
        val start = k0(config.baseGraph, z, config.redundancyVersion)
        val recovered = DoubleArray(n)
        var selected = 0
        var k = 0
        val maxSteps = n * 2 + llr.size * 2
        var steps = 0
        while (selected < llr.size) {
            require(steps++ < maxSteps) { "Rate recovery could not place E soft bits" }
            val j = (start + k) % n
            k++
            if (j < 2 * z) continue
            recovered[j] += llr[selected++]
        }
        return recovered
    }
}
