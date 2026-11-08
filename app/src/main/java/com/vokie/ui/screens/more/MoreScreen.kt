package com.vokie.ui.screens.more

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vokie.BuildConfig
import com.vokie.R
import com.vokie.map.MapRegionState
import com.vokie.tts.DEFAULT_TTS_SPEED
import com.vokie.tts.MAX_TTS_SPEED
import com.vokie.tts.MIN_TTS_SPEED
import com.vokie.tts.TtsLanguage
import com.vokie.tts.TtsState
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.components.FullLanguagePairCard
import com.vokie.ui.components.StatusBadge
import com.vokie.ui.components.VokiePanel
import com.vokie.ui.components.connectionUiState
import com.vokie.ui.map.MapViewModel
import com.vokie.ui.screens.chat.ConnectionSheet
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme
import java.util.Locale

@Composable
fun MoreScreen(
    vm: CommunicationViewModel,
    mapVm: MapViewModel = viewModel(),
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connectionState by vm.connectionState.collectAsState()
    val wifiDirectState by vm.wifiDirectState.collectAsState()
    val connectedPeerId by vm.connectedPeerId.collectAsState()
    val preferredLanguage by vm.preferredLanguage.collectAsState()
    val pushToTalk by vm.pushToTalkEnabled.collectAsState()
    val ttsSpeed by vm.ttsSpeed.collectAsState()
    val ttsStatus by vm.ttsStatus.collectAsState()
    val installedTtsLanguages by vm.installedTtsLanguages.collectAsState()
    val mapStatus by mapVm.status.collectAsState()
    val mapPoints by mapVm.points.collectAsState()

    var showConnectionSheet by remember { mutableStateOf(false) }
    val wifiPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.discoverWifiDirect() else vm.reportError("Nearby Wi-Fi devices permission is required for Wi-Fi Direct discovery.")
    }

    val bt = connectionUiState(connectionState)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(VokieTheme.colors.background),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        // SCREEN HEADER
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            ) {
                Text(
                    text = "SETTINGS & INFO",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.accent,
                    letterSpacing = 1.5.dp.value.sp,
                )
                Text(
                    text = "More",
                    style = VokieTheme.typography.header,
                    color = VokieTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = "Languages, connection options, offline storage, and telemetry.",
                    style = VokieTheme.typography.caption,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        // 1. LANGUAGE SECTION
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                Text(
                    text = "LANGUAGE CONFIGURATION",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                FullLanguagePairCard(
                    profile = preferredLanguage,
                    onClick = onOpenLanguages,
                )
            }
        }

        // 2. COMMUNICATION SECTION
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    text = "COMMUNICATION CHANNELS",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VokiePanel(modifier = Modifier.fillMaxWidth()) {
                    // Bluetooth Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(VokieTheme.colors.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                            ) {
                                Icon(Icons.Default.Bluetooth, null, tint = VokieTheme.colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Bluetooth Nearby", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
                                Text(bt.label, style = VokieTheme.typography.caption, color = bt.color)
                            }
                        }
                        OutlinedButton(
                            onClick = { showConnectionSheet = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Text("MANAGE", style = VokieTheme.typography.labelSmall)
                        }
                    }

                    HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(vertical = 12.dp))

                    // Wi-Fi Direct Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(VokieTheme.colors.surface, RoundedCornerShape(8.dp)),
                            ) {
                                Icon(Icons.Default.Wifi, null, tint = VokieTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Wi-Fi Direct", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
                                Text("Local peer transport on TCP/39721", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary)
                            }
                        }
                        StatusBadge(if (wifiDirectState == com.vokie.communication.PacketTransportState.CONNECTED) "CONNECTED" else "AVAILABLE", if (wifiDirectState == com.vokie.communication.PacketTransportState.CONNECTED) VokieTheme.colors.success else VokieTheme.colors.textSecondary)
                    }

                    HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(vertical = 12.dp))

                    // Push-To-Talk Mode Switch
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(VokieTheme.colors.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                            ) {
                                Icon(Icons.Default.RecordVoiceOver, null, tint = VokieTheme.colors.accent, modifier = Modifier.size(20.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Push-to-Talk (Hold)", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
                                Text(if (pushToTalk) "Hold button to speak" else "Tap to speak with auto silence", style = VokieTheme.typography.caption, color = VokieTheme.colors.textSecondary)
                            }
                        }
                        Switch(
                            checked = pushToTalk,
                            onCheckedChange = vm::setPushToTalk,
                        )
                    }
                }
            }
        }

        // 3. VOICE & SPEECH PLAYBACK SECTION
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    text = "SPEECH PLAYBACK",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VokiePanel(modifier = Modifier.fillMaxWidth()) {
                    var pendingSpeed by remember(ttsSpeed) { mutableFloatStateOf(ttsSpeed) }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "Speech playback speed",
                            style = VokieTheme.typography.label,
                            color = VokieTheme.colors.textPrimary,
                        )
                        Text(
                            text = String.format(Locale.US, "%.2fx", pendingSpeed),
                            style = VokieTheme.typography.labelSmall,
                            color = VokieTheme.colors.accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }

                    Slider(
                        value = pendingSpeed,
                        onValueChange = { pendingSpeed = it },
                        onValueChangeFinished = { vm.setTtsSpeed(pendingSpeed) },
                        valueRange = MIN_TTS_SPEED..MAX_TTS_SPEED,
                        steps = 2,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )

                    val activeOutput = preferredLanguage?.preferredOutputLanguage?.let {
                        TtsLanguage.fromMessageCode(it.code)
                    } ?: TtsLanguage.ENGLISH
                    // Do not report a bundled/unapproved asset as a usable voice.
                    val isVoiceReady = activeOutput in installedTtsLanguages && ttsStatus.state == TtsState.READY

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp),
                    ) {
                        StatusBadge(
                            label = if (isVoiceReady) "${activeOutput.nativeName} voice ready" else "Voice unavailable offline",
                            color = if (isVoiceReady) VokieTheme.colors.success else VokieTheme.colors.warning,
                        )
                    }
                }
            }
        }

        // 4. OFFLINE STORAGE & MAPS
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    text = "OFFLINE DATA & MAP STORAGE",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VokiePanel(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Emergency Map Baseline", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
                            Text(
                                text = if (mapStatus.state == MapRegionState.READY)
                                    "${mapPoints.size} points of interest stored offline"
                                else
                                    "Shelters, hospitals, and hazards pack",
                                style = VokieTheme.typography.caption,
                                color = VokieTheme.colors.textSecondary,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        StatusBadge(
                            label = if (mapStatus.state == MapRegionState.READY) "INSTALLED" else "NOT INSTALLED",
                            color = if (mapStatus.state == MapRegionState.READY) VokieTheme.colors.success else VokieTheme.colors.warning,
                        )
                    }

                    if (mapStatus.state != MapRegionState.READY) {
                        Button(
                            onClick = mapVm::downloadDefault,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = VokieTheme.colors.accent,
                                contentColor = Color.White,
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        ) {
                            Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("DOWNLOAD BASELINE MAP PACK", style = VokieTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }

        // 5. ABOUT SECTION
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text(
                    text = "ABOUT",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                VokiePanel(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.vokie_logo),
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = VokieTheme.typography.headerSmall,
                                color = VokieTheme.colors.textPrimary,
                            )
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME}",
                                style = VokieTheme.typography.caption,
                                color = VokieTheme.colors.textSecondary,
                            )
                        }
                    }

                    HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(vertical = 12.dp))

                    Text(
                        text = "iTantra is an offline-first emergency transceiver for low-bitrate direct peer links. Speech is transcribed and synthesized locally on-device.",
                        style = VokieTheme.typography.body,
                        color = VokieTheme.colors.textSecondary,
                    )
                }
            }
        }

        // 6. DEBUG / TELEMETRY LAB (DEBUG BUILDS ONLY)
        if (BuildConfig.DEBUG) {
            item {
                DebugTelemetrySection(vm, onDiscoverWifiDirect = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) wifiPermission.launch(Manifest.permission.NEARBY_WIFI_DEVICES)
                    else vm.discoverWifiDirect()
                })
            }
        }
    }

    if (showConnectionSheet) {
        ConnectionSheet(
            vm = vm,
            onDismiss = { showConnectionSheet = false },
        )
    }
}

