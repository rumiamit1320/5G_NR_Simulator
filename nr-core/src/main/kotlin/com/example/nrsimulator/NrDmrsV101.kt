package com.example.nrsimulator

import kotlin.math.sqrt

/** V101 additive NR DM-RS sequence and normal-CP resource mapping primitives. */
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

    /** TS 38.211 5.2.1 Gold sequence c(n). */
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

    fun cInit(slot: Int, symbol: Int, nId: Int, nSCID: Int, symbolsPerSlot: Int = 14): Long {
        require(slot >= 0 && symbol >= 0 && nId in 0..65535 && nSCID in 0..1)
        require(symbolsPerSlot in 12..14)
        val a = symbolsPerSlot.toLong() * slot + symbol + 1L
        return ((1L shl 17) * a * (2L * nId + 1L) + 2L * nId + nSCID) and 0x7fffffffL
    }

    fun sequence(slot: Int, symbol: Int, nId: Int, nSCID: Int, length: Int, symbolsPerSlot: Int = 14): Array<NrDmrsMimoV90.Complex> {
        val c = gold(cInit(slot, symbol, nId, nSCID, symbolsPerSlot), 2 * length)
        val scale = 1.0 / sqrt(2.0)
        return Array(length) { m -> NrDmrsMimoV90.Complex((1 - 2 * c[2 * m]) * scale, (1 - 2 * c[2 * m + 1]) * scale) }
    }

    private fun portInfo(port: Int, type: ConfigurationType): PortInfo {
        return if (type == ConfigurationType.TYPE1) {
            when (port) {
                1000 -> PortInfo(0, 0, intArrayOf(1, 1), intArrayOf(1, 1))
                1001 -> PortInfo(0, 0, intArrayOf(1, -1), intArrayOf(1, 1))
                1002 -> PortInfo(1, 1, intArrayOf(1, 1), intArrayOf(1, 1))
                1003 -> PortInfo(1, 1, intArrayOf(1, -1), intArrayOf(1, 1))
                1004 -> PortInfo(0, 0, intArrayOf(1, 1), intArrayOf(1, -1))
                1005 -> PortInfo(0, 0, intArrayOf(1, -1), intArrayOf(1, -1))
                1006 -> PortInfo(1, 1, intArrayOf(1, 1), intArrayOf(1, -1))
                1007 -> PortInfo(1, 1, intArrayOf(1, -1), intArrayOf(1, -1))
                else -> error("unsupported TYPE1 DM-RS port: $port")
            }
        } else {
            when (port) {
                1000 -> PortInfo(0, 0, intArrayOf(1, 1), intArrayOf(1, 1))
                1001 -> PortInfo(0, 0, intArrayOf(1, -1), intArrayOf(1, 1))
                1002 -> PortInfo(1, 2, intArrayOf(1, 1), intArrayOf(1, 1))
                1003 -> PortInfo(1, 2, intArrayOf(1, -1), intArrayOf(1, 1))
                1004 -> PortInfo(2, 4, intArrayOf(1, 1), intArrayOf(1, 1))
                1005 -> PortInfo(2, 4, intArrayOf(1, -1), intArrayOf(1, 1))
                1006 -> PortInfo(0, 0, intArrayOf(1, 1), intArrayOf(1, -1))
                1007 -> PortInfo(0, 0, intArrayOf(1, -1), intArrayOf(1, -1))
                1008 -> PortInfo(1, 2, intArrayOf(1, 1), intArrayOf(1, -1))
                1009 -> PortInfo(1, 2, intArrayOf(1, -1), intArrayOf(1, -1))
                1010 -> PortInfo(2, 4, intArrayOf(1, 1), intArrayOf(1, -1))
                1011 -> PortInfo(2, 4, intArrayOf(1, -1), intArrayOf(1, -1))
                else -> error("unsupported TYPE2 DM-RS port: $port")
            }
        }
    }

    private fun dmrsSymbols(mapping: MappingType, ld: Int, l0: Int, add: AdditionalPosition): IntArray {
        val result: IntArray = when (mapping) {
            MappingType.A -> when (ld) {
                in 3..7 -> intArrayOf(l0)
                8, 9 -> if (add == AdditionalPosition.POS0) intArrayOf(l0) else intArrayOf(l0, 7)
                10, 11 -> when (add) {
                    AdditionalPosition.POS0 -> intArrayOf(l0)
                    AdditionalPosition.POS1 -> intArrayOf(l0, 9)
                    else -> intArrayOf(l0, 6, 9)
                }
                12 -> when (add) {
                    AdditionalPosition.POS0 -> intArrayOf(l0)
                    AdditionalPosition.POS1 -> intArrayOf(l0, 9)
                    AdditionalPosition.POS2 -> intArrayOf(l0, 6, 9)
                    AdditionalPosition.POS3 -> intArrayOf(l0, 5, 8, 11)
                }
                else -> when (add) {
                    AdditionalPosition.POS0 -> intArrayOf(l0)
                    AdditionalPosition.POS1 -> intArrayOf(l0, 11)
                    AdditionalPosition.POS2 -> intArrayOf(l0, 7, 11)
                    AdditionalPosition.POS3 -> intArrayOf(l0, 5, 8, 11)
                }
            }
            MappingType.B -> when (ld) {
                2, 3, 4 -> intArrayOf(l0)
                5, 6, 7 -> if (add == AdditionalPosition.POS0) intArrayOf(l0) else intArrayOf(l0, 4)
                8 -> when (add) {
                    AdditionalPosition.POS0 -> intArrayOf(l0)
                    AdditionalPosition.POS1 -> intArrayOf(l0, 6)
                    else -> intArrayOf(l0, 3, 6)
                }
                9, 10 -> when (add) {
                    AdditionalPosition.POS0 -> intArrayOf(l0)
                    AdditionalPosition.POS1 -> intArrayOf(l0, 7)
                    else -> intArrayOf(l0, 4, 7)
                }
                11 -> when (add) {
                    AdditionalPosition.POS0 -> intArrayOf(l0)
                    AdditionalPosition.POS1 -> intArrayOf(l0, 8)
                    AdditionalPosition.POS2 -> intArrayOf(l0, 4, 8)
                    AdditionalPosition.POS3 -> intArrayOf(l0, 3, 6, 9)
                }
                else -> when (add) {
                    AdditionalPosition.POS0 -> intArrayOf(l0)
                    AdditionalPosition.POS1 -> intArrayOf(l0, 9)
                    AdditionalPosition.POS2 -> intArrayOf(l0, 5, 9)
                    AdditionalPosition.POS3 -> intArrayOf(l0, 3, 6, 9)
                }
            }
        }
        return result.distinct().toIntArray()
    }

    fun generate(config: Config): Result {
        require(config.resourceBlocks > 0 && config.allocationSymbols > 0)
        require(config.allocationStartSymbol >= 0 && config.allocationStartSymbol + config.allocationSymbols <= config.symbolsPerSlot)
        require(config.ports.isNotEmpty())
        require(config.nSCID in 0..1 && config.nId in 0..65535)

        val l0 = when (config.mappingType) {
            MappingType.A -> if (config.typeAPosition3) 3 else 2
            MappingType.B -> config.allocationStartSymbol
        }
        val candidates = dmrsSymbols(config.mappingType, config.allocationSymbols, l0, config.additionalPosition)
        val filtered = candidates.filter { it >= config.allocationStartSymbol && it < config.allocationStartSymbol + config.allocationSymbols }
        val timePositions: IntArray = if (config.maxLength == MaxLength.LEN1) {
            filtered.toIntArray()
        } else {
            val list = ArrayList<Int>()
            for (l in filtered) {
                list.add(l)
                if (l + 1 < config.symbolsPerSlot) list.add(l + 1)
            }
            list.distinct().toIntArray()
        }

        val resources = ArrayList<Resource>()
        val seqMap = LinkedHashMap<Int, Array<NrDmrsMimoV90.Complex>>()
        val initMap = LinkedHashMap<Int, Long>()
        val sequenceLength = when (config.configurationType) {
            ConfigurationType.TYPE1 -> 6 * config.resourceBlocks
            ConfigurationType.TYPE2 -> 4 * config.resourceBlocks
        }

        for (l in timePositions) {
            val seq = sequence(config.slot, l, config.nId, config.nSCID, sequenceLength, config.symbolsPerSlot)
            val init = cInit(config.slot, l, config.nId, config.nSCID, config.symbolsPerSlot)
            seqMap[l] = seq
            initMap[l] = init
            for (port in config.ports) {
                val info = portInfo(port, config.configurationType)
                var m = 0
                val lPrime = if (config.maxLength == MaxLength.LEN2 && l != timePositions.first()) 1 else 0
                when (config.configurationType) {
                    ConfigurationType.TYPE1 -> for (rb in 0 until config.resourceBlocks) {
                        for (mGroup in 0..2) for (kPrime in 0..1) {
                            val k = config.startSubcarrier + 12 * rb + 4 * mGroup + 2 * kPrime + info.delta
                            if (k < config.startSubcarrier || k >= config.startSubcarrier + 12 * config.resourceBlocks) continue
                            val value = seq[m]
                            val wf = info.wf[kPrime]
                            val wt = info.wt[lPrime.coerceAtMost(info.wt.lastIndex)]
                            resources.add(Resource(port, k, l, NrDmrsMimoV90.Complex(value.re * wf * wt, value.im * wf * wt), init, m, info.lambda, kPrime, lPrime))
                            m++
                        }
                    }
                    ConfigurationType.TYPE2 -> for (rb in 0 until config.resourceBlocks) {
                        for (mGroup in 0..1) for (kPrime in 0..1) {
                            val k = config.startSubcarrier + 12 * rb + 6 * mGroup + kPrime + info.delta
                            if (k < config.startSubcarrier || k >= config.startSubcarrier + 12 * config.resourceBlocks) continue
                            val value = seq[m]
                            val wf = info.wf[kPrime]
                            val wt = info.wt[lPrime.coerceAtMost(info.wt.lastIndex)]
                            resources.add(Resource(port, k, l, NrDmrsMimoV90.Complex(value.re * wf * wt, value.im * wf * wt), init, m, info.lambda, kPrime, lPrime))
                            m++
                        }
                    }
                }
            }
        }
        return Result(resources, timePositions, initMap, seqMap)
    }

    fun pdsch(config: Config): Result = generate(config)
    fun pusch(config: Config): Result = generate(config)
}
