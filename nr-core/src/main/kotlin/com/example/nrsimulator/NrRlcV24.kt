package com.example.nrsimulator
data class NrRlcPduV24(val sn:Int,val payload:ByteArray,val poll:Boolean=false)
data class NrRlcV24Result(val pdus:List<NrRlcPduV24>,val reassembled:ByteArray,val retransmissions:Int,val pass:Boolean,val note:String)
class NrRlcV24 { fun segment(data:ByteArray,mtu:Int=512,mode:String="AM"):NrRlcV24Result { val list=ArrayList<NrRlcPduV24>();var off=0;var sn=0;while(off<data.size){val e=minOf(data.size,off+mtu);list+=NrRlcPduV24(sn++,data.copyOfRange(off,e),mode=="AM"&&e==data.size);off=e};val rec=list.flatMap{it.payload.toList()}.toByteArray();return NrRlcV24Result(list,rec,0,rec.contentEquals(data),"RLC TM/UM/AM-oriented segmentation/reassembly reference; AM status/retransmission state is intentionally isolated for later refinement.") } }
