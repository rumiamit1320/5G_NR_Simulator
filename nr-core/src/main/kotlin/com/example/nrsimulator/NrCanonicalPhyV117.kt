package com.example.nrsimulator

import kotlin.math.sqrt

/**
 * V117 coherent canonical PHY retained as the public implementation surface.
 * V119 adds an optional frequency-selective TDL channel while preserving the
 * existing V117 configuration defaults and report shape.
 */
object NrCanonicalPhyV117 {
    data class Config(
        val payloadBits: Int = 512,
        val targetCodeRate: Double = 0.5,
        val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK,
        val layers: Int = 1,
        val txAntennas: Int = layers,
        val rxAntennas: Int = layers,
        val snrDb: Double = 35.0,
        val rv: Int = 0,
        val fftSize: Int = 2048,
        val resourceBlocks: Int = 8,
        val seed: Int = 27117,
        val tdlProfile: NrCanonicalTdlV118.Profile? = null
    )

    data class Report(
        val passed: Boolean,
        val transportCrcPassed: Boolean,
        val ldpcPassed: Boolean,
        val dmrsMapped: Boolean,
        val channelEstimated: Boolean,
        val equalized: Boolean,
        val payloadBits: Int,
        val recoveredBits: Int,
        val transmittedBits: Int,
        val evm: Double,
        val postSinrDb: Double,
        val channelMse: Double,
        val pilotCount: Int,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.payloadBits > 0 && config.targetCodeRate in 0.01..0.99)
        require(config.layers in 1..2 && config.txAntennas >= config.layers && config.rxAntennas >= config.layers)
        require(config.rv in 0..3 && config.fftSize > 0 && (config.fftSize and (config.fftSize - 1)) == 0)
        require(12 * config.resourceBlocks <= config.fftSize)

