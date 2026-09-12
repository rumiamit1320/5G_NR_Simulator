package com.example.nrsimulator

import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.min

/**
 * V66 additive integration/orchestration layer.
 *
 * Existing V1-V65 modules remain intact. V66 makes the previously separate
 * reference layers participate in one deterministic execution path:
 *
 * V65 topology/traffic -> V27 association/handover -> V28 beam selection
 * -> V16 channel characterization + V15 MIMO characterization -> V62 radio
 * conditions -> V64 closed loop PHY/scheduler/HARQ -> network KPIs.
 *
 * The V16/V15/V28 outputs are fused as an additive link-quality correction,
 * not as a second independent channel/noise application. This avoids double
 * applying fading or AWGN while allowing the richer existing models to affect
 * the V64 transport transaction.
 *
 * This remains a simulator/reference integration layer, not a claim of full
 * 3GPP 38.901/38.214/38.331 conformance.
 */
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

object NrIntegratedNetworkV66 {
    private fun jain(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        val sum = values.sum()
        val sq = values.sumOf { it * it }
        return if (sq <= 0.0) 0.0 else sum * sum / (values.size * sq)
    }

    private fun angleDeg(x: Double, y: Double, cell: NrNetworkCellV65): Double =
        Math.toDegrees(atan2(y - cell.yM, x - cell.xM))

    /**
     * Convert the richer reference outputs into one bounded additive SINR
     * correction. V62 remains the source of baseline propagation/interference;
     * V16 is used as a frequency-selectivity penalty and V15 as MIMO quality.
     */
    private fun integrateLinkQuality(
        beamGainDb: Double,
        channelFrequencySelectivityDb: Double,
        mimoEffectiveSinrDb: Double,
        baselineSinrDb: Double
    ): Double {
        val beamContribution = (beamGainDb - 6.0).coerceIn(-6.0, 8.0)
        val selectivityPenalty = (-0.12 * channelFrequencySelectivityDb).coerceIn(-6.0, 0.0)
        val mimoContribution = (mimoEffectiveSinrDb - baselineSinrDb).coerceIn(-6.0, 6.0)
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

        // V65 owns the network topology, UE trajectories and traffic accounting.
        // Running it here deliberately preserves its established network API and
        // gives V66 the same deterministic topology/association reference.
        val network = NrNetworkSimulationV65.run(
            NrNetworkSimulationConfigV65(
                slots = c.slots,
                ueCount = c.ueCount,
                cells = c.cells,
                prbs = c.prbs,
                scsKHz = c.scsKHz,
                carrierGHz = c.carrierGHz,
                cellRadiusM = c.cellRadiusM,
                velocityKmh = c.velocityKmh,
                txPowerDbm = c.txPowerDbm,
                handoverHysteresisDb = c.handoverHysteresisDb,
                trafficDemandMbps = c.trafficDemandMbps,
                txAntennas = c.txAntennas,
                rxAntennas = c.rxAntennas,
                layers = c.layers,
                seed = c.seed
            )
        )

        val mobility = NrMobilityV27()
        val beam = NrBeamV28()
        val slotResults = ArrayList<NrIntegratedSlotV66>(c.slots)
        var totalHandover = 0
        var last = emptyMap<Int, NrIntegratedUeV66>()
        val allFinal = ArrayList<NrIntegratedUeV66>()
        var beamSum = 0.0
        var selectivitySum = 0.0
        var mimoSinrSum = 0.0
        var qualitySamples = 0

        for (slot in 0 until c.slots) {
            val topologyStates = network.slotResults[slot].ueStates
            val v27Cells = network.cells.map { NrCellV27(it.cellId, it.xM, it.yM, it.txPowerDbm) }
            val mobilityResults = topologyStates.map { state ->
                mobility.evaluate(state.xM, state.yM, v27Cells, c.handoverHysteresisDb)
            }

            // V15/V16 are link/reference models. Their deterministic seeds are
            // decorrelated by slot and UE while retaining reproducibility.
            val offsets = HashMap<Int, Double>()
            val enriched = topologyStates.mapIndexed { index, state ->
                val cell = network.cells.first { it.cellId == state.servingCellId }
                val az = angleDeg(state.xM, state.yM, cell)
                val b = beam.sweep(az, c.txAntennas, c.rxAntennas.coerceAtLeast(1), c.beamCount)
                val ch = NrChannelV16(
                    NrChannelV16Config(
                        model = c.channelModel,
                        txAntennas = c.txAntennas,
                        rxAntennas = c.rxAntennas,
                        scsKHz = c.scsKHz,
                        prbs = c.prbs,
                        carrierGHz = c.carrierGHz,
                        velocityKmh = c.velocityKmh,
                        spatialCorrelation = c.spatialCorrelation,
                        timeIndex = slot,
                        seed = c.seed xor (slot * 1009 + state.ueId * 37)
                    )
                ).summary()
                val mimo = NrMimoV15().run(
                    NrMimoV15Config(
                        txAntennas = c.txAntennas,
                        rxAntennas = c.rxAntennas,
                        layers = c.layers,
                        prbs = c.prbs,
                        snrDb = state.throughputMbps.coerceIn(-5.0, 40.0),
                        channelModel = "FREQUENCY_SELECTIVE",
                        seed = c.seed xor (slot * 2017 + state.ueId * 53)
                    )
                )
                val baseline = if (network.slotResults[slot].ueStates.isNotEmpty()) {
                    // V65 does not expose SINR, so use its RSRP as a stable network
                    // reference and let the V64/V62 engine remain authoritative for
                    // actual baseline SINR and interference.
                    (state.rsrpDbm + 100.0).coerceIn(-5.0, 40.0)
                } else 0.0
                val offset = integrateLinkQuality(b.gainDb, ch.frequencySelectivityDb, mimo.effectiveSinrDb, baseline)
                offsets[state.ueId] = offset
                beamSum += b.gainDb
                selectivitySum += ch.frequencySelectivityDb
                mimoSinrSum += mimo.effectiveSinrDb
                qualitySamples++
                Triple(state, b, ch)
            }

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
                    seed = c.seed + slot * 7919,
                    externalSinrOffsetDbByUe = offsets
                )
            )

