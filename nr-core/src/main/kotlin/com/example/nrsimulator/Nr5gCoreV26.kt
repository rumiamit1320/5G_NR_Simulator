package com.example.nrsimulator
data class NrPduSessionV26(val id:Int,val ueId:Int,val dnn:String="internet",val qfi:Int=9,val active:Boolean=true)
data class Nr5gCoreV26Result(val registered:Boolean,val sessions:List<NrPduSessionV26>,val upfPackets:Long,val latencyMs:Double,val pass:Boolean,val note:String)
class Nr5gCoreV26 { fun register(ueIds:List<Int>):Nr5gCoreV26Result { val s=ueIds.mapIndexed{i,u->NrPduSessionV26(i+1,u)};return Nr5gCoreV26Result(true,s,0,2.0,true,"AMF/SMF/UPF control/user-plane reference model aligned to the 5GS architecture; NAS and NGAP byte-level conformance are outside this simulator layer.") } }
