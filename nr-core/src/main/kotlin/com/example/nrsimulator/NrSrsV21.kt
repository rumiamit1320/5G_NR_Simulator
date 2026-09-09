package com.example.nrsimulator
import kotlin.math.*
data class NrSrsV21Config(val ports:Int=2,val rbStart:Int=0,val rbCount:Int=52,val comb:Int=2,val cyclicShift:Int=0)
data class NrSrsV21Result(val rsrpDb:Double,val sinrDb:Double,val rank:Int,val preferredPort:Int,val pass:Boolean,val note:String)
class NrSrsV21 { fun run(channelSinrDb:Double=15.0,cfg:NrSrsV21Config=NrSrsV21Config()):NrSrsV21Result { val rank=if(channelSinrDb>10&&cfg.ports>1)2 else 1; val gain=10*log10(cfg.rbCount.coerceAtLeast(1).toDouble()); return NrSrsV21Result(-80.0+gain,channelSinrDb+min(3.0,gain/20),rank,cfg.cyclicShift%cfg.ports,true,"SRS sounding reference; full sequence generation and 38.211 resource mapping can be added without changing this interface.") } }
