package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

data class NrUeStateV62(
    val ueId: Int,
    val xM: Double,
    val yM: Double,
    val distanceM: Double,
    val rsrpDbm: Double,
    val sinrDb: Double,
    val cqi: Int,
    val mcs: Int,
    val rank: Int,
    val allocatedPrbs: Int,
    val throughputMbps: Double,
    val bler: Double
)

data class NrRadioEnvironmentConfigV62(
    val ueCount: Int = 8,
    val cells: Int = 1,
    val prbs: Int = 52,
    val scsKHz: Int = 30,
    val carrierGHz: Double = 3.5,
    val txPowerDbm: Double = 46.0,
    val noiseFigureDb: Double = 7.0,
    val bandwidthMHz: Double = 0.0,
    val velocityKmh: Double = 30.0,
    val cellRadiusM: Double = 250.0,
    val txAntennas: Int = 4,
    val rxAntennas: Int = 4,
    val layers: Int = 2,
    val shadowingDb: Double = 4.0,
    val trafficBufferMb: Double = 20.0,
    val slotIndex: Long = 0L,
    val seed: Int = 6201
)

data class NrRadioEnvironmentResultV62(
    val slotIndex: Long,
    val cellCount: Int,
    val ueStates: List<NrUeStateV62>,
    val totalThroughputMbps: Double,
    val meanSinrDb: Double,
    val meanCqi: Double,
    val spectralEfficiencyBpsHz: Double,
    val utilizationPercent: Double,
    val fairness: Double,
    val stageSummary: List<String>
)

/**
 * Additive V62 system-level radio environment.
 * V1-V61 engines are deliberately not modified.
 * This is a real-time educational system model: geometry -> path loss -> shadowing
 * -> Doppler/interference margin -> SINR -> CQI/MCS -> proportional-fair PRB allocation.
 */
object NrRadioEnvironmentV62 {
    private val mcsEfficiency = doubleArrayOf(
        .234, .377, .601, .877, 1.175, 1.477, 1.914, 2.406,
        2.730, 3.322, 3.902, 4.523, 5.115, 5.555, 6.226, 6.914,
        7.406, 7.914, 8.453, 8.914, 9.450, 10.029, 10.626, 11.115,
        11.830, 12.500, 13.225, 13.926
    )

    private fun pathLossDb(distanceM: Double, carrierGHz: Double): Double {
        val d = max(distanceM, 10.0)
        // 3GPP-inspired urban macro educational approximation, not a conformance model.
        return 32.4 + 20.0 * log10(carrierGHz) + 30.0 * log10(d)
    }

    private fun thermalNoiseDbm(bandwidthHz: Double, noiseFigureDb: Double): Double =
        -174.0 + 10.0 * log10(max(bandwidthHz, 1.0)) + noiseFigureDb

    private fun cqiFromSinr(sinrDb: Double): Int = when {
        sinrDb < -6.7 -> 0
        sinrDb < -4.7 -> 1
        sinrDb < -2.3 -> 2
        sinrDb < 0.2 -> 3
        sinrDb < 2.4 -> 4
        sinrDb < 4.3 -> 5
        sinrDb < 6.3 -> 6
        sinrDb < 8.4 -> 7
        sinrDb < 10.3 -> 8
        sinrDb < 12.3 -> 9
        sinrDb < 14.3 -> 10
        sinrDb < 16.3 -> 11
        else -> 12
    }

    private fun mcsFromCqi(cqi: Int): Int = (cqi * 2 + if (cqi >= 9) 1 else 0).coerceIn(0, 27)

    private fun rank(tx: Int, rx: Int, requested: Int, sinrDb: Double): Int {
        val limit = minOf(tx, rx, requested).coerceAtLeast(1)
        return when {
            sinrDb < 5.0 -> 1
            sinrDb < 12.0 -> minOf(limit, 2)
            else -> limit
        }
    }

    private fun qamOrder(mcs: Int): Int = when {
        mcs < 10 -> 2
        mcs < 17 -> 4
        mcs < 23 -> 6
        else -> 8
    }

    private fun blerFromSinr(sinrDb: Double, mcs: Int): Double {
        val required =  -5.0 + mcs * 0.75
        return (1.0 / (1.0 + exp((sinrDb - required) / 1.8))).coerceIn(0.0, 1.0)
    }

    private fun jain(values: List<Double>): Double {
        val sum = values.sum()
        val sq = values.sumOf { it * it }
        return if (sq <= 0.0) 0.0 else sum * sum / (values.size * sq)
    }

