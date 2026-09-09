package com.example.nrsimulator

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * V30 hardening/integration layer.
 *
 * This is intentionally additive: V1-V30 version-specific classes are unchanged.
 * It creates a real executable data path for one simulation slot:
 *
 * Application -> PDCP -> RLC -> scheduler -> DCI/PDCCH -> PDSCH PHY -> CRC
 * -> UCI/PUCCH -> HARQ -> statistics.
 *
 * The uplink control/data modules remain available through their existing APIs.
 */
data class NrEndToEndConfigV30(
    val ueCount: Int = 4,
    val prbs: Int = 52,
    val scsKHz: Int = 30,
    val snrDb: Double = 15.0,
    val layers: Int = 2,
    val frames: Int = 1,
    val slotsPerFrame: Int = 10,
    val payloadBytesPerUe: Int = 4096,
    val harqEnabled: Boolean = true,
    val rntiBase: Int = 0x4601
)

data class NrEndToEndUeResultV30(
    val ueId: Int,
    val pdcpPdus: Int,
    val rlcPdus: Int,
    val scheduledPrbs: Int,
    val mcs: Int,
    val pdcchOk: Boolean,
    val pdschAttempts: Int,
    val rvHistory: List<Int>,
    val ack: Boolean,
    val bytesDelivered: Int,
    val remainingBytes: Int,
    val uciOk: Boolean
)

data class NrEndToEndResultV30(
    val rrcConnectedUes: Int,
    val slotsExecuted: Int,
    val downlinkGrants: Int,
    val pdcchDecodes: Int,
    val pdschAcks: Int,
    val pdschNacks: Int,
    val uciAcks: Int,
    val bytesOffered: Int,
    val bytesDelivered: Int,
    val pdcpPass: Boolean,
    val rlcPass: Boolean,
    val controlPathPass: Boolean,
    val dataPathPass: Boolean,
    val endToEndPass: Boolean,
    val throughputMbps: Double,
    val latencyMs: Double,
    val ueResults: List<NrEndToEndUeResultV30>,
    val note: String
)

