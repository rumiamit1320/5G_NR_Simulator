package com.example.nrsimulator

enum class NrScsV50(val khz:Int){SCS15(15),SCS30(30),SCS60(60),SCS120(120),SCS240(240)}
data class NrBwpV50(val startPrb:Int,val sizePrb:Int,val scs:NrScsV50,val cpSymbols:Int=14)
data class NrSlotTimingV50(val scs:NrScsV50,val slotIndex:Int,val slotsPerFrame:Int,val symbolsPerSlot:Int,val slotDurationUs:Double)
object NrNumerologyV50{fun slotsPerFrame(s:NrScsV50)=10*(s.khz/15);fun timing(s:NrScsV50,slot:Int)=NrSlotTimingV50(s,slot,slotsPerFrame(s),14,1000.0/(s.khz/15));fun validate(b:NrBwpV50,totalPrbs:Int)=b.startPrb>=0&&b.sizePrb>0&&b.startPrb+b.sizePrb<=totalPrbs}
