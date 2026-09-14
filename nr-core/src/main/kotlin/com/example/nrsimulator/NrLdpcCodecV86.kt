package com.example.nrsimulator

/** V86 additive QC-LDPC encode/decode layer. */
object NrLdpcCodecV86 {
    data class Encoded(val bits: IntArray, val parityCheck: Array<IntArray>)
    data class Decoded(val bits: IntArray, val iterations: Int, val syndromeWeight: Int, val converged: Boolean)

    fun encode(infoBits: IntArray, table: NrLdpcV85.Table, z: Int, liftingSet: Int): Encoded {
        val h = NrLdpcV85.lift(table, z, liftingSet)
        val bits = NrLdpcV74.encode(infoBits, h)
        return Encoded(bits, h)
    }

    fun decode(llr: DoubleArray, table: NrLdpcV85.Table, z: Int, liftingSet: Int,
               maxIterations: Int = 25): Decoded {
        val h = NrLdpcV85.lift(table, z, liftingSet)
        val r = NrLdpcV74.decode(llr, h, maxIterations)
        return Decoded(r.bits, r.iterations, r.syndromeWeight, r.converged)
    }

    fun syndromeWeight(bits: IntArray, parityCheck: Array<IntArray>): Int {
        require(parityCheck.isNotEmpty() && parityCheck[0].size == bits.size)
        var weight = 0
        for (row in parityCheck) {
            var p = 0
            for (c in bits.indices) if (row[c] != 0) p = p xor (bits[c] and 1)
            if (p != 0) weight++
        }
        return weight
    }
}
