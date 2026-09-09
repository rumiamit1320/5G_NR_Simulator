package com.example.nrsimulator

data class NrGtpPacketV57(val teid:Long,val seq:Int,val payload:ByteArray,val qfi:Int=0)
data class NrTunnelV57(val teid:Long,val ueId:String,val qfi:Int)
object NrGtpV57 {
    fun encode(p:NrGtpPacketV57):ByteArray {
        require(p.qfi in 0..63); require(p.seq in 0..65535)
        val h=ByteArray(12+p.payload.size); h[0]=0x30; h[1]=0xff.toByte()
        val len=p.payload.size+4; h[2]=(len ushr 8).toByte(); h[3]=len.toByte()
        for(i in 0..3) h[4+i]=(p.teid ushr (24-8*i)).toByte()
        h[8]=(p.seq ushr 8).toByte(); h[9]=p.seq.toByte(); h[10]=p.qfi.toByte(); p.payload.copyInto(h,12)
        return h
    }
    fun decode(b:ByteArray):NrGtpPacketV57 {
        require(b.size>=12); var t=0L
        for(i in 0..3) t=(t shl 8) or (b[4+i].toLong() and 255L)
        val s=((b[8].toInt() and 255) shl 8) or (b[9].toInt() and 255)
        return NrGtpPacketV57(t,s,b.copyOfRange(12,b.size),b[10].toInt() and 63)
    }
}
