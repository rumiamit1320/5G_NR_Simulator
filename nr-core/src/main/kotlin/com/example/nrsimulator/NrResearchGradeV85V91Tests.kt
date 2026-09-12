package com.example.nrsimulator

/** Deterministic regression gate for the additive V85-V91 layers. */
object NrResearchGradeV85V91Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val out = ArrayList<String>()
        fun ck(name: String, ok: Boolean) { out += "$name: ${if (ok) "PASS" else "FAIL"}" }

        val tiny = NrLdpcV85.Table(
            2, 4,
            listOf(
                NrLdpcV85.Edge(0, 0, intArrayOf(0, 0, 0, 0, 0, 0, 0, 0)),
                NrLdpcV85.Edge(0, 1, intArrayOf(1, 1, 1, 1, 1, 1, 1, 1)),
                NrLdpcV85.Edge(1, 1, intArrayOf(2, 2, 2, 2, 2, 2, 2, 2)),
                NrLdpcV85.Edge(1, 2, intArrayOf(3, 3, 3, 3, 3, 3, 3, 3))
            )
        )
        ck("V85 table contract", tiny.validate() && tiny.edges.all { it.shiftsBySet.size == 8 })
        val h = NrLdpcV85.lift(tiny, 2, 0)
        ck("V85 lifting", h.size == 4 && h[0].size == 8)

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
