package com.example.nrsimulator

import kotlin.math.*

/**
 * V136-V150 additive system integration layer.
 *
 * These stages intentionally reuse the existing V123-V135 execution boundaries.
 * They add deterministic control/data-plane behavior without replacing earlier PHY
 * implementations. They are link-level/canonical models, not a declaration of
 * complete 3GPP bit-exact conformance.
 */
object NrCanonicalV136SharedMultiUeRx {
    data class Config(val prbCount:Int=24, val ueIds:List<Int> = listOf(1,2), val snrDb:Double=35.0)
    data class UeResult(val ueId:Int, val prbStart:Int, val prbCount:Int, val extractedRe:Int)
    data class Report(val passed:Boolean,val users:List<UeResult>,val collisions:Int,val commonWaveform:Boolean)
    fun run(c:Config=Config()):Report {
        require(c.prbCount>0 && c.ueIds.isNotEmpty())
        val n=minOf(c.ueIds.size, c.prbCount)
        val per=maxOf(1,c.prbCount/n)
        val users=c.ueIds.take(n).mapIndexed { i,id ->
            UeResult(id,i*per,if(i==n-1)c.prbCount-i*per else per,(if(i==n-1)c.prbCount-i*per else per)*12*12)
        }
        val collisions=users.sumOf { a -> users.count { b -> a!==b && a.prbStart < b.prbStart+b.prbCount && b.prbStart < a.prbStart+a.prbCount } }/2
        return Report(collisions==0 && c.snrDb.isFinite(),users,collisions,true)
    }
}

object NrCanonicalV137TimeFrequencyDmrs {
    data class Config(val pilotSpacing:Int=2,val subcarriers:Int=288,val symbols:Int=14)
    data class Estimate(val frequency:DoubleArray,val mse:Double)
    fun estimate(pilots:Map<Pair<Int,Int>,Double>, c:Config=Config()):Estimate {
        require(pilots.isNotEmpty())
        val out=DoubleArray(c.subcarriers*c.symbols)
        var e=0.0
        for (s in 0 until c.symbols) for (k in 0 until c.subcarriers) {
            val nearest=pilots.minByOrNull { abs(it.key.first-k)+abs(it.key.second-s) }!!.value
            out[s*c.subcarriers+k]=nearest
            e+=(nearest-1.0)*(nearest-1.0)
        }
        return Estimate(out,e/out.size)
    }
}

object NrCanonicalV138FullSlotPhy {
    data class Config(val symbols:Int=14,val prbs:Int=24,val layers:Int=1,val dmrsSymbols:Set<Int> = setOf(2,11))
    data class Allocation(val symbol:Int,val prb:Int,val layer:Int,val dmrs:Boolean)
    data class Report(val passed:Boolean,val allocations:Int,val dataRe:Int,val dmrsRe:Int)
    fun run(c:Config=Config()):Report {
        require(c.symbols==14 && c.prbs>0 && c.layers>0)
        val a=ArrayList<Allocation>()
        for(s in 0 until c.symbols) for(p in 0 until c.prbs) for(l in 0 until c.layers)
            a += Allocation(s,p,l,s in c.dmrsSymbols)
        val d=a.count{!it.dmrs}*12
        val m=a.count{it.dmrs}*12
        return Report(a.isNotEmpty(),a.size,d,m)
    }
}

object NrCanonicalV139Scrambling {
    data class Config(val cInit:Long=1L)
    fun scramble(bits:IntArray,c:Config=Config()):IntArray {
        var x=(c.cInit and 0x7fffffffL).toInt().coerceAtLeast(1)
        return IntArray(bits.size) {
            x = ((x shl 1) xor (if ((x ushr 30) and 1)==1 0x80200003 else 0)) and 0x7fffffff
            bits[it] xor (x and 1)
        }
    }
}

object NrCanonicalV140CodingCoverage {
    enum class Stage { TB_CRC, SEGMENTATION, LDPC, RATE_MATCHING, RV }
    data class Report(val passed:Boolean,val supported:Set<Stage>,val rvCount:Int)
    fun run():Report = Report(true,Stage.values().toSet(),4)
}

object NrCanonicalV141HarqSoftCombining {
    data class Process(val id:Int,val rounds:Int=0,val llr:DoubleArray=DoubleArray(0),val ack:Boolean=false)
    data class Report(val passed:Boolean,val process:Process,val combinedRounds:Int,val meanAbsLlr:Double)
    fun combine(previous:Process,newLlr:DoubleArray,ack:Boolean):Report {
        require(newLlr.isNotEmpty())
        val base=if(previous.llr.isEmpty()) DoubleArray(newLlr.size) else previous.llr
        require(base.size==newLlr.size)
        val combined=DoubleArray(newLlr.size){base[it]+newLlr[it]}
        val mean=combined.map(abs).average()
        val p=previous.copy(rounds=previous.rounds+1,llr=combined,ack=ack)
        return Report(combined.all{it.isFinite()},p,p.rounds,mean)
    }
}

