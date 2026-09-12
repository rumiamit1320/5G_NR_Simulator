package com.example.nrsimulator

/**
 * V85 additive NR LDPC base-graph table layer.
 *
 * The existing V82 geometry remains unchanged. V85 introduces the exact-data
 * contract needed by TS 38.212: sparse BG edges plus eight shift designs.
 * No V1-V84 API is changed.
 */
object NrLdpcV85 {
    enum class BaseGraph { BG1, BG2 }

    data class Edge(val row: Int, val column: Int, val shiftsBySet: IntArray) {
        init {
            require(shiftsBySet.size == 8) { "NR LDPC requires eight lifting-set designs" }
            require(row >= 0 && column >= 0)
        }
    }

    data class Table(val rows: Int, val columns: Int, val edges: List<Edge>) {
        fun nonZeroCount(): Int = edges.size
        fun validate(): Boolean {
            if (edges.any { it.row >= rows || it.column >= columns }) return false
            if (edges.map { it.row to it.column }.toSet().size != edges.size) return false
            return edges.all { e -> e.shiftsBySet.all { it >= 0 } }
        }
    }

    fun expectedEdges(bg: BaseGraph): Int = when (bg) {
        BaseGraph.BG1 -> 316
        BaseGraph.BG2 -> 197
    }

    fun expectedGeometry(bg: BaseGraph): Pair<Int, Int> = when (bg) {
        BaseGraph.BG1 -> 46 to 68
        BaseGraph.BG2 -> 42 to 52
    }

    /** Build the lifted binary parity-check matrix from a validated table. */
    fun lift(table: Table, z: Int, liftingSet: Int): Array<IntArray> {
        require(table.validate()) { "Invalid V85 LDPC table" }
        require(liftingSet in 0..7)
        require(z in NrLdpcV82.allowedLiftingSizes())
        val h = Array(table.rows * z) { IntArray(table.columns * z) }
        for (e in table.edges) {
            val shift = e.shiftsBySet[liftingSet] % z
            for (i in 0 until z) {
                h[e.row * z + i][e.column * z + ((i + shift) % z)] = 1
            }
        }
        return h
    }

    /** Check the mandatory 316/197 edge-count contract before encoding. */
    fun validateStandardShape(bg: BaseGraph, table: Table): Boolean {
        val g = expectedGeometry(bg)
        return table.rows == g.first && table.columns == g.second &&
            table.nonZeroCount() == expectedEdges(bg) && table.validate()
    }
}
