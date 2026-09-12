package com.example.nrsimulator

import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/**
 * V65 additive network orchestration layer.
 *
 * It deliberately sits above V62/V64 instead of replacing them. V62 remains the
 * radio environment and V64 remains the closed-loop PHY/scheduler execution
 * engine. V65 adds explicit gNB/UE topology, serving-cell association,
 * handover hysteresis, traffic demand and network-level KPIs.
 */
data class NrNetworkCellV65(
    val cellId: Int,
    val xM: Double,
    val yM: Double,
    val txPowerDbm: Double,
    val carrierGHz: Double
)

data class NrNetworkUeV65(
    val ueId: Int,
    val xM: Double,
    val yM: Double,
    val servingCellId: Int,
    val rsrpDbm: Double,
    val trafficDemandMbps: Double,
    val throughputMbps: Double,
    val handover: Boolean
)

data class NrNetworkSimulationConfigV65(
    val slots: Int = 40,
    val ueCount: Int = 12,
    val cells: Int = 3,
    val prbs: Int = 52,
    val scsKHz: Int = 30,
    val carrierGHz: Double = 3.5,
    val cellRadiusM: Double = 250.0,
    val velocityKmh: Double = 30.0,
    val txPowerDbm: Double = 46.0,
    val handoverHysteresisDb: Double = 3.0,
    val trafficDemandMbps: Double = 50.0,
    val txAntennas: Int = 4,
    val rxAntennas: Int = 4,
    val layers: Int = 2,
    val seed: Int = 6501
)

data class NrNetworkSlotV65(
    val slotIndex: Int,
    val ueStates: List<NrNetworkUeV65>,
    val throughputMbps: Double,
    val activeUes: Int,
    val handovers: Int
)

data class NrNetworkSimulationResultV65(
    val cells: List<NrNetworkCellV65>,
    val ueStates: List<NrNetworkUeV65>,
    val slotResults: List<NrNetworkSlotV65>,
    val totalThroughputMbps: Double,
    val meanThroughputMbps: Double,
    val demandSatisfiedPercent: Double,
    val handovers: Int,
    val cellLoadPercent: Map<Int, Double>,
    val fairness: Double
)

object NrNetworkSimulationV65 {
    private fun pathLoss(distanceM: Double, carrierGHz: Double): Double =
        32.4 + 20.0 * log10(carrierGHz) + 30.0 * log10(max(distanceM, 10.0))

    private fun jain(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sum = values.sum()
        val square = values.sumOf { it * it }
        return if (square <= 0.0) 0.0 else sum * sum / (values.size * square)
    }

    private fun makeCells(c: NrNetworkSimulationConfigV65): List<NrNetworkCellV65> {
        val radius = c.cellRadiusM * 1.55
        return (0 until c.cells).map { id ->
            val angle = 2.0 * Math.PI * id / c.cells
            NrNetworkCellV65(id + 1, radius * kotlin.math.cos(angle), radius * kotlin.math.sin(angle), c.txPowerDbm, c.carrierGHz)
        }
    }

    private fun uePosition(id: Int, slot: Int, c: NrNetworkSimulationConfigV65): Pair<Double, Double> {
        val angle = 2.0 * Math.PI * id / c.ueCount + 0.11 * kotlin.math.sin(slot * 0.07 + id)
        val radial = c.cellRadiusM * (0.25 + 0.55 * ((id * 37 % 97) / 96.0))
        val travel = c.velocityKmh / 3.6 * slot * 0.001
        return radial * kotlin.math.cos(angle) + travel to radial * kotlin.math.sin(angle)
    }

    private fun rsrp(ueX: Double, ueY: Double, cell: NrNetworkCellV65): Double {
        return cell.txPowerDbm - pathLoss(hypot(ueX - cell.xM, ueY - cell.yM), cell.carrierGHz)
    }

