package com.example.nrsimulator

import kotlin.math.log10
import kotlin.math.sqrt

/** V135: scheduled multi-UE PHY receiver through the canonical V121 TDL/noise boundary. */
object NrCanonicalScheduledRxV135 {
    data class Config(
        val numerology: NrCanonicalNumerologyV123.Config = NrCanonicalNumerologyV123.Config(),
        val prbCount: Int = 24,
        val ues: List<NrCanonicalSchedulerV126.Ue> = listOf(
            NrCanonicalSchedulerV126.Ue(1, bufferBytes = 256, priority = 2, maxLayers = 1),
            NrCanonicalSchedulerV126.Ue(2, bufferBytes = 256, priority = 1, maxLayers = 1)
        ),
        val snrDb: Double = 35.0,
        val modulation: NrPhyMappingV89.Modulation = NrPhyMappingV89.Modulation.QPSK,
        val targetCodeRate: Double = 0.5,
        val fftSize: Int = 2048,
        val cyclicPrefixSamples: Int = 144,
        val tdlProfile: NrCanonicalTdlV118.Profile = NrCanonicalTdlV118.Profile.TDL_A,
        val dopplerHz: Double = 0.0,
        val timeSeconds: Double = 0.0,
        val jakesOscillators: Int = 32,
        val seed: Int = 13501
    )

    data class UeReport(
        val ueId: Int,
        val payloadBits: Int,
        val codedBits: Int,
        val pilotCount: Int,
        val channelMse: Double,
        val evm: Double,
        val postSinrDb: Double,
        val crcPassed: Boolean,
        val ldpcPassed: Boolean
    )

    data class Report(
        val passed: Boolean,
        val grants: List<NrCanonicalSchedulerV126.Grant>,
        val ueReports: List<UeReport>,
        val channelChangedWithTime: Boolean,
        val waveformSamples: Int,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.prbCount > 0 && config.ues.isNotEmpty())
        require(config.snrDb.isFinite() && config.fftSize > 0 && (config.fftSize and (config.fftSize - 1)) == 0)
        require(config.cyclicPrefixSamples >= 0 && config.cyclicPrefixSamples < config.fftSize)
        require(12 * config.prbCount <= config.fftSize)
        require(config.dopplerHz >= 0.0 && config.timeSeconds.isFinite())

        val schedule = NrCanonicalSchedulerV126.schedule(
            config.ues,
            NrCanonicalSchedulerV126.Config(prbCount = config.prbCount, slot = config.numerology.slotIndex)
        )
        val reports = ArrayList<UeReport>()

        for ((index, grant) in schedule.grants.withIndex()) {
            // V135 deliberately uses one layer per scheduled UE so each UE's orthogonal PRB
            // grant forms an independently recoverable scheduled codeword.
            require(grant.layers == 1) { "V135 receiver regression currently requires one layer per UE" }
            val plan = NrCanonicalMultiSymbolV124.plan(
                NrCanonicalMultiSymbolV124.Config(
                    slot = config.numerology.slotIndex,
                    prbStart = grant.prbStart,
                    prbCount = grant.prbCount,
                    startSymbol = grant.startSymbol,
                    symbolCount = grant.symbolCount,
                    layers = 1,
                    direction = if (grant.direction == NrCanonicalSchedulerV126.Direction.DL)
                        NrCanonicalMultiSymbolV124.Direction.DOWNLINK else NrCanonicalMultiSymbolV124.Direction.UPLINK
                )
            )
            val payloadBits = minOf(grant.tbsBytes * 8, 256)
            val payload = IntArray(payloadBits) { (it * 17 + grant.ueId * 7 + config.seed) and 1 }
            val bg = NrLdpcV82.selectBaseGraph(payloadBits, config.targetCodeRate)
            val table = when (bg) {
                NrLdpcV82.BaseGraph.BG1 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG1)
                NrLdpcV82.BaseGraph.BG2 -> NrLdpcV85.exactTable(NrLdpcV85.BaseGraph.BG2)
            }
            val capacityBits = plan.data.size * config.modulation.bitsPerSymbol
            val coded = NrCodingChainV87.encode(
                payload, config.targetCodeRate, table,
                outputBitsPerCodeBlock = capacityBits - capacityBits % config.modulation.bitsPerSymbol,
                rv = 0
            )
            val codeword = coded.rateMatched.first()
            val usable = minOf(codeword.size, capacityBits).let { it - it % config.modulation.bitsPerSymbol }
            val txBits = codeword.copyOf(usable)
            val symbols = NrPhyMappingV89.modulate(txBits, config.modulation)
            val mapped = NrCanonicalPhysicalMapperV129.map(plan, config.seed + grant.ueId * 101 + index)

