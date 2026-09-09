package com.example.nrsimulator

/** V38: PDCP sequence, integrity/ciphering and reordering facade. */
data class NrPdcpPduV38(val sn:Int,val payload:ByteArray,val integrity:Int,val ciphered:Boolean)
class NrPdcpV38(private val snBits:Int=12){ private val mod=1 shl snBits; private var tx=0; private var rx=0
    fun protect(key:ByteArray,payload:ByteArray,count:Long,bearer:Int,direction:Int):NrPdcpPduV38{ val c=NrSecurityV31.nea2(key,count,bearer,direction,payload); val mac=NrSecurityV31.nia2(key,count,bearer,direction,c); val p=NrPdcpPduV38(tx,c,mac,true); tx=(tx+1)%mod; return p }
    fun verify(key:ByteArray,pdu:NrPdcpPduV38,count:Long,bearer:Int,direction:Int):ByteArray?{ val mac=NrSecurityV31.nia2(key,count,bearer,direction,pdu.payload); if(mac!=pdu.integrity)return null; val d=NrSecurityV31.nea2(key,count,bearer,direction,pdu.payload); rx=(pdu.sn+1)%mod; return d }
}
