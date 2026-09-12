package com.example.nrsimulator

/** Additive V74 reference-vector helpers for parity/syndrome validation. */
object NrLdpcV74ReferenceVectors {
    fun syndromeWeight(h:Array<IntArray>,bits:IntArray):Int {
        require(h.isNotEmpty() && bits.size==h[0].size)
        var weight=0
        for(row in h){
            var p=0
            for(c in row.indices) p = p xor (row[c] and bits[c])
            weight += p
        }
        return weight
    }

    fun isCodeword(h:Array<IntArray>,bits:IntArray):Boolean = syndromeWeight(h,bits)==0
}
