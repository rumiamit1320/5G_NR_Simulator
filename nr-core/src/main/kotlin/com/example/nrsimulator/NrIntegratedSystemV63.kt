package com.example.nrsimulator

/**
 * V63 additive system/link bridge.
 * V62 decides radio conditions and PRB allocation; V61 remains the PHY laboratory.
 */
data class NrIntegratedSystemConfigV63(
    val slots: Int = 20,
    val ueCount: Int = 8,
    val cells: Int = 1,
    val prbs: Int = 52,
    val scsKHz: Int = 30,
    val carrierGHz: Double = 3.5,
    val velocityKmh: Double = 30.0,
    val snrOffsetDb: Double = 0.0,
    val modulationOrder: Int = 16,
    val payloadBitsPerUe: Int = 128,
    val txAntennas: Int = 4,
    val rxAntennas: Int = 4,
    val layers: Int = 1,
    val seed: Int = 6301
)

data class NrIntegratedUeV63(
    val ueId: Int,
    val meanSinrDb: Double,
    val meanCqi: Double,
    val meanMcs: Double,
    val totalAllocatedPrbs: Int,
    val throughputMbps: Double,
    val meanBler: Double,
    val phyCrcPassRate: Double,
    val phyBer: Double
)

data class NrIntegratedSystemResultV63(
    val slots: Int,
    val ueStates: List<NrIntegratedUeV63>,
    val totalThroughputMbps: Double,
    val systemFairness: Double,
    val phyCrcPassRate: Double,
    val phyBer: Double,
    val slotResults: List<NrRadioEnvironmentResultV62>
)

object NrIntegratedSystemV63 {
    private fun clampModulation(mcs: Int): Int = when {
        mcs >= 23 -> 256
        mcs >= 17 -> 64
        mcs >= 10 -> 16
        else -> 4
    }

    fun run(config: NrIntegratedSystemConfigV63 = NrIntegratedSystemConfigV63()): NrIntegratedSystemResultV63 {
        val c = config.copy(
            slots = config.slots.coerceIn(1, 1000),
            ueCount = config.ueCount.coerceIn(1, 64),
            cells = config.cells.coerceIn(1, 7),
            prbs = config.prbs.coerceIn(1, 106),
            payloadBitsPerUe = config.payloadBitsPerUe.coerceIn(32, 4096),
            txAntennas = config.txAntennas.coerceIn(1, 8),
            rxAntennas = config.rxAntennas.coerceIn(1, 8),
            layers = config.layers.coerceIn(1, minOf(config.txAntennas, config.rxAntennas))
        )
        val slotResults = (0 until c.slots).map { slot ->
            NrRadioEnvironmentV62.run(
                NrRadioEnvironmentConfigV62(
                    ueCount = c.ueCount, cells = c.cells, prbs = c.prbs, scsKHz = c.scsKHz,
                    carrierGHz = c.carrierGHz, velocityKmh = c.velocityKmh,
                    txAntennas = c.txAntennas, rxAntennas = c.rxAntennas, layers = c.layers,
                    slotIndex = slot.toLong(), seed = c.seed
                )
            )
        }

        val ueStates = (1..c.ueCount).map { id ->
            val samples = slotResults.mapNotNull { it.ueStates.find { u -> u.ueId == id } }
            var phyPass = 0
            var phyErrors = 0L
            val throughputs = samples.map { it.throughputMbps }
            samples.forEach { u ->
                val modulation = clampModulation(u.mcs)
                val snr = u.sinrDb + c.snrOffsetDb
                val phy = NrIntegratedLinkV61.run(
                    NrIntegratedLinkConfigV61(
                        payloadBits = c.payloadBitsPerUe,
                        snrDb = snr,
                        modulationOrder = modulation,
                        layers = u.rank.coerceIn(1, c.layers),
                        txAntennas = c.txAntennas,
                        rxAntennas = c.rxAntennas,
                        seed = c.seed + id * 1009 + (u.ueId * 17)
                    )
                )
                if (phy.crcPass) phyPass++
                phyErrors += phy.bitErrors.toLong()
            }
            NrIntegratedUeV63(
                ueId = id,
                meanSinrDb = samples.map { it.sinrDb }.average(),
                meanCqi = samples.map { it.cqi }.average(),
                meanMcs = samples.map { it.mcs }.average(),
                totalAllocatedPrbs = samples.sumOf { it.allocatedPrbs },
                throughputMbps = throughputs.sum() / c.slots,
                meanBler = samples.map { it.bler }.average(),
                phyCrcPassRate = phyPass.toDouble() / samples.size,
                phyBer = phyErrors.toDouble() / (samples.size * c.payloadBitsPerUe)
            )
        }

        val t = ueStates.map { it.throughputMbps }
        val sum = t.sum()
        val sq = t.sumOf { it * it }
        val fairness = if (sq == 0.0) 0.0 else sum * sum / (t.size * sq)
        val crc = ueStates.map { it.phyCrcPassRate }.average()
        val ber = ueStates.map { it.phyBer }.average()
        return NrIntegratedSystemResultV63(c.slots, ueStates, sum, fairness, crc, ber, slotResults)
    }
}
