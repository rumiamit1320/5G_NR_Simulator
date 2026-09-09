package com.example.nrsimulator

/** Additive RRC transaction/configuration foundation; V1-V30 APIs are preserved. */
enum class NrRrcProcedureV31 { SETUP, SECURITY_MODE, CAPABILITY, RECONFIGURATION, RELEASE }
data class NrRrcTransactionV31(val id:Int,val procedure:NrRrcProcedureV31,val ueId:Int,val completed:Boolean=false)
data class NrRrcConfigV31(val version:Int=19,val cellId:Int=1,val pci:Int=42,val dlArfcn:Int=636666,val ulArfcn:Int=636666,val scsKHz:Int=30,val bandwidthPrbs:Int=52,val ssbPeriodMs:Int=20,val tddPattern:String="DDDDDDUUUU")
class NrRrcV31 {
    private var nextId=0
    fun begin(ueId:Int,procedure:NrRrcProcedureV31):NrRrcTransactionV31 { val t=NrRrcTransactionV31(nextId and 3,procedure,ueId,false); nextId++; return t }
    fun complete(t:NrRrcTransactionV31)=t.copy(completed=true)
    fun validate(c:NrRrcConfigV31):Boolean = c.version>=15 && c.cellId in 0..1007 && c.pci in 0..1007 && c.scsKHz in setOf(15,30,60,120) && c.bandwidthPrbs in 1..275
}
