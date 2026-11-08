package com.vokie.ui.screens.emergency

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vokie.domain.model.MessageType
import com.vokie.communication.VokieProtocol
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.components.EmergencyActionCard
import com.vokie.ui.components.StatusBadge
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmergencySheet(
    vm: CommunicationViewModel,
    onSpeak: () -> Unit,
    onLocate: () -> Unit,
    onMessages: () -> Unit,
    onDismiss: () -> Unit,
) {
    val connectedPeerId by vm.connectedPeerId.collectAsState()
    val haptic = LocalHapticFeedback.current
    var isConfirmingSos by rememberSaveable { mutableStateOf(false) }
    var sosMessage by rememberSaveable { mutableStateOf("I need emergency assistance.") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = VokieTheme.colors.surface,
        modifier = Modifier.navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Text(
                        text = "EMERGENCY MODE",
                        style = VokieTheme.typography.labelSmall,
                        color = VokieTheme.colors.alert,
                    )
                    Text(
                        text = "Urgent Action Surface",
                        style = VokieTheme.typography.header,
                        color = VokieTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                StatusBadge(
                    label = if (connectedPeerId != null) "PEER CONNECTED" else "OFFLINE QUEUE READY",
                    color = if (connectedPeerId != null) VokieTheme.colors.success else VokieTheme.colors.warning,
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!isConfirmingSos) {
                // Three primary emergency action cards
                EmergencyActionCard(
                    title = "SPEAK",
                    subtitle = "Send an urgent voice message",
                    icon = Icons.Default.Mic,
                    onClick = {
                        onDismiss()
                        onSpeak()
                    },
                )

                Spacer(Modifier.height(10.dp))

                EmergencyActionCard(
                    title = "LOCATE",
                    subtitle = "Find a nearby person or guidance",
                    icon = Icons.Default.NearMe,
                    onClick = {
                        onDismiss()
                        onLocate()
                    },
                )

                Spacer(Modifier.height(10.dp))

                EmergencyActionCard(
                    title = "MESSAGES",
                    subtitle = "View received communication",
                    icon = Icons.AutoMirrored.Filled.Chat,
                    onClick = {
                        onDismiss()
                        onMessages()
                    },
                )

                HorizontalDivider(
                    color = VokieTheme.colors.border,
                    modifier = Modifier.padding(vertical = 18.dp),
                )

                // SOS Trigger Button
                Button(
                    onClick = {
                        isConfirmingSos = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VokieTheme.colors.alert,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(VokieDimens.buttonCorner),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 60.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "BROADCAST EMERGENCY SOS",
                        style = VokieTheme.typography.label,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                // SOS Confirmation Box
                Text(
                    text = "Confirm Emergency SOS Broadcast",
                    style = VokieTheme.typography.headerSmall,
                    color = VokieTheme.colors.alert,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                Text(
                    text = "This SOS message will be stored locally and transmitted with highest priority.",
                    style = VokieTheme.typography.body,
                    color = VokieTheme.colors.textSecondary,
                )

                OutlinedTextField(
                    value = sosMessage,
                    onValueChange = {
                        if (it.length <= VokieProtocol.MAX_TEXT_CHARS) sosMessage = it
                    },
                    label = { Text("SOS Message") },
                    supportingText = { Text("${sosMessage.length}/${VokieProtocol.MAX_TEXT_CHARS}") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VokieTheme.colors.alert,
                        focusedLabelColor = VokieTheme.colors.alert,
                    ),
                    minLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                )

                Spacer(Modifier.height(14.dp))

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        vm.send(sosMessage, type = MessageType.SOS, onQueued = onDismiss)
                    },
                    enabled = sosMessage.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VokieTheme.colors.alert,
                        contentColor = Color.White,
                    ),
                    shape = RoundedCornerShape(VokieDimens.buttonCorner),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                ) {
                    Text(
                        text = if (connectedPeerId == null) "QUEUE EMERGENCY SOS" else "SEND EMERGENCY SOS",
                        style = VokieTheme.typography.label,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Spacer(Modifier.height(8.dp))

                TextButton(
                    onClick = { isConfirmingSos = false },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Back to Emergency Actions", color = VokieTheme.colors.textSecondary)
                }
            }
        }
    }
}
