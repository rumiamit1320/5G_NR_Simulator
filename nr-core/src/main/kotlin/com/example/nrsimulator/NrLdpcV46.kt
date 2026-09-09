package com.example.nrsimulator

/** V46: stronger 38.212 transport orchestration. Existing V8/V9 engines remain intact. */
data class NrLdpcCbV46(val index:Int,val k:Int,val n:Int,val filler:Int,val rv:Int,val bits:IntArray)
data class NrTransportV46(val baseGraph:Int,val zc:Int,val tbBits:Int,val tbCrcBits:Int,val codeBlocks:List<NrLdpcCbV46>,val g:Int,val ePerCb:Int,val supportedExactEngine:Boolean)
object NrLdpcV46 {
    private val zcSet=setOf(2,3,4,5,6,8,9,10,12,16,18,20,24,26,27,30,32,36,40,48,50,52,54,56,60,64,72,80,81,88,90,96,104,108,112,120,128,144,160,176,192,208,216,224,240,256,288,320,352,384)
    fun crc24a(bits:IntArray):Int=NrCrc24AV46.compute(bits)
    fun crc24b(bits:IntArray):Int=NrCrc24BV46.compute(bits)
    fun build(tb:IntArray, targetG:Int, bg:Int=if(tb.size<=292)2 else 1, rv:Int=0):NrTransportV46{
        require(targetG>=0); require(rv in 0..3); require(bg in 1..2)
        val tbCrc=NrCrc24AV46.append(tb); val maxCb=8448
        val chunks=if(tbCrc.size<=maxCb) listOf(tbCrc) else tbCrc.asList().chunked(maxCb).map{it.toIntArray()}
        val zc=chooseZc(maxOf(1, chunks.maxOf{it.size}),bg)
        val n=if(bg==1)66*zc else 50*zc; val k=if(bg==1)22*zc else 10*zc
        val e=if(chunks.isEmpty())0 else targetG/chunks.size
        val blocks=chunks.mapIndexed{idx,c->NrLdpcCbV46(idx,c.size,n, maxOf(0,k-c.size),rv, rateMatch(c,n,e,rv))}
        return NrTransportV46(bg,zc,tb.size,tbCrc.size,blocks,targetG,e,bg==2 && zc in setOf(3,6,12,24,48))
    }
    private fun chooseZc(k:Int,bg:Int):Int=zcSet.firstOrNull{(if(bg==1)22 else 10)*it>=k}?:zcSet.last()
    private fun rateMatch(bits:IntArray,n:Int,e:Int,rv:Int):IntArray{ if(e<=0)return IntArray(0); return IntArray(e){i->bits[(i + (rv*n/4))%bits.size] and 1} }
}
object NrCrc24AV46 { private const val P=0x1864CFB; fun compute(x:IntArray):Int{var c=0;for(v0 in x){val t=((c ushr 23)and 1) xor(v0 and 1);c=(c shl 1)and 0xFFFFFF;if(t!=0)c=c xor P};return c};fun append(x:IntArray)=x.copyOf(x.size+24).also{o->val c=compute(x);for(i in 0 until 24)o[x.size+i]=(c ushr(23-i))and 1} }
object NrCrc24BV46 { private const val P=0x1800063; fun compute(x:IntArray):Int{var c=0;for(v0 in x){val t=((c ushr 23)and 1) xor(v0 and 1);c=(c shl 1)and 0xFFFFFF;if(t!=0)c=c xor P};return c};fun append(x:IntArray)=x.copyOf(x.size+24).also{o->val c=compute(x);for(i in 0 until 24)o[x.size+i]=(c ushr(23-i))and 1} }
