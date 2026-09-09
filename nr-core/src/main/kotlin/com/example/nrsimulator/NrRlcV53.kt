package com.example.nrsimulator

enum class NrRlcModeV53{TM,UM,AM}
data class NrRlcStatusV53(val ackSn:Int,val nackSn:List<Int>,val poll:Boolean)
data class NrRlcEntityV53(val mode:NrRlcModeV53,val snBits:Int=12,val txSn:Int=0,val rxNext:Int=0,val txWindow:Int=0,val rxWindow:Int=0,val outstanding:Set<Int> =emptySet(),val timer:Int=0)
object NrRlcV53{fun transmit(e:NrRlcEntityV53,payload:ByteArray):Pair<NrRlcEntityV53,NrRlcAmPduV31>{val sn=e.txSn%(1 shl e.snBits);return e.copy(txSn=sn+1,outstanding=e.outstanding+sn,timer=35) to NrRlcAmPduV31(sn,0,payload,true,true,true)};fun status(e:NrRlcEntityV53,s:NrRlcStatusV53)=e.copy(rxNext=s.ackSn,outstanding=e.outstanding-s.nackSn.toSet(),timer=if(s.nackSn.isEmpty())0 else e.timer);fun tick(e:NrRlcEntityV53)=e.copy(timer=maxOf(0,e.timer-1))}
