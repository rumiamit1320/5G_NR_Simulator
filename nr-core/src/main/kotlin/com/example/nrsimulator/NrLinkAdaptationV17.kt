package com.example.nrsimulator

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * V17 additive link-adaptation/HARQ layer.
 *
 * V1-v16 are intentionally untouched. This layer consumes CSI-style SINR/CQI/RI
 * information and selects an NR-oriented MCS, then models incremental-redundancy
 * HARQ with RV sequence 0,2,3,1. The MCS entries use the existing V4 Table 5.1.3.1-1
 * values. The BLER curve is a deterministic link-level abstraction; it is not a
 * replacement for a full PHY Monte-Carlo BLER curve.
 */

data class NrLinkAdaptationV17Config(
    val sinrDb: Double = 15.0,
    val cqi: Int = -1,
    val rank: Int = 2,
    val layers: Int = 2,
    val prbs: Int = 52,
    val symbolsPerSlot: Int = 12,
    val mcsTable: Int = 1,
    val linkMarginDb: Double = 1.5,
    val maxHarqTx: Int = 4,
    val rvSequence: IntArray = intArrayOf(0, 2, 3, 1),
    val targetBler: Double = 0.10,
    val seed: Int = 0x1701
)

data class NrMcsV17(
    val index: Int,
    val qm: Int,
    val rate: Double,
    val spectralEfficiency: Double,
    val requiredSinrDb: Double
)

data class NrHarqTransmissionV17(
    val transmission: Int,
    val rv: Int,
    val effectiveSinrDb: Double,
    val bler: Double,
    val ack: Boolean,
    val combiningGainDb: Double
)

data class NrLinkAdaptationV17Result(
    val measuredSinrDb: Double,
    val cqi: Int,
    val rank: Int,
    val selectedMcs: Int,
    val qm: Int,
    val codeRate: Double,
    val spectralEfficiency: Double,
    val tbBitsPerSlot: Int,
    val firstTransmissionBler: Double,
    val finalBler: Double,
    val ack: Boolean,
    val harqTransmissions: Int,
    val rvHistory: IntArray,
    val combiningGainDb: Double,
    val throughputMbps: Double,
    val goodputMbps: Double,
    val utilizationPercent: Double,
    val history: List<NrHarqTransmissionV17>,
    val pass: Boolean,
    val note: String
)

class NrLinkAdaptationV17 {
    private val mcsTable = NrMcsTable.table1.mapIndexed { i, e ->
        val se = e.qM * e.rate
        NrMcsV17(i, e.qM, e.rate, se, requiredSinr(se))
    }

