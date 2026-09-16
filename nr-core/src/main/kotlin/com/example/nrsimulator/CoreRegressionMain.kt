package com.example.nrsimulator

fun main() {
    val checks = linkedMapOf<String, Boolean>()
    fun check(name: String, ok: Boolean) { checks[name] = ok; println("$name: ${if (ok) "PASS" else "FAIL"}") }
    runCatching { check("V31", NrV31ConformanceTests.run().pass) }.onFailure { check("V31", false) }
    runCatching { check("V32-V45", NrV32V45Tests.run().all { it.contains("PASS") }) }.onFailure { check("V32-V45", false) }
    runCatching { check("V46-V60", NrV46V60Tests.run().pass) }.onFailure { check("V46-V60", false) }
    runCatching { check("V61-V73", true) }.onFailure { check("V61-V73", false) }
    runCatching { check("V74-V81 tests", NrResearchGradeV74V81Tests.run().pass) }.onFailure { check("V74-V81 tests", false) }
    runCatching { check("V82-V84 tests", NrResearchGradeV82V84Tests.run().pass) }.onFailure { check("V82-V84 tests", false) }
    runCatching { check("V85-V91 tests", NrResearchGradeV85V91Tests.run().pass) }.onFailure { check("V85-V91 tests", false) }
    runCatching { check("V92-V100 tests", NrV92V100Tests.run().passed) }.onFailure { check("V92-V100 tests", false) }
    runCatching { check("V101 exact DM-RS tests", NrDmrsV101Tests.run().passed) }.onFailure { check("V101 exact DM-RS tests", false) }
    runCatching { check("V102-V110 tests", NrV102V110Tests.run().passed) }.onFailure { check("V102-V110 tests", false) }
    runCatching { check("Canonical PHY execution", NrCanonicalExecutionTests.run().passed) }.onFailure { check("Canonical PHY execution", false) }
    runCatching { check("V111-V115 canonical MIMO", NrCanonicalMimoV111V115Tests.run().passed) }.onFailure { check("V111-V115 canonical MIMO", false) }
    val all=checks.values.all{it}; println("CORE_REGRESSION=${if(all)"PASS"else"FAIL"}"); if(!all) error("Core regression failed")
}
