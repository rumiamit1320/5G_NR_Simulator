package com.example.nrsimulator

enum class NrNasStateV56 { DEREGISTERED, REGISTRATION_REQUESTED, REGISTERED, DEREGISTRATION_REQUESTED, PDU_SESSION_REQUESTED }
enum class NrNasProcedureV56 { REGISTRATION, AUTHENTICATION, SECURITY_MODE, PDU_SESSION, DEREGISTRATION }
data class NrNasContextV56(val supi:String,val state:NrNasStateV56=NrNasStateV56.DEREGISTERED,val ksi:Int=0,val count:Int=0)
object NrNasV56 {
    fun step(c:NrNasContextV56,p:NrNasProcedureV56):NrNasContextV56 = when(p) {
        NrNasProcedureV56.REGISTRATION -> c.copy(state=NrNasStateV56.REGISTRATION_REQUESTED,count=c.count+1)
        NrNasProcedureV56.AUTHENTICATION -> c.copy(ksi=(c.ksi+1) and 7)
        NrNasProcedureV56.SECURITY_MODE -> c.copy(state=NrNasStateV56.REGISTERED)
        NrNasProcedureV56.PDU_SESSION -> c.copy(state=NrNasStateV56.PDU_SESSION_REQUESTED)
        NrNasProcedureV56.DEREGISTRATION -> c.copy(state=NrNasStateV56.DEREGISTRATION_REQUESTED)
    }
}