        val payload = IntArray(config.payloadBits) { (it * 17 + 3) and 1 }
        val bg = NrLdpcV82.selectBaseGraph(config.payloadBits, config.targetCodeRate)
        val table = when (bg) {
            NrLdpcV82.BaseGraph.BG1 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG1)
            NrLdpcV82.BaseGraph.BG2 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG2)
        }
        val encoded = NrCodingChainV87.encode(payload, config.targetCodeRate, table, Int.MAX_VALUE, config.rv)
        val txBits = encoded.rateMatched.first()
        val usable = txBits.size - txBits.size % config.modulation.bitsPerSymbol
        require(usable > 0)
        val txBitsUsed = txBits.copyOf(usable)
        val qam = NrPhyMappingV89.modulate(txBitsUsed, config.modulation)
        val dmrs = NrDmrsV101.pdsch(
            NrDmrsV101.Config(
                configurationType = NrDmrsV101.ConfigurationType.TYPE1,
                maxLength = NrDmrsV101.MaxLength.LEN1,
                additionalPosition = NrDmrsV101.AdditionalPosition.POS0,
                allocationStartSymbol = 0,
                allocationSymbols = 14,
                slot = 0,
                nId = 231,
                nSCID = 0,
                symbolsPerSlot = 14,
                startSubcarrier = 0,
                resourceBlocks = config.resourceBlocks,
                ports = IntArray(config.layers) { 1000 + 2 * it }
            )
        )
        val ports = IntArray(config.layers) { 1000 + 2 * it }
        val pilots = Array(config.layers) { ArrayList<Pair<Int, NrCanonicalSpatialEngine.C>>() }
        for (r in dmrs.resources) {
            val layer = ports.indexOf(r.port)
            if (layer >= 0 && r.subcarrier in 0 until config.fftSize) pilots[layer].add(r.subcarrier to NrCanonicalSpatialEngine.C(r.value.re, r.value.im))
        }
        val pilotSet = pilots.flatMap { it.map { p -> p.first } }.toSet()
        val dmrsMapped = pilots.all { it.isNotEmpty() }
        val dataPositions = (0 until config.fftSize).filter { it !in pilotSet }
        require(qam.size <= dataPositions.size * config.layers) { "Codeword does not fit configured RE capacity" }

        val txFreq = Array(config.txAntennas) { Array(config.fftSize) { NrCanonicalSpatialEngine.C(0.0, 0.0) } }
        val transmitted = Array(config.layers) { ArrayList<NrCanonicalSpatialEngine.C>() }
        for (i in qam.indices) {
            val layer = i % config.layers
            val pos = i / config.layers
            val k = dataPositions[pos]
            val z = NrCanonicalSpatialEngine.C(qam[i].re, qam[i].im)
            txFreq[layer][k] = z
            transmitted[layer].add(z)
        }
        for (layer in 0 until config.layers) for ((k, x) in pilots[layer]) {
            for (t in 0 until config.txAntennas) txFreq[t][k] = NrCanonicalSpatialEngine.C(0.0, 0.0)
            txFreq[layer][k] = x
        }

        val txTime = Array(config.txAntennas) { NrCanonicalSpatialEngine.fft(txFreq[it], inverse = true) }
        val tdl = config.tdlProfile?.let {
            NrCanonicalTdlV118.build(
                NrCanonicalTdlV118.Config(
                    profile = it,
                    txAntennas = config.txAntennas,
                    rxAntennas = config.rxAntennas,
                    sampleRateHz = 30.72e6,
                    rmsDelayNs = 30.0,
                    dopplerHz = 0.0,
                    seed = config.seed + 118
                )
            )
        }
        val directH = Array(config.rxAntennas) { r -> Array(config.txAntennas) { t ->
            NrCanonicalSpatialEngine.C(if (r == t) 1.0 else 0.08, 0.002 * (r + t + 1))
        } }
        val taps = tdl?.taps ?: listOf(NrCanonicalSpatialEngine.Tap(0, directH))
        val maxDelay = taps.maxOf { it.delay }
        val cp = if (tdl != null) maxDelay else 0
        val txWithCp = Array(config.txAntennas) { t ->
            Array(config.fftSize + cp) { i -> txTime[t][if (i < cp) config.fftSize - cp + i else i - cp] }
        }
        val wf = NrCanonicalSpatialEngine.applyTdl(txWithCp, taps, config.snrDb, config.seed)
        val rxFreq = Array(config.rxAntennas) { r ->
            val useful = wf.output[r].copyOfRange(cp, cp + config.fftSize)
            NrCanonicalSpatialEngine.fft(useful)
        }
        val estimated = estimateFromDmrs(rxFreq, pilots, config.fftSize, config.rxAntennas, config.layers)
        val channelEstimated = estimated.all { it.all { row -> row.all { c -> c.re.isFinite() && c.im.isFinite() } } }
        val detected = NrCanonicalSpatialEngine.detect(rxFreq, estimated, wf.noiseVariance, "MMSE")
        val recoveredSymbols = ArrayList<NrPhyMappingV89.Complex>(qam.size)
        for (i in qam.indices) {
            val layer = i % config.layers
            val pos = i / config.layers
            val k = dataPositions[pos]
            val z = detected.symbols[layer][k]
            recoveredSymbols.add(NrPhyMappingV89.Complex(z.re, z.im))
        }
        val llr = softDemodulate(recoveredSymbols.toTypedArray(), config.modulation, wf.noiseVariance.coerceAtLeast(1e-12)).copyOf(usable)
        val recoveredLlr = NrRateMatchingV84.rateRecover(llr, NrRateMatchingV84.Config(encoded.baseGraph, encoded.liftingSize, config.rv, usable))
        val decoded = NrLdpcCodecV86.decode(recoveredLlr, table, encoded.liftingSize, encoded.liftingSet, maxIterations = 80)
        val expectedTransport = encoded.transportWithCrc
        val decodedTransport = decoded.bits.copyOf(expectedTransport.size.coerceAtMost(decoded.bits.size))
        val transportMatch = decodedTransport.contentEquals(expectedTransport)
        val crcPassed = transportMatch && NrTransportV83.checkCrc(decodedTransport, NrTransportV83.crcTypeForTransportBlock(config.payloadBits))
        val ldpcPassed = decoded.converged && decoded.syndromeWeight == 0 && transportMatch
        var err = 0.0
        var ref = 0.0
        for (i in qam.indices) {
            val a = recoveredSymbols[i]
            val b = qam[i]
            val dr = a.re - b.re; val di = a.im - b.im
            err += dr * dr + di * di
            ref += b.re * b.re + b.im * b.im
        }
        val evm = sqrt(err / ref.coerceAtLeast(1e-18))
        val postSinr = if (detected.postSinrDb.isEmpty()) Double.NEGATIVE_INFINITY else detected.postSinrDb.filter { it.isFinite() }.average()
        var mse = 0.0
        var href = 0.0
        val referenceH = if (tdl != null) NrCanonicalTdlV118.frequencyResponse(
            NrCanonicalTdlV118.Config(profile = config.tdlProfile!!, txAntennas = config.txAntennas, rxAntennas = config.rxAntennas, seed = config.seed + 118), config.fftSize
        ) else Array(config.fftSize) { directH }
        for (k in 0 until config.fftSize) for (r in 0 until config.rxAntennas) for (t in 0 until config.txAntennas) {
            val a = estimated[k][r][t]; val b = referenceH[k][r][t]
            val dr = a.re - b.re; val di = a.im - b.im
            mse += dr * dr + di * di; href += b.abs2()
        }
        mse /= href.coerceAtLeast(1e-18)
        val equalized = detected.symbols.size == config.layers && detected.symbols.all { it.size == config.fftSize } && evm.isFinite()
        val passed = crcPassed && ldpcPassed && dmrsMapped && channelEstimated && equalized
        val note = if (tdl == null) {
            "V117 baseline path: deterministic flat MIMO channel."
        } else {
            "V119 TDL-${config.tdlProfile.name} path: V87 coded transport -> QAM/layers -> V101 DM-RS -> CP/OFDM -> V118 frequency-selective MIMO TDL -> OFDM -> DM-RS LS estimation -> MMSE -> soft LLR -> V84/V86 -> V83 TB CRC."
        }
        return Report(passed, crcPassed, ldpcPassed, dmrsMapped, channelEstimated, equalized, config.payloadBits, decodedTransport.size, usable, evm, postSinr, mse, pilots.sumOf { it.size }, note)
    }

    private fun estimateFromDmrs(received: Array<Array<NrCanonicalSpatialEngine.C>>, pilots: Array<ArrayList<Pair<Int, NrCanonicalSpatialEngine.C>>>, nsc: Int, rx: Int, tx: Int): Array<Array<Array<NrCanonicalSpatialEngine.C>>> {
        val h = Array(nsc) { Array(rx) { Array(tx) { NrCanonicalSpatialEngine.C(0.0, 0.0) } } }
        for (t in 0 until tx) {
            val p = pilots[t].sortedBy { it.first }
            require(p.isNotEmpty())
            for (r in 0 until rx) for (k in 0 until nsc) {
                val exact = p.firstOrNull { it.first == k }
                if (exact != null) h[k][r][t] = received[r][k] / exact.second
                else {
                    val lo = p.lastOrNull { it.first < k } ?: p.first()
                    val hi = p.firstOrNull { it.first > k } ?: p.last()
                    val a = if (hi.first == lo.first) 0.0 else (k - lo.first).toDouble() / (hi.first - lo.first)
                    val hl = received[r][lo.first] / lo.second
                    val hu = received[r][hi.first] / hi.second
                    h[k][r][t] = hl * (1.0 - a) + hu * a
                }
            }
        }
        return h
    }

    private fun softDemodulate(symbols: Array<NrPhyMappingV89.Complex>, modulation: NrPhyMappingV89.Modulation, noiseVariance: Double): DoubleArray {
        val m = modulation.bitsPerSymbol
        val constellation = Array(1 shl m) { value -> NrPhyMappingV89.modulate(IntArray(m) { b -> (value ushr (m - 1 - b)) and 1 }, modulation)[0] }
        val out = DoubleArray(symbols.size * m)
        var at = 0
        for (r in symbols) for (bit in 0 until m) {
            var min0 = Double.POSITIVE_INFINITY; var min1 = Double.POSITIVE_INFINITY
            for (index in constellation.indices) {
                val c = constellation[index]; val dr = r.re - c.re; val di = r.im - c.im
                val d = dr * dr + di * di
                if (((index ushr (m - 1 - bit)) and 1) == 0) min0 = minOf(min0, d) else min1 = minOf(min1, d)
            }
            out[at++] = ((min1 - min0) / noiseVariance).coerceIn(-60.0, 60.0)
        }
        return out
    }
}
