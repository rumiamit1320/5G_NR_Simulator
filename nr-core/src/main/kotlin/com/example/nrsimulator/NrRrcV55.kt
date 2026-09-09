package com.example.nrsimulator

/** V55: typed RRC message/codec boundary. Not a generated 38.331 ASN.1 codec. */
enum class NrRrcMessageTypeV55 { SETUP_REQUEST, SETUP, SETUP_COMPLETE, RECONFIGURATION, RECONFIGURATION_COMPLETE, RELEASE, REESTABLISHMENT }
data class NrRrcPduV55(val type:NrRrcMessageTypeV55,val transactionId:Int,val criticalExtensions:ByteArray)
object NrRrcV55 {
    fun encode(p:NrRrcPduV55):ByteArray {
        require(p.transactionId in 0..3)
        val out=ByteArray(4+p.criticalExtensions.size)
        out[0]=p.type.ordinal.toByte(); out[1]=p.transactionId.toByte()
        out[2]=(p.criticalExtensions.size ushr 8).toByte(); out[3]=p.criticalExtensions.size.toByte()
        p.criticalExtensions.copyInto(out,4); return out
    }
    fun decode(b:ByteArray):NrRrcPduV55 {
        require(b.size>=4)
        val n=((b[2].toInt() and 255) shl 8) or (b[3].toInt() and 255)
        require(n==b.size-4)
        val type=NrRrcMessageTypeV55.entries[b[0].toInt() and 255]
        return NrRrcPduV55(type,b[1].toInt() and 3,b.copyOfRange(4,b.size))
    }
}
