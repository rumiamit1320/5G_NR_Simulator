package com.example.nrsimulator

data class NrCsiReportV80(val ueId:Int,val rank:Int,val widebandSinrDb:Double,val cqi:Int,val ri:Int,val pmi:Int)
data class NrSrsMeasurementV80(val ueId:Int,val resourceId:Int,val receivedPowerDbm:Double,val noisePowerDbm:Double,val sinrDb:Double)
object NrCsiV80 {
    fun report(ueId:Int,sinrDb:Double,rank:Int=1,pmi:Int=0):NrCsiReportV80{
        val cqi=when{sinrDb< -5->0;sinrDb< -2->1;sinrDb<0->2;sinrDb<2->3;sinrDb<4->4;sinrDb<6->5;sinrDb<8->6;sinrDb<10->7;sinrDb<12->8;sinrDb<14->9;sinrDb<16->10;sinrDb<18->11;sinrDb<20->12;sinrDb<22->13;sinrDb<24->14;else->15}
        val ri=rank.coerceIn(1,8)
        return NrCsiReportV80(ueId,ri,sinrDb,cqi,ri,pmi)
    }
    fun measure(ueId:Int,resourceId:Int,receivedPowerDbm:Double,noisePowerDbm:Double)=NrSrsMeasurementV80(ueId,resourceId,receivedPowerDbm,noisePowerDbm,receivedPowerDbm-noisePowerDbm)
}
