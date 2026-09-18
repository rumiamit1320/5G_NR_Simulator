package com.example.nrsimulator

/** V134: connects V87 coded transport blocks to the V133 shared scheduled grid and OFDM waveform. */
object NrCanonicalCodedScheduledPhyV134 {
    data class Config(
        val numerology: NrCanonicalNumerologyV123.Config = NrCanonicalNumerologyV123.Config(),
        val prbCount: Int = 24,
        val ues: List<NrCanonicalSchedulerV126.Ue> = listOf(
            NrCanonicalSchedulerV126.Ue(1, bufferBytes = 256, priority = 2, maxLayers = 1),
            NrCanonicalSchedulerV126.Ue(2, bufferBytes = 256, priority = 1, maxLayers = 1)
        ),
        val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK,
        val targetCodeRate: Double = 0.5,
        val fftSize: Int = 2048,
        val cyclicPrefixSamples: Int = 144,
        val rv: Int = 0,
        val seed: Int = 13401
    )

    data class UeReport(
        val ueId: Int,
        val payloadBits: Int,
        val codedBits: Int,
        val mappedDataRe: Int,
        val mappedDmrsRe: Int,
        val unusedDataRe: Int,
        val baseGraph: NrLdpcV82.BaseGraph,
        val crcPassed: Boolean,
        val ldpcPassed: Boolean
    )

    data class Report(
        val passed: Boolean,
        val grants: List<NrCanonicalSchedulerV126.Grant>,
        val ueReports: List<UeReport>,
        val dataElements: Int,
        val dmrsElements: Int,
        val occupiedElements: Int,
        val waveform: Array<Array<Array<NrCanonicalSpatialEngine.C>>>,
        val waveformSamples: Int,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.prbCount > 0 && config.ues.isNotEmpty())
        require(config.targetCodeRate in 0.01..0.99)
        require(config.fftSize > 0 && (config.fftSize and (config.fftSize - 1)) == 0)
        require(12 * config.prbCount <= config.fftSize)

        val schedule = NrCanonicalSchedulerV126.schedule(
            config.ues,
            NrCanonicalSchedulerV126.Config(
                prbCount = config.prbCount,
                slot = config.numerology.slotIndex
            )
        )
        val layers = schedule.grants.maxOfOrNull { it.layers } ?: 1
        val grid = Array(config.numerology.symbolsPerSlot) {
            Array(layers) { arrayOfNulls<NrCanonicalPhysicalMapperV129.Symbol>(config.fftSize) }
        }
        val owners = Array(config.numerology.symbolsPerSlot) {
            Array(layers) { arrayOfNulls<Int>(config.fftSize) }
        }

        val reports = ArrayList<UeReport>()
        var dataElements = 0
        var dmrsElements = 0
        var collisions = 0

