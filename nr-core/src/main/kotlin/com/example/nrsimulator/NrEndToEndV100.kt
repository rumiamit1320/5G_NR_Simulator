package com.example.nrsimulator

import kotlin.math.sqrt

/** Additive V100 end-to-end NR PHY path with waveform-derived soft LLR and TB CRC verification. */
object NrEndToEndV100 {
    data class Config(val payloadBits: Int = 512, val targetCodeRate: Double = 0.5, val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK, val layers: Int = 1, val snrDb: Double = 80.0, val rv: Int = 0)
    data class Report(val passed: Boolean, val payloadBits: Int, val recoveredBits: Int, val baseGraph: String, val liftingSize: Int, val codeBlocks: Int, val rateMatchedBits: Int, val qamSymbols: Int, val ofdmSymbols: Int, val fftSize: Int, val evm: Double, val snrDb: Double, val crcPassed: Boolean, val ldpcPassed: Boolean, val notes: String)

    fun run(config: Config = Config()): Report {
        require(config.payloadBits > 0 && config.layers in 1..8 && config.rv in 0..3)
        require(config.targetCodeRate in 0.01..0.99)
        val payload = IntArray(config.payloadBits) { (it * 17 + 3) and 1 }
        val selectedBg = NrLdpcV82.selectBaseGraph(config.payloadBits, config.targetCodeRate)
        val table = when (selectedBg) {
            NrLdpcV82.BaseGraph.BG1 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG1)
            NrLdpcV82.BaseGraph.BG2 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG2)
        }
        val encoded = NrCodingChainV87.encode(payload, config.targetCodeRate, table, Int.MAX_VALUE, config.rv)
        require(encoded.rateMatched.isNotEmpty())
        val txBits = encoded.rateMatched.first()
        val usable = txBits.size - (txBits.size % config.modulation.bitsPerSymbol)
        require(usable > 0)
        val modulated = NrPhyMappingV89.modulate(txBits.copyOf(usable), config.modulation)
        val layerMapped = NrPhyMappingV89.mapLayers(modulated, config.layers)
        require(NrDmrsV92.generate(NrDmrsV92.Config(maxOf(12, config.layers * 4), 1, config.layers, intArrayOf(0))).values.isNotEmpty())
        val maxLayerSymbols = layerMapped.maxOf { it.size }
        val fftSize = nextPowerOfTwo(maxOf(16, maxLayerSymbols))
        val grid = Array(config.layers) { layer -> Array(fftSize) { i -> if (i < layerMapped[layer].size) layerMapped[layer][i] else NrPhyMappingV89.Complex(0.0, 0.0) } }
        val waveform = NrOfdmV93.modulate(grid, fftSize, maxOf(1, fftSize / 8))
        val channelInput = Array(waveform.samples.size) { i -> waveform.samples[i].let { NrDmrsMimoV90.Complex(it.re, it.im) } }
        val channel = NrChannelV95.apply(channelInput, listOf(NrChannelV95.Tap(0, NrDmrsMimoV90.Complex(1.0, 0.0))), config.snrDb)
        val receivedWaveform = NrOfdmV93.Waveform(Array(channel.samples.size) { i -> channel.samples[i].let { NrPhyMappingV89.Complex(it.re, it.im) } }, fftSize, waveform.cyclicPrefix, waveform.symbols)
        val receivedGrid = NrOfdmV93.demodulate(receivedWaveform)
        val evm = NrOfdmV93.evm(grid[0], receivedGrid[0])
        val recoveredSymbols = ArrayList<NrPhyMappingV89.Complex>()
        for (i in modulated.indices) { val layer = i % config.layers; val pos = i / config.layers; if (pos < receivedGrid[layer].size) recoveredSymbols += receivedGrid[layer][pos] }
        val llr = softDemodulate(recoveredSymbols.toTypedArray(), config.modulation, channel.noiseVariance.coerceAtLeast(1e-12)).copyOf(usable)
        val recoveredCodewordLlr = NrRateMatchingV84.rateRecover(llr, NrRateMatchingV84.Config(encoded.baseGraph, encoded.liftingSize, config.rv, usable))
        val decoded = NrLdpcCodecV86.decode(recoveredCodewordLlr, table, encoded.liftingSize, encoded.liftingSet, maxIterations = 50)
        val sourceBlock = encoded.codeBlocks.first()
        val decodedTransport = decoded.bits.copyOf(sourceBlock.payload.size)
        val payloadRecovered = decodedTransport.contentEquals(encoded.transportWithCrc)
        val crcPassed = payloadRecovered && NrTransportV83.checkCrc(decodedTransport, NrTransportV83.crcTypeForTransportBlock(config.payloadBits))
        val ldpcPassed = decoded.converged && decoded.syndromeWeight == 0 && payloadRecovered
        return Report(ldpcPassed && crcPassed && evm.isFinite() && receivedGrid.size == config.layers, config.payloadBits, decodedTransport.size, encoded.baseGraph.name, encoded.liftingSize, encoded.codeBlocks.size, usable, modulated.size, waveform.symbols, fftSize, evm, channel.snrDb, crcPassed, ldpcPassed, "Waveform-derived max-log soft LLR and actual TB CRC verification; legacy APIs unchanged.")
    }

    private fun nextPowerOfTwo(n: Int): Int { var p = 1; while (p < n) p = p shl 1; return p }

    private fun softDemodulate(symbols: Array<NrPhyMappingV89.Complex>, modulation: NrPhyMappingV89.Modulation, noiseVariance: Double): DoubleArray {
        val m = modulation.bitsPerSymbol
        val constellation = Array(1 shl m) { value -> NrPhyMappingV89.modulate(IntArray(m) { b -> (value ushr (m - 1 - b)) and 1 }, modulation)[0] }
        val out = DoubleArray(symbols.size * m)
        var at = 0
        symbols.forEach { r ->
            for (bit in 0 until m) {
                var min0 = Double.POSITIVE_INFINITY; var min1 = Double.POSITIVE_INFINITY
                constellation.indices.forEach { index ->
                    val c = constellation[index]; val dr = r.re - c.re; val di = r.im - c.im; val d = dr * dr + di * di
                    if (((index ushr (m - 1 - bit)) and 1) == 0) min0 = minOf(min0, d) else min1 = minOf(min1, d)
                }
                out[at++] = ((min1 - min0) / noiseVariance).coerceIn(-60.0, 60.0)
            }
        }
        return out
    }
}
