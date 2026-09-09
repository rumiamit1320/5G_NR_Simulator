package com.example.nrsimulator

enum class NrMacCeV52{BSR,PHR,TA,DRX,SR,CRNTI}
data class NrLogicalChannelV52(val id:Int,val priority:Int,val lcg:Int,val bytes:Int)
data class NrMacPduV52(val ces:List<NrMacCeV52>,val channels:List<NrLogicalChannelV52>,val payloadBytes:Int)
object NrMacV52{fun multiplex(ch:List<NrLogicalChannelV52>,ces:List<NrMacCeV52>,budget:Int):NrMacPduV52{var left=budget;val out=ch.sortedBy{it.priority}.map{val n=minOf(left,maxOf(0,it.bytes));left-=n;it.copy(bytes=n)}.filter{it.bytes>0};return NrMacPduV52(ces,out,out.sumOf{it.bytes})};fun bsr(ch:List<NrLogicalChannelV52>)=ch.groupBy{it.lcg}.mapValues{it.value.sumOf{c->c.bytes}}}