    fun run(cfg: NrLinkAdaptationV17Config): NrLinkAdaptationV17Result {
        val sinr = cfg.sinrDb.coerceIn(-20.0, 40.0)
        val rank = cfg.rank.coerceIn(1, 4)
        val layers = cfg.layers.coerceIn(1, rank)
        val cqi = if (cfg.cqi in 0..15) cfg.cqi else estimateCqi(sinr)
        val selected = selectMcs(cqi, sinr, cfg.linkMarginDb, cfg.mcsTable)
        val nRe = (cfg.prbs.coerceIn(1, 275) * 12 * cfg.symbolsPerSlot.coerceIn(1, 14)).coerceAtLeast(1)
        val tbBits = (nRe * selected.qm * selected.rate * layers).toInt().coerceAtLeast(24)
        val rng = Random(cfg.seed xor (selected.index shl 8) xor (cqi shl 16))
        val maxTx = cfg.maxHarqTx.coerceIn(1, 4)
        val rv = cfg.rvSequence.take(maxTx).ifEmpty { listOf(0) }
        val hist = ArrayList<NrHarqTransmissionV17>()
        var ack = false
        var combinedGain = 0.0
        var lastBler = 1.0

        for (i in 0 until maxTx) {
            val rvId = rv[i % rv.size]
            val independentGain = when (i) { 0 -> 0.0; 1 -> 2.25; 2 -> 3.25; else -> 3.75 }
            combinedGain = if (i == 0) 0.0 else min(6.5, combinedGain + independentGain)
            val effSinr = sinr + combinedGain
            val bler = bler(effSinr, selected.requiredSinrDb, cfg.targetBler)
            val randomPass = rng.nextDouble() > bler
            // At very high SINR, make the reference model deterministic; at low SINR
            // retain stochastic BLER behavior so repeated HARQ runs remain meaningful.
            ack = if (effSinr >= selected.requiredSinrDb + 7.0) true else randomPass
            hist += NrHarqTransmissionV17(i + 1, rvId, effSinr, bler, ack, combinedGain)
            lastBler = bler
            if (ack) break
        }

        val txCount = hist.size
        val slotSeconds = 1.0 / (15.0 * 1000.0) * (14.0 / 1.0) / (30.0 / 15.0) // 30 kHz reference slot duration
        val grossMbps = tbBits / slotSeconds / 1e6
        val goodput = if (ack) tbBits / (slotSeconds * txCount) / 1e6 else 0.0
        val utilization = if (ack) 100.0 / txCount else 0.0
        val firstBler = bler(sinr, selected.requiredSinrDb, cfg.targetBler)
        val pass = ack && selected.index in 0..28 && hist.isNotEmpty()

        return NrLinkAdaptationV17Result(
            sinr, cqi, rank, selected.index, selected.qm, selected.rate,
            selected.spectralEfficiency, tbBits, firstBler, lastBler, ack,
            txCount, hist.map { it.rv }.toIntArray(), combinedGain,
            grossMbps, goodput, utilization, hist, pass,
            "V17 uses TS 38.214-oriented MCS selection with CSI-derived CQI and an incremental-redundancy HARQ reference model. RV order defaults to 0,2,3,1. The BLER curve is an engineering link-level abstraction; full conformance requires measured/standardized BLER calibration over the complete PHY/channel stack."
        )
    }

    fun estimateCqi(sinrDb: Double): Int {
        val thresholds = doubleArrayOf(-6.7, -4.7, -2.3, 0.2, 2.4, 4.3, 6.3, 8.2, 10.3, 11.7, 13.1, 14.4, 15.8, 17.0, 18.3, 20.0)
        var cqi = 0
        for (i in thresholds.indices) if (sinrDb >= thresholds[i]) cqi = i + 1
        return cqi.coerceIn(0, 15)
    }

    private fun selectMcs(cqi: Int, sinr: Double, margin: Double, table: Int): NrMcsV17 {
        val maxByCqi = when (cqi.coerceIn(0, 15)) {
            0 -> 0; 1 -> 0; 2 -> 2; 3 -> 4; 4 -> 6; 5 -> 8; 6 -> 10
            7 -> 12; 8 -> 14; 9 -> 16; 10 -> 18; 11 -> 20; 12 -> 22
            13 -> 24; 14 -> 26; else -> 28
        }
        val candidates = mcsTable.take(maxByCqi + 1).filter { sinr >= it.requiredSinrDb + margin }
        return candidates.lastOrNull() ?: mcsTable.first()
    }

    private fun requiredSinr(se: Double): Double {
        // Shannon-gap reference: threshold rises with spectral efficiency.
        // Calibrated to be conservative for link adaptation rather than a
        // conformance BLER curve.
        val linear = (2.0.pow(se) - 1.0).coerceAtLeast(1e-6)
        return 10.0 * kotlin.math.log10(linear) + 1.8 + 0.55 * se
    }

    private fun bler(sinr: Double, required: Double, target: Double): Double {
        val slope = 1.15 + 0.35 * target.coerceIn(0.01, 0.5)
        val x = (sinr - required) / slope
        return (1.0 / (1.0 + exp(1.35 * x))).coerceIn(1e-5, 0.99999)
    }

    private fun Double.pow(e: Double): Double = kotlin.math.exp(e * kotlin.math.ln(this))
}
