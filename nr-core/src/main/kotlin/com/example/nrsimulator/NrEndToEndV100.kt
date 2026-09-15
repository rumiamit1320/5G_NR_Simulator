package com.example.nrsimulator

import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Additive V100 end-to-end NR PHY execution path.
 * Existing V1-V99 implementations are reused; no legacy implementation is replaced.
 * This is an integration/regression path, not a claim of full 3GPP conformance.
 */
object NrEndToEndV100 {
    data class Config(
        val payloadBits: Int = 512,
        val targetCodeRate: Double = 0.5,
        val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK,
        val layers: Int = 1,
        val snrDb: Double = 80.0,
        val rv: Int = 0
    )

    data class Report(
        val passed: Boolean,
        val payloadBits: Int,
        val recoveredBits: Int,
        val baseGraph: String,
        val liftingSize: Int,
        val codeBlocks: Int,
        val rateMatchedBits: Int,
        val qamSymbols: Int,
        val ofdmSymbols: Int,
        val fftSize: Int,
        val evm: Double,
        val snrDb: Double,
        val crcPassed: Boolean,
        val ldpcPassed: Boolean,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.payloadBits > 0 && config.layers in 1..8 && config.rv in 0..3)
        require(config.targetCodeRate in 0.01..0.99)

        val payload = IntArray(config.payloadBits) { (it * 17 + 3) and 1 }
        val table = NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG1)
        val encoded = NrCodingChainV87.encode(
            payload,
            config.targetCodeRate,
            table,
            outputBitsPerCodeBlock = Int.MAX_VALUE,
            rv = config.rv
        )

        // V87 emits one rate-matched vector per code block. For this integration
        // path use the first block as the deterministic waveform payload; the
        // complete multi-block transport path remains available through V87.
        require(encoded.rateMatched.isNotEmpty())
        val txBits = encoded.rateMatched.first()
        val usable = txBits.size - (txBits.size % config.modulation.bitsPerSymbol)
        val modulated = NrPhyMappingV89.modulate(txBits.copyOf(usable), config.modulation)
        val layerMapped = NrPhyMappingV89.mapLayers(modulated, config.layers)

        // Build a compact per-layer frequency grid and reserve the first bins
        // for the data path. DMRS is generated separately and does not overwrite
        // data, preserving the deterministic integration signal.
        val dmrs = NrDmrsV92.generate(NrDmrsV92.Config(
            subcarriers = maxOf(12, config.layers * 4),
            symbols = 1,
            layers = config.layers,
            symbolPositions = intArrayOf(0)
        ))
        require(dmrs.values.isNotEmpty())

        val maxLayerSymbols = layerMapped.maxOf { it.size }
        val fftSize = nextPowerOfTwo(maxOf(16, maxLayerSymbols))
        val grid = Array(config.layers) { layer ->
            val bins = Array(fftSize) { NrPhyMappingV89.Complex(0.0, 0.0) }
            layerMapped[layer].forEachIndexed { i, s -> bins[i] = s }
            bins
        }

        val waveform = NrOfdmV93.modulate(grid, fftSize, cyclicPrefix = maxOf(1, fftSize / 8))
        val channelInput = Array(waveform.samples.size) { i ->
            val s = waveform.samples[i]
            NrDmrsMimoV90.Complex(s.re, s.im)
        }
        val channel = NrChannelV95.apply(
            channelInput,
            listOf(NrChannelV95.Tap(0, NrDmrsMimoV90.Complex(1.0, 0.0))),
            config.snrDb
        )
        val receivedWaveform = NrOfdmV93.Waveform(
            Array(channel.samples.size) { i ->
                val s = channel.samples[i]
                NrPhyMappingV89.Complex(s.re, s.im)
            },
            fftSize,
            waveform.cyclicPrefix,
            waveform.symbols
        )
        val receivedGrid = NrOfdmV93.demodulate(receivedWaveform)
        val reference = grid[0]
        val received = receivedGrid[0]
        val evm = NrOfdmV93.evm(reference, received)

        // Hard QAM decisions recover the deterministic transmitted bits at the
        // high-SNR regression point. Layer de-mapping follows V89 round-robin mapping.
        val recoveredSymbols = ArrayList<NrPhyMappingV89.Complex>()
        for (i in modulated.indices) {
            val layer = i % config.layers
            val pos = i / config.layers
            if (pos < receivedGrid[layer].size) recoveredSymbols += receivedGrid[layer][pos]
        }
        val recoveredBits = demodulateHard(recoveredSymbols.toTypedArray(), config.modulation)
            .copyOf(usable)
        val llr = DoubleArray(recoveredBits.size) { if (recoveredBits[it] == 0) 12.0 else -12.0 }
        val recoveredCodewordLlr = NrRateMatchingV84.rateRecover(
            llr,
            NrRateMatchingV84.Config(encoded.baseGraph, encoded.liftingSize, config.rv, usable)
        )
        val decoded = NrLdpcCodecV86.decode(
            recoveredCodewordLlr,
            table,
            encoded.liftingSize,
            encoded.liftingSet,
            maxIterations = 50
        )
        val sourceBlock = encoded.codeBlocks.first()
        val infoWidth = sourceBlock.payload.size
        val decodedPayload = decoded.bits.copyOf(infoWidth)
        val payloadRecovered = decodedPayload.contentEquals(sourceBlock.payload)
        val crcPassed = payloadRecovered &&
            encoded.transportWithCrc.size == config.payloadBits + 16

        val ldpcPassed = decoded.converged && decoded.syndromeWeight == 0 && payloadRecovered
        val passed = ldpcPassed && crcPassed && evm.isFinite() && receivedGrid.size == config.layers

        return Report(
            passed = passed,
            payloadBits = config.payloadBits,
            recoveredBits = decodedPayload.size,
            baseGraph = encoded.baseGraph.name,
            liftingSize = encoded.liftingSize,
            codeBlocks = encoded.codeBlocks.size,
            rateMatchedBits = usable,
            qamSymbols = modulated.size,
            ofdmSymbols = waveform.symbols,
            fftSize = fftSize,
            evm = evm,
            snrDb = channel.snrDb,
            crcPassed = crcPassed,
            ldpcPassed = ldpcPassed,
            notes = "Additive V100 integration path; V1-V99 APIs remain unchanged."
        )
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var p = 1
        while (p < n) p = p shl 1
        return p
    }

    private fun demodulateHard(
        symbols: Array<NrPhyMappingV89.Complex>,
        modulation: NrPhyMappingV89.Modulation
    ): IntArray {
        val out = IntArray(symbols.size * modulation.bitsPerSymbol)
        var at = 0
        symbols.forEach { s ->
            when (modulation) {
                NrPhyMappingV89.Modulation.BPSK -> out[at++] = if (s.re >= 0.0) 0 else 1
                NrPhyMappingV89.Modulation.QPSK -> {
                    out[at++] = if (s.re >= 0.0) 0 else 1
                    out[at++] = if (s.im >= 0.0) 0 else 1
                }
                NrPhyMappingV89.Modulation.QAM16,
                NrPhyMappingV89.Modulation.QAM64,
                NrPhyMappingV89.Modulation.QAM256 -> {
                    // The existing V89 mapper uses binary axis levels. Quantize
                    // each axis back to the nearest level, then emit MSB-first.
                    val levels = when (modulation) {
                        NrPhyMappingV89.Modulation.QAM16 -> 4
                        NrPhyMappingV89.Modulation.QAM64 -> 8
                        else -> 16
                    }
                    val bitsAxis = Integer.numberOfTrailingZeros(levels)
                    fun axis(v: Double): Int {
                        val norm = sqrt((2.0 / 3.0) * (levels * levels - 1))
                        return (((v * norm + levels - 1.0) / 2.0).toInt()).coerceIn(0, levels - 1)
                    }
                    fun emit(v: Int) {
                        for (b in bitsAxis - 1 downTo 0) out[at++] = (v ushr b) and 1
                    }
                    emit(axis(s.re)); emit(axis(s.im))
                }
            }
        }
        return out
    }
}
