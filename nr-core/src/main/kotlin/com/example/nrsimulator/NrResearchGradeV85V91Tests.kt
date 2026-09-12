package com.example.nrsimulator

/** Deterministic regression gate for the additive V85-V91 layers. */
object NrResearchGradeV85V91Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val out = ArrayList<String>()
        fun ck(name: String, ok: Boolean) { out += "$name: ${if (ok) "PASS" else "FAIL"}" }

        val bg1 = runCatching { NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG1) }.getOrNull()
        val bg2 = runCatching { NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG2) }.getOrNull()
        ck("V85 BG1 exact table", bg1 != null && NrLdpcV85.validateStandardShape(NrLdpcV85.BaseGraph.BG1, bg1))
        ck("V85 BG2 exact table", bg2 != null && NrLdpcV85.validateStandardShape(NrLdpcV85.BaseGraph.BG2, bg2))
        if (bg1 != null) {
            val h = NrLdpcV85.lift(bg1, 2, 0)
            ck("V85 BG1 lifting", h.size == 92 && h[0].size == 136 && h.sumOf { row -> row.count { it != 0 } } == 316 * 2)
        } else ck("V85 BG1 lifting", false)
        if (bg2 != null) {
            val h = NrLdpcV85.lift(bg2, 2, 0)
            ck("V85 BG2 lifting", h.size == 84 && h[0].size == 104 && h.sumOf { row -> row.count { it != 0 } } == 197 * 2)
        } else ck("V85 BG2 lifting", false)

        val mod = NrPhyMappingV89.modulate(IntArray(8) { it and 1 }, NrPhyMappingV89.Modulation.QPSK)
        ck("V89 QPSK", mod.size == 4 && mod.all { it.re.isFinite() && it.im.isFinite() })
        val layers = NrPhyMappingV89.mapLayers(mod, 2)
        ck("V89 layer mapping", layers.size == 2 && layers.sumOf { it.size } == mod.size)

        val dm = NrDmrsMimoV90.dmrs(16)
        val est = NrDmrsMimoV90.estimate(dm, dm)
        ck("V90 DMRS estimate", est.size == 16 && est.all { kotlin.math.abs(it.re - 1.0) < 1e-9 })
        val eq = NrDmrsMimoV90.equalize(dm, dm, 1e-3)
        ck("V90 equalizer", eq.symbols.size == 16 && eq.postEqSinrDb.isFinite())

        val grid = NrPdschPuschV91.mapData(NrPdschPuschV91.Channel.PDSCH, mod, 12, 14, 2)
        val dataOnly = NrPdschPuschV91.reserveDmrs(grid, intArrayOf(2, 11))
        ck("V91 resource mapping", grid.resources.isNotEmpty() && dataOnly.resources.size <= grid.resources.size)
        ck("V91 occupancy", NrPdschPuschV91.occupancy(grid) in 0.0..1.0)

        ck("V88 reference compare", NrConformanceV88.compareReference(intArrayOf(0, 1), intArrayOf(0, 1)).pass)
        return Result(out.all { it.endsWith("PASS") }, out)
    }
}
