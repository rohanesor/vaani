package com.vokie.ui.screens.chat

import androidx.compose.animation.core.*
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
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.stt.SttLanguage
import com.vokie.stt.SttState
import com.vokie.stt.SttStatus
import com.vokie.tts.PlaybackProgress
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokieTheme

/**
 * VoiceHome: Hosts Screen 10 (Home — live), Screen 11 (Listening), and Screen 12 (Incoming transmission).
 * Matches exported iTantra.dc.html exactly.
 */
@Composable
internal fun VoiceHome(
    status: SttStatus,
    language: SttLanguage,
    microphoneGranted: Boolean,
    holdToTalk: Boolean,
    onRequestMicrophone: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onCancelVoice: () -> Unit,
    incomingText: String?,
    sourceText: String?,
    peerName: String?,
    playbackProgress: PlaybackProgress?,
    onReplay: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VokieTheme.colors

    // Screen 11: Dedicated Listening View
    if (status.state == SttState.LISTENING) {
        DesignListeningScreen(
            onCancel = onCancelVoice,
            transcript = null, // Will build live when transcription events arrive
            modifier = modifier,
        )
        return
    }

    // Screen 12: Incoming Transmission View (active voice playback)
    if (incomingText != null) {
        DesignIncomingScreen(
            peerName = peerName ?: "Nearby peer",
            sourceText = sourceText ?: incomingText,
            translatedText = if (sourceText != null && sourceText != incomingText) incomingText else null,
            playbackProgress = playbackProgress,
            onReplay = onReplay,
            onStartReply = onStartVoice,
            onStopReply = onStopVoice,
            modifier = modifier,
        )
        return
    }

    // Screen 10: Home — live
    var isPressing by remember { mutableStateOf(false) }

    // Continuous 168dp itPulse animation (3.2s ease-out infinite)
    val pulseTransition = rememberInfiniteTransition(label = "home_mic_pulse")
    val pulseProgress by pulseTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart),
        label = "mic_pulse_progress",
    )
    val ringScale = 1f + pulseProgress * 0.9f
    val ringAlpha = 0.55f * (1f - (pulseProgress / 0.7f).coerceIn(0f, 1f))

    // Press scale and halo
    val buttonScale by animateFloatAsState(
        targetValue = if (isPressing) 1.06f else 1.0f,
        animationSpec = tween(160, easing = FastOutSlowInEasing),
        label = "button_scale",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Center PTT Target Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier.size(168.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Expanding itPulse 2dp Vermilion Ring
                Box(
                    Modifier
                        .fillMaxSize()
                        .scale(ringScale)
                        .alpha(ringAlpha)
                        .background(Color.Transparent, CircleShape),
                ) {
                    Surface(
                        Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(2.dp, colors.accent),
                    ) {}
                }

                // Halo on press
                if (isPressing) {
                    Box(
                        Modifier
                            .size(204.dp)
                            .background(colors.accent.copy(alpha = 0.16f), CircleShape),
                    )
                }

                // 168dp Solid Vermilion Mic Circle
                Surface(
                    modifier = Modifier
                        .size(168.dp)
                        .scale(buttonScale)
                        .pointerInput(microphoneGranted, holdToTalk) {
                            detectTapGestures(
                                onPress = {
                                    if (!microphoneGranted) {
                                        onRequestMicrophone()
                                        return@detectTapGestures
                                    }
                                    isPressing = true
                                    if (holdToTalk) {
                                        onStartVoice()
                                        tryAwaitRelease()
                                        isPressing = false
                                        onStopVoice()
                                    } else {
                                        tryAwaitRelease()
                                        isPressing = false
                                    }
                                },
                                onTap = {
                                    if (microphoneGranted && !holdToTalk) {
                                        onStartVoice()
                                    }
                                },
                            )
                        },
                    shape = CircleShape,
                    color = if (!microphoneGranted) colors.warning.copy(alpha = 0.3f) else colors.accent,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (status.state == SttState.PROCESSING || status.state == SttState.INITIALIZING) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(52.dp))
                        } else {
                            Icon(
                                imageVector = if (!microphoneGranted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                                contentDescription = "Push to talk",
                                tint = Color.White,
                                modifier = Modifier.size(66.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(26.dp))

            // Label & Hint
            val micLabel = when {
                !microphoneGranted -> "Microphone required"
                isPressing -> "Listening…"
                status.state == SttState.PROCESSING -> "Understanding…"
                holdToTalk -> "Hold to speak"
                else -> "Tap to speak"
            }
            val micHint = when {
                !microphoneGranted -> "Tap the button to enable offline speech"
                isPressing -> "Release to send"
                status.state == SttState.PROCESSING -> "Converting your speech offline…"
                else -> "Your words are sent as text, spoken aloud there"
            }

            Text(
                text = micLabel,
                style = DesignSpec.homeTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(9.dp))

            Text(
                text = micHint,
                style = DesignSpec.homeHint,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }

        // Bottom Language Selector Pill (14dp radius, #1C1C1C, 1dp #2F343C)
        Surface(
            onClick = onOpenLanguages,
            color = colors.surface,
            shape = RoundedCornerShape(DesignSpec.panelRadius),
            border = BorderStroke(1.dp, colors.border),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 14.dp)
                .heightIn(min = 52.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Translate,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(21.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${language.displayName} · ${language.nativeName}",
                    style = DesignSpec.cardTitle,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "Change language",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
