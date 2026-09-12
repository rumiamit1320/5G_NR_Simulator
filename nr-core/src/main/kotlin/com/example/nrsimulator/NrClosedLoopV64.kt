package com.example.nrsimulator

/**
 * Additive V64 closed-loop execution layer.
 *
 * Keeps V62 radio modeling and V61 PHY intact while feeding PHY ACK/NACK,
 * BLER and goodput back into the next-slot proportional-fair scheduler.
 */
data class NrClosedLoopConfigV64(
    val slots: Int = 40,
    val ueCount: Int = 8,
    val cells: Int = 2,
    val prbs: Int = 52,
    val scsKHz: Int = 30,
    val carrierGHz: Double = 3.5,
    val velocityKmh: Double = 30.0,
    val payloadBitsPerUe: Int = 128,
    val txAntennas: Int = 4,
    val rxAntennas: Int = 4,
    val layers: Int = 2,
    val seed: Int = 6401,
    val feedbackAlpha: Double = 0.25,
    val harqEnabled: Boolean = true,
    /**
     * Optional additive per-UE link-quality correction supplied by a richer
     * orchestration layer. Empty preserves the exact V64 behavior.
     */
    val externalSinrOffsetDbByUe: Map<Int, Double> = emptyMap()
)

data class NrClosedLoopUeV64(
    val ueId: Int,
    val xM: Double,
    val yM: Double,
    val sinrDb: Double,
    val cqi: Int,
    val mcs: Int,
    val rank: Int,
    val allocatedPrbs: Int,
    val throughputMbps: Double,
    val bler: Double,
    val crcPass: Boolean,
    val ber: Double,
    val harqNack: Boolean,
    val pfAverageMbps: Double
)

data class NrClosedLoopSlotV64(
    val slotIndex: Int,
    val ueStates: List<NrClosedLoopUeV64>,
    val totalThroughputMbps: Double,
    val fairness: Double,
    val crcPassRate: Double,
    val ber: Double,
    val schedulerFeedback: String
)

data class NrClosedLoopResultV64(
    val slots: Int,
    val ueStates: List<NrClosedLoopUeV64>,
    val slotResults: List<NrClosedLoopSlotV64>,
    val totalThroughputMbps: Double,
    val fairness: Double,
    val crcPassRate: Double,
    val ber: Double,
    val harqNacks: Int
)

object NrClosedLoopV64 {
    private fun modulationForMcs(mcs: Int) = when {
        mcs >= 23 -> 256
        mcs >= 17 -> 64
        mcs >= 10 -> 16
        else -> 4
    }

    private fun jain(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sum = values.sum()
        val sq = values.sumOf { it * it }
        return if (sq <= 0.0) 0.0 else sum * sum / (values.size * sq)
    }