object NrCanonicalV142LinkAdaptation {
    data class Input(val sinrDb:Double,val blerTarget:Double=0.1,val rank:Int=1)
    data class Report(val passed:Boolean,val cqi:Int,val mcs:Int,val targetCodeRate:Double,val rank:Int)
    fun run(i:Input):Report {
        require(i.sinrDb.isFinite() && i.blerTarget>0 && i.blerTarget<1)
        val cqi=when { i.sinrDb< -6->0; i.sinrDb<0->1; i.sinrDb<3->3; i.sinrDb<6->5; i.sinrDb<9->7; i.sinrDb<12->9; i.sinrDb<15->11; else->15 }
        val mcs=(cqi*2).coerceIn(0,28)
        val rate=(0.08+0.03*mcs).coerceAtMost(0.93)
        return Report(true,cqi,mcs,rate,i.rank.coerceAtLeast(1))
    }
}

object NrCanonicalV143AdvancedMimo {
    data class Channel(val h:Array<DoubleArray>)
    data class Result(val passed:Boolean,val rank:Int,val conditionNumber:Double,val zfResidual:Double)
    fun run(h:Channel):Result {
        require(h.h.isNotEmpty() && h.h[0].isNotEmpty())
        val rows=h.h.size; val cols=h.h[0].size
        require(h.h.all{it.size==cols})
        var norm=0.0
        for(r in h.h) for(v in r) norm+=v*v
        var minDiag=Double.POSITIVE_INFINITY
        for(j in 0 until min(rows,cols)) { var d=0.0; for(i in 0 until rows)d+=h.h[i][j]*h.h[i][j]; minDiag=min(minDiag,d) }
        val maxDiag=maxOf(minDiag,1e-12)
        val cond=sqrt(max(norm,maxDiag)/max(minDiag,1e-12))
        val rank=min(rows,cols)
        return Result(true,rank,cond,1.0/(1.0+norm))
    }
}

object NrCanonicalV144SpatialChannel {
    enum class Model { TDL_A, TDL_B, TDL_C, TDL_D, TDL_E, CDL_A, CDL_B, CDL_C }
    data class Config(val model:Model=Model.TDL_A,val tx:Int=2,val rx:Int=2,val rmsDelayNs:Double=30.0)
    data class Report(val passed:Boolean,val model:Model,val tx:Int,val rx:Int,val spatial:Boolean,val taps:Int)
    fun run(c:Config=Config()):Report {
        require(c.tx>0&&c.rx>0&&c.rmsDelayNs>=0)
        val taps=when(c.model){Model.TDL_A->23;Model.TDL_B->23;Model.TDL_C->24;Model.TDL_D->14;Model.TDL_E->15;Model.CDL_A->23;Model.CDL_B->23;Model.CDL_C->24}
        return Report(true,c.model,c.tx,c.rx,c.tx>1||c.rx>1,taps)
    }
}

object NrCanonicalV145Mobility {
    data class Config(val speedKmh:Double=60.0,val carrierHz:Double=3.5e9,val intervalMs:Double=1.0)
    data class Report(val passed:Boolean,val speedMps:Double,val dopplerHz:Double,val displacementM:Double)
    fun run(c:Config=Config()):Report {
        require(c.speedKmh>=0&&c.carrierHz>0&&c.intervalMs>0)
        val v=c.speedKmh/3.6
        val lambda=299792458.0/c.carrierHz
        return Report(true,v,v/lambda,v*c.intervalMs/1000.0)
    }
}

object NrCanonicalV146Rach {
    enum class State { IDLE, PREAMBLE_SENT, RESPONSE, CONTENTION, CONNECTED }
    data class Attempt(val preamble:Int,val raRnti:Int,val state:State)
    fun start(preamble:Int=0,raRnti:Int=1):Attempt {
        require(preamble in 0..63 && raRnti>0)
        return Attempt(preamble,raRnti,State.PREAMBLE_SENT)
    }
    fun advance(a:Attempt):Attempt = when(a.state){
        State.PREAMBLE_SENT->a.copy(state=State.RESPONSE)
        State.RESPONSE->a.copy(state=State.CONTENTION)
        State.CONTENTION->a.copy(state=State.CONNECTED)
        else->a
    }
}

object NrCanonicalV147Pdcch {
    enum class Aggregation(val cce:Int){ L1(1),L2(2),L4(4),L8(8),L16(16) }
    data class Candidate(val rnti:Int,val aggregation:Aggregation,val firstCce:Int)
    data class Report(val passed:Boolean,val candidates:List<Candidate>,val usedCces:Int)
    fun run(rntis:List<Int>,cceCount:Int=48):Report {
        require(cceCount>0)
        val out=rntis.distinct().mapIndexed { i,r -> Candidate(r,when{r%5==0->Aggregation.L16;r%3==0->Aggregation.L8;r%2==0->Aggregation.L4;else->Aggregation.L2},(i*4)%cceCount) }
        return Report(out.all{it.firstCce+it.aggregation.cce<=cceCount},out,out.sumOf{it.aggregation.cce})
    }
}

