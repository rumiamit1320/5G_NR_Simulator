package com.example.nrsimulator

import kotlin.math.*
import kotlin.random.Random

/**
 * V61 additive end-to-end link laboratory.
 *
 * This class deliberately does not modify Simulator or any V1-V60 engine.
 * It composes the existing DSP primitives and the existing Polar V47 codec
 * into a deterministic, inspectable transmit/receive chain:
 *
 * bits -> CRC24C -> scrambling -> Polar -> rate matching -> QAM ->
 * layer mapping -> linear precoding -> OFDM -> channel/noise ->
 * zero-forcing/MMSE equalization -> demodulation -> rate recovery ->
 * Polar decode -> CRC -> BER/EVM/throughput.
 *
 * The LDPC V8 engine remains the existing reference/conformance component;
 * this V61 path uses Polar because V47 exposes reusable encode/decode and
 * rate-match primitives. No existing core class is replaced or altered.
 */

enum class NrLinkCodingV61 { POLAR, LDPC_REFERENCE }
en
enum class NrLinkEqualizerV61 { ZF, MMSE }

data class NrIntegratedLinkConfigV61(
    val payloadBits: Int = 256,
    val snrDb: Double = 15.0,
    val modulationOrder: Int = 16,
    val layers: Int = 2,
    val txAntennas: Int = 2,
    val rxAntennas: Int = 2,
    val prbs: Int = 24,
    val scsKHz: Int = 30,
    val codeRate: Double = 0.5,
    val rv: Int = 0,
    val coding: NrLinkCodingV61 = NrLinkCodingV61.POLAR,
    val equalizer: NrLinkEqualizerV61 = NrLinkEqualizerV61.MMSE,
    val seed: Int = 6101
)

data class NrIntegratedLinkResultV61(
    val payloadBits: Int,
    val codedBits: Int,
    val transmittedBits: Int,
    val decodedBits: Int,
    val bitErrors: Int,
    val crcPass: Boolean,
    val ber: Double,
    val evmPercent: Double,
    val throughputMbps: Double,
    val qamSymbols: Int,
    val layers: Int,
    val txAntennas: Int,
    val rxAntennas: Int,
    val ofdmSize: Int,
    val occupiedSubcarriers: Int,
    val stageSummary: List<String>,
    val txConstellation: Array<Complex>,
    val rxConstellation: Array<Complex>
)

object NrIntegratedLinkV61 {
    private fun crc24c(bits: IntArray): IntArray {
        var crc = 0
        val poly = 0x1864CFB
        for (b in bits) {
            val top = ((crc ushr 23) and 1) xor (b and 1)
            crc = (crc shl 1) and 0xFFFFFF
            if (top != 0) crc = crc xor poly
        }
        return IntArray(24) { i -> (crc ushr (23 - i)) and 1 }
    }

    private fun appendCrc(bits: IntArray): IntArray = IntArray(bits.size + 24).also {
        bits.copyInto(it)
        crc24c(bits).copyInto(it, bits.size)
    }

    private fun checkCrc(bits: IntArray): Boolean {
        if (bits.size < 24) return false
        val n = bits.size - 24
        return crc24c(bits.copyOf(n)).contentEquals(bits.copyOfRange(n, bits.size))
    }

    private fun scramble(bits: IntArray, seed: Int): IntArray {
        var x = (seed and 0x7FFFFFFF) or 1
        return IntArray(bits.size) { i ->
            x = x xor (x shl 13)
            x = x xor (x ushr 17)
            x = x xor (x shl 5)
            bits[i] xor (x and 1)
        }
    }

    private fun descramble(bits: IntArray, seed: Int): IntArray = scramble(bits, seed)

    private fun qamDemod(symbols: Array<Complex>, order: Int): IntArray {
        val bps = log2(order.toDouble()).roundToInt()
        fun levels(m: Int): DoubleArray {
            val l = sqrt(m.toDouble()).roundToInt()
            val scale = sqrt((2.0 / 3.0) * (m - 1))
            return DoubleArray(l) { i -> (2 * i - l + 1) / scale }
        }
        val lv = if (order == 4) doubleArrayOf(-1.0 / sqrt(2.0), 1.0 / sqrt(2.0)) else levels(order)
        val out = IntArray(symbols.size * bps)
        for (s in symbols.indices) {
            val z = symbols[s]
            var best = 0; var bd = Double.POSITIVE_INFINITY
            for (i in lv.indices) {
                for (q in lv.indices) {
                    val d = (z.re - lv[i]).pow(2) + (z.im - lv[q]).pow(2)
                    if (d < bd) { bd = d; best = q * lv.size + i }
                }
            }
            for (k in 0 until bps) out[s * bps + k] = (best ushr (bps - 1 - k)) and 1
        }
        return out
    }

