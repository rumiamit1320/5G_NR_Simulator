package com.example.nrsimulator

/** V37: expanded RLC TM/UM/AM facade. V31 AM state is retained and extended with windows/timers. */
enum class NrRlcModeV37 { TM,UM,AM }
data class NrRlcConfigV37(val mode:NrRlcModeV37,val snBits:Int=12,val windowSize:Int=2048,val tReassembly:Int=35,val tPollRetransmit:Int=45)
data class NrRlcEntityV37(val config:NrRlcConfigV37,val txSn:Int=0,val rxSn:Int=0,val reassembly:Map<Int,ByteArray> =emptyMap(),val retransmit:Set<Int> =emptySet())
object NrRlcV37 {
    fun enqueue(e:NrRlcEntityV37,payload:ByteArray):Pair<NrRlcEntityV37,NrRlcAmPduV31>{ val p=NrRlcAmPduV31(e.txSn,0,payload,true,true,true); return e.copy(txSn=(e.txSn+1)%(1 shl e.config.snBits),retransmit=e.retransmit+e.txSn) to p }
    fun status(e:NrRlcEntityV37,ackSn:Int,nack:IntArray):NrRlcEntityV37=e.copy(rxSn=ackSn,retransmit=e.retransmit-nack.toSet().firstOrNull().let{it?:-1})
}
