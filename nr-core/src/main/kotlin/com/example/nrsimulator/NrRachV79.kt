package com.example.nrsimulator

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** V79: deterministic Zadoff-Chu PRACH preamble/detection primitive. */
data class NrPrachConfigV79(val sequenceLength:Int=839,val root:Int=1,val cyclicShift:Int=0) { init { require(sequenceLength>1);require(root>0&&root<sequenceLength);require(cyclicShift>=0&&cyclicShift<sequenceLength) } }
data class NrPrachDetectionV79(val preamble:Int,val correlation:Double,val detected:Boolean)
object NrRachV79 {
    fun generate(c:NrPrachConfigV79=NrPrachConfigV79()):Array<Complex>{
        return Array(c.sequenceLength){n->val k=(n+c.cyclicShift)%c.sequenceLength;val phase=-PI*c.root*k*(k+1)/c.sequenceLength;Complex(cos(phase),sin(phase))}
    }
    fun detect(rx:Array<Complex>,c:NrPrachConfigV79=NrPrachConfigV79(),threshold:Double=0.75):NrPrachDetectionV79{
        val ref=generate(c);require(rx.size==ref.size);var re=0.0;var im=0.0;var er=0.0;var ep=0.0;for(i in rx.indices){re+=rx[i].re*ref[i].re+rx[i].im*ref[i].im;im+=rx[i].im*ref[i].re-rx[i].re*ref[i].im;er+=rx[i].re*rx[i].re+rx[i].im*rx[i].im;ep+=1.0};val corr=Math.sqrt(re*re+im*im)/Math.sqrt(maxOf(1e-12,er*ep));return NrPrachDetectionV79(c.cyclicShift,corr,corr>=threshold)
    }
}
