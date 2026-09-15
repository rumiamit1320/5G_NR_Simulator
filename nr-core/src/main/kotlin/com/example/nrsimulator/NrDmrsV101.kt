package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * V101 additive exact-NR DM-RS primitives.
 *
 * Targets TS 38.211 sequence generation and the normal-CP PDSCH/PUSCH
 * time/frequency resource mapping rules. V92 remains available as the
 * structural compatibility implementation.
 */
object NrDmrsV101 {
    enum class MappingType { A, B }
    enum class ConfigurationType { TYPE1, TYPE2 }
    enum class MaxLength { LEN1, LEN2 }
    enum class AdditionalPosition { POS0, POS1, POS2, POS3 }

    data class Config(
        val mappingType: MappingType = MappingType.A,
        val configurationType: ConfigurationType = ConfigurationType.TYPE1,
        val maxLength: MaxLength = MaxLength.LEN1,
        val additionalPosition: AdditionalPosition = AdditionalPosition.POS2,
        val typeAPosition3: Boolean = false,
        val allocationStartSymbol: Int = 0,
        val allocationSymbols: Int = 14,
        val slot: Int = 0,
        val nId: Int = 0,
        val nSCID: Int = 0,
        val symbolsPerSlot: Int = 14,
        val startSubcarrier: Int = 0,
        val resourceBlocks: Int = 1,
        val ports: IntArray = intArrayOf(1000)
    )

    data class Resource(
        val port: Int,
        val subcarrier: Int,
        val symbol: Int,
        val value: NrDmrsMimoV90.Complex,
        val cInit: Long,
        val sequenceIndex: Int,
        val cdmGroup: Int,
        val kPrime: Int,
        val lPrime: Int
    )

    data class Result(
        val resources: List<Resource>,
        val dmrsSymbols: IntArray,
        val cInitBySymbol: Map<Int, Long>,
        val sequence: Map<Int, Array<NrDmrsMimoV90.Complex>>
    )

    private data class PortInfo(val lambda: Int, val delta: Int, val wf: IntArray, val wt: IntArray)

    /** TS 38.211 5.2.1 Gold sequence, returning c(0..length-1). */
    fun gold(cInit: Long, length: Int): IntArray {
        require(length >= 0)
        if (length == 0) return IntArray(0)
        val nc = 1600
        val total = nc + length
        val x1 = IntArray(total + 31)
        val x2 = IntArray(total + 31)
        x1[0] = 1
        for (n in 0 until 31) x2[n] = ((cInit ushr n) and 1L).toInt()
        for (n in 0 until total) {
            x1[n + 31] = (x1[n + 3] + x1[n]) and 1
            x2[n + 31] = (x2[n + 3] + x2[n + 2] + x2[n + 1] + x2[n]) and 1
        }
        return IntArray(length) { n -> (x1[n + nc] + x2[n + nc]) and 1 }
    }

    /** TS 38.211 DM-RS c_init for a given slot/symbol. */
    fun cInit(slot: Int, symbol: Int, nId: Int, nSCID: Int, symbolsPerSlot: Int = 14): Long {
        require(slot >= 0 && symbol >= 0 && nId in 0..65535 && nSCID in 0..1)
        require(symbolsPerSlot in 12..14)
        val a = symbolsPerSlot.toLong() * slot + symbol + 1L
        return ((1L shl 17) * a * (2L * nId + 1L) + 2L * nId + nSCID) and 0x7fffffffL
    }

    /** r(m)=1/sqrt(2)((1-2c(2m))+j(1-2c(2m+1))). */
    fun sequence(slot: Int, symbol: Int, nId: Int, nSCID: Int, length: Int, symbolsPerSlot: Int = 14): Array<NrDmrsMimoV90.Complex> {
        require(length >= 0)
        val c = gold(cInit(slot, symbol, nId, nSCID, symbolsPerSlot), 2 * length)
        val s = 1.0 / sqrt(2.0)
        return Array(length) { m -> NrDmrsMimoV90.Complex((1 - 2 * c[2 * m]) * s, (1 - 2 * c[2 * m + 1]) * s) }
    }

    private fun portInfo(port: Int, type: ConfigurationType): PortInfo {
        val base = if (type == ConfigurationType.TYPE1) {
            when (port) {
                in 1000..1001 -> PortInfo(0, 0, intArrayOf(1, if (port == 1000) 1 else -1), intArrayOf(1, 1))
                in 1002..1003 -> PortInfo(1, 1, intArrayOf(1, if (port == 1002) 1 else -1), intArrayOf(1, 1))
                in 1004..1005 -> PortInfo(0, 0, intArrayOf(1, if (port == 1004) 1 else -1), intArrayOf(1, -1))
                in 1006..1007 -> PortInfo(1, 1, intArrayOf(1, if (port == 1006) 1 else -1), intArrayOf(1, -1))
                else -> error("unsupported TYPE1 DM-RS port: $port")
            }
        } else {
            when (port) {
                in 1000..1001 -> PortInfo(0, 0, intArrayOf(1, if (port == 1000) 1 else -1), intArrayOf(1, 1))
                in 1002..1003 -> PortInfo(1, 2, intArrayOf(1, if (port == 1002) 1 else -1), intArrayOf(1, 1))
                in 1004..1005 -> PortInfo(2, 4, intArrayOf(1, if (port == 1004) 1 else -1), intArrayOf(1, 1))
                in 1006..1007 -> PortInfo(0, 0, intArrayOf(1, if (port == 1006) 1 else -1), intArrayOf(1, -1))
                in 1008..1009 -> PortInfo(1, 2, intArrayOf(1, if (port == 1008) 1 else -1), intArrayOf(1, -1))
                in 1010..1011 -> PortInfo(2, 4, intArrayOf(1, if (port == 1010) 1 else -1), intArrayOf(1, -1))
                else -> error("unsupported TYPE2 DM-RS port: $port")
            }
        }
        return base
    }

