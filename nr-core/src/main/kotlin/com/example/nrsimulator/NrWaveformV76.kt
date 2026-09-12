package com.example.nrsimulator

/** V76: reusable baseband OFDM waveform boundary. Existing V61 waveform path is preserved. */
data class NrOfdmConfigV76(val fftSize:Int=256,val cpLength:Int=18,val activeSubcarriers:Int=120) {
    init { require(fftSize>0 && (fftSize and (fftSize-1))==0);require(cpLength in 0 until fftSize);require(activeSubcarriers in 1..fftSize-2) }
}
data class NrWaveformFrameV76(val frequency:Array<Complex>,val time:Array<Complex>,val withCp:Array<Complex>)
object NrWaveformV76 {
    fun map(data:Array<Complex>,c:NrOfdmConfigV76):Array<Complex>{
        val out=Array(c.fftSize){Complex(0.0,0.0)};val n=minOf(data.size,c.activeSubcarriers);val h=n/2
        for(i in 0 until h)out[i+1]=data[i]
        for(i in h until n)out[c.fftSize-n+i]=data[i]
        return out
    }
    fun modulate(data:Array<Complex>,c:NrOfdmConfigV76=NrOfdmConfigV76()):NrWaveformFrameV76{
        val f=map(data,c);val t=Dsp.fft(f,true);val cp=Array(c.fftSize+c.cpLength){i->if(i<c.cpLength)t[c.fftSize-c.cpLength+i] else t[i-c.cpLength]}
        return NrWaveformFrameV76(f,t,cp)
    }
    fun demodulate(samples:Array<Complex>,c:NrOfdmConfigV76=NrOfdmConfigV76()):Array<Complex>{
        require(samples.size>=c.fftSize+c.cpLength);val body=samples.copyOfRange(c.cpLength,c.cpLength+c.fftSize);val f=Dsp.fft(body);val n=c.activeSubcarriers;return Array(n){i->if(i<n/2)f[i+1]else f[c.fftSize-n+i]}
    }
}
