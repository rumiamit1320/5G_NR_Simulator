package com.example.nrsimulator

object NrCanonicalMultiSymbolV124Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)
    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        val p = NrCanonicalMultiSymbolV124.plan(
            NrCanonicalMultiSymbolV124.Config(prbCount = 4, startSymbol = 1, symbolCount = 12, layers = 1)
        )
        c["12-symbol allocation"] = p.occupiedSymbols.contentEquals((1..12).toList().toIntArray())
        c["all RE count"] = p.resourceElementsPerLayer == 4 * 12 * 12
        c["DMRS exists"] = p.dmrsElementsPerLayer > 0
        c["data excludes DMRS"] = p.data.size + p.dmrs.size == p.allocation.size && p.data.none { it in p.dmrs.toHashSet() }
        val m = NrCanonicalMultiSymbolV124.plan(
            NrCanonicalMultiSymbolV124.Config(prbCount = 4, startSymbol = 2, symbolCount = 10, layers = 2, direction = NrCanonicalMultiSymbolV124.Direction.UPLINK)
        )
        c["two-layer plan"] = m.allocation.size == 2 * 4 * 10 * 12 && m.dataElementsPerLayer > 0
        c["UL direction retained"] = m.direction == NrCanonicalMultiSymbolV124.Direction.UPLINK
        c["no duplicate RE"] = m.allocation.toSet().size == m.allocation.size
        return Result(c.values.all { it }, c)
    }
}