        for ((index, grant) in schedule.grants.withIndex()) {
            val direction = if (grant.direction == NrCanonicalSchedulerV126.Direction.DL)
                NrCanonicalMultiSymbolV124.Direction.DOWNLINK
            else NrCanonicalMultiSymbolV124.Direction.UPLINK
            val plan = NrCanonicalMultiSymbolV124.plan(
                NrCanonicalMultiSymbolV124.Config(
                    slot = config.numerology.slotIndex,
                    prbStart = grant.prbStart,
                    prbCount = grant.prbCount,
                    startSymbol = grant.startSymbol,
                    symbolCount = grant.symbolCount,
                    layers = grant.layers,
                    direction = direction
                )
            )
            val dataCapacityBits = plan.data.size * config.modulation.bitsPerSymbol
            require(dataCapacityBits > 0)
            val payloadBits = minOf(grant.tbsBytes * 8, 256)
            require(payloadBits > 0)
            val payload = IntArray(payloadBits) { (it * 17 + grant.ueId * 7 + config.seed) and 1 }
            val bg = NrLdpcV82.selectBaseGraph(payloadBits, config.targetCodeRate)
            val table = when (bg) {
                NrLdpcV82.BaseGraph.BG1 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG1)
                NrLdpcV82.BaseGraph.BG2 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG2)
            }
            val coded = NrCodingChainV87.encode(
                payload, config.targetCodeRate, table,
                outputBitsPerCodeBlock = (dataCapacityBits / config.modulation.bitsPerSymbol)
                    .times(config.modulation.bitsPerSymbol),
                rv = config.rv
            )
            val allCoded = coded.rateMatched.flatMap { it.asIterable() }.toIntArray()
            val usable = minOf(allCoded.size, dataCapacityBits)
                .let { it - it % config.modulation.bitsPerSymbol }
            val symbols = NrPhyMappingV89.modulate(allCoded.copyOf(usable), config.modulation)
            val mapped = NrCanonicalPhysicalMapperV129.map(plan, config.seed + grant.ueId * 101 + index)

            fun put(re: NrCanonicalMultiSymbolV124.Re, symbol: NrCanonicalPhysicalMapperV129.Symbol) {
                val layer = re.layer
                val k = re.prb * 12 + re.subcarrier
                if (owners[re.symbol][layer][k] != null && owners[re.symbol][layer][k] != grant.ueId) {
                    collisions++
                } else {
                    grid[re.symbol][layer][k] = symbol
                    owners[re.symbol][layer][k] = grant.ueId
                }
            }

            plan.dmrs.forEachIndexed { i, re ->
                put(re, mapped.dmrsSymbols[re] ?: NrCanonicalPhysicalMapperV129.Symbol(
                    if ((i + config.seed) % 2 == 0) 1.0 else -1.0, 0.0
                ))
            }
            val dataRe = plan.data
            for (i in symbols.indices) {
                put(dataRe[i], NrCanonicalPhysicalMapperV129.Symbol(symbols[i].re, symbols[i].im))
            }

            val decodedBits = decodeRoundTrip(
                coded, table, allCoded, usable, config.modulation, config.rv
            )
            val expected = coded.transportWithCrc
            val recovered = decodedBits.copyOf(minOf(expected.size, decodedBits.size))
            val crcPassed = recovered.contentEquals(expected) &&
                NrTransportV83.checkCrc(recovered, NrTransportV83.crcTypeForTransportBlock(payloadBits))
            val ldpcPassed = crcPassed
            reports += UeReport(
                grant.ueId, payloadBits, usable, symbols.size, plan.dmrs.size,
                (dataRe.size - symbols.size).coerceAtLeast(0), bg, crcPassed, ldpcPassed
            )
            dataElements += symbols.size
            dmrsElements += plan.dmrs.size
        }

        val waveform = Array(layers) { Array(config.numerology.symbolsPerSlot) {
            Array(config.fftSize + config.cyclicPrefixSamples) { NrCanonicalSpatialEngine.C(0.0, 0.0) }
        } }
        for (layer in 0 until layers) for (symbol in 0 until config.numerology.symbolsPerSlot) {
            val freq = Array(config.fftSize) { k ->
                grid[symbol][layer][k]?.let { NrCanonicalSpatialEngine.C(it.re, it.im) }
                    ?: NrCanonicalSpatialEngine.C(0.0, 0.0)
            }
            val time = NrCanonicalSpatialEngine.fft(freq, inverse = true)
            for (i in 0 until config.cyclicPrefixSamples) waveform[layer][symbol][i] = time[config.fftSize - config.cyclicPrefixSamples + i]
            for (i in 0 until config.fftSize) waveform[layer][symbol][config.cyclicPrefixSamples + i] = time[i]
        }

        val occupied = dataElements + dmrsElements
        val passed = schedule.grants.isNotEmpty() &&
            reports.size == schedule.grants.size &&
            reports.all { it.crcPassed && it.ldpcPassed } &&
            collisions == 0 &&
            occupied <= schedule.grants.sumOf { grant ->
                NrCanonicalMultiSymbolV124.plan(
                    NrCanonicalMultiSymbolV124.Config(
                        slot = config.numerology.slotIndex,
                        prbStart = grant.prbStart,
                        prbCount = grant.prbCount,
                        startSymbol = grant.startSymbol,
                        symbolCount = grant.symbolCount,
                        layers = grant.layers,
                        direction = if (grant.direction == NrCanonicalSchedulerV126.Direction.DL)
                            NrCanonicalMultiSymbolV124.Direction.DOWNLINK else NrCanonicalMultiSymbolV124.Direction.UPLINK
                    )
                ).allocation.size
            }

        return Report(
            passed, schedule.grants, reports, dataElements, dmrsElements, occupied,
            waveform, config.numerology.symbolsPerSlot * (config.fftSize + config.cyclicPrefixSamples),
            "V134 connects deterministic TB -> V87 CRC/LDPC -> V89 modulation/layer allocation -> V124 scheduled REs -> V133-style shared grid -> OFDM. Round-trip coding checks are noiseless codec regressions; V135 adds channel/noise and true scheduled RX estimation."
        )
    }

    private fun decodeRoundTrip(
        encoded: NrCodingChainV87.Result,
        table: NrLdpcV85.Table,
        allCoded: IntArray,
        usable: Int,
        modulation: NrPhyMappingV89.Modulation,
        rv: Int
    ): IntArray {
        var offsetBits = 0
        val decodedBlocks = ArrayList<IntArray>()
        for (rm in encoded.rateMatched) {
            val n = minOf(rm.size, (usable - offsetBits).coerceAtLeast(0))
            if (n <= 0) break
            val bits = allCoded.copyOfRange(offsetBits, offsetBits + n)
            val llr = DoubleArray(n) { if (bits[it] == 0) 50.0 else -50.0 }
            val recovered = NrRateMatchingV84.rateRecover(
                llr,
                NrRateMatchingV84.Config(encoded.baseGraph, encoded.liftingSize, rv, n)
            )
            decodedBlocks += NrLdpcCodecV86.decode(
                recovered, table, encoded.liftingSize, encoded.liftingSet, maxIterations = 80
            ).bits
            offsetBits += n
        }
        val total = decodedBlocks.sumOf { it.size }
        val out = IntArray(total)
        var at = 0
        for (b in decodedBlocks) {
            b.copyInto(out, at)
            at += b.size
        }
        return out
    }
}
