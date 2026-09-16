package com.example.nrsimulator

import kotlin.math.sqrt

/**
 * V116 additive canonical PHY stage: exact V101 DM-RS resources drive a pilot-aided
 * frequency-domain MIMO channel estimate, followed by the existing canonical MMSE
 * detector. Historical V1-V115 APIs remain untouched.
 */
object NrCanonicalDmrsMimoV116 {
    data class Config(
        val layers: Int = 1,
        val txAntennas: Int = layers,
        val rxAntennas: Int = layers,
        val fftSize: Int = 128,
        val resourceBlocks: Int = 8,
        val snrDb: Double = 35.0,
        val seed: Int = 2201
    )

    data class Report(
        val passed: Boolean,
        val dmrsMapped: Boolean,
        val channelEstimated: Boolean,
        val equalized: Boolean,
        val evm: Double,
        val postSinrDb: Double,
        val detectedLayers: Int,
        val pilotCount: Int,
        val channelMse: Double,
        val notes: String
    )

    fun run(config: Config = Config()): Report {
        require(config.layers in 1..4)
        require(config.txAntennas >= config.layers && config.rxAntennas >= config.layers)
        require(config.fftSize > 0 && (config.fftSize and (config.fftSize - 1)) == 0)
        require(config.resourceBlocks > 0 && 12 * config.resourceBlocks <= config.fftSize)
        require(config.snrDb.isFinite())

        // Ports 1000,1002,1004,1006 use distinct TYPE-1 frequency offsets. This gives
        // deterministic orthogonal pilot locations while retaining the V101 port rules.
        val ports = IntArray(config.layers) { 1000 + 2 * it }
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
                ports = ports
            )
        )

        val pilotByLayer = Array(config.layers) { ArrayList<Pair<Int, NrCanonicalSpatialEngine.C>>() }
        for (r in dmrs.resources) {
            val layer = ports.indexOf(r.port)
            if (layer >= 0 && r.subcarrier in 0 until config.fftSize) {
                pilotByLayer[layer].add(r.subcarrier to NrCanonicalSpatialEngine.C(r.value.re, r.value.im))
            }
        }
        val uniquePilotKeys = pilotByLayer.flatMap { it.map { p -> p.first } }.toSet()
        val dmrsMapped = uniquePilotKeys.isNotEmpty() && pilotByLayer.all { it.isNotEmpty() }

        val dataSymbols = Array(config.layers) { Array(config.fftSize) { NrCanonicalSpatialEngine.C(0.0, 0.0) } }
        val txFreq = Array(config.txAntennas) { Array(config.fftSize) { NrCanonicalSpatialEngine.C(0.0, 0.0) } }
        val dataPositions = (0 until config.fftSize).filter { it !in uniquePilotKeys }
        val dataBits = IntArray(dataPositions.size * config.layers * 2) { (it + 3) and 1 }
        val qam = NrPhyMappingV89.modulate(dataBits, NrPhyMappingV89.Modulation.QPSK)
        var q = 0
        for (k in dataPositions) {
            for (layer in 0 until config.layers) {
                val z = qam[q++ % qam.size]
                dataSymbols[layer][k] = NrCanonicalSpatialEngine.C(z.re, z.im)
                txFreq[layer][k] = dataSymbols[layer][k]
            }
        }
        for (layer in 0 until config.layers) {
            for ((k, x) in pilotByLayer[layer]) {
                for (t in 0 until config.txAntennas) txFreq[t][k] = NrCanonicalSpatialEngine.C(0.0, 0.0)
                txFreq[layer][k] = x
            }
        }

        val txTime = Array(config.txAntennas) { t -> NrCanonicalSpatialEngine.fft(txFreq[t], inverse = true) }
        val h = Array(config.rxAntennas) { r -> Array(config.txAntennas) { t ->
            val direct = if (r == t) 1.0 else 0.08
            NrCanonicalSpatialEngine.C(direct, 0.003 * (r + t + 1))
        } }
        val taps = listOf(NrCanonicalSpatialEngine.Tap(0, h))
        val wf = NrCanonicalSpatialEngine.applyTdl(txTime, taps, config.snrDb, config.seed)
        val rxFreq = Array(config.rxAntennas) { r -> NrCanonicalSpatialEngine.fft(wf.output[r]) }
        val estimated = estimateSparsePilots(rxFreq, pilotByLayer, config.fftSize, config.rxAntennas, config.layers)
        val channelEstimated = estimated.all { row -> row.all { it.re.isFinite() && it.im.isFinite() } }
        val detection = NrCanonicalSpatialEngine.detect(rxFreq, estimated, wf.noiseVariance, "MMSE")

        var err = 0.0
        var ref = 0.0
        var count = 0
        for (layer in 0 until config.layers) for (k in dataPositions) {
            val a = detection.symbols[layer][k]
            val b = dataSymbols[layer][k]
            err += (a.re - b.re) * (a.re - b.re) + (a.im - b.im) * (a.im - b.im)
            ref += b.abs2()
            count++
        }
        val evm = sqrt(err / ref.coerceAtLeast(1e-18))
        var channelMse = 0.0
        var channelRef = 0.0
        for (k in 0 until config.fftSize) for (r in 0 until config.rxAntennas) for (t in 0 until config.txAntennas) {
            val a = estimated[k][r][t]
            val b = h[r][t]
            channelMse += (a.re - b.re) * (a.re - b.re) + (a.im - b.im) * (a.im - b.im)
            channelRef += b.abs2()
        }
        channelMse /= channelRef.coerceAtLeast(1e-18)
        val postSinr = if (detection.postSinrDb.isEmpty()) Double.NEGATIVE_INFINITY else detection.postSinrDb.average()
        val equalized = detection.symbols.size == config.layers && detection.symbols.all { it.size == config.fftSize } && evm.isFinite()
        return Report(
            dmrsMapped && channelEstimated && equalized,
            dmrsMapped,
            channelEstimated,
            equalized,
            evm,
            postSinr,
            detection.symbols.size,
            pilotByLayer.sumOf { it.size },
            channelMse,
            "V101 DM-RS pilots are transmitted through the canonical OFDM/MIMO waveform, estimated without the known channel, then passed to the existing MMSE detector."
        )
    }

    private fun estimateSparsePilots(
        received: Array<Array<NrCanonicalSpatialEngine.C>>,
        pilotByLayer: Array<ArrayList<Pair<Int, NrCanonicalSpatialEngine.C>>>,
        nsc: Int,
        rx: Int,
        layers: Int
    ): Array<Array<Array<NrCanonicalSpatialEngine.C>>> {
        val h = Array(nsc) { Array(rx) { Array(layers) { NrCanonicalSpatialEngine.C(0.0, 0.0) } } }
        for (t in 0 until layers) {
            val pilots = pilotByLayer[t].sortedBy { it.first }
            require(pilots.isNotEmpty())
            for (r in 0 until rx) {
                for ((k, x) in pilots) h[k][r][t] = received[r][k] / x
                for (k in 0 until nsc) if (pilots.none { it.first == k }) {
                    val lo = pilots.lastOrNull { it.first < k }
                    val hi = pilots.firstOrNull { it.first > k }
                    val l = lo ?: hi!!
                    val u = hi ?: lo!!
                    val a = if (u.first == l.first) 0.0 else (k - l.first).toDouble() / (u.first - l.first)
                    h[k][r][t] = l.secondChannel(h, received, r, t, a, l.first, u.first)
                }
            }
        }
        return h
    }

    private fun Pair<Int, NrCanonicalSpatialEngine.C>.secondChannel(
        h: Array<Array<Array<NrCanonicalSpatialEngine.C>>>,
        received: Array<Array<NrCanonicalSpatialEngine.C>>,
        r: Int,
        t: Int,
        a: Double,
        lo: Int,
        hi: Int
    ): NrCanonicalSpatialEngine.C {
        val l = h[lo][r][t]
        val u = h[hi][r][t]
        return l * (1.0 - a) + u * a
    }
}
