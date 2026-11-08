package com.vokie.ui.screens.locate

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vokie.communication.PacketTransportState
import com.vokie.location.MeasurementConfidence
import com.vokie.location.ProximityZone
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.components.ConnectionStatusBar
import com.vokie.ui.components.VokiePanel
import com.vokie.ui.screens.chat.ConnectionSheet
import com.vokie.ui.theme.VokieTheme

@Composable
fun LocateScreen(
    vm: CommunicationViewModel,
    onOpenEmergency: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionState by vm.connectionState.collectAsState()
    val wifiState by vm.wifiDirectState.collectAsState()
    val peers by vm.peers.collectAsState()
    val measurements by vm.proximityMeasurements.collectAsState()
    val localization by vm.localizationStates.collectAsState()
    val now = System.currentTimeMillis()
    val latest = remember(measurements, now / 5_000L) { measurements.values.maxByOrNull { it.timestamp } }
    val peerName = peers.firstOrNull { it.id == latest?.peerId }?.name
    var showConnectionSheet = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    Column(modifier.fillMaxSize().background(VokieTheme.colors.background)) {
        Surface(color = VokieTheme.colors.surface, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Column {
                        Text("Locate", style = VokieTheme.typography.header, color = VokieTheme.colors.textPrimary)
                        Text("Message-triggered nearby proximity", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary)
                    }
                    TextButton(onClick = onOpenEmergency) {
                        Icon(Icons.Default.Warning, "Emergency", tint = VokieTheme.colors.alert, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp)); Text("SOS", color = VokieTheme.colors.alert)
                    }
                }
                ConnectionStatusBar(
                    connectionState = connectionState,
                    connectedPeerName = peerName,
                    transportLabel = if (wifiState == PacketTransportState.CONNECTED) "Wi-Fi Direct" else null,
                    onClick = { showConnectionSheet.value = true },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            if (latest == null || latest.isStale(now)) {
                VokiePanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.NearMe, null, tint = VokieTheme.colors.textSecondary, modifier = Modifier.size(52.dp))
                        Text(if (localization.isEmpty()) "Proximity estimate unavailable" else "iTANTRA PEER AVAILABLE", style = VokieTheme.typography.headerSmall, color = VokieTheme.colors.textPrimary, modifier = Modifier.padding(top = 14.dp))
                        if (localization.isEmpty()) {
                            Text("Send or receive an iTantra message to create a local measurement.", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        } else {
                            val evidence = localization.values.maxBy { it.lastSeen }
                            Text("Transport: ${evidence.transport} · RSSI: ${evidence.rssi?.latest ?: "unavailable"}", style = VokieTheme.typography.body, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                            Text("Measurements: ${evidence.measurementCount} · Direction: Not available yet · Distance: Not available yet", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            } else {
                VokiePanel(Modifier.fillMaxWidth()) {
                    Column(Modifier.fillMaxWidth().padding(vertical = 22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(peerName ?: "Nearby iTantra peer", style = VokieTheme.typography.label, color = VokieTheme.colors.textSecondary)
                        Text(latest.zone.label(), style = VokieTheme.typography.header, color = VokieTheme.colors.accent, modifier = Modifier.padding(top = 10.dp))
                        Text("● Direct link", style = VokieTheme.typography.body, color = VokieTheme.colors.success, modifier = Modifier.padding(top = 8.dp))
                        Text("Confidence: ${latest.confidence.name.lowercase().replaceFirstChar { it.uppercase() }}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                        localization[latest.peerId]?.let { evidence ->
                            evidence.rssi?.latest?.let { Text("RSSI: $it dBm", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 4.dp)) }
                            Text("Measurements: ${evidence.measurementCount} · qualitative only", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(top = 4.dp))
                            Text("Direction: Not available yet", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(top = 4.dp))
                            Text("Distance: Not available yet", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(top = 4.dp))
                        }
                        Text("Last updated after ${latest.trigger.name.lowercase().replace('_', ' ')}", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(top = 16.dp))
                        latest.rttMs?.let { Text("ACK RTT: ${it} ms · qualitative only", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(top = 4.dp)) }
                    }
                }
            }
            Text("GPS-free • no coordinates • no distance estimate", style = VokieTheme.typography.caption, color = VokieTheme.colors.textMuted, modifier = Modifier.padding(top = 24.dp))
        }
    }
    if (showConnectionSheet.value) ConnectionSheet(vm = vm, onDismiss = { showConnectionSheet.value = false })
}

private fun ProximityZone.label() = when (this) { ProximityZone.VERY_NEAR -> "VERY NEAR"; ProximityZone.NEAR -> "NEARBY"; ProximityZone.UNKNOWN -> "UNKNOWN" }
