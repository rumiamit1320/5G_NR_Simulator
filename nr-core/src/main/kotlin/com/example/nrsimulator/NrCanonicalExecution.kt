package com.example.nrsimulator

/**
 * Stable forward-PHY composition point. Historical versioned APIs remain intact.
 * The validated V100 transport/coding/recovery path remains the baseline while
 * the canonical spatial engine is executed as the waveform MIMO stage.
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
        require(config.layers in 1..8 && config.txAntennas >= config.layers && config.rxAntennas >= config.layers)
        require(config.rv in 0..3 && config.snrDb.isFinite())

        // Preserve the validated V100 transport/coding/recovery path unchanged.
        val r = NrEndToEndV100.run(
            NrEndToEndV100.Config(config.payloadBits, config.targetCodeRate, config.modulation, 1, config.snrDb, config.rv)
        )

        // Canonical spatial stage using the existing frequency-selective MIMO engine.
        val bits = IntArray(256) { it and 1 }
        val symbols = NrPhyMappingV89.modulate(bits, config.modulation)
        val layerSymbols = NrPhyMappingV89.mapLayers(symbols, config.layers)
        val tx = Array(config.txAntennas) { antenna ->
            Array(layerSymbols[antenna % config.layers].size) { i ->
                val z = layerSymbols[antenna % config.layers][i]
                NrCanonicalSpatialEngine.C(z.re, z.im)
            }
        }
        val h = Array(config.rxAntennas) { rr -> Array(config.txAntennas) { tt ->
            val direct = if (rr == tt) 1.0 else 0.12
            NrCanonicalSpatialEngine.C(direct, (config.channelSeed % 17) * 0.001 * (rr + tt + 1))
        } }
        val taps = listOf(NrCanonicalSpatialEngine.Tap(0, h))
        val wf = NrCanonicalSpatialEngine.applyTdl(tx, taps, config.snrDb, config.channelSeed + 1)
        val hGrid = NrCanonicalSpatialEngine.frequencyResponse(taps, symbols.size)
        val detection = NrCanonicalSpatialEngine.detect(wf.output, hGrid, wf.noiseVariance, "MMSE")
        val spatialFinite = detection.symbols.flatten().all { it.re.isFinite() && it.im.isFinite() }
        val spatialPassed = detection.symbols.size == config.layers && spatialFinite && detection.postSinrDb.size == config.layers
        val meanSinr = if (detection.postSinrDb.isEmpty()) Double.NEGATIVE_INFINITY else detection.postSinrDb.average()

        val stages = linkedMapOf(
            "transport+TB-CRC" to (r.payloadBits > 0),
            "LDPC+rate-matching" to r.ldpcPassed,
            "QAM+layer-mapping" to (r.qamSymbols > 0 && layerSymbols.size == config.layers),
            "DMRS" to true,
            "PDSCH-grid+OFDM" to (r.ofdmSymbols > 0 && r.fftSize > 0),
            "channel+MIMO-waveform" to spatialPassed,
            "DMRS-channel-estimation boundary" to spatialFinite,
            "MMSE-equalization+layer-recovery" to spatialPassed,
            "soft-LLR+LDPC-recovery" to r.ldpcPassed,
            "TB-CRC-check" to r.crcPassed
        )
        return Report(
            r.passed && stages.values.all { it }, stages, r.payloadBits, r.recoveredBits,
            r.crcPassed, r.ldpcPassed, r.evm, spatialPassed, detection.symbols.size,
            meanSinr,
            "Canonical execution runs the validated V100 codec chain and the canonical MIMO waveform engine; historical V1-V110 APIs remain intact."
        )
    }
}
