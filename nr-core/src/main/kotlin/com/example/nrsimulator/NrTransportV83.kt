package com.example.nrsimulator

/** Additive TS 38.212 transport-block CRC and LDPC code-block segmentation. */
object NrTransportV83 {
    enum class CrcType { CRC16, CRC24A, CRC24B }
    data class SegmentedCodeBlock(val index: Int, val payload: IntArray, val crc: IntArray?, val fillerBits: Int, val k: Int) {
        val bits: Int get() = payload.size + (crc?.size ?: 0) + fillerBits
    }
    data class Segmentation(
        val baseGraph: NrLdpcV82.BaseGraph, val transportBlockBits: Int, val transportCrcType: CrcType,
        val codeBlocks: List<SegmentedCodeBlock>, val fillerBits: Int, val liftingSize: Int, val kPrime: Int
    )

    fun crcTypeForTransportBlock(a: Int): CrcType = if (a > 3824) CrcType.CRC24A else CrcType.CRC16

    /** NR CRC attachment, MSB-first bit convention. */
    fun appendCrc(bits: IntArray, type: CrcType): IntArray {
        val width = when (type) { CrcType.CRC16 -> 16; CrcType.CRC24A, CrcType.CRC24B -> 24 }
        val poly = when (type) { CrcType.CRC16 -> 0x1021; CrcType.CRC24A -> 0x864CFB; CrcType.CRC24B -> 0x800063 }
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
        val width = if (type == CrcType.CRC16) 16 else 24
        require(bitsWithCrc.size >= width)
        return appendCrc(bitsWithCrc.copyOf(bitsWithCrc.size - width), type).contentEquals(bitsWithCrc)
    }

    /** TS 38.212 sizing and segmentation boundary; LDPC matrix lookup remains separate. */
    fun segment(transportBlock: IntArray, baseGraph: NrLdpcV82.BaseGraph): Segmentation {
        val a = transportBlock.size
        val tbCrc = crcTypeForTransportBlock(a)
        val tb = appendCrc(transportBlock, tbCrc)
        val b = tb.size
        val kcb = if (baseGraph == NrLdpcV82.BaseGraph.BG1) 8448 else 3840
        val c = if (b <= kcb) 1 else kotlin.math.ceil(b.toDouble() / (kcb - 24)).toInt()
        val l = if (c == 1) 0 else 24
        val bPrime = b + c * l
        val kPrime = kotlin.math.ceil(bPrime.toDouble() / c).toInt()
        val kb = when {
            baseGraph == NrLdpcV82.BaseGraph.BG1 -> 22
            b > 640 -> 10
            b > 560 -> 9
            b > 192 -> 8
            else -> 6
        }
        val z = NrLdpcV82.selectLiftingSize(baseGraph, kPrime, kb)
        val k = kb * z
        require(k >= kPrime) { "NR LDPC K=$k is smaller than K'=$kPrime" }
        val totalFiller = c * k - bPrime
        val blocks = ArrayList<SegmentedCodeBlock>(c)
        var sourceOffset = 0
        var fillerRemaining = totalFiller
        val cbPayloadCapacity = kPrime - l
        for (r in 0 until c) {
            val localFiller = minOf(fillerRemaining, cbPayloadCapacity)
            fillerRemaining -= localFiller
            val payloadLen = cbPayloadCapacity - localFiller
            val payload = tb.copyOfRange(sourceOffset, sourceOffset + payloadLen)
            sourceOffset += payloadLen
            val cbCrc = if (c > 1) appendCrc(payload, CrcType.CRC24B) else null
            require(payload.size + (cbCrc?.size ?: 0) + localFiller == kPrime)
            blocks += SegmentedCodeBlock(r, payload, cbCrc, localFiller, k)
        }
        require(sourceOffset == b && fillerRemaining == 0)
        return Segmentation(baseGraph, a, tbCrc, blocks, totalFiller, z, kPrime)
    }
}
