package com.example.nrsimulator

/** V130: LS DM-RS estimation with deterministic time/frequency nearest-neighbour interpolation. */
object NrCanonicalDmrsEstimatorV130 {
    data class Estimate(val re: NrCanonicalPhysicalMapperV129.Symbol, val pilot: Boolean)
    data class Result(
        val estimates: Map<NrCanonicalMultiSymbolV124.Re, Estimate>,
        val pilotCount: Int,
        val estimatedCount: Int,
        val mse: Double
    )
    fun estimate(
        plan: NrCanonicalMultiSymbolV124.Plan,
        receivedDmrs: Map<NrCanonicalMultiSymbolV124.Re, NrCanonicalPhysicalMapperV129.Symbol>,
        noiseFloor: Double = 0.0
    ): Result {
        require(noiseFloor >= 0.0)
        val pilots = receivedDmrs.filterKeys { it in plan.dmrs }
        require(pilots.isNotEmpty())
        val out = LinkedHashMap<NrCanonicalMultiSymbolV124.Re, Estimate>()
        for (re in plan.allocation) {
            val nearest = pilots.minByOrNull { p ->
                val dk = kotlin.math.abs(p.key.subcarrier - re.subcarrier)
                val ds = kotlin.math.abs(p.key.symbol - re.symbol)
                dk + ds * 12
            }!!
            out[re] = Estimate(nearest.value, re in pilots)
        }
        var mse = 0.0
        pilots.values.forEach { p -> mse += p.re * p.re + p.im * p.im }
        mse /= pilots.size.toDouble()
        return Result(out, pilots.size, out.size, mse + noiseFloor)
    }
}
