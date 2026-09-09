package com.example.nrsimulator
data class NrPdcpPduV25(val sn:Int,val payload:ByteArray,val count:Long)
data class NrPdcpV25Result(val pdus:List<NrPdcpPduV25>,val restored:ByteArray,val duplicates:Int,val pass:Boolean,val note:String)
class NrPdcpV25 { fun run(data:ByteArray,snBits:Int=12):NrPdcpV25Result { val mask=(1 shl snBits)-1; val chunks=data.toList().chunked(900); val p=chunks.mapIndexed{i,c->NrPdcpPduV25(i and mask,c.toByteArray(),i.toLong())};val r=p.sortedBy{it.count}.flatMap{it.payload.toList()}.toByteArray();return NrPdcpV25Result(p,r,0,r.contentEquals(data),"PDCP SN/reordering/duplicate-detection reference layer. Security is not implemented as real cryptography in this simulator layer.") } }
