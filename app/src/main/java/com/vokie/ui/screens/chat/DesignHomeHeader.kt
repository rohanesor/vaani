package com.vokie.ui.screens.chat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.domain.model.TransportConnectionState
import com.vokie.ui.components.ITantraWordmark
import com.vokie.ui.components.connectionUiState
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokiePalette
import com.vokie.ui.theme.VokieTheme

/**
 * Screen 10: Home Header — 19sp Tantra Wordmark, 44dp Settings button, and Connection Pill.
 */
@Composable
internal fun DesignHomeHeader(
    state: TransportConnectionState,
    peerName: String?,
    wifiConnected: Boolean,
    onSettings: () -> Unit,
    onConnect: () -> Unit,
) {
    val colors = VokieTheme.colors
    val ui = connectionUiState(state, peerName)

    Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        // Top row: Tantra wordmark + settings button
        Row(
            Modifier.fillMaxWidth().padding(top = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ITantraWordmark(size = 19)
            Surface(
                onClick = onSettings,
                shape = RoundedCornerShape(12.dp),
                color = Color.Transparent,
                modifier = Modifier.size(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(23.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(18.dp))

        // Connection Pill (14dp radius, #1C1C1C, 1dp #2F343C)
        Surface(
            onClick = onConnect,
            color = colors.surface,
            shape = RoundedCornerShape(DesignSpec.panelRadius),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dotColor = when {
                    wifiConnected -> colors.success
                    ui.isConnected -> colors.success
                    ui.isSearching -> colors.warning
                    else -> colors.textMuted
                }
                Box(Modifier.size(8.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(12.dp))
                val netLabel = when {
                    wifiConnected -> "Paired · Wi-Fi Direct"
                    ui.isConnected -> "Paired · ${peerName ?: "Nearby device"}"
                    ui.isSearching -> "Searching for nearby devices"
                    else -> "No device linked"
                }
                Text(
                    text = netLabel,
                    color = colors.textPrimary,
                    fontSize = 15.sp,
                    lineHeight = 18.sp,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val netIcon = when {
                    ui.isConnected || wifiConnected -> Icons.Default.BluetoothConnected
                    ui.isSearching -> Icons.Default.Sensors
                    else -> Icons.Default.LinkOff
                }
                Icon(
                    imageVector = netIcon,
                    contentDescription = null,
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Screen 13: Channel Header — Back, Peer Name + Bluetooth · 12 m status, and PTT / CALL Pill Toggle.
 */
@Composable
internal fun DesignChannelHeader(
    peerName: String?,
    hold: Boolean,
    onBack: () -> Unit,
    onMode: (Boolean) -> Unit,
    onEmergency: () -> Unit,
) {
    val colors = VokieTheme.colors

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back to Speak",
                tint = colors.textSecondary,
                modifier = Modifier
                    .size(24.dp)
                    .clickable(onClick = onBack),
            )

            Column(Modifier.weight(1f)) {
                Text(
                    text = peerName ?: "Nearby device",
                    style = DesignSpec.channelHeaderTitle,
                    color = colors.textPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    Box(Modifier.size(7.dp).background(colors.success, CircleShape))
                    Text(
                        text = "Bluetooth · 12 m",
                        style = DesignSpec.channelHeaderSubtitle,
                        color = colors.success,
                    )
                }
            }

            // PTT / CALL Pill Toggle (9dp radius, #1C1C1C, 1dp #2F343C)
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Row(Modifier.padding(3.dp)) {
                    // PTT
                    Surface(
                        onClick = { onMode(true) },
                        color = if (hold) colors.accent else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = "PTT",
                                style = DesignSpec.toggleLabel,
                                color = if (hold) Color.White else colors.textSecondary,
                            )
                        }
                    }

                    // CALL
                    Surface(
                        onClick = { onMode(false) },
                        color = if (!hold) colors.accent else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Box(Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
                            Text(
                                text = "CALL",
                                style = DesignSpec.toggleLabel,
                                color = if (!hold) Color.White else colors.textSecondary,
                            )
                        }
                    }
                }
            }

            // Emergency SOS
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = "Emergency SOS",
                tint = colors.alert,
                modifier = Modifier
                    .size(22.dp)
                    .clickable(onClick = onEmergency),
            )
        }

        HorizontalDivider(color = VokiePalette.Divider)
    }
}
