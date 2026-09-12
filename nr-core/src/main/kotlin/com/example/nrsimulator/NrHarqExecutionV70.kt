package com.example.nrsimulator

/**
 * V70 additive HARQ execution adapter.
 *
 * V51 remains the HARQ state/RV primitive, V66 remains the authoritative PHY
 * execution, and V67 remains the authoritative NR slot clock. V70 adds a
 * persistent per-UE/process scheduler which consumes the CRC outcome produced
 * by V66 at both initial and retransmission opportunities.
 *
 * A retransmission never fabricates a CRC result: the adapter obtains the
 * result from an existing V66 slot execution. V51 is used for state/RV/soft
 * combining bookkeeping. This is therefore an execution-level HARQ model;
 * it does not replace V64/V66 with a second PHY implementation.
 */
data class NrHarqExecutionConfigV70(
    val processesPerUe: Int = 16,
    val ackDelaySlots: Int = 4,
    val maxTransmissions: Int = 4,
    val extraSlotsForRetransmissions: Int = 16
) {
    init {
        require(processesPerUe > 0)
        require(ackDelaySlots > 0)
        require(maxTransmissions in 1..4)
        require(extraSlotsForRetransmissions >= 0)
    }
}

data class NrHarqExecutionEventV70(
    val ueId: Int,
    val processId: Int,
    val tbId: Long,
    val transmissionSlot: Int,
    val feedbackSlot: Int,
    val transmissionNumber: Int,
    val rv: Int,
    val ack: Boolean,
    val retransmission: Boolean,
    val state: NrHarqStateV51,
    val softBuffer: Double
)

data class NrHarqExecutionResultV70(
    val config: NrHarqExecutionConfigV70,
    val events: List<NrHarqExecutionEventV70>,
    val retransmissionCount: Int,
    val ackCount: Int,
    val nackCount: Int,
    val exhaustedCount: Int
)

object NrHarqExecutionV70 {
    private fun nextRv(rv: Int): Int = when (rv) {
        0 -> 2
        2 -> 3
        3 -> 1
        else -> 0
    }

    private data class ProcessState(
        val tbId: Long,
        val process: NrHarqProcessV51,
        val nextRv: Int,
        val dueSlot: Int
    )

    /**
     * Runs V66 for the requested simulation horizon, then executes HARQ over
     * that already-produced PHY timeline. The extra tail provides slots in
     * which NACKed transport blocks can actually be retransmitted.
     */
    fun run(
        config: NrIntegratedNetworkConfigV66 = NrIntegratedNetworkConfigV66(),
        harq: NrHarqExecutionConfigV70 = NrHarqExecutionConfigV70()
    ): NrHarqExecutionResultV70 {
        val baseSlots = config.slots.coerceIn(1, 1000)
        val totalSlots = (baseSlots + harq.extraSlotsForRetransmissions).coerceAtMost(1000)
        val radio = NrIntegratedNetworkV66.run(config.copy(slots = totalSlots))

        val active = HashMap<Pair<Int, Int>, ProcessState>()
        val events = ArrayList<NrHarqExecutionEventV70>()
        var nextTbId = 1L

        for (slotResult in radio.slotResults) {
            val slot = slotResult.slotIndex
            val byUe = slotResult.ueStates.associateBy { it.ueId }

            // Feedback becomes available after ackDelaySlots. A retransmission
            // due at this slot has priority over a new TB on the same process.
            val due = active.entries
                .filter { it.value.dueSlot == slot }
                .sortedBy { it.key.first }

            val consumed = HashSet<Pair<Int, Int>>()
            for ((key, pending) in due) {
                val ue = byUe[key.first] ?: continue
                if (ue.allocatedPrbs <= 0) continue
                val transmissionNumber = pending.process.txCount + 1
                if (transmissionNumber > harq.maxTransmissions) {
                    active.remove(key)
                    continue
                }

                val ack = ue.crcPass
                val updated = if (ack) {
                    pending.process.copy(state = NrHarqStateV51.ACKED)
                } else {
                    NrHarqV51.feedback(pending.process, false)
                }
                val soft = NrHarqV51.combine(pending.process.soft, 1.0)
                val retrySlot = if (!ack && transmissionNumber < harq.maxTransmissions) {
                    slot + harq.ackDelaySlots + 1
                } else null
                val state = if (ack) NrHarqStateV51.ACKED
                else if (retrySlot != null) NrHarqStateV51.RETX
                else NrHarqStateV51.NACKED

                events += NrHarqExecutionEventV70(
                    ueId = ue.ueId,
                    processId = key.second,
                    tbId = pending.tbId,
                    transmissionSlot = slot,
                    feedbackSlot = slot + harq.ackDelaySlots,
                    transmissionNumber = transmissionNumber,
                    rv = pending.nextRv,
                    ack = ack,
                    retransmission = true,
                    state = state,
                    softBuffer = soft
                )
                consumed += key
                if (ack || retrySlot == null) active.remove(key)
                else active[key] = ProcessState(
                    tbId = pending.tbId,
                    process = updated.copy(state = NrHarqStateV51.RETX),
                    nextRv = nextRv(pending.nextRv),
                    dueSlot = retrySlot
                )
            }

            // New transmissions use the existing V66 CRC outcome. Do not start
            // a new TB on a process which is still waiting for HARQ feedback.
            for (ue in slotResult.ueStates) {
                if (ue.allocatedPrbs <= 0) continue
                val processId = ((ue.ueId - 1) + slot) % harq.processesPerUe
                val key = ue.ueId to processId
                if (key in consumed || active.containsKey(key)) continue

                val process = NrHarqV51.start(NrHarqProcessV51(processId))
                val ack = ue.crcPass
                val state = if (ack) NrHarqStateV51.ACKED else NrHarqStateV51.RETX
                val retrySlot = if (!ack && harq.maxTransmissions > 1) {
                    slot + harq.ackDelaySlots + 1
                } else null
                val tbId = nextTbId++
                events += NrHarqExecutionEventV70(
                    ueId = ue.ueId,
                    processId = processId,
                    tbId = tbId,
                    transmissionSlot = slot,
                    feedbackSlot = slot + harq.ackDelaySlots,
                    transmissionNumber = 1,
                    rv = 0,
                    ack = ack,
                    retransmission = false,
                    state = state,
                    softBuffer = if (ack) 0.0 else 1.0
                )
                if (!ack && retrySlot != null) {
                    active[key] = ProcessState(
                        tbId = tbId,
                        process = process,
                        nextRv = 2,
                        dueSlot = retrySlot
                    )
                }
            }
        }

        return NrHarqExecutionResultV70(
            config = harq,
            events = events,
            retransmissionCount = events.count { it.retransmission },
            ackCount = events.count { it.ack },
            nackCount = events.count { !it.ack },
            exhaustedCount = active.values.count { it.process.txCount >= harq.maxTransmissions }
        )
    }
}
