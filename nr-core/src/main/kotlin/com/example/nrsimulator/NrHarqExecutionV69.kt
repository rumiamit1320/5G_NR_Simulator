package com.example.nrsimulator

/**
 * V69 additive persistent HARQ execution model.
 *
 * V51 remains the HARQ state/RV primitive and V67 remains the authoritative
 * slot clock. V69 adds persistent per-UE process state and schedules actual
 * retransmission opportunities from V68 NACK events. The existing V64/V66 PHY
 * engines are intentionally not duplicated or replaced.
 *
 * This layer is an execution planner/reference model: it tracks transmission
 * numbers and RV progression and exposes the slot at which an existing PHY
 * execution path should be invoked. It does not fabricate PHY results.
 */
data class NrHarqExecutionConfigV69(
    val processesPerUe: Int = 16,
    val ackDelaySlots: Int = 4,
    val maxTransmissions: Int = 4
) {
    init {
        require(processesPerUe > 0)
        require(ackDelaySlots > 0)
        require(maxTransmissions in 1..4)
    }
}

data class NrHarqProcessExecutionV69(
    val ueId: Int,
    val processId: Int,
    val transmissionSlot: Int,
    val feedbackSlot: Int,
    val transmissionNumber: Int,
    val rv: Int,
    val ack: Boolean,
    val retransmissionSlot: Int?,
    val state: NrHarqStateV51
)

data class NrHarqExecutionResultV69(
    val config: NrHarqExecutionConfigV69,
    val transmissions: List<NrHarqProcessExecutionV69>,
    val retransmissionCount: Int,
    val ackCount: Int,
    val nackCount: Int,
    val exhaustedCount: Int
)

object NrHarqExecutionV69 {
    private fun nextRv(rv: Int): Int = when (rv) {
        0 -> 2
        2 -> 3
        3 -> 1
        else -> 0
    }

    /**
     * Execute the persistent HARQ timing state over already-observed PHY
     * outcomes. A NACK creates a real second-or-later transmission event at
     * feedbackSlot + 1, but its PHY outcome must come from the existing PHY
     * engine in a later integration layer rather than being invented here.
     */
    fun run(
        timed: NrTimedNetworkResultV67,
        config: NrHarqExecutionConfigV69 = NrHarqExecutionConfigV69()
    ): NrHarqExecutionResultV69 {
        val active = HashMap<Pair<Int, Int>, Int>()
        val rvByProcess = HashMap<Pair<Int, Int>, Int>()
        val events = ArrayList<NrHarqProcessExecutionV69>()

        for (slotResult in timed.slotResults) {
            val slot = slotResult.timing.absoluteSlot.toInt()
            for (ue in slotResult.radio.ueStates) {
                if (ue.allocatedPrbs <= 0) continue
                val processId = ((ue.ueId - 1) + slot) % config.processesPerUe
                val key = ue.ueId to processId
                val number = active.getOrDefault(key, 0) + 1
                val rv = rvByProcess.getOrDefault(key, 0)
                val feedbackSlot = slot + config.ackDelaySlots
                val ack = ue.crcPass
                val nextSlot = if (!ack && number < config.maxTransmissions) feedbackSlot + 1 else null
                val state = if (ack) NrHarqStateV51.ACKED
                else if (number < config.maxTransmissions) NrHarqStateV51.RETX
                else NrHarqStateV51.NACKED

                events += NrHarqProcessExecutionV69(
                    ueId = ue.ueId,
                    processId = processId,
                    transmissionSlot = slot,
                    feedbackSlot = feedbackSlot,
                    transmissionNumber = number,
                    rv = rv,
                    ack = ack,
                    retransmissionSlot = nextSlot,
                    state = state
                )

                if (ack || nextSlot == null) {
                    active.remove(key)
                    rvByProcess.remove(key)
                } else {
                    active[key] = number
                    rvByProcess[key] = nextRv(rv)
                }
            }
        }

        return NrHarqExecutionResultV69(
            config = config,
            transmissions = events,
            retransmissionCount = events.count { it.transmissionNumber > 1 },
            ackCount = events.count { it.ack },
            nackCount = events.count { !it.ack },
            exhaustedCount = events.count { !it.ack && it.transmissionNumber >= config.maxTransmissions }
        )
    }
}