    fun run(config: NrNetworkSimulationConfigV65 = NrNetworkSimulationConfigV65()): NrNetworkSimulationResultV65 {
        val c = config.copy(
            slots = config.slots.coerceIn(1, 1000),
            ueCount = config.ueCount.coerceIn(1, 64),
            cells = config.cells.coerceIn(1, 7),
            prbs = config.prbs.coerceIn(1, 106),
            scsKHz = if (config.scsKHz in listOf(15, 30, 60)) config.scsKHz else 30,
            carrierGHz = config.carrierGHz.coerceIn(0.5, 100.0),
            cellRadiusM = config.cellRadiusM.coerceIn(25.0, 5000.0),
            velocityKmh = config.velocityKmh.coerceIn(0.0, 500.0),
            handoverHysteresisDb = config.handoverHysteresisDb.coerceIn(0.0, 20.0),
            trafficDemandMbps = config.trafficDemandMbps.coerceAtLeast(0.0),
            txAntennas = config.txAntennas.coerceIn(1, 4),
            rxAntennas = config.rxAntennas.coerceIn(1, 4),
            layers = config.layers.coerceIn(1, min(config.txAntennas, config.rxAntennas))
        )
        val cells = makeCells(c)
        var serving = IntArray(c.ueCount) { -1 }
        var totalHandover = 0
        var lastStates = emptyList<NrNetworkUeV65>()
        val slots = ArrayList<NrNetworkSlotV65>(c.slots)
        val cellPrbs = IntArray(c.cells)

        for (slot in 0 until c.slots) {
            val positions = (0 until c.ueCount).map { uePosition(it + 1, slot, c) }
            val selected = positions.mapIndexed { index, p ->
                val candidates = cells.map { cell -> cell.cellId to rsrp(p.first, p.second, cell) }.sortedByDescending { it.second }
                val best = candidates.first()
                val previous = serving[index]
                val chosen = if (previous < 0) best.first else {
                    val previousRsrp = candidates.firstOrNull { it.first == previous }?.second ?: Double.NEGATIVE_INFINITY
                    if (best.first != previous && best.second > previousRsrp + c.handoverHysteresisDb) best.first else previous
                }
                if (previous >= 0 && chosen != previous) totalHandover++
                serving[index] = chosen
                chosen
            }

            // Preserve V64 as the PHY/scheduler execution engine. Its V62 input
            // remains the source of radio conditions; V65 supplies network-level
            // association and traffic accounting around that execution.
            val closed = NrClosedLoopV64.run(
                NrClosedLoopConfigV64(
                    slots = 1,
                    ueCount = c.ueCount,
                    cells = c.cells,
                    prbs = c.prbs,
                    scsKHz = c.scsKHz,
                    carrierGHz = c.carrierGHz,
                    velocityKmh = c.velocityKmh,
                    txAntennas = c.txAntennas,
                    rxAntennas = c.rxAntennas,
                    layers = c.layers,
                    payloadBitsPerUe = 128,
                    seed = c.seed + slot * 7919
                )
            )

            java.util.Arrays.fill(cellPrbs, 0)
            closed.ueStates.forEach { state -> cellPrbs[serving[state.ueId - 1] - 1] += state.allocatedPrbs }
            val states = closed.ueStates.map { state ->
                val id = state.ueId - 1
                val p = positions[id]
                val cell = cells[serving[id] - 1]
                val r = rsrp(p.first, p.second, cell)
                val demand = c.trafficDemandMbps
                val goodput = min(state.throughputMbps, demand)
                NrNetworkUeV65(state.ueId, p.first, p.second, cell.cellId, r, demand, goodput, false)
            }
            val slotHandovers = if (slot == 0) 0 else states.count { it.servingCellId != lastStates.getOrNull(it.ueId - 1)?.servingCellId }
            val slotThroughput = states.sumOf { it.throughputMbps }
            slots += NrNetworkSlotV65(slot, states, slotThroughput, states.count { it.throughputMbps > 0.0 }, slotHandovers)
            lastStates = states
        }

        // Reconstruct the final per-UE state with the cumulative handover count.
        val finalStates = lastStates.map { it.copy(handover = false) }
        val mean = if (finalStates.isEmpty()) 0.0 else finalStates.map { it.throughputMbps }.average()
        val demand = finalStates.sumOf { it.trafficDemandMbps }
        val delivered = finalStates.sumOf { it.throughputMbps }
        val load = cells.associate { it.cellId to 100.0 * cellPrbs[it.cellId - 1] / c.prbs }
        return NrNetworkSimulationResultV65(
            cells = cells,
            ueStates = finalStates,
            slotResults = slots,
            totalThroughputMbps = if (c.slots == 0) 0.0 else slots.sumOf { it.throughputMbps } / c.slots,
            meanThroughputMbps = mean,
            demandSatisfiedPercent = if (demand <= 0.0) 100.0 else 100.0 * delivered / demand,
            handovers = totalHandover,
            cellLoadPercent = load,
            fairness = jain(finalStates.map { it.throughputMbps })
        )
    }
}
