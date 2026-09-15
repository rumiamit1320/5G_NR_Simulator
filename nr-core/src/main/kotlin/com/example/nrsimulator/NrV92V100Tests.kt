package com.example.nrsimulator

/** Regression gates for the additive V92-V100 primitives. */
object NrV92V100Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val report = NrResearchGradeV100.run()
        val checks = LinkedHashMap(report.stages)
        checks["V92-V100 aggregate"] = report.passed
        return Result(checks.values.all { it }, checks)
    }
}
