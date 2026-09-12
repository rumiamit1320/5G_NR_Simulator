package com.example.nrsimulator

/** V81: persistent UE/gNB/session state boundary. It does not replace existing RRC/NAS/RLC/MAC implementations. */
enum class NrUeStateV81 { IDLE, RRC_SETUP, RRC_CONNECTED, REGISTERED, PDU_SESSION }
data class NrBearerV81(val qfi:Int,val fiveQi:Int,val gtpTeid:Long,val dlBytes:Long,val ulBytes:Long)
data class NrUeContextV81(val ueId:Int,val rnti:Int,val state:NrUeStateV81,val servingCell:Int,val bearers:List<NrBearerV81>)
data class NrSystemSnapshotV81(val slot:Long,val ues:List<NrUeContextV81>,val cells:Int)
class NrSystemStateV81(private val cellCount:Int){
    private val ues=LinkedHashMap<Int,NrUeContextV81>();private var slot=0L
    init { require(cellCount>0) }
    fun registerUe(ueId:Int,rnti:Int,cell:Int):NrUeContextV81{require(cell in 0 until cellCount);val c=NrUeContextV81(ueId,rnti,NrUeStateV81.RRC_CONNECTED,cell,emptyList());ues[ueId]=c;return c}
    fun establishPduSession(ueId:Int,qfi:Int=9,fiveQi:Int=9,teid:Long=ueId.toLong()):NrUeContextV81{val c=ues[ueId]?:error("UE not registered");val b=NrBearerV81(qfi,fiveQi,teid,0,0);val n=c.copy(state=NrUeStateV81.PDU_SESSION,bearers=c.bearers+ b);ues[ueId]=n;return n}
    fun advanceSlot():NrSystemSnapshotV81{slot++;return snapshot()}
    fun snapshot()=NrSystemSnapshotV81(slot,ues.values.toList(),cellCount)
}