    private fun graylessQamDemod(symbols: Array<Complex>, order: Int): IntArray {
        // Dsp.mapQam uses a natural binary index, so nearest-neighbour
        // detection above is intentionally matched to that mapping.
        return qamDemod(symbols, order)
    }

    private fun layerMap(symbols: Array<Complex>, layers: Int): Array<Array<Complex>> {
        val out = Array(layers) { Array(0) { Complex(0.0, 0.0) } }
        for (l in 0 until layers) {
            val n = (symbols.size - l + layers - 1) / layers
            out[l] = Array(n) { i -> symbols[l + i * layers] }
        }
        return out
    }

    private fun precoded(layer: Array<Complex>, layerIndex: Int, tx: Int, layers: Int): Array<Complex> {
        return Array(tx) { a ->
            val phase = 2.0 * PI * a * layerIndex / maxOf(1, tx)
            val w = Complex(cos(phase), sin(phase)) * (1.0 / sqrt(layers.toDouble()))
            Array(layer.size) { i -> layer[i] * w }
        }.let { matrix ->
            Array(layer.size) { i ->
                var z = Complex(0.0, 0.0)
                for (a in 0 until tx) z += matrix[a][i]
                z
            }
        }
    }

    private fun addChannel(x: Array<Complex>, cfg: NrIntegratedLinkConfigV61, r: Random): Array<Complex> {
        val gain = when {
            cfg.rxAntennas > cfg.txAntennas -> 1.0 + 0.08 * (cfg.rxAntennas - cfg.txAntennas)
            else -> 1.0
        }
        val y = Array(x.size) { i -> x[i] * gain }
        return Dsp.addAwgn(y, cfg.snrDb, r)
    }

    private fun ofdm(symbols: Array<Complex>, n: Int): Pair<Array<Complex>, Int> {
        val bins = minOf(symbols.size, n - 2)
        val grid = Array(n) { Complex(0.0, 0.0) }
        val half = bins / 2
        for (i in 0 until half) grid[i + 1] = symbols[i]
        for (i in half until bins) grid[n - bins + i] = symbols[i]
        return Dsp.fft(grid, true) to bins
    }

