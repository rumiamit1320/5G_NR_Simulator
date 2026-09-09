package com.example.nrsimulator

/** V41: NGAP/F1AP/XnAP common message transport contracts. */
enum class NrRanInterfaceV41 { N2_NGAP,N3_GTPU,F1AP,XnAP,E1AP }
data class NrInterfaceMessageV41(val iface:NrRanInterfaceV41,val procedure:Int,val stream:Int,val criticality:Int,val payload:ByteArray)
object NrInterfaceV41 { fun wrap(iface:NrRanInterfaceV41,procedure:Int,payload:ByteArray,stream:Int=0)=NrInterfaceMessageV41(iface,procedure,stream,0,payload) }
