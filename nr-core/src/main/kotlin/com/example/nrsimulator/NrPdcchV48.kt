package com.example.nrsimulator

/** V48: PDCCH chain adapter: DCI -> CRC/RNTI -> polar -> QPSK and reverse. */
data class NrPdcchFrameV48(val rnti:Int,val aggregation:Int,val payload:IntArray,val coded:IntArray,val qpsk:Array<Complex>)
object NrPdcchV48 {
    fun encode(payload:IntArray,rnti:Int,aggregation:Int=4):NrPdcchFrameV48{require(aggregation in setOf(1,2,4,8,16));val crc=NrCrc24CV31.append(payload).also{for(i in 0 until 24){it[payload.size+i]=it[payload.size+i] xor ((rnti ushr(23-i))and 1)}};val n=nextPow2(maxOf(32,crc.size*2));val c=NrPolarV47.encode(crc,n);val rm=NrPolarV47.rateMatch(c,aggregation*108);val q=Array((rm.size+1)/2){i->val a=rm.getOrElse(2*i){0};val b=rm.getOrElse(2*i+1){0};Complex(if(a==0)1.0 else -1.0,if(b==0)1.0 else -1.0)};return NrPdcchFrameV48(rnti,aggregation,payload,rm,q)}
    fun decode(f:NrPdcchFrameV48):IntArray{val n=nextPow2(maxOf(32,(f.payload.size+24)*2));val rec=NrPolarV47.rateRecover(f.coded,n,f.aggregation%4);val c=NrPolarV47.decode(rec,f.payload.size+24);return c.copyOf(f.payload.size)}
    private fun nextPow2(x:Int):Int{var n=1;while(n<x)n*=2;return n}
}
