package com.example.nrsimulator

object NrCanonicalV128V132Tests {
    data class Report(val passed: Boolean, val checks: List<String>)
    fun run(): Report {
        val checks = ArrayList<String>()
        fun ok(name: String, value: Boolean) { require(value) { name }; checks += name }
        val v128 = NrCanonicalSlotMapperV128.run()
        ok("V128 full slot allocation", v128.slotResourceElements == 8 * 12 * 12)
        ok("V128 data plus DMRS", v128.dataElements + v128.dmrsElements == v128.slotResourceElements)
        val v129 = NrCanonicalPhysicalMapperV129.map(v128.plan)
        ok("V129 maps all data", v129.mappedData == v128.dataElements)
        ok("V129 maps all DMRS", v129.mappedDmrs == v128.dmrsElements)
        val v130 = NrCanonicalDmrsEstimatorV130.estimate(v128.plan, v129.dmrsSymbols)
        ok("V130 pilots", v130.pilotCount == v128.dmrsElements)
        ok("V130 estimates allocation", v130.estimatedCount == v128.slotResourceElements)
        val v131 = NrCanonicalScheduledPhyV131.run()
        ok("V131 coded PHY", v131.phy.payloadBits == 256)
        val v132 = NrCanonicalScheduledMultiUeV132.run()
        ok("V132 grants", v132.grants.isNotEmpty())
        ok("V132 UE results", v132.ueResults.size == v132.grants.size)
        ok("V132 PRB bound", v132.scheduledPrbs <= 24)
        return Report(true, checks)
    }
}
