package com.example.nrsimulator

object NrCanonicalChannelV125Tests {
    data class Result(val passed: Boolean, val checks: Map<String, Boolean>)
    fun run(): Result {
        val c = linkedMapOf<String, Boolean>()
        val one = NrCanonicalSpatialEngine.C(1.0, 0.0)
        val input = Array(1) { Array(8) { one } }
        val tap = NrCanonicalSpatialEngine.Tap(1, arrayOf(arrayOf(NrCanonicalSpatialEngine.C(1.0, 0.0))))
        val a = NrCanonicalChannelV125.apply(input, listOf(tap), NrCanonicalChannelV125.Config(noiseVariance = 0.0))
        c["output dimensions"] = a.output.size == 1 && a.output[0].size == 8
        c["delay metadata"] = a.appliedDelays == listOf(1.0)
        c["zero noise deterministic"] = a.output[0][2].re == 1.0 && a.output[0][2].im == 0.0
        val noisy = NrCanonicalChannelV125.apply(input, listOf(tap), NrCanonicalChannelV125.Config(noiseVariance = 0.25, seed = 7))
        c["noise finite"] = noisy.output.flatten().all { it.re.isFinite() && it.im.isFinite() }
        return Result(c.values.all { it }, c)
    }
}
