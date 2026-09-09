package com.example.nrsimulator

/** V39: ASN.1 PER-like bit codec foundation for RRC. Full 38.331 schema remains generated-data work. */
class NrPerWriterV39 { private val b=ArrayList<Int>(); fun bit(v:Int){b+=v and 1}; fun bits(v:Int,n:Int){for(i in n-1 downTo 0)bit(v ushr i)}; fun bytes(x:ByteArray){bits(x.size,16);x.forEach{bits(it.toInt() and 255,8)}}; fun toBits()=b.toIntArray() }
class NrPerReaderV39(private val b:IntArray){private var p=0; fun bit()=b[p++]; fun bits(n:Int):Int{var v=0;repeat(n){v=(v shl 1) or bit()};return v}; fun bytes():ByteArray{val n=bits(16);return ByteArray(n){bits(8).toByte()} } }
data class NrRrcMessageV39(val procedure:NrRrcProcedureV31,val transactionId:Int,val payload:ByteArray)
object NrRrcV39 { fun encode(m:NrRrcMessageV39):IntArray=NrPerWriterV39().also{it.bits(m.procedure.ordinal,4);it.bits(m.transactionId,2);it.bytes(m.payload)}.toBits(); fun decode(b:IntArray):NrRrcMessageV39{val r=NrPerReaderV39(b);return NrRrcMessageV39(NrRrcProcedureV31.entries[r.bits(4)%NrRrcProcedureV31.entries.size],r.bits(2),r.bytes())} }
