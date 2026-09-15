package com.example.nrsimulator

/** Additive structural/reference checks for V101 exact DM-RS generation. */
object NrDmrsV101Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val checks = linkedMapOf<String, Boolean>()
        val init = NrDmrsV101.cInit(slot = 7, symbol = 2, nId = 231, nSCID = 0)
        checks["cInit-known"] = init == 1834353102L

        val gold = NrDmrsV101.gold(init, 32)
        checks["gold-binary"] = gold.all { it == 0 || it == 1 } && gold.size == 32

        val seq = NrDmrsV101.sequence(7, 2, 231, 0, 12)
        checks["sequence-unit-power"] = seq.all { kotlin.math.abs(it.abs2() - 1.0) < 1e-12 }

        val a = NrDmrsV101.pdsch(NrDmrsV101.Config(
            mappingType = NrDmrsV101.MappingType.A,
            configurationType = NrDmrsV101.ConfigurationType.TYPE1,
            additionalPosition = NrDmrsV101.AdditionalPosition.POS1,
            allocationStartSymbol = 0,
            allocationSymbols = 14,
            resourceBlocks = 1,
            ports = intArrayOf(1000, 1001)
        ))
        checks["pdsch-type1-symbols"] = a.dmrsSymbols.contentEquals(intArrayOf(2, 11))
        checks["pdsch-type1-re-count"] = a.resources.size == 24
        checks["pdsch-type1-port-separation"] = a.resources.map { it.port }.toSet() == setOf(1000, 1001)
        checks["pdsch-type1-subcarrier-range"] = a.resources.all { it.subcarrier in 0..11 }

        val b = NrDmrsV101.pusch(NrDmrsV101.Config(
            mappingType = NrDmrsV101.MappingType.B,
            configurationType = NrDmrsV101.ConfigurationType.TYPE2,
            additionalPosition = NrDmrsV101.AdditionalPosition.POS2,
            allocationStartSymbol = 0,
            allocationSymbols = 12,
            resourceBlocks = 2,
            ports = intArrayOf(1000, 1002, 1004)
        ))
        checks["pusch-type2-symbols"] = b.dmrsSymbols.isNotEmpty()
        checks["pusch-type2-re-count"] = b.resources.size == b.dmrsSymbols.size * 2 * 4 * 3
        checks["pusch-type2-subcarrier-range"] = b.resources.all { it.subcarrier in 0..23 }
        checks["cinit-changes-by-symbol"] = b.cInitBySymbol.values.distinct().size == b.dmrsSymbols.size

        return Result(checks.values.all { it }, checks)
    }
}
