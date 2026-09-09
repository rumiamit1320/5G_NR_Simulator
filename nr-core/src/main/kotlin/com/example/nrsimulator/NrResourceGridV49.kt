package com.example.nrsimulator

enum class NrChannelV49{PDSCH,PUSCH,PDCCH,PUCCH,DMRS,PT_RS,CSI_RS,SRS,PRACH}
data class NrResourceV49(val channel:NrChannelV49,val slot:Int,val symbol:Int,val prb:Int,val subcarrier:Int,val layer:Int=0)
class NrResourceGridV49(val prbs:Int,val symbols:Int=14,val scPerPrb:Int=12,val layers:Int=1){private val used=HashSet<NrResourceV49>();fun reserve(r:NrResourceV49):Boolean=used.add(r);fun reserve(channel:NrChannelV49,slot:Int,s0:Int,s1:Int,p0:Int,p1:Int,layer:Int=0):Int{var n=0;for(s in s0 until s1)for(p in p0 until p1)for(k in 0 until scPerPrb)if(reserve(NrResourceV49(channel,slot,s,p,k,layer)))n++;return n};fun isUsed(r:NrResourceV49)=r in used;fun usedCount()=used.size;fun collisions(request:List<NrResourceV49>)=request.count{it in used}}
