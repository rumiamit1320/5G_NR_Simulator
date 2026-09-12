package com.example.nrsimulator

fun main() {
    val checks = linkedMapOf<String, Boolean>()
    fun check(name: String, ok: Boolean) { checks[name] = ok; println("$name: ${if (ok) "PASS" else "FAIL"}") }

    runCatching { check("V31", NrV31ConformanceTests.run().pass) }.onFailure { check("V31", false) }
    runCatching { check("V32-V45", NrV32V45Tests.run().all { it.contains("PASS") }) }.onFailure { check("V32-V45", false) }
    runCatching { check("V46-V60", NrV46V60Tests.run().pass) }.onFailure { check("V46-V60", false) }
    runCatching {
        val r = NrIntegratedLinkV61.run(NrIntegratedLinkConfigV61(payloadBits = 128, snrDb = 40.0, modulationOrder = 16, layers = 1, txAntennas = 1, rxAntennas = 1))
        check("V61", r.crcPass && r.decodedBits == r.payloadBits && r.bitErrors == 0 && r.ber == 0.0)
    }.onFailure { check("V61", false) }
    runCatching {
        val r = NrRadioEnvironmentV62.run(NrRadioEnvironmentConfigV62(ueCount = 8, cells = 3, prbs = 52, velocityKmh = 60.0, slotIndex = 10))
        check("V62", r.ueStates.size == 8 && r.ueStates.sumOf { it.allocatedPrbs } == 52 && r.fairness in 0.0..1.0)
        check("V62 tests", NrRadioEnvironmentV62Tests.run().pass)
    }.onFailure { check("V62", false); check("V62 tests", false) }
    runCatching {
        val r = NrIntegratedSystemV63.run(NrIntegratedSystemConfigV63(slots = 4, ueCount = 4, prbs = 24, velocityKmh = 30.0))
        check("V63", r.slotResults.size == 4 && r.ueStates.size == 4 && r.systemFairness in 0.0..1.0 && r.totalThroughputMbps.isFinite())
        check("V63 tests", NrIntegratedSystemV63Tests.run().pass)
    }.onFailure { check("V63", false); check("V63 tests", false) }
    runCatching {
        val r = NrClosedLoopV64.run(NrClosedLoopConfigV64(slots = 4, ueCount = 4, cells = 2, prbs = 24, velocityKmh = 60.0, payloadBitsPerUe = 64))
        check("V64", r.slotResults.size == 4 && r.ueStates.size == 4 && r.fairness in 0.0..1.0 && r.totalThroughputMbps.isFinite())
        check("V64 tests", NrClosedLoopV64Tests.run().pass)
    }.onFailure { check("V64", false); check("V64 tests", false) }
    runCatching {
        val r = NrNetworkSimulationV65.run(NrNetworkSimulationConfigV65(slots = 4, ueCount = 6, cells = 3, prbs = 24, velocityKmh = 60.0))
        check("V65", r.slotResults.size == 4 && r.cells.size == 3 && r.ueStates.size == 6 && r.totalThroughputMbps.isFinite())
        check("V65 tests", NrNetworkSimulationV65Tests.run().pass)
    }.onFailure { check("V65", false); check("V65 tests", false) }
    runCatching {
        val r = NrIntegratedNetworkV66.run(NrIntegratedNetworkConfigV66(slots = 3, ueCount = 4, cells = 2, prbs = 24, scsKHz = 30, seed = 6607))
        check("V66", r.slotResults.size == 3 && r.cells.size == 2 && r.ueStates.size == 4 && r.totalThroughputMbps.isFinite())
        check("V66 tests", NrIntegratedNetworkV66Tests.run().pass)
    }.onFailure { check("V66", false); check("V66 tests", false) }
    runCatching {
        val r = NrTimingV67.run(NrIntegratedNetworkConfigV66(slots = 3, ueCount = 2, cells = 2, prbs = 12, scsKHz = 30, seed = 6701))
        check("V67", r.slotResults.size == 3 && r.timing.numerology == 1 && r.timing.slotsPerFrame == 20)
        check("V67 tests", NrTimingV67Tests.run().pass)
    }.onFailure { check("V67", false); check("V67 tests", false) }
    runCatching {
        val timed = NrTimingV67.run(NrIntegratedNetworkConfigV66(slots = 8, ueCount = 4, cells = 2, prbs = 24, scsKHz = 30, seed = 6801))
        val r = NrHarqTimingV68.run(timed, NrHarqTimingConfigV68(processesPerUe = 8, downlinkAckDelaySlots = 4))
        check("V68", r.events.isNotEmpty() && r.ackCount + r.nackCount == r.events.size)
        check("V68 tests", NrHarqTimingV68Tests.run().pass)
    }.onFailure { check("V68", false); check("V68 tests", false) }
    runCatching {
        val timed = NrTimingV67.run(NrIntegratedNetworkConfigV66(slots = 8, ueCount = 4, cells = 2, prbs = 24, scsKHz = 30, seed = 6901))
        val r = NrHarqExecutionV69.run(timed)
        check("V69", r.transmissions.isNotEmpty() && r.ackCount + r.nackCount == r.transmissions.size)
        check("V69 tests", NrHarqExecutionV69Tests.run().pass)
    }.onFailure { check("V69", false); check("V69 tests", false) }
    runCatching {
        val r = NrHarqExecutionV70.run(NrIntegratedNetworkConfigV66(slots = 8, ueCount = 4, cells = 2, prbs = 24, scsKHz = 30, seed = 7001), NrHarqExecutionConfigV70(extraSlotsForRetransmissions = 16))
        check("V70", r.events.isNotEmpty() && r.ackCount + r.nackCount == r.events.size)
        check("V70 tests", NrHarqExecutionV70Tests.run().pass)
    }.onFailure { check("V70", false); check("V70 tests", false) }
    runCatching { check("V71 tests", NrHarqPhyIntegrationV71Tests.run().pass) }.onFailure { check("V71 tests", false) }
    runCatching { check("V72 tests", NrHarqSoftBufferV72Tests.run().pass) }.onFailure { check("V72 tests", false) }

    val all = checks.values.all { it }
    println("CORE_REGRESSION=${if (all) "PASS" else "FAIL"}")
    if (!all) error("Core regression failed")
}
