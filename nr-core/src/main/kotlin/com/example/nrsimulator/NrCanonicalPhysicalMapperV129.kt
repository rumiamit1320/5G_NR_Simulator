package com.example.nrsimulator

/** V129: deterministic physical data/DM-RS symbol mapper over the V128 slot plan. */
object NrCanonicalPhysicalMapperV129 {
    data class Symbol(val re: Double, val im: Double)
    data class Result(
        val plan: NrCanonicalMultiSymbolV124.Plan,
        val dataSymbols: Map<NrCanonicalMultiSymbolV124.Re, Symbol>,
        val dmrsSymbols: Map<NrCanonicalMultiSymbolV124.Re, Symbol>,
        val mappedData: Int,
        val mappedDmrs: Int
    )
    fun map(
        plan: NrCanonicalMultiSymbolV124.Plan,
        seed: Int = 12901
    ): Result {
        val data = LinkedHashMap<NrCanonicalMultiSymbolV124.Re, Symbol>()
        val dmrs = LinkedHashMap<NrCanonicalMultiSymbolV124.Re, Symbol>()
        plan.data.forEachIndexed { i, re ->
            val b0 = ((i + seed) * 17 + 3) and 1
            val b1 = ((i + seed) * 31 + 7) and 1
            data[re] = Symbol(if (b0 == 0) 1.0 else -1.0, if (b1 == 0) 1.0 else -1.0)
        }
        plan.dmrs.forEachIndexed { i, re ->
            val s = if (((i + seed) and 1) == 0) 1.0 else -1.0
            dmrs[re] = Symbol(s, 0.0)
        }
        return Result(plan, data, dmrs, data.size, dmrs.size)
    }
}
