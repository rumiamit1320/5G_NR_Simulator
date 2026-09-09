package com.example.nrsimulator
data class NrTddV29Config(val pattern:String="DDDDDDUUUU",val slotsPerFrame:Int=20)
data class NrTddV29Result(val dlSymbols:Int,val ulSymbols:Int,val flexibleSymbols:Int,val guardSymbols:Int,val pass:Boolean,val note:String)
class NrTddV29 { fun run(cfg:NrTddV29Config=NrTddV29Config()):NrTddV29Result { val dl=cfg.pattern.count{it=='D'};val ul=cfg.pattern.count{it=='U'};val f=cfg.pattern.count{it=='F'};return NrTddV29Result(dl,ul,f,cfg.pattern.count{it=='G'},true,"TDD slot-format reference. The scheduler can consume this result to constrain DL/UL grants without altering existing PHY modules.") } }
