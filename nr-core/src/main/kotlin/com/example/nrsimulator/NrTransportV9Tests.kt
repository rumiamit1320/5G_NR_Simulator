package com.example.nrsimulator

/** Deterministic v9 transport/LDPC integration tests. */
object NrTransportV9KnownAnswerTests {
    fun run(): List<String> {
        val out = ArrayList<String>()
        for (rv in 0..3) {
            val r = NrTransportV9().run(
                NrTransportV9Config(
                    payloadBits = 300,
                    targetCodeRate = 0.5,
                    rv = rv,
                    qm = 2,
                    layers = 1,
                    nRe = 1200,
                    snrDb = 30.0
                )
            )
            out += "BG2/iLS1 RV=$rv Zc=${r.zc}: ${if (r.ldpcPass && r.tbCrcOk) "PASS" else "FAIL"} errors=${r.decodeErrors} syndrome=${r.syndromeWeight}"
        }
        return out
    }
}
