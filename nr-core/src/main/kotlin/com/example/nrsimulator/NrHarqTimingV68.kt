package com.example.nrsimulator

/**
 * Additive timing-aware HARQ event layer.
 *
 * V51 remains the HARQ state machine and RV/soft-combining primitive. V67
 * remains the authoritative NR slot clock. V68 binds those two existing
 * abstractions to explicit transmission and ACK/NACK slots. It intentionally
 * does not replace V51 or modify the V64/V66 PHY execution path.
 */
data class NrHarqTimingConfigV68(
    val processesPerUe: Int = 16,
    val downlinkAckDelaySlots: Int = 4,
    val maxTransmissions: Int = 4
) {
    init {
        require(processesPerUe > 0)
        require(downlinkAckDelaySlots > 0)
        require(maxTransmissions in 1..4)
    }
}

data class NrHarqEventV68(
    val ueId: Int,
    val processId: Int,
    val transmissionSlot: Int,
    val feedbackSlot: Int,
    val nextTransmissionSlot: Int?,
    val ack: Boolean,
    val transmissionNumber: Int,
    val rv: Int,
    val state: NrHarqStateV51
)

data class NrHarqTimingResultV68(
    val config: NrHarqTimingConfigV68,
    val events: List<NrHarqEventV68>,
    val pendingFeedbackBySlot: Map<Int, List<NrHarqEventV68>>,
    val ackCount: Int,
    val nackCount: Int,
    val retransmissionCount: Int
)

object NrHarqTimingV68 {
    fun bind(
        timed: NrTimedNetworkResultV67,
        config: NrHarqTimingConfigV68 = NrHarqTimingConfigV68()
    ): NrHarqTimingResultV68 {
        val events = ArrayList<NrHarqEventV68>()
        for (slotResult in timed.slotResults) {
            for (ue in slotResult.radio.ueStates) {
                if (ue.allocatedPrbs <= 0) continue
                val processId = ((ue.ueId - 1) + slotResult.timing.absoluteSlot.toInt()) % config.processesPerUe
                val txSlot = slotResult.timing.absoluteSlot.toInt()
                val feedbackSlot = txSlot + config.downlinkAckDelaySlots
                val txNumber = 1
                // Preserve V51 semantics: start the existing process and apply
                // exactly one observed ACK/NACK. Timing only determines when
                // that feedback becomes due; it does not mutate the PHY result.
                val process = NrHarqV51.start(NrHarqProcessV51(processId))
                val feedbackState = NrHarqV51.feedback(process, ue.crcPass)
                val nextSlot = if (!ue.crcPass && txNumber < config.maxTransmissions) feedbackSlot + 1 else null
                events += NrHarqEventV68(
                    ueId = ue.ueId,
                    processId = processId,
                    transmissionSlot = txSlot,
                    feedbackSlot = feedbackSlot,
                    nextTransmissionSlot = nextSlot,
                    ack = ue.crcPass,
                    transmissionNumber = txNumber,
                    rv = if (ue.crcPass) process.rv else feedbackState.rv,
                    state = feedbackState.state
                )
            }
        }
        val pending = events.groupBy { it.feedbackSlot }
        return NrHarqTimingResultV68(
            config = config,
            events = events,
            pendingFeedbackBySlot = pending,
            ackCount = events.count { it.ack },
            nackCount = events.count { !it.ack },
            retransmissionCount = events.count { it.nextTransmissionSlot != null }
        )
    }

    fun run(
        timed: NrTimedNetworkResultV67,
        config: NrHarqTimingConfigV68 = NrHarqTimingConfigV68()
    ): NrHarqTimingResultV68 = bind(timed, config)
}
