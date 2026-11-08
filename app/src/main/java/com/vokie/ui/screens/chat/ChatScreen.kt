package com.vokie.ui.screens.chat

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.vokie.communication.PacketTransportState
import com.vokie.domain.model.TransportConnectionState
import com.vokie.stt.SttLanguage
import com.vokie.tts.MessageTtsState
import com.vokie.tts.TtsLanguage
import com.vokie.tts.TtsState
import com.vokie.ui.communication.CommunicationViewModel
import com.vokie.ui.components.MessageBubble
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokiePalette
import com.vokie.ui.theme.VokieTheme

@Composable
fun ChatScreen(
    vm: CommunicationViewModel,
    onOpenEmergency: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier,
    speakOnly: Boolean = false,
    onOpenSettings: () -> Unit = {},
    onBackToSpeak: () -> Unit = {},
) {
    val holdToTalk by vm.pushToTalkEnabled.collectAsState()
    val context = LocalContext.current
    val connectionState by vm.connectionState.collectAsState()
    val wifiDirectState by vm.wifiDirectState.collectAsState()
    val connectedPeerId by vm.connectedPeerId.collectAsState()
    val peers by vm.peers.collectAsState()
    val messages by vm.selectedPeerMessages.collectAsState()
    val effectivePeer by vm.effectivePeerId.collectAsState()
    val sttStatus by vm.sttStatus.collectAsState()
    val preferredLanguage by vm.preferredLanguage.collectAsState()
    val ttsStates by vm.messageTtsStates.collectAsState()
    val installedTtsLanguages by vm.installedTtsLanguages.collectAsState()
    val ttsStatus by vm.ttsStatus.collectAsState()
    val presentations by vm.receiverPresentations.collectAsState()

    var showConnectionSheet by rememberSaveable { mutableStateOf(false) }
    var composerText by rememberSaveable { mutableStateOf("") }
    var showTextComposer by rememberSaveable { mutableStateOf(false) }
    var microphoneGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    val microphonePermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        microphoneGranted = granted
        if (!granted) {
            vm.reportError("Microphone permission is required for voice messaging.")
        }
    }

    LaunchedEffect(Unit) {
        vm.initializeStt()
    }

    DisposableEffect(Unit) {
        onDispose {
            vm.stopVoice()
        }
    }

    val selectedInputLang = preferredLanguage?.inputSttLanguage ?: SttLanguage.ENGLISH
    val selectedOutputTtsLang = preferredLanguage?.preferredOutputLanguage?.let {
        TtsLanguage.fromMessageCode(it.code)
    } ?: TtsLanguage.ENGLISH
    val isTtsInstalled = selectedOutputTtsLang in installedTtsLanguages && ttsStatus.state == TtsState.READY

    val peerSessions by vm.peerSessions.collectAsState()
    val selectedSession = effectivePeer?.let { peerSessions[it] }
    val connectedPeerName = selectedSession?.displayName
        ?: peers.firstOrNull { it.id == connectedPeerId }?.name
        ?: "Nearby peer"

    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Active playing message for Screen 12
    val playingMessage = messages.firstOrNull { vm.isIncoming(it) && ttsStates[it.id] == MessageTtsState.PLAYING }
    val activePresentation = presentations.values.firstOrNull { it.messageId == playingMessage?.id }
    val playbackSnapshot = vm.playbackSnapshot()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(VokieTheme.colors.background)
            .imePadding(),
    ) {
        if (speakOnly) {
            // Screen 10 Header
            DesignHomeHeader(
                state = connectionState,
                peerName = connectedPeerName,
                wifiConnected = wifiDirectState == PacketTransportState.CONNECTED,
                onSettings = onOpenSettings,
                onConnect = { showConnectionSheet = true },
            )

            // Center area: VoiceHome manages Screen 10 (Home), Screen 11 (Listening), and Screen 12 (Incoming)
            VoiceHome(
                status = sttStatus,
                language = selectedInputLang,
                microphoneGranted = microphoneGranted,
                holdToTalk = holdToTalk,
                onRequestMicrophone = { microphonePermission.launch(Manifest.permission.RECORD_AUDIO) },
                onStartVoice = vm::startVoice,
                onStopVoice = vm::stopVoice,
                onCancelVoice = vm::stopVoice,
                incomingText = playingMessage?.let { activePresentation?.displayText ?: it.translatedText ?: it.text },
                sourceText = activePresentation?.sourceText ?: playingMessage?.text,
                peerName = connectedPeerName,
                playbackProgress = playbackSnapshot,
                onReplay = { playingMessage?.let { vm.playMessage(it) } },
                onOpenLanguages = onOpenLanguages,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Screen 13: Channel Header
            DesignChannelHeader(
                peerName = connectedPeerName,
                hold = holdToTalk,
                onBack = onBackToSpeak,
                onMode = vm::setPushToTalk,
                onEmergency = onOpenEmergency,
            )

            // Message Stream Area (Screen 13)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (messages.isEmpty()) {
                    // Compute current date label for empty state
                    val emptyDateLabel = remember {
                        val now = System.currentTimeMillis()
                        val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                        "TODAY · ${dateFormat.format(java.util.Date(now))}"
                    }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp),
                    ) {
                        Text(
                            text = emptyDateLabel,
                            style = DesignSpec.channelDate,
                            color = VokieTheme.colors.textSecondary,
                            modifier = Modifier.padding(bottom = 24.dp),
                        )
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(56.dp)
                                .background(VokieTheme.colors.surface, CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Chat,
                                contentDescription = null,
                                tint = VokieTheme.colors.textSecondary,
                                modifier = Modifier.size(28.dp),
                            )
                        }
                        Text(
                            text = "No messages yet",
                            style = DesignSpec.homeTitle,
                            color = VokieTheme.colors.textPrimary,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        Text(
                            text = "Hold the button below to talk over the local link.",
                            style = DesignSpec.homeHint,
                            color = VokieTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                } else {
                    // Compute date label from first message timestamp
                    val firstMessageTime = messages.firstOrNull()?.timestamp ?: System.currentTimeMillis()
                    val messageDateLabel = remember(firstMessageTime) {
                        val dateFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
                        val dayFormat = java.text.SimpleDateFormat("EEEE", java.util.Locale.US)
                        val cal = java.util.Calendar.getInstance()
                        val today = cal.get(java.util.Calendar.DAY_OF_YEAR)
                        cal.timeInMillis = firstMessageTime
                        val msgDay = cal.get(java.util.Calendar.DAY_OF_YEAR)
                        val dayLabel = if (msgDay == today) "TODAY" else dayFormat.format(java.util.Date(firstMessageTime)).uppercase()
                        "$dayLabel · ${dateFormat.format(java.util.Date(firstMessageTime))}"
                    }
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = messageDateLabel,
                                    style = DesignSpec.channelDate,
                                    color = VokieTheme.colors.textSecondary,
                                )
                            }
                        }

                        items(messages, key = { it.id }) { message ->
                            val incoming = vm.isIncoming(message)
                            val presentation = presentations.values.firstOrNull { it.messageId == message.id }
                            MessageBubble(
                                message = message,
                                incoming = incoming,
                                presentation = presentation,
                                ttsState = ttsStates[message.id],
                                isTtsInstalledForLanguage = isTtsInstalled,
                                onRetry = { vm.retry(message.id) },
                                onPlayTts = { vm.playMessage(message) },
                                onStopTts = { vm.stopMessage(message.id) },
                                onAcknowledgeSos = { vm.acknowledgeSos(message.id) },
                                playbackProgress = if (ttsStates[message.id] == MessageTtsState.PLAYING) playbackSnapshot else null,
                            )
                        }

                        item {
                            // "Voice stays on the local link" privacy footer
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Lock,
                                    contentDescription = null,
                                    tint = VokieTheme.colors.textSecondary,
                                    modifier = Modifier.size(17.dp),
                                )
                                Text(
                                    text = "Voice stays on the local link",
                                    style = DesignSpec.cardSubtitle13,
                                    color = VokieTheme.colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }

            // Screen 13: Fixed Bottom Bar (88dp HOLD TO TALK button + Sub-row)
            HorizontalDivider(color = VokiePalette.Divider)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .padding(bottom = 12.dp),
            ) {
                var isPressingPtt by remember { mutableStateOf(false) }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .pointerInput(microphoneGranted, holdToTalk) {
                            detectTapGestures(
                                onPress = {
                                    if (!microphoneGranted) {
                                        microphonePermission.launch(Manifest.permission.RECORD_AUDIO)
                                        return@detectTapGestures
                                    }
                                    isPressingPtt = true
                                    if (holdToTalk) {
                                        vm.startVoice()
                                        tryAwaitRelease()
                                        isPressingPtt = false
                                        vm.stopVoice()
                                    } else {
                                        tryAwaitRelease()
                                        isPressingPtt = false
                                    }
                                },
                                onTap = {
                                    if (microphoneGranted && !holdToTalk) {
                                        vm.startVoice()
                                    }
                                },
                            )
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = VokieTheme.colors.accent,
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (isPressingPtt) "LISTENING…" else if (holdToTalk) "HOLD TO TALK" else "TAP TO TALK",
                            style = DesignSpec.pttAction,
                            color = Color.White,
                        )
                    }
                }

                // Sub-row: Language on left, Link state on right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${selectedInputLang.displayName} · ${selectedInputLang.nativeName}",
                        style = DesignSpec.cardSubtitle13,
                        color = VokieTheme.colors.textSecondary,
                        modifier = Modifier.clickable(onClick = onOpenLanguages),
                    )
                    val linkLabel = when {
                        wifiDirectState == PacketTransportState.CONNECTED -> "Wi-Fi Direct linked"
                        connectionState == TransportConnectionState.CONNECTED -> "Bluetooth linked"
                        else -> "Link idle"
                    }
                    Text(
                        text = linkLabel,
                        style = DesignSpec.cardSubtitle13,
                        color = VokieTheme.colors.textSecondary,
                    )
                }
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
