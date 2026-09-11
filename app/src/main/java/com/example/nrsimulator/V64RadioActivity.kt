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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max

class V64RadioActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { V64RadioDashboard() }
    }
}

@Composable
fun V64RadioDashboard() {
    var running by remember { mutableStateOf(true) }
    var slots by remember { mutableIntStateOf(40) }
    var ueCount by remember { mutableIntStateOf(8) }
    var cells by remember { mutableIntStateOf(2) }
    var prbs by remember { mutableIntStateOf(52) }
    var velocity by remember { mutableFloatStateOf(30f) }
    var frame by remember { mutableIntStateOf(0) }
    var result by remember { mutableStateOf(NrClosedLoopV64.run(NrClosedLoopConfigV64(slots = 1, ueCount = ueCount, cells = cells, prbs = prbs, velocityKmh = velocity.toDouble()))) }

    LaunchedEffect(running, slots, ueCount, cells, prbs, velocity) {
        while (running) {
            val r = withContext(Dispatchers.Default) {
                NrClosedLoopV64.run(NrClosedLoopConfigV64(slots = slots, ueCount = ueCount, cells = cells, prbs = prbs, velocityKmh = velocity.toDouble()))
            }
            result = r
            frame++
            delay(350)
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF1565C0))) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp)) {
            Text("V64 ANDROID 5G NR SIMULATOR", style = MaterialTheme.typography.headlineSmall)
            Text("Real-time radio environment + closed-loop PHY feedback", color = Color.Gray)
            Spacer(Modifier.height(8.dp))

            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("SIMULATION", style = MaterialTheme.typography.labelLarge)
                        Button(onClick = { running = !running }) { Text(if (running) "PAUSE" else "RUN") }
                    }
                    Text("Frame $frame  •  ${if (running) "LIVE" else "PAUSED"}")
                    Text("Slots: $slots")
                    Slider(slots.toFloat(), { slots = it.toInt().coerceIn(1, 100) }, 1f..100f)
                    Text("UEs: $ueCount")
                    Slider(ueCount.toFloat(), { ueCount = it.toInt().coerceIn(1, 16) }, 1f..16f, steps = 14)
                    Text("Cells: $cells  •  PRBs: $prbs  •  Velocity: ${velocity.toInt()} km/h")
                    Row {
                        listOf(1, 2, 3, 4, 5, 7).forEach { n ->
                            FilterChip(selected = cells == n, onClick = { cells = n }, label = { Text("$n") }, modifier = Modifier.padding(end = 3.dp))
                        }
                    }
                    Slider(prbs.toFloat(), { prbs = it.toInt().coerceIn(12, 106) }, 12f..106f)
                    Slider(velocity, { velocity = it }, 0f..120f)
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Metric("Throughput", "%.2f Mbps".format(result.totalThroughputMbps), Modifier.weight(1f))
                Metric("Fairness", "%.3f".format(result.fairness), Modifier.weight(1f))
                Metric("CRC", "%.1f%%".format(result.crcPassRate * 100.0), Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Text("BER %.3e  •  HARQ NACKs %d".format(result.ber, result.harqNacks), style = MaterialTheme.typography.bodySmall)

            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("RADIO TOPOLOGY — SLOT ${max(0, result.slotResults.lastOrNull()?.slotIndex ?: 0)}", style = MaterialTheme.typography.labelLarge)
                    RadioMap(result.ueStates, cells)
                }
            }

            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(8.dp)) {
                    Text("UE LINK STATE", style = MaterialTheme.typography.labelLarge)
                    Text("UE   SINR   CQI  MCS  Rank  PRB   Throughput   BLER   CRC   BER", style = MaterialTheme.typography.labelSmall)
                    Divider()
                    result.ueStates.forEach { u ->
                        Text(
                            "%02d  %5.1f  %3d  %3d  %4d  %3d   %8.2f   %.3f  %s  %.2e".format(
                                u.ueId, u.sinrDb, u.cqi, u.mcs, u.rank, u.allocatedPrbs, u.throughputMbps,
                                u.bler, if (u.crcPass) "PASS" else "FAIL", u.ber
                            ), style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Text("CLOSED LOOP", style = MaterialTheme.typography.labelLarge)
                    Text("UE mobility → channel/fading → SINR/interference → CQI/MCS/rank → PF scheduler → PRB grant → V61 PHY → CRC/BER/BLER → throughput/HARQ feedback → next-slot scheduler")
                    Spacer(Modifier.height(4.dp))
                    Text(result.slotResults.lastOrNull()?.schedulerFeedback ?: "Waiting for first slot", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun RadioMap(ues: List<NrClosedLoopUeV64>, cells: Int) {
    Canvas(Modifier.fillMaxWidth().height(250.dp)) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val scale = minOf(w, h) / 650f
        for (i in 0 until cells) {
            val a = (2.0 * Math.PI * i / cells).toFloat()
            val gx = cx + kotlin.math.cos(a) * w * 0.28f
            val gy = cy + kotlin.math.sin(a) * h * 0.28f
            drawCircle(Color(0xFF1565C0), 8f, Offset(gx, gy))
            drawCircle(Color(0xFF90CAF9), w * 0.22f, Offset(gx, gy), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2f))
        }
        ues.forEach { u ->
            val x = cx + u.xM.toFloat() * scale
            val y = cy + u.yM.toFloat() * scale
            drawCircle(Color(0xFFD32F2F), 6f, Offset(x, y))
            drawLine(Color.LightGray, Offset(x, y), Offset(cx, cy), strokeWidth = 1f)
        }
    }
}
