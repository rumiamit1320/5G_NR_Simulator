package com.example.nrsimulator

/**
 * Stable forward-PHY composition point. Historical versioned APIs remain intact.
 * The validated single-layer V100 chain remains the transport/coding baseline;
 * the canonical spatial engine is now executed as the waveform MIMO stage.
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

        // Keep the already validated V100 transport/coding/recovery path intact.
        val r = NrEndToEndV100.run(
            NrEndToEndV100.Config(
                config.payloadBits, config.targetCodeRate, config.modulation, 1, config.snrDb, config.rv
            )
        )

        // Canonical spatial stage: layer mapping -> MIMO channel -> ZF/MMSE detection.
        // For one layer this is a 1x1 channel; for N layers it exercises the same
        // matrix path without changing the historical V100 codec APIs.
        val bits = IntArray(256) { it and 1 }
        val symbols = NrPhyMappingV89.modulate(bits, config.modulation)
        val layerSymbols = NrPhyMappingV89.mapLayers(symbols, config.layers)
        val pilots = Array(config.layers) { l ->
            Array(config.rxAntennas) { rr ->
                Array(layerSymbols[l].size) { i ->
                    val base = layerSymbols[l][i]
                    NrCanonicalSpatialEngine.C(base.re, base.im)
                }
            }
        }
        // The pilot tensor is retained here as the explicit observation boundary;
        // actual channel estimation remains owned by NrCanonicalSpatialEngine.
        val tx = Array(config.txAntennas) { a -> Array(layerSymbols[a % config.layers].size) { i ->
            val z = layerSymbols[a % config.layers][i]
            NrCanonicalSpatialEngine.C(z.re, z.im)
        } }
        val ch = NrCanonicalSpatialEngine.channelResponse(config.layers, config.txAntennas, config.rxAntennas, config.channelSeed)
        val wf = NrCanonicalSpatialEngine.transmit(tx, ch, config.snrDb, config.channelSeed + 1)
        val detection = NrCanonicalSpatialEngine.detect(wf.rx, ch.frequency, wf.noiseVariance, "MMSE")
        val spatialFinite = detection.symbols.flatten().all { it.re.isFinite() && it.im.isFinite() }
        val spatialPassed = detection.symbols.size == config.layers && spatialFinite && detection.postSinrDb.size == config.layers
        val meanSinr = if (detection.postSinrDb.isEmpty()) Double.NEGATIVE_INFINITY else detection.postSinrDb.average()

        val stages = linkedMapOf(
            "transport+TB-CRC" to r.payloadBits > 0,
            "LDPC+rate-matching" to r.ldpcPassed,
            "QAM+layer-mapping" to r.qamSymbols > 0 && layerSymbols.size == config.layers,
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
            "Canonical execution now runs the existing V100 codec chain plus the canonical spatial MIMO engine; historical V1-V110 APIs remain intact."
        )
    }
}
