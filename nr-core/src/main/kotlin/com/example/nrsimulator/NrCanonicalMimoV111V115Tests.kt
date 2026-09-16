package com.example.nrsimulator

object NrCanonicalMimoV111V115Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val r = NrCanonicalMimoV111V115.report()
        return Result(r.passed, r.checks)
    }
}
