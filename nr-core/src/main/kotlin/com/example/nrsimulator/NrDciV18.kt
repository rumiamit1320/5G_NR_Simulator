package com.example.nrsimulator

/**
 * V18 additive DCI 1_0 reference model.
 *
 * The field set is intentionally compact and self-contained so it can feed the
 * existing V17 link-adaptation layer without changing V1-v17 source files.
 * The bit widths are BWP-aware for a reference 1_0 allocation subset.
 */
data class NrDciV18(
    val format: Int = 0,
    val identifier: Int = 0,
    val frequencyDomainAssignment: Int = 0,
    val timeDomainAssignment: Int = 0,
    val vrbToPrbMapping: Int = 0,
    val prbBundling: Int = 0,
    val mcs: Int = 16,
    val ndi: Int = 1,
    val rv: Int = 0,
    val harqProcess: Int = 0,
    val tpc: Int = 1,
    val pucchResource: Int = 0,
    val pdschToHarqTiming: Int = 1,
    val layers: Int = 2
) {
    fun toBits(bwpPrbs: Int): IntArray {
        val f = NrDciV18Fields.forBwp(bwpPrbs)
        val w = BitWriter()
        w.put(format and 1, 1)
        w.put(identifier and 0xFFFF, 16)
        w.put(frequencyDomainAssignment and ((1 shl f.freqBits) - 1), f.freqBits)
        w.put(timeDomainAssignment and 0x0F, 4)
        w.put(vrbToPrbMapping and 1, 1)
        w.put(prbBundling and 1, 1)
        w.put(mcs.coerceIn(0, 31), 5)
        w.put(ndi and 1, 1)
        w.put(rv and 3, 2)
        w.put(harqProcess and 15, 4)
        w.put(tpc and 3, 2)
        w.put(pucchResource and 0x0F, 4)
        w.put(pdschToHarqTiming and 0x0F, 4)
        w.put((layers - 1).coerceIn(0, 3), 2)
        return w.toArray()
    }

    companion object {
        fun fromBits(bits: IntArray, bwpPrbs: Int): NrDciV18 {
            val f = NrDciV18Fields.forBwp(bwpPrbs)
            val r = BitReader(bits)
            val format = r.get(1)
            val identifier = r.get(16)
            val fd = r.get(f.freqBits)
            val td = r.get(4)
            val vrb = r.get(1)
            val bundling = r.get(1)
            val mcs = r.get(5)
            val ndi = r.get(1)
            val rv = r.get(2)
            val harq = r.get(4)
            val tpc = r.get(2)
            val pucch = r.get(4)
            val k1 = r.get(4)
            val layers = r.get(2) + 1
            return NrDciV18(format, identifier, fd, td, vrb, bundling, mcs, ndi, rv, harq, tpc, pucch, k1, layers)
        }
    }
}

data class NrDciV18Fields(val freqBits: Int) {
    companion object {
        fun forBwp(prbs: Int): NrDciV18Fields {
            val p = prbs.coerceIn(1, 275)
            // Reference Type-1 contiguous allocation field width: ceil(log2(NRB*(NRB+1)/2)).
            val n = p * (p + 1) / 2
            var bits = 0
            var x = 1
            while (x < n) { bits++; x = x shl 1 }
            return NrDciV18Fields(bits.coerceAtLeast(1))
        }
    }
}

private class BitWriter {
    private val a = ArrayList<Int>()
    fun put(v0: Int, n: Int) { for (i in n - 1 downTo 0) a += ((v0 ushr i) and 1) }
    fun toArray() = a.toIntArray()
}

private class BitReader(private val a: IntArray) {
    private var p = 0
    fun get(n: Int): Int { var v = 0; repeat(n) { v = (v shl 1) or (if (p < a.size) a[p] else 0); p++ }; return v }
}

/** 24-bit CRC used for DCI scrambling/masking in the reference control path. */
object NrCrc24C {
    private const val POLY = 0x1864CFB
    fun compute(bits: IntArray): Int {
        var crc = 0
        for (b in bits) {
            val top = ((crc ushr 23) and 1) xor (b and 1)
            crc = (crc shl 1) and 0xFFFFFF
            if (top != 0) crc = crc xor POLY
        }
        return crc and 0xFFFFFF
    }

    fun appendAndMask(bits: IntArray, rnti: Int): IntArray {
        val crc = compute(bits) xor (rnti and 0xFFFF)
        val out = bits.copyOf(bits.size + 24)
        for (i in 0 until 24) out[bits.size + i] = (crc ushr (23 - i)) and 1
        return out
    }

    fun checkAndUnmask(bitsWithCrc: IntArray, rnti: Int): Pair<Boolean, IntArray> {
        if (bitsWithCrc.size < 24) return false to IntArray(0)
        val n = bitsWithCrc.size - 24
        var got = 0
        for (i in 0 until 24) got = (got shl 1) or bitsWithCrc[n + i]
        val expected = compute(bitsWithCrc.copyOf(n)) xor (rnti and 0xFFFF)
        return (got == (expected and 0xFFFFFF)) to bitsWithCrc.copyOf(n)
    }
}
