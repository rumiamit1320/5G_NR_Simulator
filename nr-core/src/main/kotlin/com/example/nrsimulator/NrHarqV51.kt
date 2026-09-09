package com.example.nrsimulator

enum class NrHarqStateV51{IDLE,WAIT_ACK,ACKED,NACKED,RETX}
data class NrHarqProcessV51(val id:Int,val rv:Int=0,val ndi:Int=0,val txCount:Int=0,val state:NrHarqStateV51=NrHarqStateV51.IDLE,val soft:Double=0.0)
object NrHarqV51{private val rvSeq=intArrayOf(0,2,3,1);fun start(p:NrHarqProcessV51,ndi:Int=1)=p.copy(ndi=ndi,rv=rvSeq[0],txCount=1,state=NrHarqStateV51.WAIT_ACK);fun feedback(p:NrHarqProcessV51,ack:Boolean):NrHarqProcessV51=if(ack)p.copy(state=NrHarqStateV51.ACKED)else{val i=minOf(p.txCount,3);p.copy(rv=rvSeq[i],txCount=p.txCount+1,state=if(p.txCount>=4)NrHarqStateV51.NACKED else NrHarqStateV51.RETX,soft=p.soft+1.0)};fun combine(a:Double,b:Double)=a+b}
