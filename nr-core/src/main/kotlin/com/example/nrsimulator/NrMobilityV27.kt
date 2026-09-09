package com.example.nrsimulator
import kotlin.math.*
data class NrCellV27(val id:Int,val x:Double,val y:Double,val txPowerDbm:Double=30.0)
data class NrHandoverV27Result(val source:Int,val target:Int,val sourceRsrp:Double,val targetRsrp:Double,val triggered:Boolean,val interruptionMs:Double,val pass:Boolean,val note:String)
class NrMobilityV27 { fun evaluate(ueX:Double,ueY:Double,cells:List<NrCellV27>,offsetDb:Double=3.0):NrHandoverV27Result { val ranked=cells.map{c->val d=hypot(ueX-c.x,ueY-c.y).coerceAtLeast(0.1);c to c.txPowerDbm-20*log10(d)}.sortedByDescending{it.second};val s=ranked.first();val t=ranked.getOrNull(1)?:s;val trig=t.second>s.second+offsetDb;return NrHandoverV27Result(s.first.id,t.first.id,s.second,t.second,trig,if(trig)10.0 else 0.0,true,"Mobility/measurement/handover decision reference; event criteria can be extended to full RRC measurement configuration.") } }
