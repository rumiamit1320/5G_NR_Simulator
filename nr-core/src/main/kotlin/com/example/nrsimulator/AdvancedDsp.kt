package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

enum class ChannelModel { AWGN, RAYLEIGH, RICIAN }
enum class Scheduler { ROUND_ROBIN, PROPORTIONAL_FAIR }

data class UeStats(
    val id: Int,
    val snrDb: Double,
    val cqi: Int,
    val mcs: Int,
    val throughputMbps: Double,
    val bler: Double,
    val scheduledPrbs: Int,
    val harqRetransmissions: Int
)

data class AdvancedResult(
    val ues: List<UeStats>,
    val totalThroughputMbps: Double,
    val meanBler: Double,
    val meanEvm: Double,
    val channel: ChannelModel,
    val scheduler: Scheduler,
    val rank: Int,
    val frame: Long
)

/**
 * System-level educational NR model. The original Simulator/Dsp path is deliberately
 * untouched; this class adds multi-UE channel, scheduling, MIMO rank, CQI/MCS and HARQ
 * behavior around the existing PHY model.
 */
class AdvancedSimulator {
    private val rng = Random(System.nanoTime())
    private val pfAverage = DoubleArray(32) { 1.0 }

    fun run(
        ueCount: Int,
        prbs: Int,
        modulationOrder: Int,
        snrBaseDb: Double,
        channel: ChannelModel,
        scheduler: Scheduler,
        antennaTx: Int,
        antennaRx: Int,
        codingRate: Double,
        harqEnabled: Boolean,
        frame: Long
    ): AdvancedResult {
        val n = ueCount.coerceIn(1, 32)
        val totalAntennas = min(antennaTx, antennaRx).coerceAtLeast(1)
        val rank = min(totalAntennas, 4)
        val demand = DoubleArray(n) { 0.7 + rng.nextDouble() * 0.6 }
        val snrs = DoubleArray(n) { (snrBaseDb + gaussian() * 3.0 - it * 0.35).coerceIn(-5.0, 35.0) }
        val cqi = IntArray(n) { snrToCqi(snrs[it]) }
        val mcs = IntArray(n) { cqiToMcs(cqi[it]) }
        val spectralEfficiency = DoubleArray(n) { mcsToEfficiency(mcs[it], codingRate, rank) }
        val allocations = allocate(prbs, demand, spectralEfficiency, scheduler)

        val stats = ArrayList<UeStats>(n)
        for (i in 0 until n) {
            val se = spectralEfficiency[i]
            val scheduled = allocations[i]
            val raw = (scheduled * 12.0 * 14.0 * 1000.0 * 0.5).coerceAtLeast(0.0)
            val throughput = raw * se / 1e6
            val idealBler = blerFromSnr(snrs[i], mcs[i], channel)
            val tx = if (harqEnabled) 1.0 - min(0.75, idealBler * idealBler) else idealBler
            val retrans = if (harqEnabled && idealBler > 0.15) 1 else 0
            val evm = evmFromSnr(snrs[i], channel)
            stats += UeStats(i + 1, snrs[i], cqi[i], mcs[i], throughput * (1.0 - tx), tx, scheduled, retrans)
        }
        val total = stats.sumOf { it.throughputMbps }
        return AdvancedResult(stats, total, stats.map { it.bler }.average(), stats.map { it.bler }.let { list ->
            if (list.isEmpty()) 0.0 else stats.map { evmFromSnr(it.snrDb, channel) }.average()
        }, channel, scheduler, rank, frame)
    }

    private fun allocate(prbs: Int, demand: DoubleArray, se: DoubleArray, scheduler: Scheduler): IntArray {
        val out = IntArray(demand.size)
        if (demand.isEmpty()) return out
        repeat(prbs.coerceAtLeast(1)) { rb ->
            val selected = when (scheduler) {
                Scheduler.ROUND_ROBIN -> rb % demand.size
                Scheduler.PROPORTIONAL_FAIR -> {
                    var best = 0
                    var bestMetric = Double.NEGATIVE_INFINITY
                    for (i in demand.indices) {
                        val metric = demand[i] * se[i] / pfAverage[i].coerceAtLeast(0.05)
                        if (metric > bestMetric) { bestMetric = metric; best = i }
                    }
                    best
                }
            }
            out[selected]++
            if (scheduler == Scheduler.PROPORTIONAL_FAIR) {
                for (i in out.indices) pfAverage[i] = 0.95 * pfAverage[i] + 0.05 * if (i == selected) se[i] else 0.0
            }
        }
        return out
    }

    private fun snrToCqi(snr: Double): Int = when {
        snr < -6 -> 0
        snr < -4 -> 1
        snr < -2 -> 2
        snr < 0 -> 3
        snr < 2 -> 4
        snr < 4 -> 5
        snr < 6 -> 6
        snr < 8 -> 7
        snr < 10 -> 8
        snr < 12 -> 9
        snr < 14 -> 10
        snr < 16 -> 11
        snr < 18 -> 12
        snr < 21 -> 13
        snr < 24 -> 14
        else -> 15
    }

    private fun cqiToMcs(cqi: Int): Int = when (cqi) {
        0 -> 0; 1 -> 1; 2 -> 3; 3 -> 5; 4 -> 7; 5 -> 9; 6 -> 11; 7 -> 13
        8 -> 15; 9 -> 17; 10 -> 19; 11 -> 21; 12 -> 23; 13 -> 25; 14 -> 27; else -> 28
    }

    private fun mcsToEfficiency(mcs: Int, requestedRate: Double, rank: Int): Double {
        val bits = when {
            mcs < 10 -> 2.0
            mcs < 17 -> 4.0
            mcs < 23 -> 6.0
            else -> 8.0
        }
        return bits * requestedRate.coerceIn(0.2, 0.93) * rank.coerceAtMost(4)
    }

    private fun blerFromSnr(snr: Double, mcs: Int, channel: ChannelModel): Double {
        val threshold = when {
            mcs < 10 -> 0.0
            mcs < 17 -> 7.0
            mcs < 23 -> 13.0
            else -> 19.0
        }
        val sigma = when (channel) { ChannelModel.AWGN -> 1.8; ChannelModel.RAYLEIGH -> 3.2; ChannelModel.RICIAN -> 2.4 }
        return (1.0 / (1.0 + exp((snr - threshold) / sigma))).coerceIn(0.001, 0.999)
    }

    private fun evmFromSnr(snr: Double, channel: ChannelModel): Double {
        val penalty = when (channel) { ChannelModel.AWGN -> 1.0; ChannelModel.RAYLEIGH -> 1.35; ChannelModel.RICIAN -> 1.15 }
        return (100.0 * penalty * 10.0.pow(-snr / 20.0)).coerceIn(0.5, 80.0)
    }

    private fun gaussian(): Double {
        var u = 0.0
        var v = 0.0
        while (u == 0.0) u = rng.nextDouble()
        v = rng.nextDouble()
        return sqrt(-2.0 * ln(u)) * cos(2.0 * PI * v)
    }
}
