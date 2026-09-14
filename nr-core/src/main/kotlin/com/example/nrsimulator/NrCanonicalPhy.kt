package com.example.nrsimulator

/**
 * Canonical additive NR PHY facade.
 *
 * This layer does not replace or delete V1-V100 implementations. It provides
 * one stable composition point for the forward PHY path while the historical
 * versioned APIs remain available for compatibility and regression coverage.
 */
object NrCanonicalPhy {
    data class Config(
        val subcarriers: Int = 12,
        val symbols: Int = 14,
        val layers: Int = 2,
        val modulation: String = "QPSK",
        val snrDb: Double = 20.0,
        val cyclicPrefix: Int = 2
    )

    data class Stage<T>(val name: String, val value: T)

    /**
     * Canonical component registry. Keeping this explicit makes the intended
     * ownership of each stage visible without changing existing implementations.
     */
    fun components(): List<String> = listOf(
        "transport+CRC+segmentation -> NrCodingChainV87/NrTransportV83",
        "LDPC -> NrLdpcV85/NrLdpcCodecV86",
        "rate matching -> NrCodingChainV87",
        "QAM+scrambling+layer mapping -> NrPhyMappingV89",
        "DMRS+channel estimation+scalar MIMO -> NrDmrsMimoV90",
        "PDSCH/PUSCH resource mapping -> NrPdschPuschV91",
        "DMRS resource/sequence primitives -> NrDmrsV92",
        "OFDM -> NrOfdmV93",
        "MIMO -> NrMimoV94",
        "channel -> NrChannelV95",
        "link adaptation/CSI -> NrLinkAdaptationV96",
        "HARQ -> NrHarqCsiV97",
        "PRACH -> NrRachV98",
        "reference vectors/regression -> NrRefVectorsV99/NrResearchGradeV100"
    )

    fun validate(config: Config): List<String> {
        require(config.subcarriers > 0) { "subcarriers must be > 0" }
        require(config.symbols > 0) { "symbols must be > 0" }
        require(config.layers in 1..8) { "layers must be 1..8" }
        require(config.cyclicPrefix >= 0) { "cyclicPrefix must be >= 0" }
        require(config.modulation.uppercase() in setOf("BPSK", "QPSK", "16-QAM", "64-QAM", "256-QAM")) {
            "unsupported modulation: ${config.modulation}"
        }
        return components()
    }
}
