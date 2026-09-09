package com.example.nrsimulator

/** V42: 5GC service-procedure orchestration over the existing V26 core model. */
enum class Nr5gcProcedureV42 { AMF_REGISTRATION,AUTHENTICATION,SECURITY_MODE,PDU_SESSION_ESTABLISHMENT,PDU_SESSION_RELEASE,UPF_FORWARDING }
data class Nr5gcSessionV42(val supi:String,val amf:String,val smf:String,val upf:String,val pduSessionId:Int,val dnn:String,val snssai:String,val state:String)
object Nr5gcV42 { fun establish(supi:String,id:Int,dnn:String="internet",snssai:String="1-010203"):Nr5gcSessionV42=Nr5gcSessionV42(supi,"amf-01","smf-01","upf-01",id,dnn,snssai,"ESTABLISHED") }
