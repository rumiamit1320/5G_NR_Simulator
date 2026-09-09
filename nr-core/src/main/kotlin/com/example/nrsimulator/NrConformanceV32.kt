package com.example.nrsimulator

/** V32: additive polar-control coding foundation. Existing PDCCH APIs remain unchanged. */
data class NrPolarConfigV32(val payloadBits:Int,val encodedBits:Int,val frozenPrefix:Int=0)
data class NrPolarCodewordV32(val bits:IntArray,val config:NrPolarConfigV32)
object NrPolarV32 {
    fun encode(info:IntArray, n:Int = nextPow2(maxOf(32, info.size))): NrPolarCodewordV32 {
        require(n and (n-1) == 0 && info.size <= n)
        var x=IntArray(n)
        info.copyInto(x,n-info.size)
        var step=1
        while(step<n){ var i=0; while(i<n){ for(j in 0 until step){ x[i+j]=(x[i+j] xor x[i+j+step]) and 1 }; i+=2*step }; step*=2 }
        return NrPolarCodewordV32(x,NrPolarConfigV32(info.size,n,n-info.size))
    }
    fun decode(codeword:IntArray, infoBits:Int):IntArray {
        require(codeword.size and (codeword.size-1)==0 && infoBits<=codeword.size)
        // Hard-decision inverse transform. This is intentionally isolated from V18's control abstraction.
        var x=codeword.copyOf(); var step=1
        while(step<codeword.size){ var i=0; while(i<codeword.size){ for(j in 0 until step){ x[i+j]=(x[i+j] xor x[i+j+step]) and 1 }; i+=2*step }; step*=2 }
        return x.copyOfRange(codeword.size-infoBits,codeword.size)
    }
    fun rateMatch(bits:IntArray,e:Int):IntArray = IntArray(e){ bits[it % bits.size] }
    fun rateRecover(bits:IntArray,n:Int):IntArray { val out=IntArray(n); for(i in bits.indices) out[i%n]=out[i%n] xor bits[i]; return out }
    private fun nextPow2(v:Int):Int { var n=1; while(n<v)n*=2; return n }
}