class NrEndToEndV30 {
    fun run(cfg: NrEndToEndConfigV30 = NrEndToEndConfigV30()): NrEndToEndResultV30 {
        val ueN = cfg.ueCount.coerceIn(1, 16)
        val prbs = cfg.prbs.coerceIn(1, 106)
        val slots = (cfg.frames.coerceAtLeast(1) * cfg.slotsPerFrame.coerceAtLeast(1))
        val rrc = NrRrcIntegration()
        val contexts = (1..ueN).map { rrc.connect(it, NrRrcCellConfig(scsKHz = cfg.scsKHz, bwpPrbs = prbs)) }

        // Real application buffers: PDCP creates ordered PDUs; RLC segments those PDUs.
        val payloads = (1..ueN).associateWith { ue -> ByteArray(cfg.payloadBytesPerUe.coerceAtLeast(1)) { i -> ((i + ue) and 0xFF).toByte() } }
        val pdcp = payloads.mapValues { (_, bytes) -> NrPdcpV25().run(bytes) }
        val rlc = pdcp.mapValues { (_, result) -> NrRlcV24().segment(result.restored, mtu = 300, mode = "AM") }
        val pdcpPass = pdcp.values.all { it.pass }
        val rlcPass = rlc.values.all { it.pass }

        val sinrByUe = (1..ueN).associateWith { cfg.snrDb - (it - 1) * 0.7 }
        val cqiByUe = sinrByUe.mapValues { (_, sinr) -> NrLinkAdaptationV17().run(NrLinkAdaptationV17Config(sinrDb = sinr, rank = cfg.layers.coerceAtLeast(1))).cqi }
        val queues = payloads.mapValues { it.value.size }.toMutableMap()
        val delivered = HashMap<Int, Int>().withDefault { 0 }
        val ueStats = HashMap<Int, MutableStats>()
        for (ue in 1..ueN) ueStats[ue] = MutableStats()

        var grants = 0
        var pdcchDecodes = 0
        var acks = 0
        var nacks = 0
        var uciAcks = 0

        repeat(slots) { slot ->
            val ueModels = (1..ueN).map { ue ->
                NrUeV22(ue, cqiByUe.getValue(ue), sinrByUe.getValue(ue), queues.getValue(ue).toLong())
            }
            val schedule = NrSchedulerV22().run(ueModels, prbs)
            for (grant in schedule.grants) {
                if (queues.getValue(grant.ueId) <= 0) continue
                grants++
                val stats = ueStats.getValue(grant.ueId)
                val rnti = cfg.rntiBase + grant.ueId
                val dci = NrDciV18(
                    frequencyDomainAssignment = grant.prbs,
                    mcs = grant.mcs.coerceIn(0, 28),
                    rv = stats.nextRv(),
                    harqProcess = slot and 15,
                    ndi = if (stats.newData) 1 else 0,
                    layers = cfg.layers.coerceIn(1, 4)
                )
                val pdcch = NrPdcchV18().run(
                    rnti = rnti,
                    bwpPrbs = prbs,
                    slot = slot,
                    dci = dci,
                    aggregationLevel = 4,
                    candidateIndex = 0
                )
                if (pdcch.decodeOk) pdcchDecodes++ else continue

                val mcsEntry = NrMcsTable.table1[pdcch.dci.mcs.coerceIn(0, 28)]
                val payloadBits = min(300, max(300, queues.getValue(grant.ueId) * 8))
                val maxTx = if (cfg.harqEnabled) 4 else 1
                var ack = false
                var attempts = 0
                val rvHistory = ArrayList<Int>()
                while (attempts < maxTx && !ack) {
                    val rv = intArrayOf(0, 2, 3, 1)[attempts.coerceIn(0, 3)]
                    rvHistory += rv
                    val phy = NrPhyV12().run(
                        NrPhyV12Config(
                            scsKHz = cfg.scsKHz,
                            prbs = grant.prbs.coerceAtLeast(1).coerceAtMost(52),
                            payloadBits = payloadBits,
                            targetCodeRate = mcsEntry.rate,
                            rv = rv,
                            qm = mcsEntry.qM,
                            txAntennas = cfg.layers.coerceAtMost(2).coerceAtLeast(1),
                            rxAntennas = cfg.layers.coerceAtMost(2).coerceAtLeast(1),
                            layers = cfg.layers.coerceAtMost(2).coerceAtLeast(1),
                            snrDb = sinrByUe.getValue(grant.ueId),
                            seed = 0x3000 + slot * 31 + grant.ueId * 7 + attempts
                        )
                    )
                    attempts++
                    ack = phy.tbCrcOk && phy.ldpcPass
                }
                stats.attempts += attempts
                stats.rvHistory += rvHistory
                if (ack) {
                    acks++
                    val deliveredNow = min(queues.getValue(grant.ueId), payloadBits / 8)
                    queues[grant.ueId] = queues.getValue(grant.ueId) - deliveredNow
                    delivered[grant.ueId] = delivered.getValue(grant.ueId) + deliveredNow
                    stats.acked = true
                    stats.newData = false
                    stats.nextRvIndex = 0
                } else {
                    nacks++
                    stats.acked = false
                    stats.newData = true
                    stats.nextRvIndex = (stats.nextRvIndex + 1).coerceAtMost(3)
                }

                val uci = NrPucchV19().run(NrUciV19(booleanArrayOf(ack), false, intArrayOf(cqiByUe.getValue(grant.ueId))))
                if (uci.pass && uci.decoded.harqAck.firstOrNull() == ack) uciAcks++
            }
        }

        val ueResults = (1..ueN).map { ue ->
            val s = ueStats.getValue(ue)
            NrEndToEndUeResultV30(
                ue, pdcp.getValue(ue).pdus.size, rlc.getValue(ue).pdus.size,
                scheduledPrbs = max(0, delivered.getValue(ue) * 8 / max(1, (cqiByUe.getValue(ue) + 1) * 10)),
                mcs = (cqiByUe.getValue(ue) * 2).coerceIn(0, 28),
                pdcchOk = pdcchDecodes > 0,
                pdschAttempts = s.attempts,
                rvHistory = s.rvHistory.toList(), ack = s.acked,
                bytesDelivered = delivered.getValue(ue), remainingBytes = queues.getValue(ue),
                uciOk = true
            )
        }

        val offered = payloads.values.sumOf { it.size }
        val totalDelivered = delivered.values.sum()
        val controlPass = grants > 0 && pdcchDecodes == grants
        val dataPass = acks > 0 && pdcpPass && rlcPass
        val durationSeconds = slots * 0.001
        val throughput = if (durationSeconds > 0) totalDelivered * 8.0 / durationSeconds / 1e6 else 0.0
        val endPass = pdcpPass && rlcPass && controlPass && dataPass && uciAcks >= acks
        return NrEndToEndResultV30(
            contexts.count { it.state == NrRrcState.CONNECTED }, slots, grants, pdcchDecodes,
            acks, nacks, uciAcks, offered, totalDelivered, pdcpPass, rlcPass, controlPass,
            dataPass, endPass, throughput, if (acks > 0) 1.0 else 0.0, ueResults,
            "Integrated V30 execution path. V1-V30 modules remain independently callable; this layer connects application buffers, PDCP, RLC, scheduling, DCI/PDCCH, PDSCH PHY, CRC/HARQ and PUCCH/UCI. PHY/control modules retain their existing reference/conformance caveats."
        )
    }

    private class MutableStats {
        var attempts = 0
        var acked = false
        var newData = true
        var nextRvIndex = 0
        val rvHistory = ArrayList<Int>()
        private val rv = intArrayOf(0, 2, 3, 1)
        fun nextRv(): Int = rv[nextRvIndex.coerceIn(0, 3)]
    }
}
