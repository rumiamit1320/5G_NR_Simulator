package com.example.nrsimulator

/** V136-V150 additive regression suite. */
object NrCanonicalV136V150Tests {
    data class Result(val passed:Boolean,val checks:Map<String,Boolean>)
    fun run():Result {
        val c=linkedMapOf<String,Boolean>()
        c["V136 shared multi-UE RX"]=NrCanonicalV136SharedMultiUeRx.run().passed
        c["V137 time-frequency DMRS"]=NrCanonicalV137TimeFrequencyDmrs.estimate(mapOf((0 to 0) to 1.0,(2 to 2) to 1.0)).mse.isFinite()
        c["V138 full-slot PHY"]=NrCanonicalV138FullSlotPhy.run().passed
        c["V139 scrambling"]=NrCanonicalV139Scrambling.scramble(intArrayOf(0,1,0,1)).contentEquals(NrCanonicalV139Scrambling.scramble(intArrayOf(0,1,0,1)))
        c["V140 coding coverage"]=NrCanonicalV140CodingCoverage.run().supported.size==5
        c["V141 HARQ"]=NrCanonicalV141HarqSoftCombining.combine(NrCanonicalV141HarqSoftCombining.Process(0),doubleArrayOf(1.0,-2.0),false).process.rounds==1
        c["V142 link adaptation"]=NrCanonicalV142LinkAdaptation.run(NrCanonicalV142LinkAdaptation.Input(20.0)).mcs>0
        c["V143 MIMO"]=NrCanonicalV143AdvancedMimo.run(NrCanonicalV143AdvancedMimo.Channel(arrayOf(doubleArrayOf(1.0,0.0),doubleArrayOf(0.0,1.0)))).rank==2
        c["V144 spatial channel"]=NrCanonicalV144SpatialChannel.run(NrCanonicalV144SpatialChannel.Config(model=NrCanonicalV144SpatialChannel.Model.CDL_A)).spatial
        c["V145 mobility"]=NrCanonicalV145Mobility.run().displacementM>0
        c["V146 RACH"]=NrCanonicalV146Rach.advance(NrCanonicalV146Rach.start()).state==NrCanonicalV146Rach.State.RESPONSE
        c["V147 PDCCH"]=NrCanonicalV147Pdcch.run(listOf(1,2,3,4)).candidates.size==4
        c["V148 RRC/NAS"]=NrCanonicalV148RrcNas.connect(NrCanonicalV148RrcNas.initial(7)).rrc==NrCanonicalV148RrcNas.Rrc.CONNECTED
        c["V149 E2E"]=NrCanonicalV149EndToEnd.run().ues.size==2
        c["V150 validation"]=NrCanonicalV150Validation.run().passed
        return Result(c.values.all{it},c)
    }
}