    fun run(cfg0: NrIntegratedLinkConfigV61 = NrIntegratedLinkConfigV61()): NrIntegratedLinkResultV61 {
        val cfg = cfg0.copy(
            payloadBits = cfg0.payloadBits.coerceIn(32, 4096),
            modulationOrder = listOf(4, 16, 64, 256).minByOrNull { abs(it - cfg0.modulationOrder) } ?: 16,
            layers = cfg0.layers.coerceIn(1, minOf(cfg0.txAntennas, cfg0.rxAntennas).coerceAtLeast(1)),
            txAntennas = cfg0.txAntennas.coerceIn(1, 4),
            rxAntennas = cfg0.rxAntennas.coerceIn(1, 4),
            prbs = cfg0.prbs.coerceIn(1, 106),
            scsKHz = if (cfg0.scsKHz == 15 || cfg0.scsKHz == 30 || cfg0.scsKHz == 60) cfg0.scsKHz else 30,
            codeRate = cfg0.codeRate.coerceIn(0.1, 0.95),
            rv = cfg0.rv and 3
        )
        require(cfg.coding == NrLinkCodingV61.POLAR) {
            "V61 bit-accurate chain currently uses POLAR; LDPC remains available through the existing V8 reference engine"
        }

        val rng = Random(cfg.seed)
        val stages = mutableListOf<String>()
        val payload = Dsp.bits(cfg.payloadBits, rng)
        stages += "Bits: ${payload.size}"

        val withCrc = appendCrc(payload)
        stages += "CRC24C: ${withCrc.size}"

        val scrambled = scramble(withCrc, cfg.seed xor 0x61)
        stages += "Scrambling: ${scrambled.size}"

        val n = generateSequence(32) { it * 2 }.first { it >= scrambled.size / cfg.codeRate }
            .coerceAtMost(4096)
        val polarInput = scrambled.copyOf(minOf(scrambled.size, n))
        val codeword = NrPolarV47.encode(polarInput, n)
        stages += "Polar encode: N=$n K=${polarInput.size}"

        val e = maxOf(polarInput.size, (n * cfg.codeRate).roundToInt())
        val rateMatched = NrPolarV47.rateMatch(codeword, e, cfg.rv)
        stages += "Rate matching: E=${rateMatched.size} RV=${cfg.rv}"

        val qam = Dsp.qam(rateMatched, cfg.modulationOrder)
        stages += "QAM: M=${cfg.modulationOrder} symbols=${qam.size}"

        val layers = layerMap(qam, cfg.layers)
        stages += "Layer mapping: ${cfg.layers} layers"

        val perLayer = Array(cfg.layers) { li -> precoded(layers[li], li, cfg.txAntennas, cfg.layers) }
        val txSymbols = Array(qam.size) { i ->
            var z = Complex(0.0, 0.0)
            for (li in perLayer.indices) if (i < perLayer[li].size) z += perLayer[li][i]
            z
        }
        stages += "Precoding: ${cfg.txAntennas}Tx/${cfg.layers}L"

        val (txTd, occupied) = ofdm(txSymbols, 256)
        stages += "OFDM: N=256 occupied=$occupied"

        val rxTd = addChannel(txTd, cfg, rng)
        val rxFd = Dsp.fft(rxTd)
        val recovered = Array(occupied) { i ->
            if (i < occupied / 2) rxFd[i + 1] else rxFd[256 - occupied + i]
        }
        stages += "Channel/noise: AWGN ${cfg.snrDb} dB"

        val eqGain = if (cfg.equalizer == NrLinkEqualizerV61.MMSE) {
            val snrLin = 10.0.pow(cfg.snrDb / 10.0)
            snrLin / (snrLin + 1.0)
        } else 1.0
        val equalized = Array(recovered.size) { i -> recovered[i] * (1.0 / eqGain) }
        stages += "Equalizer: ${cfg.equalizer}"

        val evm = Dsp.evm(txSymbols.take(equalized.size).toTypedArray(), equalized)
        val demod = graylessQamDemod(equalized, cfg.modulationOrder).copyOf(rateMatched.size)
        stages += "Demodulation: ${demod.size} bits"

        val recoveredCode = NrPolarV47.rateRecover(demod, n, cfg.rv)
        val decodedPolar = NrPolarV47.decode(recoveredCode, polarInput.size)
        val descrambled = descramble(decodedPolar, cfg.seed xor 0x61)
        val decoded = descrambled.copyOf(minOf(withCrc.size, descrambled.size))
        stages += "Polar decode: ${decoded.size} bits"

        val dataBits = decoded.copyOf(minOf(payload.size, decoded.size))
        val crcPass = decoded.size >= withCrc.size && checkCrc(decoded.copyOf(withCrc.size))
        var errors = 0
        for (i in 0 until minOf(payload.size, dataBits.size)) if (payload[i] != dataBits[i]) errors++
        errors += abs(payload.size - dataBits.size)
        val ber = errors.toDouble() / payload.size
        stages += "CRC: ${if (crcPass) "PASS" else "FAIL"}"

        val usefulBitsPerSecond = cfg.prbs * 12 * 14 * cfg.scsKHz * 1000.0 *
            log2(cfg.modulationOrder.toDouble()) * cfg.codeRate * cfg.layers * 0.83
        val throughput = usefulBitsPerSecond / 1e6
        stages += "Metrics: BER=${"%.6g".format(ber)} EVM=${"%.3f".format(evm)}%"

        return NrIntegratedLinkResultV61(
            payloadBits = payload.size,
            codedBits = codeword.size,
            transmittedBits = rateMatched.size,
            decodedBits = dataBits.size,
            bitErrors = errors,
            crcPass = crcPass,
            ber = ber,
            evmPercent = evm,
            throughputMbps = throughput,
            qamSymbols = qam.size,
            layers = cfg.layers,
            txAntennas = cfg.txAntennas,
            rxAntennas = cfg.rxAntennas,
            ofdmSize = 256,
            occupiedSubcarriers = occupied,
            stageSummary = stages,
            txConstellation = qam.take(96).toTypedArray(),
            rxConstellation = equalized.take(96).toTypedArray()
        )
    }
}
