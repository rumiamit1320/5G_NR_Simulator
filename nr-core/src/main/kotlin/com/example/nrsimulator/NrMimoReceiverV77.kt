package com.example.nrsimulator

/** V77: receiver-side reference processing. It consumes measured pilots/data and never invents a channel response. */
data class NrChannelEstimateV77(val h:Array<Array<Complex>>,val noiseVariance:Double)
data class NrEqualizedV77(val symbols:Array<Complex>,val postEqSinrDb:Double,val estimate:NrChannelEstimateV77)
object NrMimoReceiverV77 {
    fun estimate(dmrsTx:Array<Complex>,dmrsRx:Array<Complex>,noiseVariance:Double=1e-3):NrChannelEstimateV77{
        require(dmrsTx.isNotEmpty()&&dmrsTx.size==dmrsRx.size);require(noiseVariance>=0)
        var p=0.0;var q=0.0;for(i in dmrsTx.indices){p+=dmrsTx[i].re*dmrsRx[i].re+dmrsTx[i].im*dmrsRx[i].im;q+=dmrsTx[i].re*dmrsTx[i].re+dmrsTx[i].im*dmrsTx[i].im}
        val g=if(q==0.0)Complex(0.0,0.0)else Complex(p/q,0.0)
        return NrChannelEstimateV77(arrayOf(arrayOf(g)),noiseVariance)
    }
    fun equalize(data:Array<Complex>,estimate:NrChannelEstimateV77):NrEqualizedV77{
        require(estimate.h.isNotEmpty()&&estimate.h[0].isNotEmpty());val h=estimate.h[0][0];val den=h.re*h.re+h.im*h.im+estimate.noiseVariance
        val inv=if(den==0.0)Complex(0.0,0.0)else Complex(h.re/den,-h.im/den);val y=Array(data.size){data[it]*inv};val sig=h.re*h.re+h.im*h.im;val snr=10.0*Math.log10(maxOf(1e-12,sig/maxOf(1e-12,estimate.noiseVariance)));return NrEqualizedV77(y,snr,estimate)
    }
}
