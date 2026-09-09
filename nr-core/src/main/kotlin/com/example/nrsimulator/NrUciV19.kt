package com.example.nrsimulator

data class NrUciV19(val harqAck: BooleanArray = booleanArrayOf(), val sr: Boolean = false, val csi: IntArray = intArrayOf())
data class NrPucchV19Config(val format: Int = 1, val resourceId: Int = 0, val symbols: Int = 2, val prbs: Int = 1)
data class NrPucchV19Result(val payloadBits: Int, val format: Int, val encoded: IntArray, val decoded: NrUciV19, val pass: Boolean, val note: String)
class NrPucchV19 {
 fun run(uci: NrUciV19, cfg: NrPucchV19Config = NrPucchV19Config()): NrPucchV19Result {
  val bits=ArrayList<Int>(); uci.harqAck.forEach{bits+=if(it)1 else 0}; bits+=if(uci.sr)1 else 0; uci.csi.forEach{v->for(i in 0 until 4) bits+=(v shr i) and 1}
  val enc=IntArray(bits.size*2){bits[it/2]}; var p=0; val take={val b=enc[p];p+=2;b}; val ack=BooleanArray(uci.harqAck.size){take()==1}; val sr=take()==1; val csi=IntArray(uci.csi.size){var x=0; for(i in 0 until 4)x= x or ((take() and 1) shl i);x}
  val d=NrUciV19(ack,sr,csi); return NrPucchV19Result(bits.size,cfg.format,enc,d,d.harqAck.contentEquals(uci.harqAck)&&d.sr==uci.sr&&d.csi.contentEquals(uci.csi),"PUCCH/UCI reference model; formats and payload/control semantics are represented, not full 38.211 waveform conformance.")
 }
}