    private fun singlePositions(mapping: MappingType, ld: Int, l0: Int, add: AdditionalPosition): IntArray {
        val a = when (mapping) {
            MappingType.A -> when (ld) {
                in 3..7 -> intArrayOf(l0)
                8, 9 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); else -> intArrayOf(l0, 7) }
                10, 11 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 9); else -> intArrayOf(l0, 6, 9) }
                12 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 9); AdditionalPosition.POS2 -> intArrayOf(l0, 6, 9); AdditionalPosition.POS3 -> intArrayOf(l0, 5, 8, 11) }
                else -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 11); AdditionalPosition.POS2 -> intArrayOf(l0, 7, 11); AdditionalPosition.POS3 -> intArrayOf(l0, 5, 8, 11) }
            }
            MappingType.B -> when (ld) {
                2, 3, 4 -> intArrayOf(l0)
                5, 6, 7 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); else -> intArrayOf(l0, 4) }
                8 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 6); else -> intArrayOf(l0, 3, 6) }
                9 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 7); else -> intArrayOf(l0, 4, 7) }
                10 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 7); else -> intArrayOf(l0, 4, 7) }
                11 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 8); AdditionalPosition.POS2 -> intArrayOf(l0, 4, 8); else -> intArrayOf(l0, 3, 6, 9) }
                12, 13, 14 -> when (add) { AdditionalPosition.POS0 -> intArrayOf(l0); AdditionalPosition.POS1 -> intArrayOf(l0, 9); AdditionalPosition.POS2 -> intArrayOf(l0, 5, 9); else -> intArrayOf(l0, 3, 6, 9) }
                else -> intArrayOf(l0)
            }
        }
        return a.distinct().toIntArray()
    }

    fun generate(config: Config): Result {
        require(config.resourceBlocks > 0 && config.allocationSymbols > 0)
        require(config.allocationStartSymbol >= 0 && config.allocationStartSymbol + config.allocationSymbols <= config.symbolsPerSlot)
        require(config.ports.isNotEmpty())
        require(config.nSCID in 0..1 && config.nId in 0..65535)
        val l0 = when (config.mappingType) {
            MappingType.A -> if (config.typeAPosition3) 3 else 2
            MappingType.B -> 0
        }
        val ld = config.allocationSymbols
        val base = singlePositions(config.mappingType, ld, l0, config.additionalPosition)
            .filter { it >= config.allocationStartSymbol && it < config.allocationStartSymbol + config.allocationSymbols }
        val timePositions = if (config.maxLength == MaxLength.LEN1) base else base.flatMap { intArrayOf(it, it + 1) }.distinct().toIntArray()
        val out = ArrayList<Resource>()
        val seqMap = LinkedHashMap<Int, Array<NrDmrsMimoV90.Complex>>()
        val initMap = LinkedHashMap<Int, Long>()
        val nREPerSymbol = when (config.configurationType) { ConfigurationType.TYPE1 -> 6 * config.resourceBlocks; ConfigurationType.TYPE2 -> 4 * config.resourceBlocks }
        for (l in timePositions) {
            val seq = sequence(config.slot, l, config.nId, config.nSCID, nREPerSymbol, config.symbolsPerSlot)
            seqMap[l] = seq
            val init = cInit(config.slot, l, config.nId, config.nSCID, config.symbolsPerSlot)
            initMap[l] = init
            for (port in config.ports) {
                val info = portInfo(port, config.configurationType)
                var m = 0
                val lPrime = if (config.maxLength == MaxLength.LEN2 && l != timePositions.first()) 1 else 0
                val nGroups = if (config.configurationType == ConfigurationType.TYPE1) config.resourceBlocks else 2 * config.resourceBlocks
                for (n in 0 until nGroups) {
                    for (kp in 0..1) {
                        val k = if (config.configurationType == ConfigurationType.TYPE1) {
                            config.startSubcarrier + 12 * n + 2 * kp + info.delta
                        } else {
                            config.startSubcarrier + 6 * n + kp + info.delta
                        }
                        if (k >= config.startSubcarrier + 12 * config.resourceBlocks) continue
                        val value = seq[m.coerceAtMost(seq.lastIndex)]
                        val wf = info.wf[kp]
                        val wt = info.wt[lPrime.coerceAtMost(info.wt.lastIndex)]
                        out += Resource(port, k, l, NrDmrsMimoV90.Complex(value.re * wf * wt, value.im * wf * wt), init, m, info.lambda, kp, lPrime)
                        m++
                    }
                }
            }
        }
        return Result(out, timePositions, initMap, seqMap)
    }

    fun pdsch(config: Config): Result = generate(config)
    fun pusch(config: Config): Result = generate(config)
}
