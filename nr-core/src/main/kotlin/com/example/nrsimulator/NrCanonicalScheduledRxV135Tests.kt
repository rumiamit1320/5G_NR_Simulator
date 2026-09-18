package com.example.nrsimulator

/** Deterministic V135 scheduled receiver regression suite. */
object NrCanonicalScheduledRxV135Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        val r = NrCanonicalScheduledRxV135.run(
            NrCanonicalScheduledRxV135.Config(snrDb = 45.0, dopplerHz = 0.0)
        )
        c["execution passes"] = r.passed
        c["multiple UE grants"] = r.grants.size >= 2
        c["one RX report per UE"] = r.ueReports.size == r.grants.size
        c["DMRS pilots present"] = r.ueReports.all { it.pilotCount > 0 }
        c["finite channel MSE"] = r.ueReports.all { it.channelMse.isFinite() }
        c["finite EVM/SINR"] = r.ueReports.all { it.evm.isFinite() && it.postSinrDb.isFinite() }
        c["LDPC recovery"] = r.ueReports.all { it.ldpcPassed }
        c["TB CRC recovery"] = r.ueReports.all { it.crcPassed }
        val d = NrCanonicalScheduledRxV135.run(
            NrCanonicalScheduledRxV135.Config(snrDb = 45.0, dopplerHz = 70.0, timeSeconds = 0.001)
        )
        c["time-varying channel changes"] = d.channelChangedWithTime
        c["time-varying RX remains valid"] = d.ueReports.all { it.channelMse.isFinite() && it.evm.isFinite() }
        return Result(c.values.all { it }, c)
    }
}
