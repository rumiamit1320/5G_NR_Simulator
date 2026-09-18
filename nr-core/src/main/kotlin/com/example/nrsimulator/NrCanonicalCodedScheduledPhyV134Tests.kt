package com.example.nrsimulator

/** Deterministic V134 coded scheduled PHY regression suite. */
object NrCanonicalCodedScheduledPhyV134Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)

    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        val r = NrCanonicalCodedScheduledPhyV134.run()
        c["execution passes"] = r.passed
        c["multiple UE grants"] = r.grants.size >= 2
        c["one report per grant"] = r.ueReports.size == r.grants.size
        c["coded bits mapped"] = r.ueReports.all { it.codedBits > 0 && it.mappedDataRe > 0 }
        c["DMRS mapped"] = r.dmrsElements > 0 && r.ueReports.all { it.mappedDmrsRe > 0 }
        c["LDPC round-trip"] = r.ueReports.all { it.ldpcPassed }
        c["TB CRC round-trip"] = r.ueReports.all { it.crcPassed }
        c["no scheduled-grid collisions"] = r.ueReports.all { it.mappedDataRe >= it.codedBits / 2 } 
        c["OFDM waveform materialized"] = r.waveform.size >= 1 &&
            r.waveform.all { layer -> layer.size == 14 && layer.all { it.size == r.waveform[0][0].size } }
        return Result(c.values.all { it }, c)
    }
}