            val states = enriched.map { (state, b, ch) ->
                val radio = closed.ueStates.first { it.ueId == state.ueId }
                val v27 = mobilityResults.first { it.source == state.servingCellId }
                val handover = if (slot == 0) false else state.servingCellId != last[state.ueId]?.servingCellId
                NrIntegratedUeV66(
                    ueId = state.ueId,
                    servingCellId = state.servingCellId,
                    xM = state.xM,
                    yM = state.yM,
                    rsrpDbm = state.rsrpDbm,
                    beam = b.beam,
                    beamGainDb = b.gainDb,
                    channelFrequencySelectivityDb = ch.frequencySelectivityDb,
                    mimoEffectiveSinrDb = NrMimoV15().run(
                        NrMimoV15Config(
                            txAntennas = c.txAntennas,
                            rxAntennas = c.rxAntennas,
                            layers = c.layers,
                            prbs = c.prbs,
                            snrDb = state.throughputMbps.coerceIn(-5.0, 40.0),
                            channelModel = "FREQUENCY_SELECTIVE",
                            seed = c.seed xor (slot * 2017 + state.ueId * 53)
                        )
                    ).effectiveSinrDb,
                    integratedSinrOffsetDb = offsets.getValue(state.ueId),
                    sinrDb = radio.sinrDb,
                    cqi = radio.cqi,
                    mcs = radio.mcs,
                    allocatedPrbs = radio.allocatedPrbs,
                    throughputMbps = min(radio.throughputMbps, c.trafficDemandMbps),
                    crcPass = radio.crcPass,
                    handover = handover && v27.triggered
                )
            }

            val cellPrbs = IntArray(c.cells)
            states.forEach { s -> cellPrbs[s.servingCellId - 1] += s.allocatedPrbs }
            val load = network.cells.associate { it.cellId to 100.0 * cellPrbs[it.cellId - 1] / c.prbs }
            val handovers = states.count { it.handover }
            totalHandover += handovers
            slotResults += NrIntegratedSlotV66(slot, states, states.sumOf { it.throughputMbps }, handovers, load)
            last = states.associateBy { it.ueId }
            allFinal.clear(); allFinal.addAll(states)
        }

        val delivered = allFinal.sumOf { it.throughputMbps }
        val demand = allFinal.sumOf { c.trafficDemandMbps }
        return NrIntegratedNetworkResultV66(
            cells = network.cells,
            ueStates = allFinal.toList(),
            slotResults = slotResults,
            totalThroughputMbps = slotResults.map { it.throughputMbps }.average(),
            demandSatisfiedPercent = if (demand <= 0.0) 100.0 else 100.0 * delivered / demand,
            fairness = jain(allFinal.map { it.throughputMbps }),
            handovers = totalHandover,
            meanBeamGainDb = beamSum / qualitySamples.coerceAtLeast(1),
            meanChannelFrequencySelectivityDb = selectivitySum / qualitySamples.coerceAtLeast(1),
            meanMimoEffectiveSinrDb = mimoSinrSum / qualitySamples.coerceAtLeast(1)
        )
    }
}
