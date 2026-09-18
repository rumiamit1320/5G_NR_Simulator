package com.example.nrsimulator

object NrCanonicalV123Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)
    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        c["15 kHz has 10 slots/frame"] = NrCanonicalNumerologyV123.slotsPerFrame(NrCanonicalNumerologyV123.Scs.SCS15) == 10
        c["30 kHz has 20 slots/frame"] = NrCanonicalNumerologyV123.slotsPerFrame(NrCanonicalNumerologyV123.Scs.SCS30) == 20
        c["60 kHz has 40 slots/frame"] = NrCanonicalNumerologyV123.slotsPerFrame(NrCanonicalNumerologyV123.Scs.SCS60) == 40
        c["120 kHz has 80 slots/frame"] = NrCanonicalNumerologyV123.slotsPerFrame(NrCanonicalNumerologyV123.Scs.SCS120) == 80
        c["30 kHz slot is 500 us"] = NrCanonicalNumerologyV123.slotDurationUs(NrCanonicalNumerologyV123.Scs.SCS30) == 500.0
        val t = NrCanonicalNumerologyV123.timing(NrCanonicalNumerologyV123.Config(NrCanonicalNumerologyV123.Scs.SCS30, 2, 7))
        c["absolute slot index"] = t.absoluteSlot == 47L
        c["14 normal-CP symbols"] = t.symbolsPerSlot == 14
        val g = NrCanonicalResourceGridV123(10, layers = 2)
        val a = g.reserve(3, 2, 4, 1, 5, 0)
        val b = g.reserve(3, 2, 4, 1, 5, 0)
        c["one allocation is 240 RE"] = a.inserted == 4 * 5 * 12
        c["repeat is collision-only"] = b.collisions == a.inserted && b.inserted == 0
        c["layer isolation"] = g.reserve(3, 2, 4, 1, 5, 1).inserted == a.inserted
        c["RE indexing"] = NrCanonicalResourceGridV123.Key(0, 1, 2, 7, 0).absoluteSubcarrier() == 31
        c["slot wrapping"] = NrCanonicalNumerologyV123.slotInFrame(20, NrCanonicalNumerologyV123.Scs.SCS30) == 0
        return Result(c.values.all { it }, c)
    }
}
