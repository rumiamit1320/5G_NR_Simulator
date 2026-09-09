package com.example.nrsimulator

/** V45: release/certification gate. Simulation PASS is not a 3GPP certification claim. */
data class NrCommercialResultV45(val modules:Int,val passed:Int,val failed:Int,val blockers:List<String>,val certificationReady:Boolean)
object NrCommercialGateV45 {
    fun evaluate(r:NrConformanceReportV44):NrCommercialResultV45 {
        val blockers=ArrayList<String>()
        if(!r.pass) blockers += "Conformance cases failed"
        blockers += "Official 3GPP TTCN/conformance vectors, RF, interoperability and certification evidence are still required"
        return NrCommercialResultV45(r.passed+r.failed,r.passed,r.failed,blockers,false)
    }
}
