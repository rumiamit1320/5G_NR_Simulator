package com.example.nrsimulator

import kotlin.math.sqrt
import kotlin.random.Random

/**
 * NR v10 additive OFDM/resource-grid reference path.
 *
 * This does not replace the existing v1-v9 PHY. It adds a physical resource
 * grid -> IFFT -> cyclic prefix -> AWGN -> CP removal -> FFT round-trip for a
 * normal-CP NR slot. Numerology, FFT size, sample rate and occupied PRBs are
 * explicit so later DM-RS/channel-estimation work can attach to this grid.
 */

data class NrOfdmV10Config(
    val scsKHz: Int = 30,
    val prbs: Int = 52,
    val symbolsPerSlot: Int = 14,
    val snrDb: Double = 15.0,
    val seed: Int = 0x1027
)

data class NrOfdmV10Result(
    val scsKHz: Int,
    val prbs: Int,
    val fftSize: Int,
    val cpSamples: Int,
    val firstCpSamples: Int,
    val sampleRateMHz: Double,
    val occupiedSubcarriers: Int,
    val gridSymbols: Int,
    val mappedQamSymbols: Int,
    val ofdmSamples: Int,
    val roundTripEvmPercent: Double,
    val powerRatioDb: Double,
    val pass: Boolean,
    val note: String
)

class NrOfdmV10 {
    fun run(cfg: NrOfdmV10Config): NrOfdmV10Result {
        require(cfg.scsKHz == 15 || cfg.scsKHz == 30 || cfg.scsKHz == 60)
        val prbs = cfg.prbs.coerceIn(1, 275)
        val nSc = prbs * 12
        val nfft = nextPow2(maxOf(128, nSc + 2))
        val cp = (nfft * 72.0 / 1024.0 * 15.0 / cfg.scsKHz).roundToIntCompat()
        val firstCp = (nfft * 80.0 / 1024.0 * 15.0 / cfg.scsKHz).roundToIntCompat()
        val sampleRateMHz = nfft * cfg.scsKHz / 1000.0
        val symbols = cfg.symbolsPerSlot.coerceIn(1, 14)
        val rng = Random(cfg.seed xor prbs xor cfg.scsKHz)
        val mapped = nSc * symbols
        val txGrid = Array(symbols) { Array(nfft) { Complex(0.0, 0.0) } }
        val txRef = ArrayList<Complex>(mapped)

        val offset = (nfft - nSc) / 2
        for (l in 0 until symbols) {
            for (k in 0 until nSc) {
                val b0 = rng.nextInt(2)
                val b1 = rng.nextInt(2)
                val re = if (b0 == 0) 1.0 else -1.0
                val im = if (b1 == 0) 1.0 else -1.0
                val q = Complex(re, im) * (1.0 / sqrt(2.0))
                txGrid[l][offset + k] = q
                txRef += q
            }
        }

        val recovered = ArrayList<Complex>(mapped)
        var totalTdSamples = 0
        for (l in 0 until symbols) {
            val td = Dsp.fft(txGrid[l], inverse = true)
            val cpLen = if (l == 0) firstCp else cp
            val tx = Array(nfft + cpLen) { i -> if (i < cpLen) td[nfft - cpLen + i] else td[i - cpLen] }
            totalTdSamples += tx.size
            val rx = Dsp.addAwgn(tx, cfg.snrDb, rng)
            val noCp = rx.copyOfRange(cpLen, cpLen + nfft)
            val fd = Dsp.fft(noCp)
            for (k in 0 until nSc) recovered += fd[offset + k]
        }

        val evm = Dsp.evm(txRef.toTypedArray(), recovered.toTypedArray())
        val txPower = txRef.sumOf { it.abs2() } / txRef.size.coerceAtLeast(1)
        val rxPower = recovered.sumOf { it.abs2() } / recovered.size.coerceAtLeast(1)
        val powerRatioDb = 10.0 * kotlin.math.log10(rxPower / txPower.coerceAtLeast(1e-12))
        val pass = evm < 100.0 && recovered.size == txRef.size

        return NrOfdmV10Result(
            cfg.scsKHz, prbs, nfft, cp, firstCp, sampleRateMHz,
            nSc, symbols, mapped, totalTdSamples, evm, powerRatioDb, pass,
            "Normal-CP NR resource-grid/OFDM reference path. PDSCH-like QPSK is mapped to all occupied subcarriers; DC/guard bins remain zero. This layer is isolated for the next DM-RS, channel, and MIMO upgrades."
        )
    }

    private fun nextPow2(x: Int): Int {
        var n = 1
        while (n < x) n = n shl 1
        return n
    }

    private fun Double.roundToIntCompat(): Int = kotlin.math.round(this).toInt()
}
