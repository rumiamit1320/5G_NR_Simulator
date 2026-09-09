package com.example.nrsimulator

/** V35: additive exact-mapping contracts for DM-RS/PT-RS/PUSCH/PUCCH/SRS paths. */
enum class NrChannelV35 { PDSCH,PUSCH,PDCCH,PUCCH,SRS,CSI_RS,PRACH }
data class NrResourceElementV35(val symbol:Int,val subcarrier:Int,val channel:NrChannelV35,val dmrs:Boolean=false,val ptrs:Boolean=false)
data class NrPhyAllocationV35(val channel:NrChannelV35,val startSymbol:Int,val symbolCount:Int,val startRb:Int,val rbCount:Int,val layers:Int)
object NrPhyMappingV35 {
    fun allocate(a:NrPhyAllocationV35):List<NrResourceElementV35>{
        require(a.symbolCount>0 && a.rbCount>0 && a.layers>0)
        val out=ArrayList<NrResourceElementV35>(); for(s in a.startSymbol until a.startSymbol+a.symbolCount) for(rb in a.startRb until a.startRb+a.rbCount) for(k in 0 until 12) out += NrResourceElementV35(s,rb*12+k,a.channel,s==a.startSymbol&&k%4==0,s%4==0&&k%4==0)
        return out
    }
}
