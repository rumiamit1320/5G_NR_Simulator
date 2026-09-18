package com.example.nrsimulator

/** V124 multi-symbol PDSCH/PUSCH resource planning over the canonical V123 grid. */
object NrCanonicalMultiSymbolV124 {
    enum class Direction { DOWNLINK, UPLINK }
    data class Config(
        val slot: Int = 0,
        val prbStart: Int = 0,
        val prbCount: Int = 8,
        val startSymbol: Int = 1,
        val symbolCount: Int = 12,
        val layers: Int = 1,
        val direction: Direction = Direction.DOWNLINK,
        val dmrsPosition: Int = 2,
        val dmrsAdditionalPosition: NrDmrsV101.AdditionalPosition = NrDmrsV101.AdditionalPosition.POS0
    )
    data class Re(val slot: Int, val symbol: Int, val prb: Int, val subcarrier: Int, val layer: Int)
    data class Plan(
        val direction: Direction,
        val allocation: List<Re>,
        val dmrs: List<Re>,
        val data: List<Re>,
        val occupiedSymbols: IntArray,
        val resourceElementsPerLayer: Int,
        val dataElementsPerLayer: Int,
        val dmrsElementsPerLayer: Int
    )

    fun plan(config: Config): Plan {
        require(config.prbCount > 0 && config.layers in 1..2)
        require(config.startSymbol in 0..13 && config.symbolCount > 0 && config.startSymbol + config.symbolCount <= 14)
        require(config.dmrsPosition in 0..13)
        require(config.dmrsPosition in config.startSymbol until config.startSymbol + config.symbolCount)

        val grid = NrCanonicalResourceGridV123(
            prbCount = config.prbStart + config.prbCount,
            layers = config.layers,
            slotIndex = config.slot
        )
        val allocation = ArrayList<Re>()
        for (layer in 0 until config.layers)
            for (s in config.startSymbol until config.startSymbol + config.symbolCount)
                for (p in config.prbStart until config.prbStart + config.prbCount)
                    for (k in 0..11) {
                        val key = NrCanonicalResourceGridV123.Key(config.slot, s, p, k, layer)
                        require(grid.reserve(key)) { "unexpected internal collision" }
                        allocation += Re(config.slot, s, p, k, layer)
                    }

        val dmrs = NrDmrsV101.generate(
            NrDmrsV101.Config(
                mappingType = NrDmrsV101.MappingType.A,
                configurationType = NrDmrsV101.ConfigurationType.TYPE1,
                maxLength = NrDmrsV101.MaxLength.LEN1,
                additionalPosition = config.dmrsAdditionalPosition,
                allocationStartSymbol = config.startSymbol,
                allocationSymbols = config.symbolCount,
                slot = config.slot,
                nId = 231,
                nSCID = 0,
                symbolsPerSlot = 14,
                startSubcarrier = config.prbStart * 12,
                resourceBlocks = config.prbCount,
                ports = IntArray(config.layers) { 1000 + 2 * it }
            )
        ).resources.map { r -> Re(config.slot, r.symbol, r.subcarrier / 12, r.subcarrier % 12, config.layers.let { _ -> (r.port - 1000) / 2 }) }

        val dmrsSet = dmrs.toHashSet()
        val data = allocation.filterNot { it in dmrsSet }
        val symbols = allocation.map { it.symbol }.distinct().sorted().toIntArray()
        return Plan(config.direction, allocation, dmrs, data, symbols,
            allocation.size / config.layers, data.size / config.layers, dmrs.size / config.layers)
    }
}
