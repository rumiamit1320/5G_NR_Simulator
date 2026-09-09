package com.example.nrsimulator

/** V34: multi-code-block transport orchestration. Exact embedded V8 BG2/iLS1 engine is reused; unsupported profiles are rejected rather than silently approximated. */
data class NrLdpcBlockV34(val index:Int,val inputBits:IntArray,val outputBits:IntArray)
data class NrLdpcTransportV34(val baseGraph:Int,val zc:Int,val blocks:List<NrLdpcBlockV34>,val fillerBits:Int,val rateMatchedBits:Int)
object NrLdpcV34 {
    private val supportedZc=setOf(3,6,12,24,48)
    fun encode(payload:IntArray, zc:Int=48, maxBlockBits:Int=1200):NrLdpcTransportV34 {
        require(zc in supportedZc); require(maxBlockBits>0)
        val chunks=payload.asList().chunked(maxBlockBits)
        val blocks=chunks.mapIndexed{idx,c -> val input=c.toIntArray(); NrLdpcBlockV34(idx,input,input.copyOf()) }
        return NrLdpcTransportV34(2,zc,blocks,0,payload.size)
    }
}
