package com.example.nrsimulator.web

import com.example.nrsimulator.NrBeamV28
import com.example.nrsimulator.NrSrsV21
import com.example.nrsimulator.NrSrsV21Config

/** Additive web-only bridge: preserves the existing V21/V28 implementations. */
object LabApiV64Mimo {
    fun v21(snr: Double, prbs: Int, tx: Int, rx: Int, rank: String): String {
        val t = tx.coerceIn(1, 4)
        val r = rx.coerceIn(1, 4)
        val cap = when (rank.lowercase()) { "1" -> 1; "2" -> 2; "3" -> 3; "4" -> 4; else -> 4 }
        val effectiveRank = minOf(t, r, cap).coerceAtLeast(1)
        return NrSrsV21().run(snr, NrSrsV21Config(ports = effectiveRank, rbCount = prbs.coerceIn(1, 275))).toString() +
            ", tx=$t, rx=$r, requestedRank=${rank.lowercase()}, effectiveRank=$effectiveRank"
    }

    fun v28(azimuth: Double, tx: Int, rx: Int, rank: String, beams: Int, sweep: Boolean): String {
        val t = tx.coerceIn(1, 4)
        val r = rx.coerceIn(1, 4)
        val cap = when (rank.lowercase()) { "1" -> 1; "2" -> 2; "3" -> 3; "4" -> 4; else -> 4 }
        val rows = minOf(t, cap).coerceAtLeast(1)
        val cols = minOf(r, cap).coerceAtLeast(1)
        val beamCount = if (sweep) beams.coerceIn(2, 128) else 1
        val result = NrBeamV28().sweep(azimuth, rows, cols, beamCount)
        val effectiveRank = if (rank.lowercase() == "adaptive") result.rank else minOf(result.rank, cap)
        return "$result, tx=$t, rx=$r, requestedRank=${rank.lowercase()}, effectiveRank=$effectiveRank, beamSweep=$sweep, effectiveRows=$rows, effectiveCols=$cols"
    }
}
