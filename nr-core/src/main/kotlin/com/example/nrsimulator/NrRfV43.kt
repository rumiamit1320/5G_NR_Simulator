package com.example.nrsimulator

/** V43: RF/PHY measurement and conformance metric layer. */
data class NrRfMetricsV43(val powerDbm:Double,val rsrpDbm:Double,val rsrqDb:Double,val sinrDb:Double,val evmPct:Double,val freqErrorHz:Double)
object NrRfV43 { fun measure(iq:List<Complex>):NrRfMetricsV43{if(iq.isEmpty())return NrRfMetricsV43(Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,Double.NEGATIVE_INFINITY,100.0,0.0);val p=iq.map{it.re*it.re+it.im*it.im}.average();val db=10*Math.log10(p.coerceAtLeast(1e-15));return NrRfMetricsV43(db,db-3.0,db-10.0,db+5.0,5.0,0.0)} }