@Composable
private fun DebugTelemetrySection(vm: CommunicationViewModel, onDiscoverWifiDirect: () -> Unit) {
    val location by vm.telemetryLocation.collectAsState()
    val heading by vm.telemetryHeading.collectAsState()
    val rssi by vm.telemetryRssi.collectAsState()
    val guidance by vm.telemetryGuidance.collectAsState()
    val stt by vm.sttStatus.collectAsState()
    val tts by vm.ttsStatus.collectAsState()
    val debugFastStt by vm.debugFastSttEnabled.collectAsState()
    val wifiDirectState by vm.wifiDirectState.collectAsState()
    val wifiDirectPeers by vm.wifiDirectPeers.collectAsState()

    Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp),
        ) {
            Icon(Icons.Default.BugReport, null, tint = VokieTheme.colors.warning, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                text = "DEBUG / TELEMETRY LAB (DEBUG BUILDS ONLY)",
                style = VokieTheme.typography.labelSmall,
                color = VokieTheme.colors.warning,
                fontWeight = FontWeight.Bold,
            )
        }

        VokiePanel(
            borderColor = VokieTheme.colors.warning.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // Wi-Fi Direct is intentionally Debug-only while physical transport validation is in progress.
            Text("Wi-Fi Direct Validation", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
            Text(
                text = "State: $wifiDirectState",
                style = VokieTheme.typography.caption,
                color = VokieTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDiscoverWifiDirect, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text("FIND WI-FI PEERS", style = VokieTheme.typography.caption)
                }
                OutlinedButton(onClick = vm::disconnectWifiDirect, modifier = Modifier.weight(1f).height(44.dp)) {
                    Text("DISCONNECT", style = VokieTheme.typography.caption)
                }
            }
            wifiDirectPeers.forEach { peer ->
                OutlinedButton(
                    onClick = { vm.connectWifiDirect(peer.address) },
                    enabled = peer.available,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).heightIn(min = 48.dp),
                ) { Text("CONNECT ${peer.name}", style = VokieTheme.typography.caption) }
            }

            HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(vertical = 10.dp))

            // Location Fix Telemetry
            Text("GPS Location Telemetry", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
            Text(
                text = "State: ${location.availability} • Accuracy: ${location.accuracyMeters?.let { "±${it.toInt()}m" } ?: "n/a"} • Sequence: ${location.locationSequence}",
                style = VokieTheme.typography.caption,
                color = VokieTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::startTelemetryLocation, modifier = Modifier.weight(1f).height(36.dp)) {
                    Text("START GPS", style = VokieTheme.typography.caption)
                }
                OutlinedButton(onClick = vm::stopTelemetryLocation, modifier = Modifier.weight(1f).height(36.dp)) {
                    Text("STOP GPS", style = VokieTheme.typography.caption)
                }
            }

            HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(vertical = 10.dp))

            // Heading Telemetry
            Text("Compass & Heading Sensor", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
            Text(
                text = "State: ${heading.status} • Bearing: ${heading.headingDegrees?.let { String.format(Locale.US, "%.1f°", it) } ?: "n/a"}",
                style = VokieTheme.typography.caption,
                color = VokieTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::startTelemetryHeading, modifier = Modifier.weight(1f).height(36.dp)) {
                    Text("START HEADING", style = VokieTheme.typography.caption)
                }
                OutlinedButton(onClick = vm::stopTelemetryHeading, modifier = Modifier.weight(1f).height(36.dp)) {
                    Text("STOP HEADING", style = VokieTheme.typography.caption)
                }
            }

            HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(vertical = 10.dp))

            // RSSI Telemetry
            Text("RSSI Discovery Telemetry", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
            Text(
                text = "Median: ${rssi.filtered?.rssiDbm?.let { String.format(Locale.US, "%.0f dBm", it) } ?: "n/a"} • Trend: ${rssi.filtered?.trend ?: "UNKNOWN"} • Samples: ${rssi.filtered?.sampleCount ?: 0}",
                style = VokieTheme.typography.caption,
                color = VokieTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 2.dp),
            )

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = vm::startTelemetryRssi, modifier = Modifier.weight(1f).height(36.dp)) {
                    Text("SAMPLE RSSI", style = VokieTheme.typography.caption)
                }
                OutlinedButton(onClick = vm::refreshTelemetryRssi, modifier = Modifier.weight(1f).height(36.dp)) {
                    Text("REFRESH", style = VokieTheme.typography.caption)
                }
            }

            HorizontalDivider(color = VokieTheme.colors.border, modifier = Modifier.padding(vertical = 10.dp))

            // STT / TTS JNI Diagnostics
            Text("Engine Diagnostics (JNI / ONNX)", style = VokieTheme.typography.label, color = VokieTheme.colors.textPrimary)
            stt.result?.let {
                Text(
                    text = "STT: Audio ${it.audioDurationMs}ms • Proc ${it.processingTimeMs}ms • RTF ${it.realTimeFactor?.let { f -> String.format(Locale.US, "%.2f", f) } ?: "n/a"}",
                    style = VokieTheme.typography.caption,
                    color = VokieTheme.colors.textSecondary,
                )
            }
            tts.result?.let {
                Text(
                    text = "TTS: Synth ${it.synthesisTimeMs}ms • Audio ${it.audioDurationMs}ms • RTF ${it.realTimeFactor?.let { f -> String.format(Locale.US, "%.2f", f) } ?: "n/a"}",
                    style = VokieTheme.typography.caption,
                    color = VokieTheme.colors.textSecondary,
                )
            }

            OutlinedButton(
                onClick = vm::replayLastPcmBenchmark,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp),
            ) {
                Icon(Icons.Default.Replay, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("REPLAY LAST PCM BENCHMARK", style = VokieTheme.typography.caption)
            }
            OutlinedButton(
                onClick = vm::resetOnboardingForDebug,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(36.dp),
            ) { Text("RESET ONBOARDING (DEBUG)", style = VokieTheme.typography.caption) }
        }
    }
}
