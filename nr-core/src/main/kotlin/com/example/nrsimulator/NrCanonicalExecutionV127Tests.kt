package com.example.nrsimulator

object NrCanonicalExecutionV127Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)
    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        val r = NrCanonicalExecutionV127.run()
        c["integration passes"] = r.passed
        c["20 slots at 30 kHz"] = r.timing.slotsPerFrame == 20
        c["schedule has grants"] = r.schedule.grants.isNotEmpty()
        c["plans match grants"] = r.plans.size == r.schedule.grants.size
        c["data RE generated"] = r.totalDataRe > 0
        c["DMRS RE generated"] = r.totalDmrsRe > 0
        c["all plan symbols valid"] = r.plans.all { it.occupiedSymbols.all { s -> s in 0..13 } }
        return Result(c.values.all { it }, c)
    }
}