            // The V124 schedule spans multiple slot symbols; each slot symbol is
            // transmitted as its own OFDM symbol through the same per-run TDL
            // realization, so no two scheduled REs collide in the frequency grid.
            val channel = if (config.dopplerHz > 0.0) {
                NrCanonicalTdlV121.buildAtTime(
                    NrCanonicalTdlV121.Config(
                        tdl = NrCanonicalTdlV118.Config(
                            profile = config.tdlProfile, txAntennas = 1, rxAntennas = 1,
                            sampleRateHz = 30.72e6, rmsDelayNs = 30.0,
                            dopplerHz = config.dopplerHz, seed = config.seed + grant.ueId
                        ),
                        oscillators = config.jakesOscillators
                    ), config.timeSeconds
                )
            } else {
                NrCanonicalTdlV118.build(
                    NrCanonicalTdlV118.Config(
                        profile = config.tdlProfile, txAntennas = 1, rxAntennas = 1,
                        sampleRateHz = 30.72e6, rmsDelayNs = 30.0, seed = config.seed + grant.ueId
                    )
                )
            }
            val cp = config.cyclicPrefixSamples

            val txPilotByK = HashMap<Int, NrCanonicalSpatialEngine.C>()
            val dmrsBySymbol = HashMap<Int, ArrayList<Pair<Int, NrCanonicalSpatialEngine.C>>>()
            for ((re, s) in mapped.dmrsSymbols) {
                val k = re.prb * 12 + re.subcarrier
                val z = NrCanonicalSpatialEngine.C(s.re, s.im)
                txPilotByK[k] = z
                dmrsBySymbol.getOrPut(re.symbol) { ArrayList() }.add(k to z)
            }
            val dataBySymbol = HashMap<Int, ArrayList<Pair<Int, Int>>>()
            for (i in symbols.indices) {
                val re = plan.data[i]
                dataBySymbol.getOrPut(re.symbol) { ArrayList() }.add(re.prb * 12 + re.subcarrier to i)
            }

            val rxFreqBySymbol = HashMap<Int, Array<NrCanonicalSpatialEngine.C>>()
            var noiseVariance = Double.NaN
            for (symbol in (dmrsBySymbol.keys + dataBySymbol.keys).sorted()) {
                val freq = Array(config.fftSize) { NrCanonicalSpatialEngine.C(0.0, 0.0) }
                for ((k, z) in dmrsBySymbol[symbol] ?: emptyList()) freq[k] = z
                for ((k, i) in dataBySymbol[symbol] ?: emptyList()) {
                    freq[k] = NrCanonicalSpatialEngine.C(symbols[i].re, symbols[i].im)
                }
                val txTime = NrCanonicalSpatialEngine.fft(freq, inverse = true)
                val txWithCp = Array(1) { Array(config.fftSize + cp) { i ->
                    txTime[if (i < cp) config.fftSize - cp + i else i - cp]
                } }
                val ch = NrCanonicalSpatialEngine.applyTdl(
                    txWithCp, channel.taps, config.snrDb, config.seed + grant.ueId * 131 + symbol * 17
                )
                noiseVariance = ch.noiseVariance
                rxFreqBySymbol[symbol] = NrCanonicalSpatialEngine.fft(
                    ch.output[0].copyOfRange(cp, cp + config.fftSize)
                )
            }

            // The schedule has pilots on the DM-RS symbol; the scheduled
            // frequency-domain realization is estimated from those pilots and shared
            // across the grant's slot symbols (static per-run channel).
            val pilotK = txPilotByK.keys.sorted().toIntArray()
            val pilotRxByK = HashMap<Int, NrCanonicalSpatialEngine.C>()
            for ((re, _) in mapped.dmrsSymbols) {
                val k = re.prb * 12 + re.subcarrier
                rxFreqBySymbol[re.symbol]?.let { pilotRxByK[k] = it[k] }
            }
            val h = Array(config.fftSize) { NrCanonicalSpatialEngine.C(0.0, 0.0) }
            for (k in 0 until config.fftSize) {
                val lo = pilotK.lastOrNull { it <= k } ?: pilotK.first()
                val hi = pilotK.firstOrNull { it >= k } ?: pilotK.last()
                val a = if (hi == lo) 0.0 else (k - lo).toDouble() / (hi - lo)
                val hl = pilotRxByK[lo]!! / txPilotByK[lo]!!
                val hh = pilotRxByK[hi]!! / txPilotByK[hi]!!
                h[k] = hl * (1.0 - a) + hh * a
            }

            val trueH = NrCanonicalSpatialEngine.frequencyResponse(channel.taps, config.fftSize)
            var mse = 0.0
            var href = 0.0
            for (k in 0 until config.fftSize) {
                val d = h[k] - trueH[k][0][0]
                mse += d.abs2()
                href += trueH[k][0][0].abs2()
            }
            mse /= href.coerceAtLeast(1e-18)

