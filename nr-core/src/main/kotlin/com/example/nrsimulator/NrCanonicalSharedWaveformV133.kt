package com.example.nrsimulator

/** V133: materializes V126/V128 scheduled allocations into one shared OFDM slot waveform. */
object NrCanonicalSharedWaveformV133 {
    data class Config(
        val numerology: NrCanonicalNumerologyV123.Config = NrCanonicalNumerologyV123.Config(),
        val prbCount: Int = 24,
        val ues: List<NrCanonicalSchedulerV126.Ue> = listOf(
            NrCanonicalSchedulerV126.Ue(1, bufferBytes = 2048, priority = 2, maxLayers = 1),
            NrCanonicalSchedulerV126.Ue(2, bufferBytes = 2048, priority = 1, maxLayers = 1)
        ),
        val fftSize: Int = 2048,
        val cyclicPrefixSamples: Int = 144,
        val seed: Int = 13301
    )

    data class Placement(
        val ueId: Int,
        val prbStart: Int,
        val prbCount: Int,
        val layers: Int,
        val dataRe: Int,
        val dmrsRe: Int
    )

    data class Report(
        val passed: Boolean,
        val grants: List<NrCanonicalSchedulerV126.Grant>,
        val placements: List<Placement>,
        val dataElements: Int,
        val dmrsElements: Int,
        val occupiedElements: Int,
        val collisionCount: Int,
        val fftSize: Int,
        val ofdmSymbols: Int,
        val waveformSamples: Int,
        val waveform: Array<Array<Array<NrCanonicalSpatialEngine.C>>>,
        val frequencyGrid: Array<Array<Array<NrCanonicalPhysicalMapperV129.Symbol?>>>,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.prbCount > 0 && config.ues.isNotEmpty())
        require(config.fftSize > 0 && (config.fftSize and (config.fftSize - 1)) == 0)
        require(12 * config.prbCount <= config.fftSize)
        require(config.cyclicPrefixSamples in 0 until config.fftSize)

        val schedule = NrCanonicalSchedulerV126.schedule(
            config.ues,
            NrCanonicalSchedulerV126.Config(
                prbCount = config.prbCount,
                slot = config.numerology.slotIndex
            )
        )

        val layers = schedule.grants.maxOfOrNull { it.layers } ?: 1
        val grid = Array(config.numerology.symbolsPerSlot) {
            Array(layers) {
                arrayOfNulls<NrCanonicalPhysicalMapperV129.Symbol>(config.fftSize)
            }
        }
        val owners = Array(config.numerology.symbolsPerSlot) {
            Array(layers) { arrayOfNulls<Int>(config.fftSize) }
        }
        var collisions = 0
        var dataCount = 0
        var dmrsCount = 0
        val placements = ArrayList<Placement>()

        for ((index, grant) in schedule.grants.withIndex()) {
            val direction = if (grant.direction == NrCanonicalSchedulerV126.Direction.DL)
                NrCanonicalMultiSymbolV124.Direction.DOWNLINK
            else NrCanonicalMultiSymbolV124.Direction.UPLINK
            val plan = NrCanonicalMultiSymbolV124.plan(
                NrCanonicalMultiSymbolV124.Config(
                    slot = config.numerology.slotIndex,
                    prbStart = grant.prbStart,
                    prbCount = grant.prbCount,
                    layers = grant.layers,
                    direction = direction
                )
            )
            val mapped = NrCanonicalPhysicalMapperV129.map(plan, config.seed + grant.ueId * 101 + index)

            fun put(
                re: NrCanonicalMultiSymbolV124.Re,
                symbol: NrCanonicalPhysicalMapperV129.Symbol
            ) {
                val layer = re.layer
                require(layer in 0 until layers)
                require(re.symbol in grid.indices && re.subcarrier + re.prb * 12 in 0 until config.fftSize)
                val k = re.prb * 12 + re.subcarrier
                val previous = owners[re.symbol][layer][k]
                if (previous != null && previous != grant.ueId) {
                    collisions++
                } else {
                    grid[re.symbol][layer][k] = symbol
                    owners[re.symbol][layer][k] = grant.ueId
                }
            }

            mapped.dataSymbols.forEach { (re, symbol) -> put(re, symbol) }
            mapped.dmrsSymbols.forEach { (re, symbol) -> put(re, symbol) }
            dataCount += mapped.mappedData
            dmrsCount += mapped.mappedDmrs
            placements += Placement(
                grant.ueId, grant.prbStart, grant.prbCount, grant.layers,
                mapped.mappedData, mapped.mappedDmrs
            )
        }

        val waveformSymbols = config.numerology.symbolsPerSlot
        val tx = layers
        val waveform = Array(tx) { Array(waveformSymbols) {
            Array(config.fftSize + config.cyclicPrefixSamples) {
                NrCanonicalSpatialEngine.C(0.0, 0.0)
            }
        } }

        for (layer in 0 until layers) {
            for (symbol in 0 until waveformSymbols) {
                val freq = Array(config.fftSize) { k ->
                    grid[symbol][layer][k]?.let {
                        NrCanonicalSpatialEngine.C(it.re, it.im)
                    } ?: NrCanonicalSpatialEngine.C(0.0, 0.0)
                }
                val time = NrCanonicalSpatialEngine.fft(freq, inverse = true)
                val cp = config.cyclicPrefixSamples
                for (i in 0 until cp) waveform[layer][symbol][i] = time[config.fftSize - cp + i]
                for (i in 0 until config.fftSize) waveform[layer][symbol][cp + i] = time[i]
            }
        }

        val occupied = dataCount + dmrsCount
        val expected = schedule.grants.sumOf { g ->
            NrCanonicalMultiSymbolV124.plan(
                NrCanonicalMultiSymbolV124.Config(
                    slot = config.numerology.slotIndex,
                    prbStart = g.prbStart,
                    prbCount = g.prbCount,
                    layers = g.layers,
                    direction = if (g.direction == NrCanonicalSchedulerV126.Direction.DL)
                        NrCanonicalMultiSymbolV124.Direction.DOWNLINK
                    else NrCanonicalMultiSymbolV124.Direction.UPLINK
                )
            ).allocation.size
        }

        val passed = schedule.scheduledPrbs <= config.prbCount &&
            collisions == 0 &&
            occupied == expected &&
            placements.size == schedule.grants.size &&
            waveform.all { antenna -> antenna.size == waveformSymbols &&
                antenna.all { it.size == config.fftSize + config.cyclicPrefixSamples } }

        return Report(
            passed, schedule.grants, placements, dataCount, dmrsCount, occupied, collisions,
            config.fftSize, waveformSymbols, waveformSymbols * (config.fftSize + config.cyclicPrefixSamples),
            waveform,
            Array(waveformSymbols) { s -> Array(layers) { l -> grid[s][l].copyOf() } },
            "V133 creates one collision-checked shared scheduled grid and OFDM waveform from V126 grants via V128/V129; V117-V132 remain unchanged."
        )
    }
}
