package com.example.nrsimulator

import kotlin.math.atan2
import kotlin.math.min

data class NrIntegratedNetworkConfigV66(
    val slots: Int = 20,
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
    val channelModel: String = "TDL-C",
    val beamCount: Int = 16,
    val spatialCorrelation: Double = 0.35,
    val seed: Int = 6601
)

data class NrIntegratedUeV66(
    val ueId: Int,
    val servingCellId: Int,
    val xM: Double,
    val yM: Double,
    val rsrpDbm: Double,
    val beam: Int,
    val beamGainDb: Double,
    val channelFrequencySelectivityDb: Double,
    val mimoEffectiveSinrDb: Double,
    val integratedSinrOffsetDb: Double,
    val sinrDb: Double,
    val cqi: Int,
    val mcs: Int,
    val allocatedPrbs: Int,
    val throughputMbps: Double,
    val crcPass: Boolean,
    val handover: Boolean
)

data class NrIntegratedSlotV66(
    val slotIndex: Int,
    val ueStates: List<NrIntegratedUeV66>,
    val throughputMbps: Double,
    val handovers: Int,
    val cellLoadPercent: Map<Int, Double>
)

data class NrIntegratedNetworkResultV66(
    val cells: List<NrNetworkCellV65>,
    val ueStates: List<NrIntegratedUeV66>,
    val slotResults: List<NrIntegratedSlotV66>,
    val totalThroughputMbps: Double,
    val demandSatisfiedPercent: Double,
    val fairness: Double,
    val handovers: Int,
    val meanBeamGainDb: Double,
    val meanChannelFrequencySelectivityDb: Double,
    val meanMimoEffectiveSinrDb: Double
)

/**
 * V66 composes existing layers without replacing their APIs:
 * V65 topology/traffic -> V27 mobility -> V28 beams -> V16 channel -> V15 MIMO
 * -> V62 baseline radio -> V64 closed-loop PHY/scheduler/HARQ -> network KPIs.
 *
 * V16/V15/V28 are fused as an additive bounded SINR correction. V62 remains the
 * authoritative propagation/interference baseline, so fading and AWGN are not
 * applied twice. This is an integration/reference model, not a 3GPP conformance
 * claim.
 */
object NrIntegratedNetworkV66 {
    private data class LinkDescriptor(
        val network: NrNetworkUeV65,
        val beam: NrBeamV28Result,
        val channel: NrChannelV16Result,
        val mimo: NrMimoV15Result,
        val offsetDb: Double
    )

    private fun jain(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sum = values.sum()
        val sq = values.sumOf { it * it }
        return if (sq <= 0.0) 0.0 else sum * sum / (values.size * sq)
    }

    private fun angleDeg(x: Double, y: Double, cell: NrNetworkCellV65): Double =
        Math.toDegrees(atan2(y - cell.yM, x - cell.xM))

    private fun integrateLinkQuality(
        beamGainDb: Double,
        channelFrequencySelectivityDb: Double,
        mimoEffectiveSinrDb: Double,
        mimoReferenceSinrDb: Double
    ): Double {
        val beamContribution = (beamGainDb - 6.0).coerceIn(-6.0, 8.0)
        val selectivityPenalty = (-0.12 * channelFrequencySelectivityDb).coerceIn(-6.0, 0.0)
        val mimoContribution = (mimoEffectiveSinrDb - mimoReferenceSinrDb).coerceIn(-6.0, 6.0)
        return (beamContribution + selectivityPenalty + 0.35 * mimoContribution).coerceIn(-12.0, 12.0)
    }

