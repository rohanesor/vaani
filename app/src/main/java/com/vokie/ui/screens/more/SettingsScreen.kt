package com.vokie.ui.screens.more

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.BuildConfig
import com.vokie.tts.*
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokiePalette
import com.vokie.ui.theme.VokieTheme

/**
 * Screen 14: Settings — matches iTantra.dc.html exactly.
 * Three groups (LANGUAGE, SPEECH, DEVICE), nine rows, custom 46x26dp vermilion switches,
 * custom slider, and 14dp radius surface cards.
 */
@Composable
fun SettingsScreen(
    vm: CommunicationViewModel,
    onOpenLanguages: () -> Unit,
    onBack: () -> Unit,
    onDevices: () -> Unit,
    onLocate: () -> Unit,
) {
    var details by rememberSaveable { mutableStateOf(false) }
    var packs by rememberSaveable { mutableStateOf(false) }
    var about by rememberSaveable { mutableStateOf(false) }
    val profile by vm.preferredLanguage.collectAsState()
    val hold by vm.pushToTalkEnabled.collectAsState()
    val speed by vm.ttsSpeed.collectAsState()
    val connectionState by vm.connectionState.collectAsState()
    val alertsFullVolume by vm.alertsFullVolume.collectAsState()
    val colors = VokieTheme.colors

    BackHandler { if (details) details = false else onBack() }

    if (details) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            TextButton(onClick = { details = false }, modifier = Modifier.padding(8.dp)) {
                Text("Back to settings", color = colors.accent)
            }
            MoreScreen(vm = vm, onOpenLanguages = onOpenLanguages)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Top Header: Back Arrow + "Settings" (20sp bold)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack),
            )
            Text(
                text = "Settings",
                style = DesignSpec.settingsHeader,
                color = colors.textPrimary,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 30.dp),
        ) {
            // Group 1: LANGUAGE
            Text(
                text = "LANGUAGE",
                style = DesignSpec.settingsSection,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 10.dp, bottom = 12.dp),
            )

            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(DesignSpec.panelRadius),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // Row 1: My language
                    SettingsNavRow(
                        icon = Icons.Rounded.Translate,
                        title = "My language",
                        value = profile?.preferredInputLanguage?.displayName ?: "Telugu",
                        onClick = onOpenLanguages,
                    )

                    HorizontalDivider(color = VokiePalette.Divider)

                    // Row 2: Installed packs
                    SettingsNavRow(
                        icon = Icons.Rounded.SdStorage,
                        title = "Installed packs",
                        value = "2 · 146 MB",
                        onClick = { packs = true },
                    )
                }
            }

            // Group 2: SPEECH
            Text(
                text = "SPEECH",
                style = DesignSpec.settingsSection,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )

            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(DesignSpec.panelRadius),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // Row 3: Push to talk
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Push to talk",
                                style = DesignSpec.cardTitle,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Off: works like a call, mic stays open",
                                style = DesignSpec.cardSubtitle13,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        PillSwitch(
                            checked = hold,
                            onCheckedChange = vm::setPushToTalk,
                        )
                    }

                    HorizontalDivider(color = VokiePalette.Divider)

                    // Row 4: Playback speed
                    var pendingSpeed by remember(speed) { mutableFloatStateOf(speed) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.VolumeUp,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Playback speed",
                                style = DesignSpec.cardTitle,
                                color = colors.textPrimary,
                            )
                            // Custom slider
                            Slider(
                                value = pendingSpeed,
                                onValueChange = { pendingSpeed = it },
                                onValueChangeFinished = { vm.setTtsSpeed(pendingSpeed) },
                                valueRange = MIN_TTS_SPEED..MAX_TTS_SPEED,
                                colors = SliderDefaults.colors(
                                    thumbColor = colors.accent,
                                    activeTrackColor = colors.accent,
                                    inactiveTrackColor = VokiePalette.Divider,
                                ),
                                modifier = Modifier.fillMaxWidth().height(24.dp),
                            )
                        }
                        Text(
                            text = String.format(java.util.Locale.US, "%.1fx", pendingSpeed),
                            style = DesignSpec.monoBody,
                            color = colors.textSecondary,
                        )
                    }

                    HorizontalDivider(color = VokiePalette.Divider)

                    // Row 5: Alerts at full volume
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Alerts at full volume",
                                style = DesignSpec.cardTitle,
                                color = colors.textPrimary,
                            )
                            Text(
                                text = "Cannot be interrupted",
                                style = DesignSpec.cardSubtitle13,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        PillSwitch(
                            checked = alertsFullVolume,
                            onCheckedChange = { vm.setAlertsFullVolume(it) },
                        )
                    }
                }
            }

            // Group 3: DEVICE
            Text(
                text = "DEVICE",
                style = DesignSpec.settingsSection,
                color = colors.textMuted,
                modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
            )

            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(DesignSpec.panelRadius),
                border = BorderStroke(1.dp, colors.border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    // Row 6: Nearby devices
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onDevices)
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Hub,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                        Text(
                            text = "Nearby devices",
                            style = DesignSpec.cardTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "CONNECTED",
                            style = DesignSpec.badgeReady,
                            color = colors.success,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    HorizontalDivider(color = VokiePalette.Divider)

                    // Row 7: About iTantra
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = { about = true })
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = colors.textSecondary,
                            modifier = Modifier.size(21.dp),
                        )
                        Text(
                            text = "About iTantra",
                            style = DesignSpec.cardTitle,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = DesignSpec.monoBody,
                            color = colors.textMuted,
                        )
                        Icon(
                            imageVector = Icons.Rounded.ChevronRight,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // Developer tools & locate access
            Spacer(Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(onClick = onLocate) {
                    Text("Radar / Locate", color = colors.textSecondary)
                }
                if (BuildConfig.DEBUG) {
                    TextButton(onClick = { details = true }) {
                        Text("Diagnostics", color = colors.textSecondary)
                    }
                }
            }
        }
    }

    if (packs) {
        com.vokie.ui.screens.onboarding.InstalledPacksDialog(onDismiss = { packs = false })
    }

    if (about) {
        AlertDialog(
            onDismissRequest = { about = false },
            title = { Text("iTantra") },
            text = {
                Text("Voice notes over Bluetooth or Wi-Fi Direct, in ten Indian languages, fully offline.\n\nVersion ${BuildConfig.VERSION_NAME}\nEverything runs on your phone. No account, no tower, no data.")
            },
            confirmButton = {
                TextButton(onClick = { about = false }) { Text("Close", color = colors.accent) }
            },
            containerColor = colors.surface,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textSecondary,
        )
    }
}

/**
 * 46x26dp custom pill switch matching iTantra.dc.html exactly.
 */
@Composable
private fun PillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = VokieTheme.colors
    val offset by animateDpAsState(
        targetValue = if (checked) 20.dp else 0.dp,
        animationSpec = tween(160),
        label = "switch_offset",
    )

    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = RoundedCornerShape(13.dp),
        color = if (checked) colors.accent else Color(0xFF232830),
        modifier = Modifier.size(width = 46.dp, height = 26.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(3.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .offset(x = offset)
                    .size(20.dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun SettingsNavRow(
    icon: ImageVector,
    title: String,
    value: String,
    onClick: () -> Unit,
) {
    val colors = VokieTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier.size(21.dp),
        )
        Text(
            text = title,
            style = DesignSpec.cardTitle,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (value.isNotEmpty()) {
            Text(
                text = value,
                style = DesignSpec.setupSubtitle,
                color = colors.textSecondary,
            )
        }
        Icon(
            imageVector = Icons.Rounded.ChevronRight,
            contentDescription = null,
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}
