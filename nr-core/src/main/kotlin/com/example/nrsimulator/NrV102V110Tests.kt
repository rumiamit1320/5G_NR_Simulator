package com.example.nrsimulator

/** Additive regression gates for V102-V110. */
object NrV102V110Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val r = NrV102V110Additive.report()
        return Result(r.passed, r.checks)
    }
}