            val detected = ArrayList<NrPhyMappingV89.Complex>(symbols.size)
            var err = 0.0
            var ref = 0.0
            for (i in symbols.indices) {
                val re = plan.data[i]
                val k = re.prb * 12 + re.subcarrier
                val z = rxFreqBySymbol[re.symbol]!![k] / h[k]
                detected += NrPhyMappingV89.Complex(z.re, z.im)
                err += (z.re - symbols[i].re) * (z.re - symbols[i].re) +
                    (z.im - symbols[i].im) * (z.im - symbols[i].im)
                ref += symbols[i].re * symbols[i].re + symbols[i].im * symbols[i].im
            }
            val evm = sqrt(err / ref.coerceAtLeast(1e-18))
            val postSinr = 10.0 * log10((1.0 / evm.coerceAtLeast(1e-12).pow2()).coerceAtLeast(1e-12))
            val llr = softDemodulate(detected.toTypedArray(), config.modulation, noiseVariance)
            val recovered = NrRateMatchingV84.rateRecover(
                llr, NrRateMatchingV84.Config(coded.baseGraph, coded.liftingSize, 0, usable)
            )
            val decoded = NrLdpcCodecV86.decode(
                recovered, table, coded.liftingSize, coded.liftingSet, maxIterations = 80
            )
            val expected = coded.transportWithCrc
            val actual = decoded.bits.copyOf(minOf(decoded.bits.size, expected.size))
            val crc = actual.contentEquals(expected) &&
                NrTransportV83.checkCrc(actual, NrTransportV83.crcTypeForTransportBlock(payloadBits))
            reports += UeReport(
                grant.ueId, payloadBits, usable, pilotK.size, mse, evm, postSinr,
                crc, decoded.converged && decoded.syndromeWeight == 0 && crc
            )
        }

        val channelChanged = if (config.dopplerHz > 0.0) {
            val a = NrCanonicalTdlV121.buildAtTime(
                NrCanonicalTdlV121.Config(
                    tdl = NrCanonicalTdlV118.Config(profile = config.tdlProfile, txAntennas = 1, rxAntennas = 1,
                        sampleRateHz = 30.72e6, rmsDelayNs = 30.0, dopplerHz = config.dopplerHz, seed = config.seed),
                    oscillators = config.jakesOscillators
                ), config.timeSeconds)
            val b = NrCanonicalTdlV121.buildAtTime(
                NrCanonicalTdlV121.Config(
                    tdl = NrCanonicalTdlV118.Config(profile = config.tdlProfile, txAntennas = 1, rxAntennas = 1,
                        sampleRateHz = 30.72e6, rmsDelayNs = 30.0, dopplerHz = config.dopplerHz, seed = config.seed,
                    ),
                    oscillators = config.jakesOscillators
                ), config.timeSeconds + 0.001)
            a.taps.zip(b.taps).any { (x, y) -> x.h[0][0].let { p -> (p - y.h[0][0]).abs2() > 1e-18 } }
        } else false

        val passed = reports.isNotEmpty() &&
            reports.all { it.crcPassed && it.ldpcPassed && it.pilotCount > 0 && it.channelMse.isFinite() }
        return Report(
            passed, schedule.grants, reports, channelChanged,
            config.numerology.symbolsPerSlot * (config.fftSize + config.cyclicPrefixSamples),
            "V135 adds scheduled coded RX through V121/V118 TDL and AWGN, multi-symbol DM-RS-derived frequency interpolation, equalization, soft LLR, V84 rate recovery, V86 LDPC and V83 TB CRC. Current receiver regression is one layer per UE and uses the scheduled OFDM frequency grid per UE; V136 can extend this to a simultaneous shared waveform/channel."
        )
    }

    private fun softDemodulate(
        symbols: Array<NrPhyMappingV89.Complex>,
        modulation: NrPhyMappingV89.Modulation,
        noiseVariance: Double
    ): DoubleArray {
        val m = modulation.bitsPerSymbol
        val constellation = Array(1 shl m) { value ->
            NrPhyMappingV89.modulate(IntArray(m) { b -> (value ushr (m - 1 - b)) and 1 }, modulation)[0]
        }
        val out = DoubleArray(symbols.size * m)
        var at = 0
        for (r in symbols) for (bit in 0 until m) {
            var min0 = Double.POSITIVE_INFINITY
            var min1 = Double.POSITIVE_INFINITY
            for (index in constellation.indices) {
                val c = constellation[index]
                val d = (r.re - c.re) * (r.re - c.re) + (r.im - c.im) * (r.im - c.im)
                if (((index ushr (m - 1 - bit)) and 1) == 0) min0 = minOf(min0, d)
                else min1 = minOf(min1, d)
            }
            out[at++] = ((min1 - min0) / noiseVariance.coerceAtLeast(1e-12)).coerceIn(-60.0, 60.0)
        }
        return out
    }

    private fun Double.pow2(): Double = this * this
}
