package com.example.nrsimulator

object NrHarqSoftBufferV72Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val checks = mutableListOf<String>()
        fun check(name: String, ok: Boolean) { checks += "$name: ${if (ok) "PASS" else "FAIL"}" }

        val a = NrHarqSoftBufferV72Factory.fromBits(intArrayOf(1, 0, 1, 0), 0.75)
        val b = NrHarqSoftBufferV72Factory.fromBits(intArrayOf(1, 1, 0, 0), 0.50)
        val c = a.combine(b.llr)
        check("size preserved", c.llr.size == 4)
        check("LLR accumulation", c.llr.contentEquals(doubleArrayOf(1.25, -0.25, 0.25, -1.25)))
        check("hard decision", c.hardDecision().contentEquals(intArrayOf(1, 0, 1, 0)))
        check("energy finite", c.energy().isFinite())
        check("mean magnitude positive", c.meanAbs() > 0.0)

        val empty = NrHarqSoftBufferV72().combine(doubleArrayOf(1.0, -2.0))
        check("empty initialization", empty.llr.contentEquals(doubleArrayOf(1.0, -2.0)))
        return Result(checks.all { it.endsWith("PASS") }, checks)
    }
}
