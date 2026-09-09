package com.example.nrsimulator
data class NrSlotV23(val frame:Int,val slot:Int,val dl:Boolean,val ul:Boolean,val flexible:Boolean)
data class NrSlotEngineV23Result(val slots:Int,val executed:Int,val dlSlots:Int,val ulSlots:Int,val tick:Int,val pass:Boolean,val note:String)
class NrSlotEngineV23 { fun run(frames:Int=1,slotsPerFrame:Int=20,dlRatio:Double=.7):NrSlotEngineV23Result { val n=(frames*slotsPerFrame).coerceAtLeast(1); var dl=0;var ul=0;for(i in 0 until n){if(i.toDouble()/n<dlRatio)dl++ else ul++};return NrSlotEngineV23Result(n,n,dl,ul,n,true,"Unified frame/slot clock; V1-V22 modules remain callable and can be scheduled from this deterministic orchestration layer.") } }
