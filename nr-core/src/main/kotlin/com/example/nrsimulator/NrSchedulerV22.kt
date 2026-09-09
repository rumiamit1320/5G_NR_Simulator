package com.example.nrsimulator
import kotlin.math.*
data class NrUeV22(val id:Int,val cqi:Int,val sinrDb:Double,val queueBits:Long=1_000_000,val weight:Double=1.0)
data class NrGrantV22(val ueId:Int,val prbs:Int,val mcs:Int,val metric:Double)
data class NrSchedulerV22Result(val grants:List<NrGrantV22>,val usedPrbs:Int,val fairness:Double,val throughputMbps:Double,val pass:Boolean,val note:String)
class NrSchedulerV22 { fun run(ues:List<NrUeV22>,totalPrbs:Int=106):NrSchedulerV22Result { if(ues.isEmpty())return NrSchedulerV22Result(emptyList(),0,1.0,0.0,true,"No UEs"); val metrics=ues.map{it to (it.weight*(it.cqi+1.0)/(1.0+it.queueBits/1e7))}.sortedByDescending{it.second}; val base=max(1,totalPrbs/ues.size); var used=0; val g=metrics.map{(u,m)->val p=min(base,totalPrbs-used);used+=p;NrGrantV22(u.id,p,(u.cqi*2).coerceIn(0,28),m)}; val x=g.map{it.prbs.toDouble()}; val mean=x.average(); val j=if(mean==0.0)1.0 else x.sumOf{it*it}/(x.size*x.sum()); val tp=g.sumOf{it.prbs*(it.mcs+1)}.toDouble()/100.0; return NrSchedulerV22Result(g,used,j,tp,true,"Proportional-fair-style multi-UE scheduler reference; replace metric policy without changing grant API.") } }
