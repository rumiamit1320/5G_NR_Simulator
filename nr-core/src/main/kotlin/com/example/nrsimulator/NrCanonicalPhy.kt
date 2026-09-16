package com.example.nrsimulator

/** Canonical additive NR PHY facade; historical versioned APIs remain intact. */
object NrCanonicalPhy {
    data class Config(val subcarriers: Int = 12, val symbols: Int = 14, val layers: Int = 2, val modulation: String = "QPSK", val snrDb: Double = 20.0, val cyclicPrefix: Int = 2)
    data class Stage<T>(val name: String, val value: T)

    fun components(): List<String> = listOf(
        "canonical execution -> NrCanonicalExecution",
        "transport+CRC+segmentation -> NrCodingChainV87/NrTransportV83",
        "LDPC -> NrLdpcV85/NrLdpcCodecV86",
        "rate matching -> NrCodingChainV87",
        "QAM+layer mapping -> NrPhyMappingV89",
        "DMRS -> NrDmrsV101/NrDmrsV92",
        "PDSCH/PUSCH resource mapping -> NrPdschPuschV91",
        "OFDM -> NrOfdmV93/NrV102V110Additive",
        "MIMO -> NrCanonicalMimoV111V115",
        "channel -> NrCanonicalMimoV111V115/NrChannelV95",
        "soft QAM LLR -> NrEndToEndV100",
        "rate recovery + LDPC decode + TB CRC -> NrRateMatchingV84/NrLdpcCodecV86/NrTransportV83",
        "link adaptation/CSI -> NrLinkAdaptationV96",
        "HARQ -> NrHarqCsiV97",
        "PRACH -> NrRachV98",
        "reference vectors/regression -> NrRefVectorsV99/NrResearchGradeV100"
    )

    fun validate(config: Config): List<String> {
        require(config.subcarriers > 0 && config.symbols > 0 && config.layers in 1..8 && config.cyclicPrefix >= 0)
        require(config.modulation.uppercase() in setOf("BPSK", "QPSK", "16-QAM", "64-QAM", "256-QAM"))
        return components()
    }

    fun execute(config: NrCanonicalExecution.Config = NrCanonicalExecution.Config()): NrCanonicalExecution.Report = NrCanonicalExecution.run(config)
}
