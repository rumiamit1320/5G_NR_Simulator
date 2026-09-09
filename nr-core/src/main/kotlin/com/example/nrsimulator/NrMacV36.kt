package com.example.nrsimulator

/** V36: MAC procedure/timer foundation behind the existing scheduler. */
enum class NrMacTimerV36 { T300,T301,T304,T310,T311,BSR_RETX,SR_PROHIBIT,DRX_RETX }
data class NrMacHarqProcessV36(val id:Int,val rv:Int=0,val ndi:Int=0,val waiting:Boolean=false,val txCount:Int=0)
data class NrMacStateV36(val slot:Int=0,val harq:List<NrMacHarqProcessV36> =emptyList(),val timers:Map<NrMacTimerV36,Int> =emptyMap())
object NrMacV36 {
    fun tick(s:NrMacStateV36):NrMacStateV36= s.copy(slot=s.slot+1,timers=s.timers.mapValues{maxOf(0,it.value-1)})
    fun scheduleHarq(s:NrMacStateV36,id:Int,rv:Int):NrMacStateV36= s.copy(harq=(s.harq.filterNot{it.id==id}+NrMacHarqProcessV36(id,rv,1,true, (s.harq.find{it.id==id}?.txCount?:0)+1)).sortedBy{it.id})
    fun ack(s:NrMacStateV36,id:Int):NrMacStateV36=s.copy(harq=s.harq.map{if(it.id==id)it.copy(waiting=false)else it})
}
