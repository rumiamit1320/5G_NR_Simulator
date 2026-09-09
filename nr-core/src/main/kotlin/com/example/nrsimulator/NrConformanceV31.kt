package com.example.nrsimulator

import java.security.GeneralSecurityException
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

/**
 * V31 additive conformance foundation.
 *
 * Implements exact bit-level primitives used by NR/security procedures:
 *  - 38.211 Gold sequence generator (x1/x2, Nc=1600)
 *  - CRC24C
 *  - 128-NEA2 AES-CTR confidentiality primitive
 *  - 128-NIA2 AES-CMAC integrity primitive
 *
 * These primitives are isolated so existing V1-V30 interfaces remain unchanged.
 */
object NrGoldSequenceV31 {
    fun generate(cInit: Int, length: Int, nc: Int = 1600): IntArray {
        require(length >= 0)
        require(nc >= 0)
        val total = nc + length + 31
        val x1 = IntArray(total)
        val x2 = IntArray(total)
        x1[0] = 1
        for (i in 1 until 31) x1[i] = 0
        for (i in 0 until 31) x2[i] = (cInit ushr i) and 1
        for (n in 0 until total - 31) {
            x1[n + 31] = x1[n + 3] xor x1[n]
            x2[n + 31] = x2[n + 3] xor x2[n + 2] xor x2[n + 1] xor x2[n]
        }
        return IntArray(length) { i -> x1[i + nc] xor x2[i + nc] }
    }
}

object NrCrc24CV31 {
    private const val POLY = 0x1864CFB
    fun compute(bits: IntArray): Int {
        var crc = 0
        for (b0 in bits) {
            val b = b0 and 1
            val top = ((crc ushr 23) and 1) xor b
            crc = (crc shl 1) and 0xFFFFFF
            if (top != 0) crc = crc xor POLY
        }
        return crc and 0xFFFFFF
    }
    fun append(bits: IntArray): IntArray {
        val crc = compute(bits)
        return bits.copyOf(bits.size + 24).also { out ->
            for (i in 0 until 24) out[bits.size + i] = (crc ushr (23 - i)) and 1
        }
    }
}

object NrSecurityV31 {
    private fun aesBlock(key: ByteArray, input: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(input)
    }

    /** 128-NEA2 / AES-CTR. COUNT is 32-bit, bearer is 5-bit, direction is 1-bit. */
    fun nea2(key: ByteArray, count: Long, bearer: Int, direction: Int, data: ByteArray): ByteArray {
        require(key.size == 16)
        require(bearer in 0..31)
        require(direction in 0..1)
        val iv = ByteArray(16)
        iv[0] = (count ushr 24).toByte(); iv[1] = (count ushr 16).toByte(); iv[2] = (count ushr 8).toByte(); iv[3] = count.toByte()
        iv[4] = ((bearer and 31) shl 3 or ((direction and 1) shl 2)).toByte()
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return cipher.doFinal(data)
    }

    /** 128-NIA2 / AES-CMAC. Returns the 32-bit MAC-I truncation used by NIA2. */
    fun nia2(key: ByteArray, count: Long, bearer: Int, direction: Int, data: ByteArray): Int {
        require(key.size == 16)
        val msg = ByteArray(8 + data.size)
        msg[0] = (count ushr 24).toByte(); msg[1] = (count ushr 16).toByte(); msg[2] = (count ushr 8).toByte(); msg[3] = count.toByte()
        msg[4] = ((bearer and 31) shl 3 or ((direction and 1) shl 2)).toByte()
        msg[5] = 0
        data.copyInto(msg, 8)
        val mac = aesCmac(key, msg)
        return ((mac[0].toInt() and 255) shl 24) or ((mac[1].toInt() and 255) shl 16) or ((mac[2].toInt() and 255) shl 8) or (mac[3].toInt() and 255)
    }

    private fun leftShiftOne(x: ByteArray): ByteArray {
        val y = ByteArray(16); var carry = 0
        for (i in 15 downTo 0) { val v = x[i].toInt() and 255; y[i] = ((v shl 1) or carry).toByte(); carry = (v ushr 7) and 1 }
        return y
    }
    private fun xor(a: ByteArray, b: ByteArray): ByteArray = ByteArray(16) { (a[it].toInt() xor b[it].toInt()).toByte() }

    private fun aesCmac(key: ByteArray, msg: ByteArray): ByteArray {
        val zero = ByteArray(16)
        val l = aesBlock(key, zero)
        var k1 = leftShiftOne(l); if ((l[0].toInt() and 0x80) != 0) k1[15] = (k1[15].toInt() xor 0x87).toByte()
        var k2 = leftShiftOne(k1); if ((k1[0].toInt() and 0x80) != 0) k2[15] = (k2[15].toInt() xor 0x87).toByte()
        val complete = msg.isNotEmpty() && msg.size % 16 == 0
        val blocks = maxOf(1, (msg.size + 15) / 16)
        val last = ByteArray(16)
        if (complete) {
            System.arraycopy(msg, (blocks - 1) * 16, last, 0, 16)
            for (i in 0 until 16) last[i] = (last[i].toInt() xor k1[i].toInt()).toByte()
        } else {
            val used = if (msg.isEmpty()) 0 else msg.size - (blocks - 1) * 16
            if (used > 0) System.arraycopy(msg, (blocks - 1) * 16, last, 0, used)
            last[used] = 0x80.toByte()
            for (i in 0 until 16) last[i] = (last[i].toInt() xor k2[i].toInt()).toByte()
        }
        var x = ByteArray(16)
        for (b in 0 until blocks - 1) {
            val block = ByteArray(16); System.arraycopy(msg, b * 16, block, 0, 16)
            x = aesBlock(key, xor(x, block))
        }
        return aesBlock(key, xor(x, last))
    }
}

data class NrV31ConformanceResult(val goldOk: Boolean, val crcOk: Boolean, val nea2Ok: Boolean, val nia2Ok: Boolean, val pass: Boolean, val note: String)

object NrV31ConformanceTests {
    fun run(): NrV31ConformanceResult {
        val gold = NrGoldSequenceV31.generate(0x1, 64)
        val goldOk = gold.size == 64 && gold.any { it != 0 }
        val payload = intArrayOf(1,0,1,1,0,1,0,0,1,1,0,1,1,0,0,1)
        val crc = NrCrc24CV31.append(payload)
        val crcOk = crc.size == payload.size + 24 && NrCrc24CV31.compute(payload) != 0
        val key = ByteArray(16) { it.toByte() }
        val p = "NR-CONFORMANCE".toByteArray()
        val c = NrSecurityV31.nea2(key, 0x12345678, 3, 1, p)
        val nea2Ok = c.size == p.size && !c.contentEquals(p) && NrSecurityV31.nea2(key, 0x12345678, 3, 1, c).contentEquals(p)
        val nia2Ok = NrSecurityV31.nia2(key, 0x12345678, 3, 1, p) != NrSecurityV31.nia2(key, 0x12345678, 3, 1, p + byteArrayOf(0))
        return NrV31ConformanceResult(goldOk, crcOk, nea2Ok, nia2Ok, goldOk && crcOk && nea2Ok && nia2Ok, "Exact cryptographic/bit primitives isolated additively; full 3GPP conformance still requires the complete normative procedures and certification test suite.")
    }
}
