package com.example.nrsimulator

/** V87 additive TB-to-rate-matched coding chain. */
object NrCodingChainV87 {
    data class Result(
        val baseGraph: NrLdpcV82.BaseGraph,
        val liftingSize: Int,
        val liftingSet: Int,
        val transportWithCrc: IntArray,
        val codeBlocks: List<NrTransportV83.SegmentedCodeBlock>,
        val codewords: List<IntArray>,
        val rateMatched: List<IntArray>
    )

    fun encode(
        transportBlock: IntArray,
        targetCodeRate: Double,
        table: NrLdpcV85.Table,
        outputBitsPerCodeBlock: Int,
        rv: Int = 0
    ): Result {
        require(transportBlock.isNotEmpty())
        val bg = NrLdpcV82.selectBaseGraph(transportBlock.size, targetCodeRate)
        require((bg == NrLdpcV82.BaseGraph.BG1 && table.rows == 46) ||
                (bg == NrLdpcV82.BaseGraph.BG2 && table.rows == 42))
        val seg = NrTransportV83.segment(transportBlock, bg)
        val z = seg.liftingSize
        val ils = NrLdpcV82.liftingSet(z)
        val codewords = seg.codeBlocks.map { cb ->
            val input = IntArray(22 * z) { i -> if (i < cb.payload.size) cb.payload[i] else 0 }
            NrLdpcCodecV86.encode(input, table, z, ils).bits
        }
        val rm = codewords.map { cw ->
            NrRateMatchingV84.rateMatch(cw, NrRateMatchingV84.Config(bg, z, rv, outputBitsPerCodeBlock.coerceAtMost(cw.size)))
        }
        return Result(bg, z, ils, NrTransportV83.appendCrc(transportBlock, NrTransportV83.crcTypeForTransportBlock(transportBlock.size)), seg.codeBlocks, codewords, rm)
    }
}
