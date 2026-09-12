package com.example.nrsimulator

/** V91 additive physical-resource mapping primitives for downlink/uplink data. */
object NrPdschPuschV91 {
    enum class Channel { PDSCH, PUSCH }
    data class Resource(val subcarrier: Int, val symbol: Int, val layer: Int, val value: NrPhyMappingV89.Complex)
    data class Grid(val subcarriers: Int, val symbols: Int, val layers: Int, val resources: List<Resource>)

    fun mapData(
        channel: Channel,
        symbols: Array<NrPhyMappingV89.Complex>,
        subcarriers: Int,
        ofdmSymbols: Int,
        layers: Int = 1,
        startSubcarrier: Int = 0,
        startSymbol: Int = 0
    ): Grid {
        require(subcarriers > 0 && ofdmSymbols > 0 && layers in 1..8)
        require(startSubcarrier in 0 until subcarriers && startSymbol in 0 until ofdmSymbols)
        val mapped = ArrayList<Resource>()
        symbols.forEachIndexed { i, value ->
            val layer = i % layers
            val linear = i / layers
            val l = startSymbol + (linear / subcarriers)
            val k = startSubcarrier + (linear % subcarriers)
            if (l < ofdmSymbols && k < subcarriers) mapped += Resource(k, l, layer, value)
        }
        return Grid(subcarriers, ofdmSymbols, layers, mapped)
    }

    fun reserveDmrs(grid: Grid, dmrsSymbols: IntArray): Grid {
        val dmrsSet = dmrsSymbols.toSet()
        val kept = grid.resources.filter { it.symbol !in dmrsSet }
        return grid.copy(resources = kept)
    }

    fun occupancy(grid: Grid): Double {
        val total = grid.subcarriers * grid.symbols * grid.layers
        return if (total == 0) 0.0 else grid.resources.size.toDouble() / total
    }
}
