package com.vokie.ui.screens.chat

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vokie.communication.BluetoothPermission
import com.vokie.domain.model.Peer
import com.vokie.domain.model.TransportConnectionState
import com.vokie.ui.components.StatusBadge
import com.vokie.ui.components.VokiePanel
import com.vokie.ui.components.connectionUiState
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSheet(
    vm: CommunicationViewModel,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val connectionState by vm.connectionState.collectAsState()
    val connectedPeerId by vm.connectedPeerId.collectAsState()
    val peers by vm.peers.collectAsState()
    val error by vm.error.collectAsState()

    val ui = connectionUiState(connectionState)

    var pendingPeer by remember { mutableStateOf<String?>(null) }
    var pendingVisibility by remember { mutableStateOf(false) }

    val discoverability = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_CANCELED) {
            vm.reportError("This phone was not made visible. It can still connect to known peers.")
        }
    }

    val permissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.all { it }) {
            when {
                pendingVisibility -> {
                    pendingVisibility = false
                    vm.startListening()
                    discoverability.launch(vm.discoverabilityRequest())
                }
                pendingPeer != null -> {
                    val p = pendingPeer
                    pendingPeer = null
                    p?.let(vm::connect)
                }
                else -> vm.discover()
            }
        } else {
            vm.reportError("Nearby Devices permission is required to discover and connect.")
            pendingPeer = null
            pendingVisibility = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VokieTheme.colors.surface,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "NEARBY DEVICES",
                        style = VokieTheme.typography.labelSmall,
                        color = VokieTheme.colors.accent,
                    )
                    Text(
                        text = "Bluetooth Connection",
                        style = VokieTheme.typography.header,
                        color = VokieTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                StatusBadge(
                    label = ui.label,
                    color = ui.color,
                    loading = ui.isSearching,
                )
            }

            // Error Banner
            if (error != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(VokieTheme.colors.alert.copy(alpha = 0.12f))
                        .padding(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = VokieTheme.colors.alert,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = error.orEmpty(),
                        style = VokieTheme.typography.caption,
                        color = VokieTheme.colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = vm::clearError,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss error",
                            tint = VokieTheme.colors.textSecondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Action Buttons: Make Visible & Find Nearby
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = {
                        pendingVisibility = true
                        pendingPeer = null
                        if (BluetoothPermission.hasDiscoverability(context)) {
                            vm.startListening()
                            discoverability.launch(vm.discoverabilityRequest())
                        } else {
                            permissions.launch(BluetoothPermission.discoverabilityPermissions())
                        }
                    },
                    shape = RoundedCornerShape(VokieDimens.buttonCorner),
                    border = BorderStroke(1.dp, VokieTheme.colors.accent.copy(alpha = 0.7f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VokieTheme.colors.textPrimary),
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                ) {
                    Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("MAKE VISIBLE", style = VokieTheme.typography.labelSmall)
                }

                Button(
                    onClick = {
                        if (connectionState == TransportConnectionState.SEARCHING) {
                            vm.stopDiscovery()
                        } else {
                            pendingVisibility = false
                            pendingPeer = null
                            if (BluetoothPermission.hasDiscovery(context)) {
                                vm.discover()
                            } else {
                                permissions.launch(BluetoothPermission.discoveryPermissions())
                            }
                        }
                    },
                    shape = RoundedCornerShape(VokieDimens.buttonCorner),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VokieTheme.colors.accent,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                ) {
                    if (connectionState == TransportConnectionState.SEARCHING) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                        Text("STOP SCAN", style = VokieTheme.typography.labelSmall)
                    } else {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("FIND NEARBY", style = VokieTheme.typography.labelSmall)
                    }
                }
            }

            HorizontalDivider(
                color = VokieTheme.colors.border,
                modifier = Modifier.padding(vertical = 16.dp),
            )

            // Discovered Peers List
            if (peers.isEmpty()) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                        contentDescription = null,
                        tint = VokieTheme.colors.textSecondary,
                        modifier = Modifier.size(36.dp),
                    )
                    Text(
                        text = if (connectionState == TransportConnectionState.SEARCHING)
                            "Scanning for nearby iTantra devices…"
                        else
                            "No devices found. Tap 'Find Nearby' or 'Make Visible' on another phone.",
                        style = VokieTheme.typography.caption,
                        color = VokieTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            } else {
                Text(
                    text = "DISCOVERED DEVICES",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                ) {
                    items(peers, key = { it.id }) { peer ->
                        PeerRowItem(
                            peer = peer,
                            isConnected = connectedPeerId == peer.id,
                            onConnect = {
                                pendingPeer = peer.id
                                if (BluetoothPermission.hasConnection(context)) {
                                    vm.connect(peer.id)
                                } else {
                                    permissions.launch(BluetoothPermission.connectionPermissions())
                                }
                            },
                            onDisconnect = vm::disconnect,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerRowItem(
    peer: Peer,
    isConnected: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
) {
    Surface(
        color = VokieTheme.colors.background,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            if (isConnected) VokieTheme.colors.success else VokieTheme.colors.border,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) VokieTheme.colors.success.copy(alpha = 0.15f)
                            else VokieTheme.colors.accent.copy(alpha = 0.12f)
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        tint = if (isConnected) VokieTheme.colors.success else VokieTheme.colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = peer.name.ifBlank { "Nearby Device" },
                        style = VokieTheme.typography.label,
                        color = VokieTheme.colors.textPrimary,
                    )
                    Text(
                        text = if (isConnected) "Connected" else "Available",
                        style = VokieTheme.typography.caption,
                        color = if (isConnected) VokieTheme.colors.success else VokieTheme.colors.textSecondary,
                    )
                }
            }

            if (isConnected) {
                TextButton(onClick = onDisconnect) {
                    Text("DISCONNECT", color = VokieTheme.colors.alert, style = VokieTheme.typography.labelSmall)
                }
            } else {
                Button(
                    onClick = onConnect,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VokieTheme.colors.accent,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("CONNECT", style = VokieTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
