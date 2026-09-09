package com.example.nrsimulator

fun main() {
    val checks = linkedMapOf<String, Boolean>()
    fun check(name: String, ok: Boolean) { checks[name] = ok; println("$name: ${if (ok) "PASS" else "FAIL"}") }

    runCatching { check("V31", NrV31ConformanceTests.run().pass) }.onFailure { check("V31", false) }
    runCatching { check("V32-V45", NrV32V45Tests.run().all { it.contains("PASS") }) }.onFailure { check("V32-V45", false) }
    runCatching { check("V46-V60", NrV46V60Tests.run().pass) }.onFailure { check("V46-V60", false) }

    val all = checks.values.all { it }
    println("CORE_REGRESSION=${if (all) "PASS" else "FAIL"}")
    if (!all) error("Core regression failed")
}
