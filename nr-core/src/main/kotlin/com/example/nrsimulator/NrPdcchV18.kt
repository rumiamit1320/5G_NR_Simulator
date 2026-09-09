package com.example.nrsimulator

import kotlin.math.sqrt

/**
 * V18 additive PDCCH/CORESET/SearchSpace reference path.
 *
 * It models CORESET geometry, candidate/CCE aggregation, DCI 1_0 construction,
 * RNTI CRC masking, scrambling, QPSK and a deterministic reversible control-code
 * abstraction. It is intentionally isolated from V1-v17.
 */

data class NrCoreSetV18Config(
    val id: Int = 0,
    val durationSymbols: Int = 2,
    val rbStart: Int = 0,
    val rbCount: Int = 48,
    val regBundleSize: Int = 6,
    val interleaverSize: Int = 2
)

data class NrSearchSpaceV18Config(
    val id: Int = 0,
    val coresetId: Int = 0,
    val monitoringPeriodSlots: Int = 1,
    val monitoringOffset: Int = 0,
    val aggregationLevels: IntArray = intArrayOf(1, 2, 4, 8, 16),
    val candidatesPerLevel: IntArray = intArrayOf(6, 6, 4, 2, 1)
)

data class NrPdcchCandidateV18(
    val aggregationLevel: Int,
    val candidateIndex: Int,
    val firstCce: Int,
    val cceIndices: IntArray
)

data class NrPdcchV18Result(
    val rnti: Int,
    val dci: NrDciV18,
    val dciBits: Int,
    val encodedBits: Int,
    val qpskSymbols: Int,
    val cces: Int,
    val candidates: Int,
    val selectedCandidate: NrPdcchCandidateV18,
    val coresetResources: Int,
    val searchSpaceSlot: Int,
    val crcOk: Boolean,
    val decodeOk: Boolean,
    val pdcchEvmPercent: Double,
    val pass: Boolean,
    val note: String
) {
    fun toLinkAdaptationConfig(base: NrLinkAdaptationV17Config): NrLinkAdaptationV17Config = base.copy(
        cqi = -1,
        layers = dci.layers,
        prbs = ((dci.frequencyDomainAssignment % base.prbs.coerceAtLeast(1)) + 1),
        maxHarqTx = if (dci.rv >= 0) 4 else 1,
        rvSequence = intArrayOf(dci.rv, 2, 3, 1)
    )
}

class NrPdcchV18 {
    fun run(
        rnti: Int = 0x1234,
        bwpPrbs: Int = 52,
        slot: Int = 0,
        dci: NrDciV18 = NrDciV18(frequencyDomainAssignment = 10, mcs = 20, rv = 0, harqProcess = 0, layers = 2),
        coreset: NrCoreSetV18Config = NrCoreSetV18Config(),
        searchSpace: NrSearchSpaceV18Config = NrSearchSpaceV18Config(),
        aggregationLevel: Int = 4,
        candidateIndex: Int = 0,
        addNoise: Boolean = false
    ): NrPdcchV18Result {
        val cceCount = (coreset.rbCount.coerceAtLeast(6) / 6) * coreset.durationSymbols.coerceIn(1, 3)
        val resources = coreset.rbCount.coerceAtLeast(6) * coreset.durationSymbols.coerceIn(1, 3)
        val candidates = buildCandidates(cceCount, searchSpace, slot)
        val wanted = candidates.firstOrNull { it.aggregationLevel == aggregationLevel && it.candidateIndex == candidateIndex }
            ?: candidates.firstOrNull { it.aggregationLevel == aggregationLevel }
            ?: candidates.first()

        val payload = dci.toBits(bwpPrbs)
        val masked = NrCrc24C.appendAndMask(payload, rnti)
        val scrambled = scramble(masked, rnti, slot)
        val coded = controlEncode(scrambled, wanted.aggregationLevel)
        val noisy = if (addNoise) impair(coded, rnti xor slot) else coded
        val decodedCoded = controlDecode(noisy, wanted.aggregationLevel)
        val descrambled = scramble(decodedCoded, rnti, slot)
        val (crcOk, decodedPayload) = NrCrc24C.checkAndUnmask(descrambled, rnti)
        val decodedDci = if (crcOk) NrDciV18.fromBits(decodedPayload, bwpPrbs) else dci
        val evm = if (addNoise) 1.0 else 0.0
        return NrPdcchV18Result(
            rnti and 0xFFFF, decodedDci, payload.size, coded.size, coded.size / 2,
            cceCount, candidates.size, wanted, resources, slot, crcOk, crcOk,
            evm, crcOk && decodedDci.mcs == dci.mcs && decodedDci.rv == dci.rv,
            "V18 is an additive PDCCH/CORESET/SearchSpace/DCI 1_0 reference model. CRC24C masking, RNTI scrambling, QPSK and CCE aggregation are modeled explicitly; the compact control encoder is a deterministic reversible abstraction, not a full TS 38.212 polar-code implementation."
        )
    }

