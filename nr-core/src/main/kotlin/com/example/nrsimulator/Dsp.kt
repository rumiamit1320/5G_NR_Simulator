package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

data class Complex(val re: Double, val im: Double) {
    operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
    operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
    operator fun times(o: Complex) = Complex(re*o.re - im*o.im, re*o.im + im*o.re)
    operator fun times(s: Double) = Complex(re*s, im*s)
    fun conj() = Complex(re, -im)
    fun abs2() = re*re + im*im
}

object Dsp {
    fun bits(n: Int, r: Random): IntArray = IntArray(n) { r.nextInt(2) }
    fun qam(bits: IntArray, order: Int): Array<Complex> {
        val bps = log2(order.toDouble()).roundToInt(); val out = Array(bits.size / bps) { Complex(0.0,0.0) }
        var p=0
        for (i in out.indices) { var v=0; repeat(bps){ v=(v shl 1) or bits[p++] }; out[i]=mapQam(v, order) }
        return out
    }
    private fun mapQam(v:Int, m:Int):Complex {
        if(m==4){ return when(v){0->Complex(1.0,1.0);1->Complex(-1.0,1.0);2->Complex(1.0,-1.0);else->Complex(-1.0,-1.0)} * (1/sqrt(2.0)) }
        val l=sqrt(m.toDouble()).roundToInt(); val i=v%l; val q=v/l; val levels=(0 until l).map{2*it-l+1}; val scale=sqrt((2.0/3.0)*(m-1)); return Complex(levels[i]/scale, levels[q]/scale)
    }
    fun fft(x:Array<Complex>, inverse:Boolean=false):Array<Complex>{
        val n=x.size; require(n>0 && n and (n-1)==0); val a=x.copyOf(); var j=0
        for(i in 1 until n){ var bit=n shr 1; while(j and bit !=0){j=j xor bit;bit=bit shr 1}; j=j xor bit; if(i<j){val t=a[i];a[i]=a[j];a[j]=t} }
        var len=2; while(len<=n){ val ang=(if(inverse)2 else -2)*PI/len; val wlen=Complex(cos(ang),sin(ang)); var i=0; while(i<n){var w=Complex(1.0,0.0);for(k in 0 until len/2){val u=a[i+k];val v=a[i+k+len/2]*w;a[i+k]=u+v;a[i+k+len/2]=u-v;w=w*wlen};i+=len};len=len shl 1 }
        if(inverse) for(i in a.indices) a[i]=a[i]*(1.0/n); return a
    }
    fun addAwgn(x:Array<Complex>, snrDb:Double, r:Random):Array<Complex>{
        val p=x.map{it.abs2()}.average(); val np=p/10.0.pow(snrDb/10.0); val s=sqrt(np/2); fun g():Double{var u=0.0;var v=0.0;while(u==0.0)u=r.nextDouble();v=r.nextDouble();return sqrt(-2*ln(u))*cos(2*PI*v)}; return Array(x.size){x[it]+Complex(g()*s,g()*s)}
    }
    fun evm(ref:Array<Complex>, rx:Array<Complex>):Double{ var e=0.0;var p=0.0;for(i in ref.indices){e+=(ref[i]-rx[i]).abs2();p+=ref[i].abs2()};return sqrt(e/p)*100 }
}

data class SimResult(val bits:Int,val symbols:Int,val snr:Double,val evm:Double,val throughputMbps:Double,val ber:Double,val constellation:Array<Complex>,val rx:Array<Complex>)

class Simulator {
    fun run(scsKhz:Int, nRb:Int, order:Int, snr:Double, bandwidthMHz:Double, durationMs:Double):SimResult {
        val r=Random(System.nanoTime()); val bps=log2(order.toDouble()).roundToInt(); val n=256; val raw=Dsp.bits(2048,r); val tx=Dsp.qam(raw,order); val grid=Array(n){Complex(0.0,0.0)}; tx.take(n/2-1).forEachIndexed{idx,z->grid[idx+1]=z}; tx.take(n/2-1).forEachIndexed{idx,z->grid[n/2+1+idx]=z}; val td=Dsp.fft(grid,true); val rxTd=Dsp.addAwgn(td,snr,r); val rxFd=Dsp.fft(rxTd); val rx=Array(tx.size){rxFd[(it%(n-2))+if((it%(n-2))<(n/2-1))1 else 2]}; val evm=Dsp.evm(tx.take(rx.size).toTypedArray(),rx); val ber=(0.5*10.0.pow(-snr/10.0)).coerceIn(0.0,0.5); val symRate=(scsKhz*1000.0*14.0*nRb); val thr=bandwidthMHz*1e6*(log2(order.toDouble()))*0.75*(1-0.14)/1e6; return SimResult(raw.size,tx.size,snr,evm,thr,ber,tx.take(80).toTypedArray(),rx.take(80).toTypedArray())
    }
}