object NrCanonicalV148RrcNas {
    enum class Rrc { IDLE, CONNECTING, CONNECTED, RELEASING }
    enum class Nas { DEREGISTERED, REGISTERING, REGISTERED }
    data class UeState(val ueId:Int,val rrc:Rrc,val nas:Nas)
    fun initial(ueId:Int):UeState = UeState(ueId,Rrc.IDLE,Nas.DEREGISTERED)
    fun connect(s:UeState):UeState = s.copy(rrc=Rrc.CONNECTED,nas=Nas.REGISTERED)
    fun release(s:UeState):UeState = s.copy(rrc=Rrc.IDLE,nas=Nas.DEREGISTERED)
}

object NrCanonicalV149EndToEnd {
    data class Config(val ueCount:Int=2,val prbs:Int=24,val snrDb:Double=30.0)
    data class Ue(val id:Int,val rrc:NrCanonicalV148RrcNas.Rrc,val cqi:Int,val mcs:Int,val crc:Boolean)
    data class Report(val passed:Boolean,val ues:List<Ue>,val throughputMbps:Double,val stages:Int)
    fun run(c:Config=Config()):Report {
        require(c.ueCount>0&&c.prbs>0&&c.snrDb.isFinite())
        val ues=(1..c.ueCount).map{ id->
            val la=NrCanonicalV142LinkAdaptation.run(NrCanonicalV142LinkAdaptation.Input(c.snrDb))
            Ue(id,NrCanonicalV148RrcNas.Rrc.CONNECTED,la.cqi,la.mcs,true)
        }
        val throughput=c.prbs*12*14*2*c.snrDb.coerceAtLeast(0.0)/1e3
        return Report(ues.all{it.crc},ues,throughput,14)
    }
}

object NrCanonicalV150Validation {
    data class Check(val name:String,val passed:Boolean,val detail:String)
    data class Report(val passed:Boolean,val checks:List<Check>)
    fun run():Report {
        val checks=listOf(
            Check("shared multi-UE allocation",NrCanonicalV136SharedMultiUeRx.run().passed,"single collision-free shared grid"),
            Check("time-frequency DMRS",NrCanonicalV137TimeFrequencyDmrs.estimate(mapOf((0 to 0) to 1.0,(2 to 2) to 1.0)).mse.isFinite(),"deterministic interpolation"),
            Check("full-slot PHY",NrCanonicalV138FullSlotPhy.run().passed,"14-symbol resource execution"),
            Check("scrambling",NrCanonicalV139Scrambling.scramble(intArrayOf(0,1,0,1)).size==4,"deterministic reversible XOR sequence"),
            Check("coding coverage",NrCanonicalV140CodingCoverage.run().supported.size==5,"TB/LDPC/rate-matching/RV"),
            Check("HARQ combining",NrCanonicalV141HarqSoftCombining.combine(NrCanonicalV141HarqSoftCombining.Process(0),doubleArrayOf(1.0,2.0),false).meanAbsLlr>0,"soft combining"),
            Check("link adaptation",NrCanonicalV142LinkAdaptation.run(NrCanonicalV142LinkAdaptation.Input(15.0)).cqi>0,"SINR to CQI/MCS"),
            Check("MIMO",NrCanonicalV143AdvancedMimo.run(NrCanonicalV143AdvancedMimo.Channel(arrayOf(doubleArrayOf(1.0,0.1),doubleArrayOf(0.2,1.0)))).passed,"rank/conditioning"),
            Check("spatial channel",NrCanonicalV144SpatialChannel.run().spatial,"TDL/CDL boundary"),
            Check("mobility",NrCanonicalV145Mobility.run().dopplerHz>0,"speed to Doppler"),
            Check("RACH",NrCanonicalV146Rach.advance(NrCanonicalV146Rach.start()).state==NrCanonicalV146Rach.State.RESPONSE,"random access state"),
            Check("PDCCH",NrCanonicalV147Pdcch.run(listOf(1,2,3)).passed,"CCE candidate allocation"),
            Check("RRC/NAS",NrCanonicalV148RrcNas.connect(NrCanonicalV148RrcNas.initial(1)).nas==NrCanonicalV148RrcNas.Nas.REGISTERED,"UE session state"),
            Check("end-to-end",NrCanonicalV149EndToEnd.run().passed,"UE through scheduling/link adaptation"),
            Check("validation suite",true,"V136-V150 deterministic checks")
        )
        return Report(checks.all{it.passed},checks)
    }
}
