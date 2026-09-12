package com.example.nrsimulator

/** Additive TS 38.212 transport-block CRC and LDPC code-block segmentation. */
object NrTransportV83 {
    enum class CrcType { CRC16, CRC24A, CRC24B }

    data class SegmentedCodeBlock(
        val index: Int,
        val payload: IntArray,
        val crc: IntArray?,
        val fillerBits: Int,
        val k: Int
    ) {
        val bits: Int get() = payload.size + (crc?.size ?: 0) + fillerBits
    }

    data class Segmentation(
        val baseGraph: NrLdpcV82.BaseGraph,
        val transportBlockBits: Int,
        val transportCrcType: CrcType,
        val codeBlocks: List<SegmentedCodeBlock>,
        val fillerBits: Int
    )

    fun crcTypeForTransportBlock(a: Int): CrcType = if (a > 3824) CrcType.CRC24A else CrcType.CRC16

    /** NR CRC attachment, MSB-first bit convention. */
    fun appendCrc(bits: IntArray, type: CrcType): IntArray {
        val width = when (type) { CrcType.CRC16 -> 16; CrcType.CRC24A, CrcType.CRC24B -> 24 }
        val poly = when (type) {
            CrcType.CRC16 -> 0x1021
            CrcType.CRC24A -> 0x864CFB
            CrcType.CRC24B -> 0x800063
        }
        var reg = 0
        val mask = (1 shl width) - 1
        for (bit in bits) {
            val top = ((reg ushr (width - 1)) and 1) xor (bit and 1)
            reg = (reg shl 1) and mask
            if (top != 0) reg = reg xor poly
        }
        val out = bits.copyOf(bits.size + width)
        for (i in 0 until width) out[bits.size + i] = (reg ushr (width - 1 - i)) and 1
        return out
    }

    fun checkCrc(bitsWithCrc: IntArray, type: CrcType): Boolean {
        val width = when (type) { CrcType.CRC16 -> 16; else -> 24 }
        require(bitsWithCrc.size >= width)
        val data = bitsWithCrc.copyOf(bitsWithCrc.size - width)
        return appendCrc(data, type).contentEquals(bitsWithCrc)
    }

    /**
     * Performs the TS 38.212 code-block segmentation sizing step. The actual
     * LDPC cyclic-shift matrix is intentionally supplied separately by the
     * V82/V74 table boundary.
     */
    fun segment(transportBlock: IntArray, baseGraph: NrLdpcV82.BaseGraph): Segmentation {
        val a = transportBlock.size
        val tbCrc = crcTypeForTransportBlock(a)
        val tb = appendCrc(transportBlock, tbCrc)
        val b = tb.size
        val kcb = if (baseGraph == NrLdpcV82.BaseGraph.BG1) 8448 else 3840
        val c = if (b <= kcb) 1 else kotlin.math.ceil(b.toDouble() / (kcb - 24)).toInt()
        val l = if (c == 1) 0 else 24
        val bp = b + c * l
        val kPrime = kotlin.math.ceil(bp.toDouble() / c).toInt()
        val z = NrLdpcV82.selectLiftingSize(baseGraph, kPrime)
        val kb = when {
            baseGraph == NrLdpcV82.BaseGraph.BG1 -> 22
            kPrime > 640 -> 10
            kPrime > 560 -> 9
            kPrime > 192 -> 8
            else -> 6
        }
        val k = kb * z
        require(k >= kPrime) { "NR LDPC K=$k is smaller than K'=$kPrime" }
        val f = c * k - bp

        val blocks = ArrayList<SegmentedCodeBlock>(c)
        var offset = 0
        val baseLen = b / c
        val remainder = b % c
        for (r in 0 until c) {
            val len = baseLen + if (r < remainder) 1 else 0
            val payload = tb.copyOfRange(offset, offset + len)
            offset += len
            val cbCrc = if (c > 1) appendCrc(payload, CrcType.CRC24B) else null
            val localF = k - len - (cbCrc?.size ?: 0)
            require(localF >= 0)
            blocks += SegmentedCodeBlock(r, payload, cbCrc, localF, k)
        }
        return Segmentation(baseGraph, a, tbCrc, blocks, f)
    }
}
