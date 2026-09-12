package com.example.nrsimulator

/**
 * V74 additive NR QC-LDPC table boundary.
 *
 * The codec remains generic: this object owns the standardized-table lookup
 * boundary so existing V1-V73 codecs and APIs are untouched. Until the full
 * 3GPP table set is imported, only explicitly registered matrices are exposed;
 * no fabricated BG1/BG2 entries are returned.
 */
object NrLdpcNrTablesV74 {
    enum class BaseGraph { BG1, BG2 }

    data class Spec(
        val baseGraph: BaseGraph,
        val rows: Int,
        val columns: Int,
        val liftingSize: Int,
        val shifts: Array<IntArray>
    ) {
        init {
            require(rows > 0 && columns > rows && liftingSize > 0)
            require(shifts.size == rows && shifts.all { it.size == columns })
        }

        fun matrix(): NrQcLdpcMatrixV74 =
            NrQcLdpcMatrixV74(rows, columns, liftingSize, shifts.map { it.copyOf() }.toTypedArray())
    }

    private val registry = LinkedHashMap<String, Spec>()

    fun register(spec: Spec) {
        registry[key(spec.baseGraph, spec.liftingSize)] = spec
    }

    fun lookup(baseGraph: BaseGraph, liftingSize: Int): NrQcLdpcMatrixV74? =
        registry[key(baseGraph, liftingSize)]?.matrix()

    fun isRegistered(baseGraph: BaseGraph, liftingSize: Int): Boolean =
        registry.containsKey(key(baseGraph, liftingSize))

    fun registeredCount(): Int = registry.size

    private fun key(baseGraph: BaseGraph, liftingSize: Int) = "$baseGraph:$liftingSize"
}
