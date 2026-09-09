package com.example.nrsimulator

/** Deterministic reference tests for the embedded BG2/iLS1 graph. */
object NrLdpcV8KnownAnswerTests {
    fun run(): List<String> {
        val out = ArrayList<String>()
        for (z in intArrayOf(3, 6, 12, 24, 48)) {
            val r = NrLdpcV8().run(NrLdpcV8Config(z = z, snrDb = 20.0, iterations = 20, runNoisyTest = false))
            out += "BG2 iLS1 Zc=$z: ${if (r.noNoisePass) "PASS" else "FAIL"} errors=${r.bitErrors} syndrome=${r.syndromeWeight}"
        }
        return out
    }
}
