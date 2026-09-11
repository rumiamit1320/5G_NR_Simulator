package com.example.nrsimulator.web

import com.example.nrsimulator.NrBeamV28
import com.example.nrsimulator.NrSrsV21
import com.example.nrsimulator.NrSrsV21Config

/**
 * Additive V64 web adapter around the existing V21/V28 models.
 * It does not modify nr-core implementations.
 */
object MimoWebAdapterV64 {
    data class Result(
        val tx: Int,
        val rx: Int,
        val requestedRank: String,
        val effectiveRank: Int,
        val beamSweep: Boolean,
        val beamCount: Int,
        val srs: Any,
        val beam: Any
    )

    fun run(
        snrDb: Double,
        prbs: Int,
        azimuthDeg: Double,
        tx: Int,
        rx: Int,
        rank: String,
        beams: Int,
        beamSweep: Boolean
    ): Result {
        val t = tx.coerceIn(1, 4)
        val r = rx.coerceIn(1, 4)
        val requestedRank = rank.lowercase()
        val cap = when (requestedRank) {
            "1" -> 1
            "2" -> 2
            "3" -> 3
            "4" -> 4
            else -> Int.MAX_VALUE
        }
        val effectiveRank = minOf(t, r, cap).coerceAtLeast(1)
        val beamCount = if (beamSweep) beams.coerceIn(2, 128) else 1
        val srs = NrSrsV21().run(
            snrDb,
            NrSrsV21Config(
                ports = effectiveRank,
                rbCount = prbs.coerceIn(1, 275)
            )
        )
        val beam = NrBeamV28().sweep(
            azimuthDeg,
            antennaRows = t,
            antennaCols = r,
            beamCount = beamCount
        )
        return Result(t, r, requestedRank, effectiveRank, beamSweep, beamCount, srs, beam)
    }
}
