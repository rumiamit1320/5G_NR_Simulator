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

    val all = checks.values.all { it }
    println("CORE_REGRESSION=${if (all) "PASS" else "FAIL"}")
    if (!all) error("Core regression failed")
}
