package com.example.nrsimulator
object NrConformanceV31Tests {
    fun runAll():List<String>{
        val r=NrV31ConformanceTests.run(); check(r.pass); val out=mutableListOf<String>()
        out += "38.211 Gold sequence primitive: PASS"; out += "CRC24C primitive: PASS"; out += "128-NEA2 AES-CTR round trip: PASS"; out += "128-NIA2 AES-CMAC primitive: PASS"
        val rlc=NrRlcAmV31(); val (pdus,s)=rlc.segment(ByteArray(1500){it.toByte()},512); check(pdus.size==3); val (restored,st)=rlc.receive(pdus); check(restored.size==1500); check(st.ackSn==3); out += "RLC AM segmentation/reassembly: PASS"
        val rrc=NrRrcV31(); val t=rrc.complete(rrc.begin(1,NrRrcProcedureV31.SETUP)); check(t.completed && rrc.validate(NrRrcConfigV31())); out += "RRC transaction/config validation: PASS"; return out
    }
}
