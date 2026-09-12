package com.example.nrsimulator

import kotlin.math.abs
import kotlin.math.max

data class NrQcLdpcMatrixV74(val rows:Int,val cols:Int,val z:Int,val shifts:Array<IntArray>) {
    init { require(rows>0 && cols>rows && z>0); require(shifts.size==rows && shifts.all{it.size==cols}) }
}
data class NrLdpcDecodeV74(val bits:IntArray,val iterations:Int,val syndromeWeight:Int,val converged:Boolean)
object NrLdpcV74 {
    private fun syndrome(h:Array<IntArray>,x:IntArray):Int { var w=0; for(r in h.indices){var p=0;for(c in h[r].indices)p=p xor (h[r][c] and x[c]);w+=p};return w }
    fun lift(m:NrQcLdpcMatrixV74):Array<IntArray>{
        val h=Array(m.rows*m.z){IntArray(m.cols*m.z)}
        for(br in 0 until m.rows) for(bc in 0 until m.cols){val s=m.shifts[br][bc];if(s>=0)for(i in 0 until m.z)h[br*m.z+i][bc*m.z+(i+s)%m.z]=1}
        return h
    }
    /** Systematic reference encoder for an explicitly supplied full-rank binary parity-check matrix. */
    fun encode(info:IntArray,h:Array<IntArray>):IntArray{
        require(h.isNotEmpty());val n=h[0].size;require(h.all{it.size==n});val k=n-h.size;require(info.size==k){"info length must equal n-m"}
        val a=Array(h.size){r->IntArray(n+1){c->if(c<n)h[r][c] else 0}}
        var row=0
        for(col in 0 until n){
            val p=(row until h.size).firstOrNull{a[it][col]==1}?:continue
            val tmp=a[row];a[row]=a[p];a[p]=tmp
            for(r in h.indices) if(r!=row&&a[r][col]==1) for(c in col until n) a[r][c]=a[r][c] xor a[row][c]
            row++;if(row==h.size)break
        }
        require(row==h.size){"parity-check matrix is not full row rank"}
        val piv=IntArray(h.size){r->(0 until n).firstOrNull{a[r][it]==1}?:-1}
        val x=IntArray(n);val pivotSet=piv.toSet();val non=(0 until n).filter{it !in pivotSet};require(non.size==k)
        for(i in 0 until k)x[non[i]]=info[i] and 1
        for(r in h.indices.reversed()){
            val pc=piv[r];var v=0
            for(c in pc+1 until n)if(a[r][c]==1)v=v xor x[c]
            x[pc]=v
        }
        require(syndrome(h,x)==0){"LDPC encoder produced non-zero syndrome"};return x
    }
    /** Normalized min-sum decoder. Positive LLR denotes bit 0. */
    fun decode(llr:DoubleArray,h:Array<IntArray>,maxIterations:Int=25,offset:Double=0.15):NrLdpcDecodeV74{
        require(h.isNotEmpty() && llr.size==h[0].size && h.all{it.size==llr.size})
        require(maxIterations>0 && offset>=0)
        val m=h.size;val n=llr.size
        val checks=Array(m){r->h[r].indices.filter{h[r][it]!=0}.toIntArray()}
        val edge=Array(m){r->DoubleArray(checks[r].size)}
        val x=IntArray(n)
        repeat(maxIterations){it->
            val v2c=Array(m){r->DoubleArray(checks[r].size)}
            for(r in 0 until m)for(i in checks[r].indices){val c=checks[r][i];var v=llr[c];for(rr in 0 until m)if(rr!=r){val j=checks[rr].indexOf(c);if(j>=0)v+=edge[rr][j]};v2c[r][i]=v}
            for(r in 0 until m){val cs=checks[r];for(i in cs.indices){var sign=1;var mn=Double.POSITIVE_INFINITY;for(j in cs.indices)if(j!=i){val v=v2c[r][j];if(v<0)sign=-sign;mn=minOf(mn,abs(v))};edge[r][i]=sign*max(0.0,mn-offset)}}
            for(c in 0 until n){var v=llr[c];for(r in 0 until m){val j=checks[r].indexOf(c);if(j>=0)v+=edge[r][j]};x[c]=if(v<0)1 else 0}
            val sw=syndrome(h,x);if(sw==0)return NrLdpcDecodeV74(x,it+1,0,true)
        }
        val sw=syndrome(h,x);return NrLdpcDecodeV74(x,maxIterations,sw,sw==0)
    }
}
