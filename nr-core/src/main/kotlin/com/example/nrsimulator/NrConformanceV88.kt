package com.example.nrsimulator

/** V88 additive conformance gates; deliberately distinguishes structural checks from full certification. */
object NrConformanceV88 {
    data class Check(val name: String, val pass: Boolean, val detail: String)
    data class Report(val checks: List<Check>) { val pass: Boolean get() = checks.all { it.pass } }

    fun validateLdpcTable(bg: NrLdpcV85.BaseGraph, table: NrLdpcV85.Table): Report {
        val checks = mutableListOf<Check>()
        val g = NrLdpcV85.expectedGeometry(bg)
        checks += Check("geometry", table.rows == g.first && table.columns == g.second, "${table.rows}x${table.columns}")
        checks += Check("edge count", table.nonZeroCount() == NrLdpcV85.expectedEdges(bg), "${table.nonZeroCount()} edges")
        checks += Check("unique edges", table.edges.map { it.row to it.column }.toSet().size == table.edges.size, "duplicate-free")
        checks += Check("eight shift designs", table.edges.all { it.shiftsBySet.size == 8 }, "8 designs per edge")
        return Report(checks)
    }

    fun validateCodeword(bits: IntArray, h: Array<IntArray>): Report {
        val syndrome = NrLdpcCodecV86.syndromeWeight(bits, h)
        return Report(listOf(Check("zero syndrome", syndrome == 0, "syndrome weight=$syndrome")))
    }

    /** Reference vectors can be injected without changing the production architecture. */
    fun compareReference(actual: IntArray, expected: IntArray): Check {
        return Check("reference vector", actual.contentEquals(expected), "length=${actual.size}")
    }
}
