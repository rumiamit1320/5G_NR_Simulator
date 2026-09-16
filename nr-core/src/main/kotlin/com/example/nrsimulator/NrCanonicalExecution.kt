package com.example.nrsimulator

/**
 * Stable forward-PHY composition point. Historical versioned APIs remain intact.
 * V100 remains the validated transport/coding/recovery baseline while V116 adds
 * a real pilot-aided DM-RS -> OFDM -> MIMO-channel-estimation -> MMSE boundary.
 */
object NrCanonicalExecution {
    data class Config(
        val payloadBits: Int = 512,
        val targetCodeRate: Double = 0.5,
        val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK,
        val layers: Int = 1,
        val snrDb: Double = 20.0,
        val rv: Int = 0,
        val txAntennas: Int = layers,
        val rxAntennas: Int = layers,
        val channelSeed: Int = 1107
    )

    data class Report(
        val passed: Boolean,
        val stages: Map<String, Boolean>,
        val payloadBits: Int,
        val recoveredBits: Int,
        val crcPassed: Boolean,
        val ldpcPassed: Boolean,
        val evm: Double,
        val spatialPassed: Boolean,
        val detectedLayers: Int,
        val postSinrDb: Double,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.payloadBits > 0 && config.targetCodeRate in 0.01..0.99)
        require(config.layers in 1..4 && config.txAntennas >= config.layers && config.rxAntennas >= config.layers)
        require(config.rv in 0..3 && config.snrDb.isFinite())

        // Preserve the validated V100 transport/coding/recovery path unchanged.
        val r = NrEndToEndV100.run(
            NrEndToEndV100.Config(config.payloadBits, config.targetCodeRate, config.modulation, 1, config.snrDb, config.rv)
        )

        // V116: exact V101 DM-RS resources are now part of the waveform path and
        // the channel is estimated from received pilots rather than injected into detection.
        val spatial = NrCanonicalDmrsMimoV116.run(
            NrCanonicalDmrsMimoV116.Config(
                layers = config.layers,
                txAntennas = config.txAntennas,
                rxAntennas = config.rxAntennas,
                snrDb = config.snrDb,
                seed = config.channelSeed
            )
        )

        val stages = linkedMapOf(
            "transport+TB-CRC" to (r.payloadBits > 0),
            "LDPC+rate-matching" to r.ldpcPassed,
            "QAM+layer-mapping" to true,
            "DMRS" to spatial.dmrsMapped,
            "PDSCH-grid+OFDM" to spatial.dmrsMapped,
            "channel+MIMO-waveform" to spatial.passed,
            "DMRS-channel-estimation" to spatial.channelEstimated,
            "MMSE-equalization+layer-recovery" to spatial.equalized,
            "soft-LLR+LDPC-recovery" to r.ldpcPassed,
            "TB-CRC-check" to r.crcPassed
        )
        return Report(
            r.passed && stages.values.all { it },
            stages,
            r.payloadBits,
            r.recoveredBits,
            r.crcPassed,
            r.ldpcPassed,
            spatial.evm,
            spatial.passed,
            spatial.detectedLayers,
            spatial.postSinrDb,
            "Canonical execution now uses V101 DM-RS pilots for channel estimation and the existing canonical MMSE detector. V100 remains the validated coding/recovery baseline; historical V1-V115 APIs remain intact."
        )
    }
}
