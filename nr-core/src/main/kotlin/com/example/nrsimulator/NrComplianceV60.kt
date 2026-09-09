package com.example.nrsimulator

data class NrComplianceItemV60(val id:String,val requirement:String,val implementation:String,val status:String)
object NrComplianceV60 {
    fun matrix():List<NrComplianceItemV60> = listOf(
        NrComplianceItemV60("PHY","38.211/212/213/214","V8-V18 + V46-V50","PARTIAL"),
        NrComplianceItemV60("MAC","38.321","V22/V36 + V52","PARTIAL"),
        NrComplianceItemV60("RLC","38.322","V24/V31/V37 + V53","PARTIAL"),
        NrComplianceItemV60("PDCP","38.323","V25/V38 + V54","PARTIAL"),
        NrComplianceItemV60("RRC","38.331","V31/V39 + V55","PARTIAL"),
        NrComplianceItemV60("NAS","24.501/33.501","V40 + V56","PARTIAL"),
        NrComplianceItemV60("5GC","23.501/29-series","V26/V42 + V57","PARTIAL"),
        NrComplianceItemV60("Conformance","38.508/521/523","V44/V45/V59","NOT CERTIFIED")
    )
    fun summary() = matrix().joinToString(" | ") { "${it.id}:${it.status}" }
}
