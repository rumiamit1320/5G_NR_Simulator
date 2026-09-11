package com.example.nrsimulator

data class NrIntegratedSystemConfigV63(val slots:Int=20,val ueCount:Int=8,val cells:Int=1,val prbs:Int=52,val scsKHz:Int=30,val carrierGHz:Double=3.5,val velocityKmh:Double=30.0,val snrOffsetDb:Double=0.0,val modulationOrder:Int=16,val payloadBitsPerUe:Int=128,val txAntennas:Int=4,val rxAntennas:Int=4,val layers:Int=1,val seed:Int=6301)
data class NrIntegratedUeV63(val ueId:Int,val meanSinrDb:Double,val meanCqi:Double,val meanMcs:Double,val totalAllocatedPrbs:Int,val throughputMbps:Double,val meanBler:Double,val phyCrcPassRate:Double,val phyBer:Double)
data class NrIntegratedSystemResultV63(val slots:Int,val ueStates:List<NrIntegratedUeV63>,val totalThroughputMbps:Double,val systemFairness:Double,val phyCrcPassRate:Double,val phyBer:Double,val slotResults:List<NrRadioEnvironmentResultV62>)

/** Additive V63 bridge: V62 radio conditions drive the existing V61 PHY. */
object NrIntegratedSystemV63 {
 private fun modulationForMcs(mcs:Int)=when{mcs>=23->256;mcs>=17->64;mcs>=10->16;else->4}
 fun run(config:NrIntegratedSystemConfigV63=NrIntegratedSystemConfigV63()):NrIntegratedSystemResultV63{
  val c=config.copy(slots=config.slots.coerceIn(1,1000),ueCount=config.ueCount.coerceIn(1,64),cells=config.cells.coerceIn(1,7),prbs=config.prbs.coerceIn(1,106),scsKHz=if(config.scsKHz in listOf(15,30,60))config.scsKHz else 30,payloadBitsPerUe=config.payloadBitsPerUe.coerceIn(32,200),txAntennas=config.txAntennas.coerceIn(1,4),rxAntennas=config.rxAntennas.coerceIn(1,4),layers=config.layers.coerceIn(1,minOf(config.txAntennas,config.rxAntennas)))
  val slots=(0 until c.slots).map{slot->NrRadioEnvironmentV62.run(NrRadioEnvironmentConfigV62(ueCount=c.ueCount,cells=c.cells,prbs=c.prbs,scsKHz=c.scsKHz,carrierGHz=c.carrierGHz,velocityKmh=c.velocityKmh,txAntennas=c.txAntennas,rxAntennas=c.rxAntennas,layers=c.layers,slotIndex=slot.toLong(),seed=c.seed))}
  val ue=(1..c.ueCount).map{id->val samples=slots.mapNotNull{s->s.ueStates.find{it.ueId==id}};var pass=0;var errors=0L;samples.forEach{u->val phy=NrIntegratedLinkV61.run(NrIntegratedLinkConfigV61(payloadBits=c.payloadBitsPerUe,snrDb=(u.sinrDb+c.snrOffsetDb).coerceIn(-5.0,40.0),modulationOrder=modulationForMcs(u.mcs),layers=u.rank.coerceIn(1,c.layers),txAntennas=c.txAntennas,rxAntennas=c.rxAntennas,prbs=u.allocatedPrbs.coerceAtLeast(1),scsKHz=c.scsKHz,seed=c.seed+id*1009+u.ueId*17));if(phy.crcPass)pass++;errors+=phy.bitErrors.toLong()};NrIntegratedUeV63(id,samples.map{it.sinrDb}.average(),samples.map{it.cqi}.average(),samples.map{it.mcs}.average(),samples.sumOf{it.allocatedPrbs},samples.sumOf{it.throughputMbps}/c.slots,samples.map{it.bler}.average(),pass.toDouble()/samples.size,errors.toDouble()/(samples.size*c.payloadBitsPerUe))}
  val t=ue.map{it.throughputMbps};val sum=t.sum();val sq=t.sumOf{it*it};val fairness=if(sq==0.0)0.0 else sum*sum/(t.size*sq)
  return NrIntegratedSystemResultV63(c.slots,ue,sum,fairness,ue.map{it.phyCrcPassRate}.average(),ue.map{it.phyBer}.average(),slots)
 }
}
