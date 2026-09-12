package com.example.nrsimulator

/** Deterministic regression gate for the additive V82-V84 standards-oriented layer. */
object NrResearchGradeV82V84Tests {
    data class Result(val pass: Boolean, val checks: List<String>)

    fun run(): Result {
        val out = ArrayList<String>()
        fun ck(name: String, ok: Boolean) { out += "$name: ${if (ok) "PASS" else "FAIL"}" }

        val bg1 = NrLdpcV82.geometry(NrLdpcV82.BaseGraph.BG1)
        val bg2 = NrLdpcV82.geometry(NrLdpcV82.BaseGraph.BG2)
        ck("V82 BG1 geometry", bg1.rows == 46 && bg1.columns == 68 && bg1.informationColumns == 22 && bg1.codeColumns == 66)
        ck("V82 BG2 geometry", bg2.rows == 42 && bg2.columns == 52 && bg2.informationColumns == 10 && bg2.codeColumns == 50)
        ck("V82 lifting sets", NrLdpcV82.allowedLiftingSizes().size == 51 && NrLdpcV82.liftingSet(384) == 1)
        ck("V82 BG selection", NrLdpcV82.selectBaseGraph(200, 0.5) == NrLdpcV82.BaseGraph.BG2 && NrLdpcV82.selectBaseGraph(4000, 0.8) == NrLdpcV82.BaseGraph.BG1)
        ck("V82 Z selection", NrLdpcV82.selectLiftingSize(NrLdpcV82.BaseGraph.BG1, 22 * 32) == 32)

        val bits = IntArray(64) { it and 1 }
        val seg = NrTransportV83.segment(bits, NrLdpcV82.BaseGraph.BG2)
        ck("V83 CRC16 selection", NrTransportV83.crcTypeForTransportBlock(64) == NrTransportV83.CrcType.CRC16)
        ck("V83 segmentation", seg.codeBlocks.size == 1 && seg.codeBlocks[0].k > 0 && seg.fillerBits >= 0)
        val crc = NrTransportV83.appendCrc(bits, NrTransportV83.CrcType.CRC16)
        ck("V83 CRC check", NrTransportV83.checkCrc(crc, NrTransportV83.CrcType.CRC16))

        val z = 32
        val bg = NrLdpcV82.BaseGraph.BG1
        val n = NrLdpcV82.encodedSize(bg, z)
        val cw = IntArray(n) { it and 1 }
        val rm = NrRateMatchingV84.rateMatch(cw, NrRateMatchingV84.Config(bg, z, 0, 200))
        ck("V84 rate matching", rm.size == 200)
        val soft = NrRateMatchingV84.rateRecover(DoubleArray(200) { if (rm[it] == 0) 4.0 else -4.0 }, NrRateMatchingV84.Config(bg, z, 2, 200))
        ck("V84 rate recovery", soft.size == n && soft.count { it != 0.0 } > 0)

        return Result(out.all { it.endsWith("PASS") }, out)
    }
}
