package com.example.nrsimulator

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.*

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) { super.onCreate(b); setContent { NRApp() } }
}

@Composable
fun NRApp() {
    var running by remember { mutableStateOf(true) }
    var snr by remember { mutableFloatStateOf(15f) }
    var mod by remember { mutableStateOf("64-QAM") }
    var scs by remember { mutableStateOf("30 kHz") }
    var rb by remember { mutableFloatStateOf(52f) }
    var tick by remember { mutableLongStateOf(0) }
    var advanced by remember { mutableStateOf(true) }
    var ueCount by remember { mutableFloatStateOf(4f) }
    var txAnt by remember { mutableStateOf("4") }
    var rxAnt by remember { mutableStateOf("4") }
    var channel by remember { mutableStateOf(ChannelModel.RAYLEIGH) }
    var scheduler by remember { mutableStateOf(Scheduler.PROPORTIONAL_FAIR) }
    var codingRate by remember { mutableFloatStateOf(0.75f) }
    var harq by remember { mutableStateOf(true) }
    var nrMcs by remember { mutableIntStateOf(16) }
    var nrLayers by remember { mutableIntStateOf(2) }
    var nrPhy by remember { mutableStateOf(NrPhyV3().run(NrPhyConfig(mcs = 16, layers = 2, snrDb = 15.0, prbs = 52))) }
    var nrV4 by remember { mutableStateOf(NrPhyV4().run(NrV4Config(mcs = 16, layers = 2, snrDb = 15.0, prbs = 52))) }
    var nrV5 by remember { mutableStateOf(NrLdpcV5().run(NrLdpcV5Config(payloadBits = 480, z = 48, snrDb = 15.0, iterations = 8))) }
    var nrV7 by remember { mutableStateOf(NrConformanceV7().run(NrV7Config())) }
    var nrV8 by remember { mutableStateOf(NrLdpcV8().run(NrLdpcV8Config())) }
    var nrV6 by remember { mutableStateOf(NrTransportV6().run(NrTransportV6Config(payloadBits = 4000, targetCodeRate = 0.5, rv = 0, snrDb = 15.0, zHint = 48))) }
    var nrV9 by remember { mutableStateOf(NrTransportV9().run(NrTransportV9Config())) }
    var nrV10 by remember { mutableStateOf(NrOfdmV10().run(NrOfdmV10Config())) }
    var nrV11 by remember { mutableStateOf(NrPhyV11().run(NrPhyV11Config())) }
    var nrV12 by remember { mutableStateOf(NrPhyV12().run(NrPhyV12Config())) }
    var nrV13 by remember { mutableStateOf(NrPhyV13().run(NrPhyV13Config())) }
    var nrV14 by remember { mutableStateOf(NrCsiRsV14().run(NrCsiRsV14Config())) }
    var nrV15 by remember { mutableStateOf(NrMimoV15().run(NrMimoV15Config())) }
    var nrV16 by remember { mutableStateOf(NrChannelV16(NrChannelV16Config()).summary()) }
    var nrV17 by remember { mutableStateOf(NrLinkAdaptationV17().run(NrLinkAdaptationV17Config())) }
    var nrV18 by remember { mutableStateOf(NrPdcchV18().run()) }
    var nrV31 by remember { mutableStateOf(NrV31ConformanceTests.run()) }
    var nrV32V45 by remember { mutableStateOf(NrV32V45Tests.run()) }
    var nrV46V60 by remember { mutableStateOf(NrV46V60Tests.run()) }
    var nrStack by remember { mutableStateOf(NrSystemStackV23V30().run()) }
    var nrE2E by remember { mutableStateOf(NrEndToEndV30().run(NrEndToEndConfigV30(ueCount = 2, prbs = 24, snrDb = 15.0, frames = 1, slotsPerFrame = 2, payloadBytesPerUe = 1024))) }

    val order = when (mod) { "QPSK" -> 4; "16-QAM" -> 16; "256-QAM" -> 256; else -> 64 }
    val scsK = scs.substringBefore(" ").toInt()
    var result by remember { mutableStateOf(Simulator().run(scsK, rb.toInt(), order, snr.toDouble(), 20.0, 1.0)) }
    var sys by remember { mutableStateOf(AdvancedSimulator().run(4, rb.toInt(), order, snr.toDouble(), channel, scheduler, 4, 4, codingRate.toDouble(), harq, 0)) }

    LaunchedEffect(running, snr, mod, scs, rb, advanced, ueCount, txAnt, rxAnt, channel, scheduler, codingRate, harq, nrMcs, nrLayers) {
        while (running) {
            result = Simulator().run(scsK, rb.toInt(), order, snr.toDouble(), 20.0, 1.0)
            if (advanced) sys = AdvancedSimulator().run(ueCount.toInt(), rb.toInt(), order, snr.toDouble(), channel, scheduler, txAnt.toInt(), rxAnt.toInt(), codingRate.toDouble(), harq, tick)
            tick++
            nrPhy = NrPhyV3().run(NrPhyConfig(mcs = nrMcs, layers = nrLayers, snrDb = snr.toDouble(), prbs = rb.toInt()))
            nrV4 = NrPhyV4().run(NrV4Config(mcs = nrMcs, layers = nrLayers, snrDb = snr.toDouble(), prbs = rb.toInt(), scsKHz = scsK))
            nrV5 = NrLdpcV5().run(NrLdpcV5Config(payloadBits = 480, z = 48, snrDb = snr.toDouble(), iterations = 8))
            nrV7 = NrConformanceV7().run(NrV7Config(a = 4000, targetRate = codingRate.toDouble(), rv = tick.toInt() and 3, qm = if (mod == "QPSK") 2 else if (mod == "16QAM") 4 else if (mod == "64QAM") 6 else 8, layers = nrLayers, nRe = maxOf(1, rb.toInt() * 12 * 12)) )
            nrV6 = NrTransportV6().run(NrTransportV6Config(payloadBits = 4000, targetCodeRate = 0.5, rv = tick.toInt() and 3, snrDb = snr.toDouble(), zHint = 48))
            nrV9 = NrTransportV9().run(NrTransportV9Config(payloadBits = 300, targetCodeRate = 0.5, rv = tick.toInt() and 3, qm = if (mod == "QPSK") 2 else if (mod == "16-QAM") 4 else if (mod == "64-QAM") 6 else 8, layers = nrLayers, nRe = maxOf(600, rb.toInt() * 12 * 10), snrDb = snr.toDouble()))
            nrV10 = NrOfdmV10().run(NrOfdmV10Config(scsKHz = scsK, prbs = rb.toInt(), snrDb = snr.toDouble()))
            nrV8 = NrLdpcV8().run(NrLdpcV8Config(payloadBits = 480, z = 48, snrDb = snr.toDouble(), iterations = 12, normalization = 0.8, runNoisyTest = true))
            val v11Ant = minOf(txAnt.toInt(), 4)
            val v11Rx = minOf(rxAnt.toInt(), 4)
            val v11Layers = minOf(v11Ant, v11Rx)
            nrV11 = NrPhyV11().run(NrPhyV11Config(scsKHz = scsK, prbs = rb.toInt().coerceAtMost(52), dmrsSymbol = 2, txAntennas = v11Ant, rxAntennas = v11Rx, dmrsPorts = v11Layers, snrDb = snr.toDouble(), channelModel = "FREQUENCY_SELECTIVE", equalizer = "MMSE", seed = 0x1101 + tick.toInt()))
            val v12Qm = if (mod == "QPSK") 2 else if (mod == "16-QAM") 4 else if (mod == "64-QAM") 6 else 8
            nrV12 = NrPhyV12().run(NrPhyV12Config(scsKHz = scsK, prbs = rb.toInt().coerceAtMost(52), dmrsSymbol = 2, payloadBits = 300, targetCodeRate = 0.5, rv = tick.toInt() and 3, qm = v12Qm, txAntennas = minOf(v11Ant, 2), rxAntennas = minOf(v11Rx, 2), layers = minOf(v11Layers, 2), snrDb = snr.toDouble(), channelModel = "FREQUENCY_SELECTIVE", equalizer = "MMSE", seed = 0x1201 + tick.toInt()))
            nrV13 = NrPhyV13().run(NrPhyV13Config(scsKHz = scsK, prbs = rb.toInt().coerceAtMost(52), dmrsSymbol = 2, payloadBits = 300, targetCodeRate = 0.5, rv = tick.toInt() and 3, qm = v12Qm, txAntennas = minOf(v11Ant, 2), rxAntennas = minOf(v11Rx, 2), layers = minOf(v11Layers, 2), snrDb = snr.toDouble(), channelModel = "FREQUENCY_SELECTIVE", equalizer = "MMSE", ptRsEnabled = true, cfoHz = 250.0, sfoPpm = 2.0, phaseNoiseStdRad = 0.015, seed = 0x1301 + tick.toInt()))
            nrV14 = NrCsiRsV14().run(NrCsiRsV14Config(prbs = rb.toInt().coerceAtMost(52), txAntennas = minOf(v11Ant, 4), rxAntennas = minOf(v11Rx, 4), csiPorts = minOf(v11Ant, v11Rx, 4), snrDb = snr.toDouble(), channelModel = "FREQUENCY_SELECTIVE", seed = 0x1401 + tick.toInt()))
            nrV15 = NrMimoV15().run(NrMimoV15Config(txAntennas = minOf(v11Ant, 4), rxAntennas = minOf(v11Rx, 4), layers = minOf(v11Layers, minOf(v11Ant, v11Rx)), prbs = rb.toInt().coerceAtMost(52), snrDb = snr.toDouble(), channelModel = "FREQUENCY_SELECTIVE", seed = 0x1501 + tick.toInt()))
            nrV16 = NrChannelV16(NrChannelV16Config(model = "TDL-C", txAntennas = v11Ant, rxAntennas = v11Rx, scsKHz = scsK, prbs = rb.toInt().coerceAtMost(52), carrierGHz = 3.5, velocityKmh = 30.0, rmsDelayNs = 100.0, spatialCorrelation = 0.35, losKDb = 9.0, snrDb = snr.toDouble(), timeIndex = tick.toInt(), seed = 0x1601 + tick.toInt())).summary()
            nrV17 = NrLinkAdaptationV17().run(NrLinkAdaptationV17Config(sinrDb = nrV14.sinrDb, cqi = nrV14.cqi, rank = nrV14.rank, layers = nrLayers.coerceAtMost(nrV14.rank), prbs = rb.toInt(), linkMarginDb = 1.5, maxHarqTx = if (harq) 4 else 1, seed = 0x1701 + tick.toInt()))
            nrV18 = NrPdcchV18().run(rnti = 0x1234, bwpPrbs = rb.toInt(), slot = tick.toInt(), dci = NrDciV18(frequencyDomainAssignment = (rb.toInt() / 4).coerceAtLeast(1), mcs = nrV17.selectedMcs, rv = nrV17.rvHistory.firstOrNull() ?: 0, harqProcess = tick.toInt() and 15, layers = nrV17.rank.coerceIn(1, 4)), aggregationLevel = 4, candidateIndex = 0)
            nrV31 = NrV31ConformanceTests.run()
            nrV32V45 = NrV32V45Tests.run()
            nrV46V60 = NrV46V60Tests.run()
            nrStack = NrSystemStackV23V30().run()
            nrE2E = NrEndToEndV30().run(NrEndToEndConfigV30(ueCount = ueCount.toInt().coerceIn(1, 4), prbs = rb.toInt().coerceAtMost(52), scsKHz = scsK, snrDb = snr.toDouble(), layers = nrLayers.coerceIn(1, 2), frames = 1, slotsPerFrame = 2, payloadBytesPerUe = 1024, harqEnabled = harq))
            kotlinx.coroutines.delay(250)
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1565C0))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Text("5G NR REAL-TIME SIMULATOR", style = MaterialTheme.typography.headlineSmall)
            Text("PHY + multi-UE system simulation", color = Color.Gray)
            Spacer(Modifier.height(12.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("LINK CONFIGURATION", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text("SNR: ${snr.toInt()} dB")
                    Slider(snr, { snr = it }, 0f..30f)
                    Text("PRBs: ${rb.toInt()}")
                    Slider(rb, { rb = it }, 10f..106f)
                    Row(Modifier.fillMaxWidth()) { listOf("QPSK", "16-QAM", "64-QAM", "256-QAM").forEach { FilterChip(selected = mod == it, onClick = { mod = it }, label = { Text(it) }, modifier = Modifier.padding(end = 4.dp)) } }
                    Spacer(Modifier.height(6.dp))
                    Row { listOf("15 kHz", "30 kHz", "60 kHz").forEach { FilterChip(selected = scs == it, onClick = { scs = it }, label = { Text(it) }, modifier = Modifier.padding(end = 4.dp)) } }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row { Button(onClick = { running = !running }) { Text(if (running) "PAUSE" else "RUN") }; Spacer(Modifier.width(8.dp)); Text("Frame ${tick % 10000}", modifier = Modifier.padding(12.dp)) }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Throughput", "%.2f Mbps".format(result.throughputMbps), Modifier.weight(1f))
                Metric("EVM", "%.2f %%".format(result.evm), Modifier.weight(1f))
                Metric("BER", "%.2e".format(result.ber), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Plot("TX / RX CONSTELLATION", result.constellation, result.rx)
            Spacer(Modifier.height(12.dp))
            Plot("RESOURCE GRID — 12 SC / RB", null, null, grid = true)

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("5G NR SYSTEM MODEL", style = MaterialTheme.typography.labelLarge)
                        Switch(advanced, { advanced = it })
                    }
                    if (advanced) {
                        Text("UEs: ${ueCount.toInt()}")
                        Slider(ueCount, { ueCount = it }, 1f..16f)
                        Text("Coding rate: ${"%.2f".format(codingRate)}")
                        Slider(codingRate, { codingRate = it }, 0.2f..0.93f)
                        Text("Channel", style = MaterialTheme.typography.labelMedium)
                        Row { ChannelModel.values().forEach { c -> FilterChip(selected = channel == c, onClick = { channel = c }, label = { Text(c.name) }, modifier = Modifier.padding(end = 4.dp)) } }
                        Spacer(Modifier.height(4.dp))
                        Text("Scheduler", style = MaterialTheme.typography.labelMedium)
                        Row { Scheduler.values().forEach { s -> FilterChip(selected = scheduler == s, onClick = { scheduler = s }, label = { Text(if (s == Scheduler.ROUND_ROBIN) "Round Robin" else "Proportional Fair") }, modifier = Modifier.padding(end = 4.dp)) } }
                        Row { Text("MIMO: ${txAnt}×${rxAnt}", modifier = Modifier.padding(vertical = 10.dp)); Spacer(Modifier.weight(1f)); FilterChip(selected = harq, onClick = { harq = !harq }, label = { Text("HARQ") }) }
                        Text("NR PHY v3 — LDPC/DM-RS/MIMO/MMSE", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v4 — 3GPP profile", style = MaterialTheme.typography.labelLarge)
                        Text("MCS ${nrV4.mcs} | Qm ${nrV4.qm} | R %.3f | TBS ${nrV4.tbs} b | CRC ${if (nrV4.crcOk) "PASS" else "FAIL"}".format(nrV4.targetCodeRate))
                        Text("EVM %.2f%% | BLER %.3f | %.2f Mbps | %d layers | DM-RS %d | Fs %.2f MHz".format(nrV4.evmPercent, nrV4.bler, nrV4.throughputMbps, nrV4.layers, nrV4.dmrsSymbols, nrV4.sampleRateMHz))
                        Text(nrV4.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR LDPC v5 — BG2 QC graph", style = MaterialTheme.typography.labelLarge)
                        Text("BG${nrV5.bg} | Zc ${nrV5.z} | K ${nrV5.k} | N ${nrV5.n} | payload ${nrV5.payloadBits} b | coded ${nrV5.codedBits} b")
                        Text("Decoded ${nrV5.decodedBits} b | errors ${nrV5.bitErrors} | EVM %.2f%%".format(nrV5.evmPercent))
                        Text(nrV5.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR CONFORMANCE v7 — transport/rate-matching engine", style = MaterialTheme.typography.labelLarge)
                        Text("A ${nrV7.a} b | BG${nrV7.bg} | C ${nrV7.c} | Zc ${nrV7.zc} | K ${nrV7.k} | N ${nrV7.n}")
                        Text("B' ${nrV7.bPrime} b | filler ${nrV7.filler} | Ncb ${nrV7.ncb} | G ${nrV7.g} | RV ${nrV7.rv}")
                        Text("E/CB ${nrV7.ePerCb.joinToString()} | unique RM indices ${nrV7.uniqueRateMatchIndices} | duplicates ${nrV7.duplicateRateMatchIndices}")
                        Text(nrV7.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR LDPC v8 — exact QC reference engine", style = MaterialTheme.typography.labelLarge)
                        Text("BG${nrV8.bg} | iLS ${nrV8.iLs} | Zc ${nrV8.z} | K ${nrV8.k} | N ${nrV8.n}")
                        Text("No-noise ${if (nrV8.noNoisePass) "PASS" else "FAIL"} | noisy ${if (nrV8.noisyPass) "PASS" else "FAIL"} | errors ${nrV8.bitErrors} | syndrome ${nrV8.syndromeWeight}")
                        Text("Iterations ${nrV8.iterationsUsed} | encode %.2f ms | decode %.2f ms".format(nrV8.encodeMs, nrV8.decodeMs))
                        Text(nrV8.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR TRANSPORT v6 — TB CRC / segmentation / rate matching", style = MaterialTheme.typography.labelLarge)
                        Text("A ${nrV6.payloadBits} b | TB CRC ${if (nrV6.tbCrcOk) "PASS" else "FAIL"} | BG${nrV6.bg} | C ${nrV6.codeBlocks} | Zc ${nrV6.zc}")
                        Text("K ${nrV6.k} | N ${nrV6.n} | filler ${nrV6.fillerBits} | CB CRC ${nrV6.cbCrcBits} | E ${nrV6.rateMatchedBits} | recovered ${nrV6.recoveredBits}")
                        Text("effective rate %.3f | RV %d | errors %d".format(nrV6.codeRate, tick.toInt() and 3, nrV6.bitErrors))
                        Text(nrV6.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR TRANSPORT v9 — exact BG2/iLS1 LDPC + RV rate matching", style = MaterialTheme.typography.labelLarge)
                        Text("A ${nrV9.a} b | CRC${nrV9.tbCrcBits} | BG${nrV9.bg} | C ${nrV9.c} | Zc ${nrV9.zc} | K ${nrV9.k} | N ${nrV9.n}")
                        Text("G ${nrV9.g} | E/CB ${nrV9.ePerCb.joinToString()} | RV ${nrV9.rv} | k0 ${nrV9.k0} | selected ${nrV9.selectedBits}")
                        Text("LDPC ${if (nrV9.ldpcPass) "PASS" else "FAIL"} | TB CRC ${if (nrV9.tbCrcOk) "PASS" else "FAIL"} | errors ${nrV9.decodeErrors} | syndrome ${nrV9.syndromeWeight}")
                        Text("Encode %.2f ms | decode %.2f ms".format(nrV9.encodeMs, nrV9.decodeMs))
                        Text(nrV9.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR OFDM v10 — resource grid / IFFT / CP / FFT", style = MaterialTheme.typography.labelLarge)
                        Text("SCS ${nrV10.scsKHz} kHz | PRB ${nrV10.prbs} | NFFT ${nrV10.fftSize} | CP ${nrV10.cpSamples}/${nrV10.firstCpSamples} | Fs %.2f MHz".format(nrV10.sampleRateMHz))
                        Text("${nrV10.occupiedSubcarriers} occupied SC | ${nrV10.gridSymbols} symbols | ${nrV10.mappedQamSymbols} QPSK symbols | EVM %.2f%% | power %.2f dB | ${if (nrV10.pass) "PASS" else "FAIL"}".format(nrV10.roundTripEvmPercent, nrV10.powerRatioDb))
                        Text(nrV10.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v11 — DM-RS / channel estimation / MIMO", style = MaterialTheme.typography.labelLarge)
                        Text("${nrV11.txAntennas}×${nrV11.rxAntennas} MIMO | ${nrV11.layers} layers | DM-RS l=${nrV11.dmrsSymbol} | resources ${nrV11.dmrsResources} | taps ${nrV11.channelTaps}")
                        Text("H error %.2f%% | equalized EVM %.2f%% | residual %.2f dB | rank %d | cond %.2f dB | ${if (nrV11.pass) "PASS" else "FAIL"}".format(nrV11.channelErrorPercent, nrV11.equalizedEvmPercent, nrV11.residualPowerDb, nrV11.rank, nrV11.conditionNumberDb))
                        Text(nrV11.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v12 — end-to-end PDSCH reference link", style = MaterialTheme.typography.labelLarge)
                        Text("TB ${nrV12.payloadBits} b | BG${nrV12.bg} | Zc ${nrV12.zc} | N ${nrV12.codewordBits} | G ${nrV12.rateMatchedBits} | RV ${tick.toInt() and 3}")
                        Text("${nrV12.txAntennas}×${nrV12.rxAntennas} | ${nrV12.layers} layers | data RE ${nrV12.dataRe} | QAM symbols ${nrV12.qamSymbols} | NFFT ${nrV12.fftSize}")
                        Text("H error %.2f%% | EQ EVM %.2f%% | OFDM EVM %.3g%% | errors %d | TB CRC ${if (nrV12.tbCrcOk) "PASS" else "FAIL"} | ${if (nrV12.pass) "PASS" else "FAIL"}".format(nrV12.channelErrorPercent, nrV12.equalizedEvmPercent, nrV12.ofdmEvmPercent, nrV12.bitErrors))
                        Text("Throughput %.3f Mbps | BLER %.3f".format(nrV12.throughputMbps, nrV12.bler))
                        Text(nrV12.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v13 — PT-RS / CFO / SFO / phase noise", style = MaterialTheme.typography.labelLarge)
                        Text("PT-RS ${nrV13.ptRsResources} RE | CFO 250 Hz | SFO 2 ppm | phase-noise 0.015 rad")
                        Text("PT-RS phase estimate %.2f° | diagnostic ${if (nrV13.cfoCorrectionPass) "PASS" else "CHECK"} | H error %.2f%% | EQ EVM %.2f%% | TB CRC ${if (nrV13.tbCrcOk) "PASS" else "FAIL"}".format(nrV13.residualPhaseDeg, nrV13.channelErrorPercent, nrV13.equalizedEvmPercent))
                        Text("errors ${nrV13.bitErrors} | throughput %.3f Mbps | ${if (nrV13.pass) "PASS" else "REFERENCE/IMPAIRMENT"}".format(nrV13.throughputMbps))
                        Text(nrV13.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v14 — CSI-RS / CSI measurement", style = MaterialTheme.typography.labelLarge)
                        Text("NZP CSI-RS ${nrV14.resources} RE | ${nrV14.txAntennas}×${nrV14.rxAntennas} | ${nrV14.csiPorts} ports")
                        Text("RSRP %.2f dB | SINR %.2f dB | RI %d | PMI %d | CQI %d | H error %.2f%% | ${if (nrV14.pass) "PASS" else "CHECK"}".format(nrV14.rsrpDb, nrV14.sinrDb, nrV14.rank, nrV14.pmi, nrV14.cqi, nrV14.channelErrorPercent))
                        Text("Condition %.2f dB".format(nrV14.conditionNumberDb))
                        Text(nrV14.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v15 — MIMO codebook / precoding / beam selection", style = MaterialTheme.typography.labelLarge)
                        Text("${nrV15.txAntennas}×${nrV15.rxAntennas} | ${nrV15.layers} layers | PMI ${nrV15.pmi} | ${nrV15.codebookName}")
                        Text("Beam gain %.2f dB | effective SINR %.2f dB | rank %d | condition %.2f dB | W error %.3g%% | ${if (nrV15.pass) "PASS" else "CHECK"}".format(nrV15.beamGainDb, nrV15.effectiveSinrDb, nrV15.effectiveRank, nrV15.conditionNumberDb, nrV15.precoderErrorPercent))
                        Text("Avg singular value %.3f".format(nrV15.avgSingularValue))
                        Text(nrV15.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v16 — 3GPP TR 38.901-oriented channel", style = MaterialTheme.typography.labelLarge)
                        Text("${nrV16.model} | ${nrV16.txAntennas}×${nrV16.rxAntennas} | taps ${nrV16.taps} | RMS delay %.1f ns | Doppler %.1f Hz".format(nrV16.rmsDelayNs, nrV16.dopplerHz))
                        Text("Spatial corr %.2f | ${if (nrV16.los) "LOS/Ricean" else "NLOS/Rayleigh-style"} | selectivity %.2f dB | coherence %.1f µs | ${if (nrV16.pass) "PASS" else "CHECK"}".format(nrV16.spatialCorrelation, nrV16.frequencySelectivityDb, nrV16.coherenceTimeUs))
                        Text(nrV16.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v17 — adaptive MCS / CQI / HARQ", style = MaterialTheme.typography.labelLarge)
                        Text("SINR %.2f dB | CQI %d | RI %d | MCS %d | Qm %d | R %.3f | SE %.3f".format(nrV17.measuredSinrDb, nrV17.cqi, nrV17.rank, nrV17.selectedMcs, nrV17.qm, nrV17.codeRate, nrV17.spectralEfficiency))
                        Text("TB %d b/slot | first BLER %.3f | final BLER %.3f | HARQ %d tx | RV %s | gain %.2f dB".format(nrV17.tbBitsPerSlot, nrV17.firstTransmissionBler, nrV17.finalBler, nrV17.harqTransmissions, nrV17.rvHistory.joinToString("→"), nrV17.combiningGainDb))
                        Text("Goodput %.2f Mbps | utilization %.1f%% | ${if (nrV17.ack) "ACK" else "NACK"} | ${if (nrV17.pass) "PASS" else "CHECK"}".format(nrV17.goodputMbps, nrV17.utilizationPercent))
                        Text(nrV17.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR PHY v18 — PDCCH / CORESET / SearchSpace / DCI", style = MaterialTheme.typography.labelLarge)
                        Text("RNTI 0x%04X | DCI 1_0 | CCE %d | candidates %d | AL %d | candidate %d".format(nrV18.rnti, nrV18.cces, nrV18.candidates, nrV18.selectedCandidate.aggregationLevel, nrV18.selectedCandidate.candidateIndex))
                        Text("MCS %d | RV %d | HARQ %d | layers %d | DCI bits %d | coded %d | QPSK %d".format(nrV18.dci.mcs, nrV18.dci.rv, nrV18.dci.harqProcess, nrV18.dci.layers, nrV18.dciBits, nrV18.encodedBits, nrV18.qpskSymbols))
                        Text("CRC ${if (nrV18.crcOk) "PASS" else "FAIL"} | decode ${if (nrV18.decodeOk) "PASS" else "FAIL"} | ${if (nrV18.pass) "PASS" else "CHECK"}")
                        Text(nrV18.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("NR V19–V30 — integrated protocol/system stack", style = MaterialTheme.typography.labelLarge)
                        Text("V19 UCI ${nrStack.uci.payloadBits} b | V20 UL-SCH TB ${nrStack.pusch.tbBits} b | V21 SRS SINR %.1f dB | V22 grants %d / %d PRB".format(nrStack.srs.sinrDb, nrStack.scheduler.grants.size, nrStack.scheduler.usedPrbs))
                        Text("V23 slots ${nrStack.slots.executed} | V24 RLC PDUs ${nrStack.rlc.pdus.size} | V25 PDCP PDUs ${nrStack.pdcp.pdus.size} | V26 sessions ${nrStack.core.sessions.size}")
                        Text("V27 HO ${if (nrStack.mobility.triggered) "TRIGGERED" else "STABLE"} | V28 beam ${nrStack.beam.beam} gain %.2f dB | V29 DL/UL ${nrStack.tdd.dlSymbols}/${nrStack.tdd.ulSymbols} | V30 score %.1f".format(nrStack.beam.gainDb, nrStack.analytics.score))
                        Text("Integrated stack: ${if (nrStack.analytics.pass) "PASS" else "CHECK"}", style = MaterialTheme.typography.bodySmall)
                        Text("V31 conformance primitives: ${if (nrV31.pass) "PASS" else "CHECK"} | Gold ${if (nrV31.goldOk) "OK" else "FAIL"} | CRC ${if (nrV31.crcOk) "OK" else "FAIL"} | NEA2 ${if (nrV31.nea2Ok) "OK" else "FAIL"} | NIA2 ${if (nrV31.nia2Ok) "OK" else "FAIL"}", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                        Text("V32–V45 CONFORMANCE EXPANSION", style = MaterialTheme.typography.labelLarge)
                        Text(nrV32V45.joinToString("  •  "), style = MaterialTheme.typography.bodySmall)
                        Text("Architecture preserved: V1–V31 retained; V32–V45 are additive adapters/foundations. Certification gate remains CLOSED until official 3GPP test evidence is supplied.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("V46–V60 NR CORE EXACTNESS / CONFORMANCE EXPANSION", style = MaterialTheme.typography.labelLarge)
                        Text(nrV46V60.summary, style = MaterialTheme.typography.bodySmall)
                        Text("V46–V60 regression: ${if (nrV46V60.pass) "PASS" else "CHECK"} | certification: CLOSED | Release-19 normative evidence still required", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        Spacer(Modifier.height(8.dp))
                        Text("V30 HARDENED END-TO-END DATA PATH", style = MaterialTheme.typography.labelLarge)
                        Text("RRC connected ${nrE2E.rrcConnectedUes} | slots ${nrE2E.slotsExecuted} | grants ${nrE2E.downlinkGrants} | PDCCH ${nrE2E.pdcchDecodes}")
                        Text("PDSCH ACK ${nrE2E.pdschAcks} / NACK ${nrE2E.pdschNacks} | UCI ${nrE2E.uciAcks} | delivered ${nrE2E.bytesDelivered}/${nrE2E.bytesOffered} B")
                        Text("Throughput %.3f Mbps | control ${if (nrE2E.controlPathPass) "PASS" else "FAIL"} | data ${if (nrE2E.dataPathPass) "PASS" else "FAIL"} | END-TO-END ${if (nrE2E.endToEndPass) "PASS" else "CHECK"}".format(nrE2E.throughputMbps))
                        Text(nrE2E.note, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

                        Text("MCS: $nrMcs    Layers: $nrLayers    CRC: ${if (nrPhy.crcOk) "PASS" else "FAIL"}")
                        Slider(nrMcs.toFloat(), { nrMcs = it.roundToInt() }, 0f..27f, steps = 26)
                        Row { listOf(1, 2, 4).forEach { l -> FilterChip(selected = nrLayers == l, onClick = { nrLayers = l }, label = { Text("${l}L") }, modifier = Modifier.padding(end = 4.dp)) } }
                        Text("Payload ${nrPhy.payloadBits} b | Coded ${nrPhy.codedBits} b | Errors ${nrPhy.bitErrors} | EVM %.2f%% | BLER %.3f | %.2f Mbps".format(nrPhy.evmPercent, nrPhy.bler, nrPhy.throughputMbps))
                        Spacer(Modifier.height(6.dp))
                        Text("System throughput: %.2f Mbps   |   BLER: %.3f   |   Rank: %d".format(sys.totalThroughputMbps, sys.meanBler, sys.rank))
                        Spacer(Modifier.height(8.dp))
                        sys.ues.take(8).forEach { u ->
                            Text("UE%02d  SNR %5.1f dB  CQI %2d  MCS %2d  PRB %3d  %.2f Mbps  BLER %.3f  HARQ %d".format(u.id, u.snrDb, u.cqi, u.mcs, u.scheduledPrbs, u.throughputMbps, u.bler, u.harqRetransmissions), style = MaterialTheme.typography.bodySmall)
                        }
                    } else Text("Advanced system model disabled; original PHY simulator remains active.", color = Color.Gray)
                }
            }

            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("SIGNAL CHAIN", style = MaterialTheme.typography.labelLarge)
                    Text("Bits → QAM → Resource Grid → IFFT → Channel → FFT → Equalization → Metrics")
                    Text("System: CQI → MCS → Scheduler → PRB allocation → HARQ", color = Color.Gray)
                    Text("SCS = $scs   |   PRB = ${rb.toInt()}   |   Modulation = $mod", color = Color.Gray)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Note: PHY and system calculations are simulated in software; this app does not control the phone's 5G modem.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
    }
}

@Composable fun Metric(a: String, b: String, modifier: Modifier = Modifier) { Card(modifier) { Column(Modifier.padding(10.dp)) { Text(a, style = MaterialTheme.typography.labelSmall); Text(b, style = MaterialTheme.typography.titleMedium) } } }

@Composable
fun Plot(title: String, tx: Array<Complex>?, rx: Array<Complex>?, grid: Boolean = false) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Canvas(Modifier.fillMaxWidth().height(240.dp)) {
                val w = size.width; val h = size.height
                drawLine(Color.LightGray, Offset(w / 2, 0f), Offset(w / 2, h))
                drawLine(Color.LightGray, Offset(0f, h / 2), Offset(w, h / 2))
                if (grid) {
                    for (i in 0..24) drawLine(Color(0xFFE8E8E8), Offset(i * w / 24, 0f), Offset(i * w / 24, h))
                    for (i in 0..14) drawLine(Color(0xFFE8E8E8), Offset(0f, i * h / 14), Offset(w, i * h / 14))
                } else {
                    fun p(z: Complex) = Offset(w / 2 + z.re.toFloat() * w / 4, h / 2 - z.im.toFloat() * h / 4)
                    tx?.forEach { drawCircle(Color(0xFF1565C0), 2.5f, p(it)) }
                    rx?.forEach { drawCircle(Color(0xFFD32F2F), 2.5f, p(it)) }
                }
            }
        }
    }
}
