package com.example.nrsimulator

/** V40: 5GS NAS security/session envelope foundation. */
enum class NrNasMessageV40 { REGISTRATION_REQUEST,REGISTRATION_ACCEPT,AUTHENTICATION_REQUEST,AUTHENTICATION_RESPONSE,SECURITY_MODE_COMMAND,SECURITY_MODE_COMPLETE,PDU_SESSION_ESTABLISHMENT }
data class NrNasEnvelopeV40(val message:NrNasMessageV40,val securityHeader:Int,val sequence:Int,val payload:ByteArray,val mac:Int?=null)
object NrNasV40 { fun protect(key:ByteArray,e:NrNasEnvelopeV40,count:Long,bearer:Int,direction:Int):NrNasEnvelopeV40{val c=NrSecurityV31.nea2(key,count,bearer,direction,e.payload);val m=NrSecurityV31.nia2(key,count,bearer,direction,c);return e.copy(payload=c,mac=m,securityHeader=2)} }
