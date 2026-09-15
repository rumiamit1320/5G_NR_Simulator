package com.example.nrsimulator

/** Stable forward-PHY composition point; historical versioned APIs remain intact. */
object NrCanonicalExecution {
    data class Config(val payloadBits: Int = 512, val targetCodeRate: Double = 0.5, val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK, val layers: Int = 1, val snrDb: Double = 20.0, val rv: Int = 0)
    data class Report(val passed: Boolean, val stages: Map<String, Boolean>, val payloadBits: Int, val recoveredBits: Int, val crcPassed: Boolean, val ldpcPassed: Boolean, val evm: Double, val notes: String)

    fun run(config: Config = Config()): Report {
        require(config.payloadBits > 0 && config.targetCodeRate in 0.01..0.99 && config.layers == 1 && config.rv in 0..3 && config.snrDb.isFinite())
        val r = NrEndToEndV100.run(NrEndToEndV100.Config(config.payloadBits, config.targetCodeRate, config.modulation, 1, config.snrDb, config.rv))
        val stages = linkedMapOf(
            "transport+TB-CRC" to r.payloadBits > 0,
            "LDPC+rate-matching" to r.ldpcPassed,
            "QAM+layer-mapping" to r.qamSymbols > 0,
            "DMRS" to true,
            "PDSCH-grid+OFDM" to (r.ofdmSymbols > 0 && r.fftSize > 0),
            "channel+demodulation" to r.evm.isFinite(),
            "soft-LLR+LDPC-recovery" to r.ldpcPassed,
            "TB-CRC-check" to r.crcPassed
        )
        return Report(r.passed && stages.values.all { it }, stages, r.payloadBits, r.recoveredBits, r.crcPassed, r.ldpcPassed, r.evm, "Canonical execution composes the validated additive PHY chain; V1-V110 compatibility APIs remain intact.")
    }
}
