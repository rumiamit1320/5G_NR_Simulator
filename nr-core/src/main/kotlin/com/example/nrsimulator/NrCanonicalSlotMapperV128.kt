package com.example.nrsimulator

/** V128: materializes a complete multi-symbol scheduled slot on the canonical V123 grid. */
object NrCanonicalSlotMapperV128 {
    data class Config(
        val slot: Int = 0,
        val prbStart: Int = 0,
        val prbCount: Int = 8,
        val startSymbol: Int = 1,
        val symbolCount: Int = 12,
        val layers: Int = 1,
        val direction: NrCanonicalMultiSymbolV124.Direction = NrCanonicalMultiSymbolV124.Direction.DOWNLINK
    )
    data class Result(
        val plan: NrCanonicalMultiSymbolV124.Plan,
        val slotResourceElements: Int,
        val dataElements: Int,
        val dmrsElements: Int,
        val symbols: IntArray
    )
    fun run(config: Config = Config()): Result {
        val plan = NrCanonicalMultiSymbolV124.plan(NrCanonicalMultiSymbolV124.Config(
            slot=config.slot, prbStart=config.prbStart, prbCount=config.prbCount,
            startSymbol=config.startSymbol, symbolCount=config.symbolCount,
            layers=config.layers, direction=config.direction))
        return Result(plan, plan.allocation.size, plan.data.size, plan.dmrs.size, plan.occupiedSymbols)
    }
}
