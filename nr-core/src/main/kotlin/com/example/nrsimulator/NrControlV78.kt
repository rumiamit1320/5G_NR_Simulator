package com.example.nrsimulator

/** V78: persistent control-plane allocation model. Existing PDCCH/DCI engines remain authoritative where present. */
data class NrDciGrantV78(val ueId:Int,val rnti:Int,val slot:Int,val rbStart:Int,val rbLength:Int,val mcs:Int,val harqProcess:Int,val ndi:Boolean,val rv:Int)
data class NrControlSlotV78(val slot:Int,val grants:List<NrDciGrantV78>,val usedRbs:Int)
object NrControlV78 {
    fun schedule(slot:Int,ueIds:List<Int>,availableRbs:Int,mcsByUe:Map<Int,Int>,rntiByUe:Map<Int,Int>,harqByUe:Map<Int,Int>):NrControlSlotV78{
        require(availableRbs>=0);var cursor=0;val grants=ArrayList<NrDciGrantV78>();for(ue in ueIds.distinct().sorted()){
            if(cursor>=availableRbs)break;val len=maxOf(1,minOf(availableRbs-cursor,4));grants+=NrDciGrantV78(ue,rntiByUe[ue]?:1000+ue,slot,cursor,len,(mcsByUe[ue]?:0).coerceIn(0,27),harqByUe[ue]?:0,true,0);cursor+=len
        };return NrControlSlotV78(slot,grants,cursor)
    }
}
