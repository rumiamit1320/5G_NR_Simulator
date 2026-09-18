package com.example.nrsimulator

import kotlin.math.log2

/** Additive canonical NR numerology/timing layer. Existing V50 timing remains unchanged. */
object NrCanonicalNumerologyV123 {
    enum class Scs(val khz: Int) {
        SCS15(15), SCS30(30), SCS60(60), SCS120(120)
    }
    enum class CyclicPrefix { NORMAL }

    data class Config(
        val scs: Scs = Scs.SCS30,
        val frameIndex: Int = 0,
        val slotIndex: Int = 0,
        val symbolsPerSlot: Int = 14,
        val cyclicPrefix: CyclicPrefix = CyclicPrefix.NORMAL
    )
    data class Timing(
        val mu: Int,
        val scsKHz: Int,
        val subcarrierSpacingHz: Double,
        val slotsPerSubframe: Int,
        val slotsPerFrame: Int,
        val slotDurationUs: Double,
        val symbolDurationUs: Double,
        val symbolsPerSlot: Int,
        val absoluteSlot: Long
    )

    fun mu(scs: Scs): Int = log2(scs.khz / 15.0).toInt()
    fun slotsPerSubframe(scs: Scs): Int = 1 shl mu(scs)
    fun slotsPerFrame(scs: Scs): Int = 10 * slotsPerSubframe(scs)
    fun slotDurationUs(scs: Scs): Double = 1000.0 / slotsPerSubframe(scs)

    fun timing(config: Config = Config()): Timing {
        require(config.frameIndex >= 0)
        require(config.slotIndex in 0 until slotsPerFrame(config.scs))
        require(config.symbolsPerSlot == 14) { "V123 currently models normal CP only" }
        val d = slotDurationUs(config.scs)
        val n = config.symbolsPerSlot
        return Timing(
            mu(config.scs), config.scs.khz, config.scs.khz * 1000.0,
            slotsPerSubframe(config.scs), slotsPerFrame(config.scs), d, d / n, n,
            config.frameIndex.toLong() * slotsPerFrame(config.scs) + config.slotIndex
        )
    }

    fun slotInFrame(absoluteSlot: Long, scs: Scs): Int =
        Math.floorMod(absoluteSlot, slotsPerFrame(scs).toLong()).toInt()

    fun frameOf(absoluteSlot: Long, scs: Scs): Long =
        Math.floorDiv(absoluteSlot, slotsPerFrame(scs).toLong())
}
