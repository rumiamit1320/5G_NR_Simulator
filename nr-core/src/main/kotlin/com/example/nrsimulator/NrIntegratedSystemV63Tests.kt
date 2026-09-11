package com.example.nrsimulator

object NrIntegratedSystemV63Tests {
 data class Result(val pass:Boolean,val checks:List<String>)
 fun run():Result{
  val r=NrIntegratedSystemV63.run(NrIntegratedSystemConfigV63(slots=4,ueCount=4,prbs=24,velocityKmh=30.0))
  val checks=listOf(
   "slot count" to (r.slotResults.size==4),
   "UE count" to (r.ueStates.size==4),
   "PRB conservation" to r.slotResults.all{s->s.ueStates.sumOf{it.allocatedPrbs}==24},
   "PHY executed" to (r.ueStates.all{it.phyCrcPassRate>=0.0 && it.phyCrcPassRate<=1.0 && it.phyBer.isFinite()}),
   "system fairness" to (r.systemFairness in 0.0..1.0),
   "system throughput finite" to r.totalThroughputMbps.isFinite()
  ).map{"${it.first}: ${if(it.second)"PASS" else "FAIL"}"}
  return Result(checks.all{it.endsWith("PASS")},checks)
 }
}
