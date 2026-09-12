package com.example.nrsimulator

/** V75: standards-oriented transport-block accounting around the existing V46/V74 codecs. */
data class NrTransportConfigV75(val tbBits:Int,val targetG:Int,val baseGraph:Int,val rv:Int=0,val codeRate:Double=0.5) {
    init { require(tbBits>0);require(targetG>=0);require(baseGraph in 1..2);require(rv in 0..3);require(codeRate>0.0&&codeRate<=1.0) }
}
data class NrCodeBlockV75(val index:Int,val payloadBits:Int,val fillerBits:Int,val zc:Int,val n:Int,val e:Int,val rv:Int)
data class NrTransportResultV75(val config:NrTransportConfigV75,val tbCrcBits:Int,val codeBlocks:List<NrCodeBlockV75>,val effectiveCodeRate:Double)
object NrTransportV75 {
    private val zc=listOf(2,3,4,5,6,8,9,10,12,16,18,20,24,26,27,30,32,36,40,48,50,52,54,56,60,64,72,80,81,88,90,96,104,108,112,120,128,144,160,176,192,208,216,224,240,256,288,320,352,384)
    fun plan(c:NrTransportConfigV75):NrTransportResultV75{
        val crc= c.tbBits+24
        val cb=if(crc<=8448)1 else (crc+8447)/8448
        val per=(crc+cb-1)/cb
        val z=zc.firstOrNull{(if(c.baseGraph==1)22 else 10)*it>=per}?:zc.last()
        val n=(if(c.baseGraph==1)66 else 50)*z
        val k=(if(c.baseGraph==1)22 else 10)*z
        val e=if(cb==0)0 else c.targetG/cb
        val blocks=(0 until cb).map{i->val p=minOf(per,crc-i*per);NrCodeBlockV75(i,p,maxOf(0,k-p),z,n,e,c.rv)}
        val total=blocks.sumOf{it.e}
        return NrTransportResultV75(c,crc,blocks,if(total==0)0.0 else c.tbBits.toDouble()/total)
    }
}
