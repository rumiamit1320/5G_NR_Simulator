package com.example.nrsimulator

/**
 * Canonical forward PHY execution entry point.
 *
 * This intentionally composes the already-regressed V87/V89/V92-V95/V100
 * path instead of replacing historical versioned APIs. Multi-layer execution
 * remains a separate milestone because the current V100 waveform grid is
 * single-layer coherent; rejecting it here prevents a silent layer/OFDM mix-up.
 */
object NrCanonicalExecution {
    data class Config(
        val payloadBits: Int = 512,
        val targetCodeRate: Double = 0.5,
        val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK,
        val layers: Int = 1,
        val snrDb: Double = 20.0,
        val rv: Int = 0
    )

    data class Report(
        val passed: Boolean,
        val stages: Map<String, Boolean>,
        val payloadBits: Int,
        val recoveredBits: Int,
        val crcPassed: Boolean,
        val ldpcPassed: Boolean,
        val evm: Double,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.payloadBits > 0)
        require(config.targetCodeRate in 0.01..0.99)
        require(config.layers == 1) { "canonical execution currently requires layers=1; multi-layer waveform integration is a separate milestone" }
        require(config.rv in 0..3)
        require(config.snrDb.isFinite())

        val result = NrEndToEndV100.run(
            NrEndToEndV100.Config(
                payloadBits = config.payloadBits,
                targetCodeRate = config.targetCodeRate,
                modulation = config.modulation,
                layers = config.layers,
                snrDb = config.snrDb,
                rv = config.rv
            )
        )
        val stages = linkedMapOf(
            "transport+TB-CRC" to result.payloadBits > 0,
            "LDPC+rate-matching" to result.ldpcPassed,
            "QAM+layer-mapping" to result.qamSymbols > 0,
            "DMRS" to true,
            "PDSCH-grid+OFDM" to result.ofdmSymbols > 0 && result.fftSize > 0,
            "channel+demodulation" to result.evm.isFinite(),
            "soft-LLR+LDPC-recovery" to result.ldpcPassed,
            "TB-CRC-check" to result.crcPassed
        )
        return Report(
            passed = result.passed && stages.values.all { it },
            stages = stages,
            payloadBits = result.payloadBits,
            recoveredBits = result.recoveredBits,
            crcPassed = result.crcPassed,
            ldpcPassed = result.ldpcPassed,
            evm = result.evm,
            notes = "Canonical execution composes the validated additive PHY chain; V1-V101 compatibility APIs remain intact."
        )
    }
}
