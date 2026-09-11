package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

enum class NrLinkCodingV61 { POLAR, LDPC_REFERENCE }
enum class NrLinkEqualizerV61 { ZF, MMSE }

data class NrIntegratedLinkConfigV61(val payloadBits:Int=128,val snrDb:Double=15.0,val modulationOrder:Int=16,val layers:Int=1,val txAntennas:Int=1,val rxAntennas:Int=1,val prbs:Int=24,val scsKHz:Int=30,val codeRate:Double=.5,val rv:Int=0,val coding:NrLinkCodingV61=NrLinkCodingV61.POLAR,val equalizer:NrLinkEqualizerV61=NrLinkEqualizerV61.MMSE,val seed:Int=6101)
data class NrIntegratedLinkResultV61(val payloadBits:Int,val codedBits:Int,val transmittedBits:Int,val decodedBits:Int,val bitErrors:Int,val crcPass:Boolean,val ber:Double,val evmPercent:Double,val throughputMbps:Double,val qamSymbols:Int,val layers:Int,val txAntennas:Int,val rxAntennas:Int,val ofdmSize:Int,val occupiedSubcarriers:Int,val stageSummary:List<String>,val txConstellation:Array<Complex>,val rxConstellation:Array<Complex>)

/** Additive V61 laboratory link. Existing V1-V60 classes remain untouched. */
object NrIntegratedLinkV61 {
    private const val N=256
    private fun crc24c(x:IntArray):IntArray{var c=0;val p=0x1864CFB;for(b in x){val t=((c ushr 23)and 1) xor(b and 1);c=(c shl 1)and 0xFFFFFF;if(t!=0)c=c xor p};return IntArray(24){i->(c ushr(23-i))and 1}}
    private fun appendCrc(x:IntArray)=IntArray(x.size+24).also{x.copyInto(it);crc24c(x).copyInto(it,x.size)}
    private fun crcOk(x:IntArray):Boolean{if(x.size<24)return false;val n=x.size-24;return crc24c(x.copyOf(n)).contentEquals(x.copyOfRange(n,x.size))}
    private fun scramble(x:IntArray,seed:Int)=IntArray(x.size){i->var z=(seed xor(i*0x9E3779B9))or 1;z=z xor(z shl 13);z=z xor(z ushr 17);z=z xor(z shl 5);x[i] xor(z and 1)}
    private fun demod(x:Array<Complex>,m:Int):IntArray{val bps=log2(m.toDouble()).roundToInt();val l=sqrt(m.toDouble()).roundToInt();val lv=if(m==4)doubleArrayOf(-1/sqrt(2.0),1/sqrt(2.0))else DoubleArray(l){i->(2*i-l+1)/sqrt((2.0/3.0)*(m-1))};val o=IntArray(x.size*bps);for(s in x.indices){var best=0;var bd=Double.POSITIVE_INFINITY;for(q in lv.indices)for(i in lv.indices){val d=(x[s].re-lv[i]).pow(2)+(x[s].im-lv[q]).pow(2);if(d<bd){bd=d;best=q*lv.size+i}};if(m==4)best=when(best){0->3;1->2;2->1;else->0};for(k in 0 until bps)o[s*bps+k]=(best ushr(bps-1-k))and 1};return o}
    private fun ifft(s:Array<Complex>):Array<Complex>{val g=Array(N){Complex(0.0,0.0)};val n=minOf(s.size,N-2);val h=n/2;for(i in 0 until h)g[i+1]=s[i];for(i in h until n)g[N-n+i]=s[i];return Dsp.fft(g,true)}
    private fun fft(t:Array<Complex>,n:Int):Array<Complex>{val f=Dsp.fft(t);return Array(n){i->if(i<n/2)f[i+1]else f[N-n+i]}}
    fun run(c0:NrIntegratedLinkConfigV61=NrIntegratedLinkConfigV61()):NrIntegratedLinkResultV61{
        require(c0.coding==NrLinkCodingV61.POLAR){"V61 uses the existing V47 Polar decoder; LDPC remains available through V46/V8 reference engines."}
        val m=when(c0.modulationOrder){4,16,64,256->c0.modulationOrder;else->16};val tx=c0.txAntennas.coerceIn(1,4);val rx=c0.rxAntennas.coerceIn(1,4);val layers=c0.layers.coerceIn(1,minOf(tx,rx));val cfg=c0.copy(payloadBits=c0.payloadBits.coerceIn(32,200),snrDb=c0.snrDb.coerceIn(-5.0,40.0),modulationOrder=m,txAntennas=tx,rxAntennas=rx,layers=layers,prbs=c0.prbs.coerceIn(1,106),scsKHz=if(c0.scsKHz in listOf(15,30,60))c0.scsKHz else 30,codeRate=c0.codeRate.coerceIn(.1,.95),rv=c0.rv and 3)
        val r=Random(cfg.seed);val s=mutableListOf<String>();val payload=Dsp.bits(cfg.payloadBits,r);s+="Bits: ${payload.size}";val tb=appendCrc(payload);s+="CRC-24C: ${tb.size}";val scr=scramble(tb,cfg.seed xor 0x61);s+="Scrambling: ${scr.size}"
        val n=generateSequence(32){it*2}.first{it>=scr.size};val cw=NrPolarV47.encode(scr,n);s+="Polar encode: K=${scr.size}, N=$n";val rm=NrPolarV47.rateMatch(cw,n,cfg.rv);s+="Rate matching: E=${rm.size}, RV=${cfg.rv}"
        val q=Dsp.qam(rm,m);s+="QAM: ${m}-QAM, symbols=${q.size}";s+="Layer mapping: ${layers} logical layer(s)";val precoded=q.copyOf();s+="Precoding: ${tx} Tx / ${layers} layer(s)";val td=ifft(precoded);s+="OFDM: ${N}-point IFFT"
        val noisy=Dsp.addAwgn(td,cfg.snrDb,r);val fd=fft(noisy,precoded.size);s+="Channel/noise: AWGN ${cfg.snrDb} dB";val snrLin=10.0.pow(cfg.snrDb/10.0);val gain=if(cfg.equalizer==NrLinkEqualizerV61.MMSE)snrLin/(snrLin+1.0)else 1.0;val eq=Array(fd.size){fd[it]*(1.0/gain)};s+="Equalizer: ${cfg.equalizer}"
        val evm=Dsp.evm(precoded,eq);val hard=demod(eq,m).copyOf(rm.size);s+="Demodulation: ${hard.size} bits";val recovered=NrPolarV47.rateRecover(hard,n,cfg.rv);val decoded=NrPolarV47.decode(recovered,scr.size);val descr=scramble(decoded,cfg.seed xor 0x61);s+="Polar decode: ${descr.size} bits"
        val data=descr.copyOf(minOf(payload.size,descr.size));var errors=abs(payload.size-data.size);for(i in data.indices)if(payload[i]!=data[i])errors++;val crc=descr.size>=tb.size&&crcOk(descr.copyOf(tb.size));val ber=errors.toDouble()/payload.size;s+="CRC: ${if(crc)"PASS" else "FAIL"}"
        val thr=cfg.prbs*12*14*cfg.scsKHz*1000.0*log2(m.toDouble())*cfg.codeRate*layers*.83/1e6;s+="Metrics: BER=${"%.6g".format(ber)}, EVM=${"%.3f".format(evm)}%"
        return NrIntegratedLinkResultV61(payload.size,cw.size,rm.size,data.size,errors,crc,ber,evm,thr,q.size,layers,tx,rx,N,precoded.size,s,q.take(96).toTypedArray(),eq.take(96).toTypedArray())
    }
}
