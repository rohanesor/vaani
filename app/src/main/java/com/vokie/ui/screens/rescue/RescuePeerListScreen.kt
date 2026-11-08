package com.vokie.ui.screens.rescue

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.vokie.BuildConfig
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.communication.PeerSessionState
import com.vokie.domain.model.TransportConnectionState
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.components.StatusBadge
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RescuePeerListScreen(
    vm: CommunicationViewModel,
    onPeerSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sessions by vm.peerSessions.collectAsState()

    val connected = sessions.values.filter {
        it.connectionState == TransportConnectionState.CONNECTED
    }.sortedByDescending { it.lastMessageTimestamp ?: it.lastSeen ?: 0L }

    val other = sessions.values.filter {
        it.connectionState != TransportConnectionState.CONNECTED
    }.sortedByDescending { it.lastSeen ?: 0L }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VokieTheme.colors.background),
    ) {
        // Header
        Surface(
            color = VokieTheme.colors.surface,
            border = BorderStroke(1.dp, VokieTheme.colors.border),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    text = "Peers",
                    style = VokieTheme.typography.header,
                    color = VokieTheme.colors.textPrimary,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                ) {
                    Text(
                        text = "${sessions.size} peer${if (sessions.size != 1) "s" else ""} · ${connected.size} connected",
                        style = VokieTheme.typography.caption,
                        color = VokieTheme.colors.textSecondary,
                    )
                    if (BuildConfig.DEBUG) {
                        val hasSimulated = sessions.keys.any { it.startsWith("SIM-") }
                        OutlinedButton(
                            onClick = { if (hasSimulated) vm.removeSimulatedPeers() else vm.addSimulatedPeers() },
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        ) {
                            Text(
                                text = if (hasSimulated) "REMOVE" else "ADD SIM",
                                style = VokieTheme.typography.labelSmall,
                                color = VokieTheme.colors.accent,
                            )
                        }
                    }
                }
            }
        }

        if (sessions.isEmpty()) {
            // Empty state
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .background(VokieTheme.colors.surface, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = VokieTheme.colors.textSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = "No peer sessions",
                    style = VokieTheme.typography.label,
                    color = VokieTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    text = "Connect to nearby devices using the Chat or More screen. Peers will appear here automatically.",
                    style = VokieTheme.typography.body,
                    color = VokieTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (connected.isNotEmpty()) {
                    item {
                        Text(
                            text = "CONNECTED",
                            style = VokieTheme.typography.labelSmall,
                            color = VokieTheme.colors.success,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    items(connected, key = { it.peerId }) { session ->
                        PeerSessionCard(session = session, onClick = { onPeerSelected(session.peerId) })
                    }
                }
                if (other.isNotEmpty()) {
                    item {
                        Text(
                            text = if (connected.isNotEmpty()) "OTHER" else "PEERS",
                            style = VokieTheme.typography.labelSmall,
                            color = VokieTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = if (connected.isNotEmpty()) 12.dp else 0.dp, bottom = 4.dp),
                        )
                    }
                    items(other, key = { it.peerId }) { session ->
                        PeerSessionCard(session = session, onClick = { onPeerSelected(session.peerId) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PeerSessionCard(
    session: PeerSessionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isConnected = session.connectionState == TransportConnectionState.CONNECTED
    val borderColor = if (isConnected) VokieTheme.colors.success else VokieTheme.colors.border
    val priorityInfo = priorityUi(session.priority)

    Surface(
        color = VokieTheme.colors.surface,
        shape = RoundedCornerShape(VokieDimens.cardCorner),
        border = BorderStroke(VokieDimens.borderWidth, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(14.dp),
        ) {
            // Avatar with unread badge
            Box {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (isConnected) VokieTheme.colors.success.copy(alpha = 0.15f)
                            else VokieTheme.colors.accent.copy(alpha = 0.12f)
                        ),
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = if (isConnected) VokieTheme.colors.success else VokieTheme.colors.accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                // Unread badge
                if (session.unreadCount > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-2).dp)
                            .size(18.dp)
                            .background(VokieTheme.colors.accent, CircleShape),
                    ) {
                        Text(
                            text = if (session.unreadCount > 9) "9+" else session.unreadCount.toString(),
                            style = VokieTheme.typography.caption,
                            color = Color.White,
                            fontSize = 10.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.width(12.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = session.displayName ?: shortId(session.peerId),
                        style = VokieTheme.typography.label,
                        color = VokieTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (priorityInfo != null) {
                        Spacer(Modifier.width(8.dp))
                        StatusBadge(label = priorityInfo.first, color = priorityInfo.second)
                    }
                }

                Spacer(Modifier.height(2.dp))

                Text(
                    text = connectionLabel(session),
                    style = VokieTheme.typography.caption,
                    color = if (isConnected) VokieTheme.colors.success else VokieTheme.colors.textSecondary,
                )

                // Language & transport info
                val details = buildList {
                    session.sourceLanguage?.let { add("Source: $it") }
                    session.transport?.name?.let { add(it.replace("_", " ")) }
                }
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = VokieTheme.typography.caption,
                        color = VokieTheme.colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // Last message preview
                session.lastMessagePreview?.let { preview ->
                    Text(
                        text = preview,
                        style = VokieTheme.typography.caption,
                        color = VokieTheme.colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }

                // Unread / pending / unacked counts
                val meta = buildList {
                    if (session.unreadCount > 0) add("${session.unreadCount} unread")
                    if (session.pendingMessageCount > 0) add("${session.pendingMessageCount} pending")
                    if (session.unacknowledgedMessageCount > 0) add("${session.unacknowledgedMessageCount} unacked")
                }
                if (meta.isNotEmpty()) {
                    Text(
                        text = meta.joinToString(" · "),
                        style = VokieTheme.typography.caption,
                        color = if (session.unreadCount > 0) VokieTheme.colors.accent else VokieTheme.colors.textMuted,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

private fun connectionLabel(session: PeerSessionState): String = when (session.connectionState) {
    TransportConnectionState.CONNECTED -> "Connected"
    TransportConnectionState.CONNECTING -> "Connecting…"
    TransportConnectionState.SEARCHING -> "Searching…"
    TransportConnectionState.DISCONNECTED -> session.lastSeen?.let { "Disconnected · Last seen ${formatTime(it)}" } ?: "Disconnected"
    TransportConnectionState.FAILED -> "Connection failed"
    else -> session.lastSeen?.let { "Last seen ${formatTime(it)}" } ?: "Idle"
}

private fun priorityUi(priority: Int): Pair<String, Color>? = when {
    priority >= 200 -> "HIGH" to Color(0xFFEF4444)
    priority >= 100 -> "MED" to Color(0xFFF97316)
    priority >= 50 -> "LOW" to Color(0xFFF59E0B)
    else -> null
}

private fun shortId(id: String): String =
    if (id.length > 12) "${id.take(4)}…${id.takeLast(4)}" else id

private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
private fun formatTime(timestamp: Long): String = timeFormat.format(Date(timestamp))
