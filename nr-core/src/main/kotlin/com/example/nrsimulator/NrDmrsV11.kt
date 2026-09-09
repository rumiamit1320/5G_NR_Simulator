package com.example.nrsimulator

import kotlin.math.sqrt

/**
 * NR v11 PDSCH DM-RS reference implementation.
 *
 * Scope: TS 38.211 PDSCH DM-RS configuration type 1, single-symbol DM-RS,
 * mapping type A, ports 1000..1003.  The sequence uses the NR Gold
 * sequence and the type-1 frequency-domain orthogonal cover code.
 */
data class NrDmrsV11Config(
    val nId: Int = 1,
    val slotNumber: Int = 0,
    val symbol: Int = 2,
    val prbs: Int = 52,
    val ports: Int = 2,
    val amplitude: Double = 1.0
)

data class NrDmrsResource(
    val port: Int,
    val symbol: Int,
    val subcarrier: Int,
    val value: Complex
)

data class NrDmrsV11Result(
    val nId: Int,
    val symbol: Int,
    val prbs: Int,
    val ports: Int,
    val resourceCount: Int,
    val orthogonalityError: Double,
    val pass: Boolean,
    val resources: List<NrDmrsResource>,
    val note: String
)

class NrDmrsV11 {
    fun run(cfg: NrDmrsV11Config): NrDmrsV11Result {
        val resources = generate(cfg)
        val byPort = resources.groupBy { it.port }
        var worst = 0.0
        val ports = byPort.keys.sorted()
        for (i in ports.indices) for (j in i + 1 until ports.size) {
            val a = byPort[ports[i]].orEmpty().associateBy { it.subcarrier }
            val b = byPort[ports[j]].orEmpty().associateBy { it.subcarrier }
            val common = a.keys.intersect(b.keys)
            var corr = Complex(0.0, 0.0)
            for (k in common) corr += a.getValue(k).value * b.getValue(k).value.conj()
            worst = maxOf(worst, corr.abs2())
        }
        return NrDmrsV11Result(
            cfg.nId, cfg.symbol, cfg.prbs, cfg.ports.coerceIn(1, 4), resources.size,
            sqrt(worst), sqrt(worst) < 1e-8, resources.take(32),
            "TS 38.211 PDSCH DM-RS configuration type 1, single-symbol, ports 1000..1003. Gold sequence + frequency-domain OCC are generated explicitly."
        )
    }

    fun generate(cfg: NrDmrsV11Config): List<NrDmrsResource> {
        require(cfg.nId in 0..65535)
        val prbs = cfg.prbs.coerceIn(1, 275)
        val portCount = cfg.ports.coerceIn(1, 4)
        val nSc = prbs * 12
        val amp = cfg.amplitude / sqrt(2.0)
        val out = ArrayList<NrDmrsResource>()
        for (portIndex in 0 until portCount) {
            val p = 1000 + portIndex
            val delta = if (portIndex < 2) 0 else 1
            val wf = if (portIndex % 2 == 0) intArrayOf(1, 1) else intArrayOf(1, -1)
            val seq = goldComplex(2 * (nSc / 2 + 4), cfg.nId, cfg.slotNumber, cfg.symbol)
            var m = 0
            var kPrime = 0
            while (true) {
                val k = 4 * m + 2 * kPrime + delta
                if (k >= nSc) break
                val base = seq[2 * m + kPrime]
                val value = base * (amp * wf[kPrime])
                out += NrDmrsResource(p, cfg.symbol, k, value)
                if (kPrime == 1) { kPrime = 0; m++ } else kPrime++
            }
        }
        return out
    }

    private fun goldComplex(count: Int, nId: Int, slot: Int, l: Int): Array<Complex> {
        val len = count.coerceAtLeast(2)
        val c = goldBits(2 * len + 1600, nId, slot, l)
        val out = Array(len) { i -> Complex(1.0 - 2.0 * c[2 * i], 1.0 - 2.0 * c[2 * i + 1]) }
        for (i in out.indices) out[i] = out[i] * (1.0 / sqrt(2.0))
        return out
    }

    private fun goldBits(count: Int, nId: Int, slot: Int, l: Int): DoubleArray {
        val nc = 1600
        val n = count + nc + 32
        val x1 = IntArray(n)
        val x2 = IntArray(n)
        x1[0] = 1
        val cInit = ((1L shl 17) * (14L * slot + l + 1L) * (2L * nId + 1L) + 2L * nId) and 0x7fffffffL
        for (i in 0 until 31) x2[i] = ((cInit ushr i) and 1L).toInt()
        for (i in 31 until n) {
            x1[i] = x1[i - 28] xor x1[i - 31]
            x2[i] = x2[i - 28] xor x2[i - 29] xor x2[i - 30] xor x2[i - 31]
        }
        val out = DoubleArray(count)
        for (i in 0 until count) out[i] = (x1[i + nc] xor x2[i + nc]).toDouble()
        return out
    }
}
