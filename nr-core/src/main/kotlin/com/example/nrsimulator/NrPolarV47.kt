package com.example.nrsimulator

/** V47: reusable polar codec/rate-matching foundation. Reliability ordering is deterministic; normative KATs remain a separate gate. */
data class NrPolarConfigV47(val n:Int,val k:Int,val e:Int,val crcBits:Int=0)
data class NrPolarCodewordV47(val bits:IntArray,val infoBits:IntArray,val frozen:IntArray)
object NrPolarV47 {
    private fun isPow2(n:Int)=n>0 && (n and(n-1))==0
    fun encode(info:IntArray,n:Int):IntArray{
        require(isPow2(n)&&info.size<=n)
        val pos=reliability(n).take(info.size).sorted(); val u=IntArray(n);info.forEachIndexed{i,b->u[pos[i]]=b and 1};var step=1;while(step<n){var i=0;while(i<n){for(j in 0 until step){u[i+j]=u[i+j] xor u[i+j+step]};i+=2*step};step*=2};return u
    }
    fun decode(code:IntArray,k:Int):IntArray{require(isPow2(code.size)&&k<=code.size);val pos=reliability(code.size).take(k).sorted();val u=code.copyOf();var step=1;while(step<code.size){var i=0;while(i<code.size){for(j in 0 until step){u[i+j]=u[i+j] xor u[i+j+step]};i+=2*step};step*=2};return IntArray(k){u[pos[it]]}
    }
    fun rateMatch(bits:IntArray,e:Int,rv:Int=0):IntArray{require(e>=0);if(e==0)return IntArray(0);val off=((rv and 3)*bits.size)/4;return IntArray(e){bits[(off+it)%bits.size]}}
    fun rateRecover(rx:IntArray,n:Int,rv:Int=0):IntArray{val out=IntArray(n);if(rx.isEmpty())return out;val off=((rv and 3)*n)/4;for(i in rx.indices)out[(off+i)%n]=rx[i];return out}
    fun reliability(n:Int):List<Int>{require(isPow2(n));val stages=IntArray(n){0};var pw=1;while(pw<n){for(i in 0 until n){var x=i;var w=0;repeat(Integer.numberOfTrailingZeros(pw)){w+=x and 1;x=x ushr 1};stages[i]=w};pw*=2};return (0 until n).sortedWith(compareBy<Int>{stages[it]}.thenBy{it})}
}