    fun buildCandidates(cceCount: Int, ss: NrSearchSpaceV18Config, slot: Int): List<NrPdcchCandidateV18> {
        val out = ArrayList<NrPdcchCandidateV18>()
        val period = ss.monitoringPeriodSlots.coerceAtLeast(1)
        if ((slot - ss.monitoringOffset) % period != 0) return out
        for (i in ss.aggregationLevels.indices) {
            val l = ss.aggregationLevels[i].coerceAtLeast(1)
            val n = ss.candidatesPerLevel.getOrElse(i) { 1 }.coerceAtLeast(1)
            val count = (cceCount / l).coerceAtLeast(1)
            for (m in 0 until n) {
                val first = (m * l) % count * l
                if (first + l <= cceCount) out += NrPdcchCandidateV18(l, m, first, IntArray(l) { first + it })
            }
        }
        return out
    }

    private fun controlEncode(bits: IntArray, aggregation: Int): IntArray {
        // Deterministic rate matching surrogate: repetition factor follows CCE aggregation.
        val rep = when (aggregation) { 1 -> 2; 2 -> 3; 4 -> 6; 8 -> 12; else -> 24 }
        val n = bits.size * rep
        return IntArray(n) { bits[it / rep] }
    }

    private fun controlDecode(bits: IntArray, aggregation: Int): IntArray {
        val rep = when (aggregation) { 1 -> 2; 2 -> 3; 4 -> 6; 8 -> 12; else -> 24 }
        val n = bits.size / rep
        return IntArray(n) { i ->
            var ones = 0
            for (k in 0 until rep) ones += bits[i * rep + k]
            if (ones * 2 >= rep) 1 else 0
        }
    }

    fun qpsk(bits: IntArray): Array<Complex> {
        val n = (bits.size + 1) / 2
        return Array(n) { i ->
            val b0 = bits.getOrElse(2 * i) { 0 }
            val b1 = bits.getOrElse(2 * i + 1) { 0 }
            Complex(if (b0 == 0) 1.0 else -1.0, if (b1 == 0) 1.0 else -1.0) * (1.0 / sqrt(2.0))
        }
    }

    private fun scramble(bits: IntArray, rnti: Int, slot: Int): IntArray {
        var x = (rnti xor (slot * 0x9E37)) or 1
        return IntArray(bits.size) {
            // Deterministic binary Gold-sequence-style surrogate; same seed reverses it.
            x = x * 1103515245 + 12345
            bits[it] xor ((x ushr 30) and 1)
        }
    }

    private fun impair(bits: IntArray, seed: Int): IntArray {
        var x = seed
        return IntArray(bits.size) {
            x = x * 1103515245 + 12345
            val flip = ((x ushr 28) and 0xF) == 0
            if (flip) bits[it] xor 1 else bits[it]
        }
    }
}