    fun run(config: NrClosedLoopConfigV64 = NrClosedLoopConfigV64()): NrClosedLoopResultV64 {
        val c = config.copy(
            slots = config.slots.coerceIn(1, 1000),
            ueCount = config.ueCount.coerceIn(1, 64),
            cells = config.cells.coerceIn(1, 7),
            prbs = config.prbs.coerceIn(1, 106),
            scsKHz = if (config.scsKHz in listOf(15, 30, 60)) config.scsKHz else 30,
            payloadBitsPerUe = config.payloadBitsPerUe.coerceIn(32, 200),
            txAntennas = config.txAntennas.coerceIn(1, 4),
            rxAntennas = config.rxAntennas.coerceIn(1, 4),
            layers = config.layers.coerceIn(1, minOf(config.txAntennas, config.rxAntennas)),
            feedbackAlpha = config.feedbackAlpha.coerceIn(0.01, 1.0),
            externalSinrOffsetDbByUe = config.externalSinrOffsetDbByUe.mapValues { it.value.coerceIn(-30.0, 30.0) }
        )

        val averages = DoubleArray(c.ueCount) { 1.0 }
        val lastStates = ArrayList<NrClosedLoopUeV64>(c.ueCount)
        val slotResults = ArrayList<NrClosedLoopSlotV64>(c.slots)
        var totalThroughput = 0.0
        var totalBits = 0L
        var totalErrors = 0L
        var crcPasses = 0
        var crcTrials = 0
        var harqNacks = 0

        for (slot in 0 until c.slots) {
            val radio = NrRadioEnvironmentV62.run(
                NrRadioEnvironmentConfigV62(
                    ueCount = c.ueCount,
                    cells = c.cells,
                    prbs = c.prbs,
                    scsKHz = c.scsKHz,
                    carrierGHz = c.carrierGHz,
                    velocityKmh = c.velocityKmh,
                    txAntennas = c.txAntennas,
                    rxAntennas = c.rxAntennas,
                    layers = c.layers,
                    slotIndex = slot.toLong(),
                    seed = c.seed
                )
            )

            // Closed-loop scheduler: retain V62's radio-derived allocation, then
            // bias the next grant by measured goodput / PF history. Every PRB is
            // conserved; poor PHY results increase priority on the next slot.
            val rawWeights = radio.ueStates.associate { u ->
                val feedback = 1.0 / averages[u.ueId - 1].coerceAtLeast(0.05)
                u.ueId to (u.throughputMbps + 0.25) * feedback
            }
            val totalWeight = rawWeights.values.sum().coerceAtLeast(1e-9)
            val grants = radio.ueStates.associate { u ->
                u.ueId to kotlin.math.floor(c.prbs * rawWeights.getValue(u.ueId) / totalWeight).toInt().coerceAtLeast(0)
            }.toMutableMap()
            var left = c.prbs - grants.values.sum()
            val order = radio.ueStates.sortedByDescending { rawWeights.getValue(it.ueId) / (grants.getValue(it.ueId) + 1.0) }
            var k = 0
            while (left > 0 && order.isNotEmpty()) {
                val id = order[k % order.size].ueId
                grants[id] = grants.getValue(id) + 1
                left--
                k++
            }

            val states = radio.ueStates.map { u ->
                val prbs = grants.getValue(u.ueId)
                val effectiveSinrDb = (u.sinrDb + c.externalSinrOffsetDbByUe.getOrDefault(u.ueId, 0.0)).coerceIn(-5.0, 40.0)
                val phy = NrIntegratedLinkV61.run(
                    NrIntegratedLinkConfigV61(
                        payloadBits = c.payloadBitsPerUe,
                        snrDb = effectiveSinrDb,
                        modulationOrder = modulationForMcs(u.mcs),
                        layers = u.rank.coerceIn(1, c.layers),
                        txAntennas = c.txAntennas,
                        rxAntennas = c.rxAntennas,
                        prbs = prbs.coerceAtLeast(1),
                        scsKHz = c.scsKHz,
                        seed = c.seed + slot * 10007 + u.ueId * 97
                    )
                )
                val nack = !phy.crcPass
                if (nack) harqNacks++
                val observed = phy.throughputMbps * if (phy.crcPass) 1.0 else 0.0
                averages[u.ueId - 1] = (1.0 - c.feedbackAlpha) * averages[u.ueId - 1] + c.feedbackAlpha * observed.coerceAtLeast(0.01)
                totalThroughput += observed
                totalBits += c.payloadBitsPerUe.toLong()
                totalErrors += phy.bitErrors.toLong()
                if (phy.crcPass) crcPasses++
                crcTrials++
                val blockErrorRate = if (phy.crcPass) 0.0 else 1.0
                NrClosedLoopUeV64(
                    ueId = u.ueId,
                    xM = u.xM,
                    yM = u.yM,
                    sinrDb = effectiveSinrDb,
                    cqi = u.cqi,
                    mcs = u.mcs,
                    rank = u.rank,
                    allocatedPrbs = prbs,
                    throughputMbps = observed,
                    bler = blockErrorRate,
                    crcPass = phy.crcPass,
                    ber = phy.bitErrors.toDouble() / c.payloadBitsPerUe,
                    harqNack = nack && c.harqEnabled,
                    pfAverageMbps = averages[u.ueId - 1]
                )
            }

            lastStates.clear()
            lastStates.addAll(states)
            val t = states.map { it.throughputMbps }
            slotResults += NrClosedLoopSlotV64(
                slotIndex = slot,
                ueStates = states,
                totalThroughputMbps = t.sum(),
                fairness = jain(t),
                crcPassRate = states.count { it.crcPass }.toDouble() / states.size,
                ber = states.map { it.ber }.average(),
                schedulerFeedback = if (c.externalSinrOffsetDbByUe.isEmpty())
                    "PHY feedback applied: ACK/NACK + goodput -> PF history -> next-slot PRB grant"
                else
                    "PHY feedback + external radio integration offsets applied: beam/channel/MIMO -> SINR -> PHY ACK/NACK -> PF grant"
            )
        }

        return NrClosedLoopResultV64(
            slots = c.slots,
            ueStates = lastStates.toList(),
            slotResults = slotResults,
            totalThroughputMbps = totalThroughput / c.slots,
            fairness = jain(lastStates.map { it.throughputMbps }),
            crcPassRate = crcPasses.toDouble() / crcTrials.coerceAtLeast(1),
            ber = totalErrors.toDouble() / totalBits.coerceAtLeast(1L),
            harqNacks = harqNacks
        )
    }
}