    fun run(c0: NrRadioEnvironmentConfigV62 = NrRadioEnvironmentConfigV62()): NrRadioEnvironmentResultV62 {
        val c = c0.copy(
            ueCount = c0.ueCount.coerceIn(1, 64),
            cells = c0.cells.coerceIn(1, 7),
            prbs = c0.prbs.coerceIn(1, 106),
            scsKHz = if (c0.scsKHz in listOf(15, 30, 60)) c0.scsKHz else 30,
            carrierGHz = c0.carrierGHz.coerceIn(0.5, 100.0),
            txAntennas = c0.txAntennas.coerceIn(1, 8),
            rxAntennas = c0.rxAntennas.coerceIn(1, 8),
            layers = c0.layers.coerceIn(1, minOf(c0.txAntennas, c0.rxAntennas)),
            velocityKmh = c0.velocityKmh.coerceIn(0.0, 500.0),
            cellRadiusM = c0.cellRadiusM.coerceIn(25.0, 5000.0)
        )
        val r = Random(c.seed + c.slotIndex.toInt())
        val bwHz = if (c.bandwidthMHz > 0.0) c.bandwidthMHz * 1e6 else c.prbs * 12.0 * c.scsKHz * 1000.0
        val noiseDbm = thermalNoiseDbm(bwHz, c.noiseFigureDb)
        val wavelength = 299792458.0 / (c.carrierGHz * 1e9)
        val dopplerHz = c.velocityKmh / 3.6 / wavelength
        val stage = mutableListOf<String>()
        stage += "Geometry: ${c.cells}-cell / ${c.ueCount}-UE radio environment"
        stage += "Numerology: ${c.scsKHz} kHz SCS / ${c.prbs} PRBs / BW=${"%.2f".format(bwHz / 1e6)} MHz"
        stage += "Mobility: ${"%.1f".format(c.velocityKmh)} km/h / Doppler=${"%.1f".format(dopplerHz)} Hz"
        stage += "Channel: distance + path loss + log-normal shadowing + inter-cell interference"

        data class Raw(val id: Int, val x: Double, val y: Double, val distance: Double, val sinr: Double, val rsrp: Double)
        val raw = (0 until c.ueCount).map { id ->
            val angle = 2.0 * Math.PI * id / c.ueCount + 0.13 * sin(c.slotIndex * 0.07 + id)
            val radial = c.cellRadiusM * (0.15 + 0.78 * ((id * 37 % 101) / 100.0))
            val travel = c.velocityKmh / 3.6 * 0.001 * c.slotIndex
            val x = radial * cos(angle) + travel
            val y = radial * sin(angle)
            val d = hypot(x, y).coerceAtMost(c.cellRadiusM * 1.35)
            val pl = pathLossDb(d, c.carrierGHz)
            val shadow = if (c.shadowingDb == 0.0) 0.0 else r.nextGaussian() * c.shadowingDb
            val rsrp = c.txPowerDbm - pl + shadow
            val interCellDb = if (c.cells <= 1) 0.0 else 10.0 * log10(c.cells.toDouble()) + 2.5
            val dopplerPenalty = min(6.0, dopplerHz / 1000.0)
            val sinr = rsrp - noiseDbm - interCellDb - dopplerPenalty
            Raw(id + 1, x, y, d, sinr, rsrp)
        }

        val cqi = raw.associate { it.id to cqiFromSinr(it.sinr) }
        val mcs = raw.associate { it.id to mcsFromCqi(cqi.getValue(it.id)) }
        val ranks = raw.associate { it.id to rank(c.txAntennas, c.rxAntennas, c.layers, it.sinr) }
        val weights = raw.associate { u ->
            // A deterministic starting average throughput prevents a near-zero UE from monopolising all PRBs.
            u.id to (mcsEfficiency[mcs.getValue(u.id)] * ranks.getValue(u.id) + 0.25) / (1.0 + 0.12 * u.id)
        }
        val totalWeight = weights.values.sum().coerceAtLeast(1e-9)
        val allocated = raw.associate { u ->
            u.id to maxOf(0, floor(c.prbs * weights.getValue(u.id) / totalWeight).toInt())
        }.toMutableMap()
        var left = c.prbs - allocated.values.sum()
        val order = raw.sortedByDescending { weights.getValue(it.id) / maxOf(1, allocated.getValue(it.id)) }
        var k = 0
        while (left > 0) {
            val id = order[k % order.size].id
            allocated[id] = allocated.getValue(id) + 1
            left--
            k++
        }
        stage += "Scheduler: proportional-fair weighted PRB allocation"
        stage += "Link adaptation: SINR -> CQI -> MCS -> rank"

        val states = raw.map { u ->
            val q = cqi.getValue(u.id)
            val m = mcs.getValue(u.id)
            val rk = ranks.getValue(u.id)
            val prb = allocated.getValue(u.id)
            val eff = mcsEfficiency[m] * rk
            val rawMbps = prb * 12.0 * 14.0 * c.scsKHz * 1000.0 * eff / 1e6
            val bler = blerFromSinr(u.sinr, m)
            val throughput = rawMbps * (1.0 - bler) * 0.92 * (c.trafficBufferMb / (c.trafficBufferMb + 1.0)).coerceIn(0.05, 1.0)
            NrUeStateV62(u.id, u.x, u.y, u.distance, u.rsrp, u.sinr, q, m, rk, prb, throughput, bler)
        }
        val throughputs = states.map { it.throughputMbps }
        val total = throughputs.sum()
        val occupied = states.sumOf { it.allocatedPrbs }
        val meanSinr = states.map { it.sinrDb }.average()
        val meanCqi = states.map { it.cqi }.average()
        val spectral = if (bwHz > 0.0) total * 1e6 / bwHz else 0.0
        val fairness = jain(throughputs)
        stage += "PHY abstraction: MIMO rank + MCS spectral efficiency + BLER"
        stage += "KPIs: throughput=${"%.2f".format(total)} Mbps / BLER=${"%.3f".format(states.map { it.bler }.average())} / fairness=${"%.3f".format(fairness)}"

        return NrRadioEnvironmentResultV62(c.slotIndex, c.cells, states, total, meanSinr, meanCqi, spectral, 100.0 * occupied / c.prbs, fairness, stage)
    }
}
