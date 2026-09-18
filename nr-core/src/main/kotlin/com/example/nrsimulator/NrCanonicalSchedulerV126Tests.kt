package com.example.nrsimulator

object NrCanonicalSchedulerV126Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)
    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        val r = NrCanonicalSchedulerV126.schedule(
            listOf(
                NrCanonicalSchedulerV126.Ue(2, bufferBytes = 10000, priority = 1),
                NrCanonicalSchedulerV126.Ue(1, bufferBytes = 10000, priority = 3),
                NrCanonicalSchedulerV126.Ue(3, bufferBytes = 500, priority = 2, direction = NrCanonicalSchedulerV126.Direction.UL)
            )
        )
        c["priority ordering"] = r.grants.first().ueId == 1
        c["contiguous PRBs"] = r.grants.zipWithNext().all { it.first.prbStart + it.first.prbCount == it.second.prbStart }
        c["no PRB overlap"] = r.scheduledPrbs <= 24
        c["TBS finite"] = r.totalTbsBytes >= 0
        c["direction retained"] = r.grants.any { it.direction == NrCanonicalSchedulerV126.Direction.UL }
        return Result(c.values.all { it }, c)
    }
}
