package com.example.nrsimulator

enum class NrSplitPlaneV58{F1_C,F1_U,E1_C,E1_U,XN_C,XN_U,N2,N3}
data class NrUeContextV58(val ueId:String,val rnti:Int,val amfId:String?=null,val gnbId:String?=null)
data class NrSplitMessageV58(val plane:NrSplitPlaneV58,val ueId:String,val payload:ByteArray)
interface NrUeGnbAdapterV58{fun send(m:NrSplitMessageV58):Boolean}
class NrLoopbackSplitV58:NrUeGnbAdapterV58{val received=ArrayList<NrSplitMessageV58>();override fun send(m:NrSplitMessageV58):Boolean{received+=m;return true}}
object NrUeGnbV58{fun ueToGnb(ue:String,p:ByteArray)=NrSplitMessageV58(NrSplitPlaneV58.F1_U,ue,p);fun gnbToCore(ue:String,p:ByteArray)=NrSplitMessageV58(NrSplitPlaneV58.N3,ue,p)}
