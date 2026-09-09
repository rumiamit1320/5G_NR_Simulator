package com.example.nrsimulator

data class NrPdcpCountV54(val sn:Int,val hfn:Int,val count:Long)
data class NrPdcpPduV54(val count:NrPdcpCountV54,val payload:ByteArray,val mac:Int)
object NrPdcpV54{fun count(sn:Int,hfn:Int,snBits:Int=12)=NrPdcpCountV54(sn,hfn,(hfn.toLong() shl snBits) or sn.toLong());fun protect(key:ByteArray,data:ByteArray,c:NrPdcpCountV54,bearer:Int,dir:Int):NrPdcpPduV54{val x=NrSecurityV31.nea2(key,c.count,bearer,dir,data);return NrPdcpPduV54(c,x,NrSecurityV31.nia2(key,c.count,bearer,dir,x))};fun verify(key:ByteArray,p:NrPdcpPduV54,bearer:Int,dir:Int):ByteArray?{if(NrSecurityV31.nia2(key,p.count.count,bearer,dir,p.payload)!=p.mac)return null;return NrSecurityV31.nea2(key,p.count.count,bearer,dir,p.payload)}}
