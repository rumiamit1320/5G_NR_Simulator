package com.example.nrsimulator

/**
 * V71 additive PHY-level HARQ adapter.
 *
 * V71 is retained as a compatibility layer over V61. It records a confidence
 * metric for diagnostics, but CRC truth always comes from the actual V61
 * transmission. Real symbol-derived LLR combining is provided by V73.
 */
data class NrHarqPhyConfigV71(
    val payloadBits: Int = 128,
    val snrDb: Double = 8.0,
    val modulationOrder: Int = 16,
    val layers: Int = 1,
    val txAntennas: Int = 1,
    val rxAntennas: Int = 1,
    val prbs: Int = 24,
    val scsKHz: Int = 30,
    val codeRate: Double = 0.5,
    val seed: Int = 7101
)

data class NrHarqPhyTransmissionV71(
    val processId: Int,
    val transmissionNumber: Int,
    val rv: Int,
    val snrDb: Double,
    val transmittedBits: Int,
    val crcPass: Boolean,
    val softMetric: Double,
    val combinedSoftMetric: Double
)

data class NrHarqPhyResultV71(
    val transmissions: List<NrHarqPhyTransmissionV71>,
    val combinedSoftMetric: Double,
    val finalCrcPass: Boolean,
    val retransmissionCount: Int
)

object NrHarqPhyIntegrationV71 {
    private fun rvSequence(index: Int): Int = intArrayOf(0, 2, 3, 1)[index.coerceIn(0, 3)]

    /**
     * Execute one HARQ process using the existing V61 link for every RV.
     * The confidence metric is diagnostic only; it is never converted into
     * a fabricated CRC. V73 is the symbol/LLR-based soft-combining path.
     */
    fun run(
        processId: Int = 0,
        maxTransmissions: Int = 4,
        config: NrHarqPhyConfigV71 = NrHarqPhyConfigV71()
    ): NrHarqPhyResultV71 {
        require(processId >= 0)
        require(maxTransmissions in 1..4)
        val tx = ArrayList<NrHarqPhyTransmissionV71>()
        var combined = 0.0
        var finalCrc = false

        for (number in 1..maxTransmissions) {
            val rv = rvSequence(number - 1)
            val link = NrIntegratedLinkV61.run(
                NrIntegratedLinkConfigV61(
                    payloadBits = config.payloadBits,
                    snrDb = config.snrDb,
                    modulationOrder = config.modulationOrder,
                    layers = config.layers,
                    txAntennas = config.txAntennas,
                    rxAntennas = config.rxAntennas,
                    prbs = config.prbs,
                    scsKHz = config.scsKHz,
                    codeRate = config.codeRate,
                    rv = rv,
                    seed = config.seed
                )
            )
            val confidence = (1.0 - link.ber).coerceIn(0.0, 1.0) *
                (1.0 / (1.0 + link.evmPercent / 100.0))
            combined += confidence
            finalCrc = link.crcPass
            tx += NrHarqPhyTransmissionV71(
                processId = processId,
                transmissionNumber = number,
                rv = rv,
                snrDb = config.snrDb,
                transmittedBits = link.transmittedBits,
                crcPass = link.crcPass,
                softMetric = confidence,
                combinedSoftMetric = combined
            )
            if (link.crcPass) break
        }

        return NrHarqPhyResultV71(
            transmissions = tx,
            combinedSoftMetric = combined,
            finalCrcPass = finalCrc,
            retransmissionCount = (tx.size - 1).coerceAtLeast(0)
        )
    }
}
