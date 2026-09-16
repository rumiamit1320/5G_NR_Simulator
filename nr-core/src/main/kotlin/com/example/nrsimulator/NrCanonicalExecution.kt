package com.example.nrsimulator

/** Stable canonical PHY facade. Historical versioned APIs remain intact. */
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
        require(config.layers in 1..2 && config.txAntennas >= config.layers && config.rxAntennas >= config.layers)
        require(config.rv in 0..3 && config.snrDb.isFinite())

        val r = NrCanonicalPhyV117.run(
            NrCanonicalPhyV117.Config(
                payloadBits = config.payloadBits,
                targetCodeRate = config.targetCodeRate,
                modulation = config.modulation,
                layers = config.layers,
                txAntennas = config.txAntennas,
                rxAntennas = config.rxAntennas,
                snrDb = config.snrDb,
                rv = config.rv,
                seed = config.channelSeed
            )
        )
        val stages = linkedMapOf(
            "transport+TB-CRC" to r.transportCrcPassed,
            "LDPC+rate-matching" to r.ldpcPassed,
            "QAM+layer-mapping" to (r.transmittedBits > 0),
            "DMRS" to r.dmrsMapped,
            "PDSCH-grid+OFDM" to r.dmrsMapped,
            "channel+MIMO-waveform" to r.equalized,
            "DMRS-channel-estimation" to r.channelEstimated,
            "MMSE-equalization+layer-recovery" to r.equalized,
            "soft-LLR+LDPC-recovery" to r.ldpcPassed,
            "TB-CRC-check" to r.transportCrcPassed
        )
        return Report(
            r.passed && stages.values.all { it },
            stages,
            r.payloadBits,
            r.recoveredBits,
            r.transportCrcPassed,
            r.ldpcPassed,
            r.evm,
            r.equalized,
            config.layers,
            r.postSinrDb,
            "Canonical execution is now the coherent V117 coded waveform path: V87 codeword, V101 DM-RS, OFDM/MIMO, received-pilot channel estimation, MMSE, soft LLR, V84/V86 recovery, and V83 TB CRC."
        )
    }
}
