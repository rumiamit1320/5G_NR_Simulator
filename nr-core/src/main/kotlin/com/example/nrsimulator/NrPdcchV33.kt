package com.example.nrsimulator

/** V33: PDCCH/CORESET/REG/DM-RS mapping foundation layered under V18. */
data class NrCoresetV33(val id:Int,val startRb:Int,val rbCount:Int,val durationSymbols:Int,val mapping:String="nonInterleaved")
data class NrSearchSpaceV33(val id:Int,val coresetId:Int,val aggregationLevels:IntArray=intArrayOf(1,2,4,8,16),val candidates:Int=8)
data class NrPdcchMappingV33(val cce:IntArray,val reg:IntArray,val dmrs:IntArray)
object NrPdcchV33 {
    fun map(coreset:NrCoresetV33, aggregation:Int, candidate:Int):NrPdcchMappingV33 {
        require(aggregation in intArrayOf(1,2,4,8,16).toList())
        val cces=IntArray(aggregation){candidate*aggregation+it}
        val regs=cces.flatMap{ c -> (0 until 6).map{s->c*6+s} }.toIntArray()
        val dmrs=regs.filter{it%3==0}.toIntArray()
        return NrPdcchMappingV33(cces,regs,dmrs)
    }
}
