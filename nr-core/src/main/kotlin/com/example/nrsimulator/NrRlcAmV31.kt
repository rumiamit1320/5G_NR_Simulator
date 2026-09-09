package com.example.nrsimulator

/** Additive RLC AM state machine foundation. Existing V24 API remains untouched. */
data class NrRlcAmPduV31(val sn:Int,val so:Int,val payload:ByteArray,val fiStart:Boolean,val fiEnd:Boolean,val poll:Boolean)
data class NrRlcAmStatusV31(val ackSn:Int,val nackSn:IntArray= intArrayOf())
data class NrRlcAmStateV31(val nextTxSn:Int=0,val nextRxSn:Int=0,val txWindowStart:Int=0,val rxWindowStart:Int=0,val pending:Map<Int,NrRlcAmPduV31> = emptyMap(),val retransmit:List<Int> = emptyList())

class NrRlcAmV31(private val snBits:Int=12, private val pollPdu:Int=16) {
    private val mod = 1 shl snBits
    fun segment(data:ByteArray, mtu:Int=512, state:NrRlcAmStateV31=NrRlcAmStateV31()): Pair<List<NrRlcAmPduV31>,NrRlcAmStateV31> {
        require(mtu > 0)
        val out=ArrayList<NrRlcAmPduV31>(); var off=0; var sn=state.nextTxSn
        while(off<data.size){ val end=minOf(data.size,off+mtu); out += NrRlcAmPduV31(sn,off,data.copyOfRange(off,end),off==0,end==data.size,(out.size+1)%pollPdu==0 || end==data.size); off=end; sn=(sn+1)%mod }
        val p=out.associateBy{it.sn}; return out to state.copy(nextTxSn=sn,pending=p)
    }
    fun acknowledge(state:NrRlcAmStateV31,status:NrRlcAmStatusV31):NrRlcAmStateV31 {
        val bad=status.nackSn.toSet(); val kept=state.pending.filterKeys{it !in bad && it>=status.ackSn}
        return state.copy(txWindowStart=status.ackSn,retransmit=status.nackSn.toList(),pending=kept)
    }
    fun receive(pdUs:List<NrRlcAmPduV31>,state:NrRlcAmStateV31=NrRlcAmStateV31()):Pair<ByteArray,NrRlcAmStatusV31>{
        val ordered=pdUs.sortedBy{(it.sn-state.nextRxSn+mod)%mod}; var expected=state.nextRxSn; val bytes=ArrayList<Byte>(); val nack=ArrayList<Int>()
        for (p in ordered) { if (p.sn==expected) { bytes.addAll(p.payload.toList()); expected=(expected+1)%mod } else if ((p.sn-expected+mod)%mod < mod/2) nack += expected }
        return bytes.toByteArray() to NrRlcAmStatusV31(expected,nack.distinct().toIntArray())
    }
}
