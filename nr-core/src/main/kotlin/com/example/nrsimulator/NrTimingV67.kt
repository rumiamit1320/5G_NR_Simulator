package com.example.nrsimulator

/**
 * 3GPP NR timing primitives used by the V67 integration layer.
 *
 * For normal CP, one radio frame is 10 ms and one subframe is 1 ms.
 * With numerology mu, there are 2^mu slots per subframe and therefore
 * 10 * 2^mu slots per frame. This timing layer annotates the existing V66
 * execution without changing V1-V66 public APIs or radio calculations.
 */
data class NrTimingConfigV67(
    val scsKHz: Int = 30,
    val startFrame: Long = 0L,
    val startSlot: Long = 0L
) {
    val numerology: Int
        get() = when (scsKHz) {
            15 -> 0
            30 -> 1
            60 -> 2
            120 -> 3
            240 -> 4
            else -> throw IllegalArgumentException("Unsupported SCS: $scsKHz kHz")
        }

    val slotsPerSubframe: Int
        get() = 1 shl numerology

    val slotsPerFrame: Int
        get() = 10 * slotsPerSubframe

    val slotDurationUs: Double
        get() = 1000.0 / slotsPerSubframe
}

data class NrSlotTimingV67(
    val absoluteSlot: Long,
    val frame: Long,
    val subframe: Int,
    val slotInSubframe: Int,
    val slotInFrame: Int,
    val startTimeUs: Double,
    val endTimeUs: Double,
    val durationUs: Double,
    val symbolsPerSlot: Int = 14
)

data class NrTimedSlotResultV67(
    val timing: NrSlotTimingV67,
    val radio: NrIntegratedSlotV66
)

data class NrTimedNetworkResultV67(
    val timing: NrTimingConfigV67,
    val radio: NrIntegratedNetworkResultV66,
    val slotResults: List<NrTimedSlotResultV67>
)

object NrTimingV67 {
    fun slot(config: NrTimingConfigV67, absoluteSlot: Long): NrSlotTimingV67 {
        require(absoluteSlot >= 0L) { "absoluteSlot must be non-negative" }
        val slotInFrame = (absoluteSlot % config.slotsPerFrame).toInt()
        val frame = absoluteSlot / config.slotsPerFrame
        val subframe = slotInFrame / config.slotsPerSubframe
        val slotInSubframe = slotInFrame % config.slotsPerSubframe
        val startUs = (config.startFrame * 10_000L).toDouble() +
            (absoluteSlot - config.startSlot) * config.slotDurationUs
        return NrSlotTimingV67(
            absoluteSlot = absoluteSlot,
            frame = frame,
            subframe = subframe,
            slotInSubframe = slotInSubframe,
            slotInFrame = slotInFrame,
            startTimeUs = startUs,
            endTimeUs = startUs + config.slotDurationUs,
            durationUs = config.slotDurationUs
        )
    }

    fun annotate(
        radio: NrIntegratedNetworkResultV66,
        config: NrTimingConfigV67 = NrTimingConfigV67(scsKHz = 30)
    ): NrTimedNetworkResultV67 {
        require(radio.slotResults.isNotEmpty()) { "V66 result contains no slots" }
        val timed = radio.slotResults.map { result ->
            val absoluteSlot = config.startSlot + result.slotIndex
            NrTimedSlotResultV67(slot(config, absoluteSlot), result)
        }
        return NrTimedNetworkResultV67(config, radio, timed)
    }

    fun run(
        config: NrIntegratedNetworkConfigV66 = NrIntegratedNetworkConfigV66(),
        timing: NrTimingConfigV67 = NrTimingConfigV67(scsKHz = config.scsKHz)
    ): NrTimedNetworkResultV67 {
        require(timing.scsKHz == config.scsKHz) {
            "Timing SCS (${timing.scsKHz} kHz) must match V66 SCS (${config.scsKHz} kHz)"
        }
        return annotate(NrIntegratedNetworkV66.run(config), timing)
    }
}
