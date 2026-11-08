package com.vokie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vokie.domain.model.TransportConnectionState
import com.vokie.ui.theme.VokieTheme

data class ConnectionUiState(
    val label: String,
    val isConnected: Boolean,
    val isSearching: Boolean,
    val color: androidx.compose.ui.graphics.Color,
)

@Composable
fun connectionUiState(state: TransportConnectionState, connectedPeerName: String? = null, transportLabel: String? = null): ConnectionUiState {
    return when (state) {
        TransportConnectionState.CONNECTED -> ConnectionUiState(
            label = if (!transportLabel.isNullOrBlank()) "Connected directly · $transportLabel" else if (!connectedPeerName.isNullOrBlank()) "Connected to $connectedPeerName" else "Bluetooth connected",
            isConnected = true,
            isSearching = false,
            color = VokieTheme.colors.success,
        )
        TransportConnectionState.SEARCHING, TransportConnectionState.CONNECTING -> ConnectionUiState(
            label = if (state == TransportConnectionState.CONNECTING) "Connecting…" else "Searching for nearby devices…",
            isConnected = false,
            isSearching = true,
            color = VokieTheme.colors.accent,
        )
        TransportConnectionState.PERMISSION_REQUIRED -> ConnectionUiState(
            label = "Permission required",
            isConnected = false,
            isSearching = false,
            color = VokieTheme.colors.warning,
        )
        TransportConnectionState.BLUETOOTH_DISABLED -> ConnectionUiState(
            label = "Bluetooth turned off",
            isConnected = false,
            isSearching = false,
            color = VokieTheme.colors.warning,
        )
        TransportConnectionState.UNAVAILABLE -> ConnectionUiState(
            label = "Bluetooth unavailable",
            isConnected = false,
            isSearching = false,
            color = VokieTheme.colors.alert,
        )
        TransportConnectionState.FAILED -> ConnectionUiState(
            label = "Connection failed",
            isConnected = false,
            isSearching = false,
            color = VokieTheme.colors.alert,
        )
        TransportConnectionState.IDLE, TransportConnectionState.DISCONNECTED -> ConnectionUiState(
            label = "Offline / no peer",
            isConnected = false,
            isSearching = false,
            color = VokieTheme.colors.textSecondary,
        )
    }
}

@Composable
fun ConnectionStatusBar(
    connectionState: TransportConnectionState,
    connectedPeerName: String? = null,
    transportLabel: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ui = connectionUiState(connectionState, connectedPeerName, transportLabel)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(ui.color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = ui.label,
            style = VokieTheme.typography.caption,
            color = ui.color,
        )
    }
}