    fun run(config: NrIntegratedNetworkConfigV66 = NrIntegratedNetworkConfigV66()): NrIntegratedNetworkResultV66 {
        val c = config.copy(
            slots = config.slots.coerceIn(1, 1000),
            ueCount = config.ueCount.coerceIn(1, 64),
            cells = config.cells.coerceIn(1, 7),
            prbs = config.prbs.coerceIn(1, 106),
            scsKHz = if (config.scsKHz in listOf(15, 30, 60)) config.scsKHz else 30,
            carrierGHz = config.carrierGHz.coerceIn(0.5, 100.0),
            cellRadiusM = config.cellRadiusM.coerceIn(25.0, 5000.0),
            velocityKmh = config.velocityKmh.coerceIn(0.0, 500.0),
            txPowerDbm = config.txPowerDbm.coerceIn(-20.0, 80.0),
            handoverHysteresisDb = config.handoverHysteresisDb.coerceIn(0.0, 20.0),
            trafficDemandMbps = config.trafficDemandMbps.coerceAtLeast(0.0),
            txAntennas = config.txAntennas.coerceIn(1, 4),
            rxAntennas = config.rxAntennas.coerceIn(1, 4),
            layers = config.layers.coerceIn(1, min(config.txAntennas, config.rxAntennas)),
            beamCount = config.beamCount.coerceIn(2, 64)
        )

        val network = NrNetworkSimulationV65.run(
            NrNetworkSimulationConfigV65(
                slots = c.slots, ueCount = c.ueCount, cells = c.cells, prbs = c.prbs,
                scsKHz = c.scsKHz, carrierGHz = c.carrierGHz, cellRadiusM = c.cellRadiusM,
                velocityKmh = c.velocityKmh, txPowerDbm = c.txPowerDbm,
                handoverHysteresisDb = c.handoverHysteresisDb,
                trafficDemandMbps = c.trafficDemandMbps, txAntennas = c.txAntennas,
                rxAntennas = c.rxAntennas, layers = c.layers, seed = c.seed
            )
        )

        val mobility = NrMobilityV27()
        val beam = NrBeamV28()
        val slotResults = ArrayList<NrIntegratedSlotV66>(c.slots)
        var totalHandover = 0
        var previousServing = IntArray(c.ueCount) { -1 }
        var finalStates = emptyList<NrIntegratedUeV66>()
        var beamSum = 0.0
        var selectivitySum = 0.0
        var mimoSinrSum = 0.0
        var samples = 0

        for (slot in 0 until c.slots) {
            val topologyStates = network.slotResults[slot].ueStates
            val v27Cells = network.cells.map { NrCellV27(it.cellId, it.xM, it.yM, it.txPowerDbm) }
            val mobilityByUe = topologyStates.associate { state ->
                state.ueId to mobility.evaluate(state.xM, state.yM, v27Cells, c.handoverHysteresisDb)
            }
            val descriptors = topologyStates.associate { state ->
                val cell = network.cells.first { it.cellId == state.servingCellId }
                val beamResult = beam.sweep(angleDeg(state.xM, state.yM, cell), c.txAntennas, c.rxAntennas, c.beamCount)
                val channelResult = NrChannelV16(
                    NrChannelV16Config(
                        model = c.channelModel, txAntennas = c.txAntennas, rxAntennas = c.rxAntennas,
                        scsKHz = c.scsKHz, prbs = c.prbs, carrierGHz = c.carrierGHz,
                        velocityKmh = c.velocityKmh, spatialCorrelation = c.spatialCorrelation,
                        timeIndex = slot, seed = c.seed xor (slot * 1009 + state.ueId * 37)
                    )
                ).summary()
                // V15's SNR input is a network-level RSRP-derived reference only;
                // V62/V64 still supplies the actual propagation/interference SINR.
                val referenceSinr = (state.rsrpDbm + 100.0).coerceIn(-5.0, 40.0)
                val mimoResult = NrMimoV15().run(
                    NrMimoV15Config(
                        txAntennas = c.txAntennas, rxAntennas = c.rxAntennas,
                        layers = c.layers, prbs = c.prbs, snrDb = referenceSinr,
                        channelModel = "FREQUENCY_SELECTIVE",
                        seed = c.seed xor (slot * 2017 + state.ueId * 53)
                    )
                )
                val offset = integrateLinkQuality(
                    beamResult.gainDb, channelResult.frequencySelectivityDb,
                    mimoResult.effectiveSinrDb, referenceSinr
                )
                beamSum += beamResult.gainDb
                selectivitySum += channelResult.frequencySelectivityDb
                mimoSinrSum += mimoResult.effectiveSinrDb
                samples++
                state.ueId to LinkDescriptor(state, beamResult, channelResult, mimoResult, offset)
            }

            val offsets = descriptors.mapValues { it.value.offsetDb }
            val closed = NrClosedLoopV64.run(
                NrClosedLoopConfigV64(
                    slots = 1, ueCount = c.ueCount, cells = c.cells, prbs = c.prbs,
                    scsKHz = c.scsKHz, carrierGHz = c.carrierGHz, velocityKmh = c.velocityKmh,
                    txAntennas = c.txAntennas, rxAntennas = c.rxAntennas, layers = c.layers,
                    payloadBitsPerUe = 128, seed = c.seed + slot * 7919,
                    externalSinrOffsetDbByUe = offsets
                )
            )

            val states = descriptors.map { (ueId, d) ->
                val radio = closed.ueStates.first { it.ueId == ueId }
                val previous = previousServing[ueId - 1]
                val changed = previous >= 0 && d.network.servingCellId != previous
                val mobilityTriggered = mobilityByUe[ueId]?.triggered == true
                NrIntegratedUeV66(
                    ueId = ueId, servingCellId = d.network.servingCellId,
                    xM = d.network.xM, yM = d.network.yM, rsrpDbm = d.network.rsrpDbm,
                    beam = d.beam.beam, beamGainDb = d.beam.gainDb,
                    channelFrequencySelectivityDb = d.channel.frequencySelectivityDb,
                    mimoEffectiveSinrDb = d.mimo.effectiveSinrDb,
                    integratedSinrOffsetDb = d.offsetDb, sinrDb = radio.sinrDb,
                    cqi = radio.cqi, mcs = radio.mcs, allocatedPrbs = radio.allocatedPrbs,
                    throughputMbps = min(radio.throughputMbps, c.trafficDemandMbps),
                    crcPass = radio.crcPass,
                    handover = changed && mobilityTriggered
                )
            }

            val cellPrbs = IntArray(c.cells)
            states.forEach { s -> cellPrbs[s.servingCellId - 1] += s.allocatedPrbs }
            val load = network.cells.associate { it.cellId to 100.0 * cellPrbs[it.cellId - 1] / c.prbs }
            val handovers = states.count { it.handover }
            totalHandover += handovers
            slotResults += NrIntegratedSlotV66(slot, states, states.sumOf { it.throughputMbps }, handovers, load)
            previousServing = IntArray(c.ueCount) { i -> states.first { it.ueId == i + 1 }.servingCellId }
            finalStates = states
        }

        val delivered = finalStates.sumOf { it.throughputMbps }
        val demand = finalStates.sumOf { c.trafficDemandMbps }
        return NrIntegratedNetworkResultV66(
            cells = network.cells, ueStates = finalStates, slotResults = slotResults,
            totalThroughputMbps = slotResults.map { it.throughputMbps }.average(),
            demandSatisfiedPercent = if (demand <= 0.0) 100.0 else 100.0 * delivered / demand,
            fairness = jain(finalStates.map { it.throughputMbps }), handovers = totalHandover,
            meanBeamGainDb = beamSum / samples.coerceAtLeast(1),
            meanChannelFrequencySelectivityDb = selectivitySum / samples.coerceAtLeast(1),
            meanMimoEffectiveSinrDb = mimoSinrSum / samples.coerceAtLeast(1)
        )
    }
}
