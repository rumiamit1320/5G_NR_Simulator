package com.example.nrsimulator
import kotlin.math.*
data class NrBeamV28Result(val beam:Int,val gainDb:Double,val rank:Int,val beamsScanned:Int,val pass:Boolean,val note:String)
class NrBeamV28 { fun sweep(azimuthDeg:Double,antennaRows:Int=4,antennaCols:Int=4,beamCount:Int=16):NrBeamV28Result { var best=0;var bg=-1e9;for(b in 0 until beamCount){val a=-60+120.0*b/(beamCount-1).coerceAtLeast(1);val g=10*log10((antennaRows*antennaCols).toDouble())-0.08*(azimuthDeg-a)*(azimuthDeg-a);if(g>bg){bg=g;best=b}};return NrBeamV28Result(best,bg,if(antennaRows*antennaCols>=8)2 else 1,beamCount,true,"Beam sweeping/codebook reference with array gain and rank selection; full SSB/CSI-RS beam procedures remain a future conformance layer.") } }
