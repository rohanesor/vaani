package com.vokie.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.domain.model.DeliveryState
import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.translation.ReceiverPresentation
import com.vokie.tts.MessageTtsState
import com.vokie.tts.PlaybackProgress
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokiePalette
import com.vokie.ui.theme.VokieTheme

private val peerWaveHeights = listOf(8, 14, 20, 11, 22, 16, 9, 18, 22, 13, 7, 17, 21, 10, 15, 20, 12, 8, 16, 22, 14, 9, 19, 11, 6)
private val selfWaveHeights = listOf(6, 12, 19, 22, 14, 8, 17, 21, 12, 9, 20, 15, 7, 18, 22, 11, 16, 9, 21, 13, 8, 19, 14, 10, 6)
private val miniHeights = listOf(9, 16, 24, 13, 26, 19, 11, 22, 15, 8)
private val miniDelays = listOf(0, 90, 180, 270, 360, 450, 540, 630, 720, 810)

/**
 * Screen 13: Channel Message Card — matches iTantra.dc.html exactly.
 * 14dp radius cards with 3dp left accent border, 44dp play/pause buttons,
 * 25-bar discrete waveforms, and translated text layout.
 */
@Composable
fun MessageBubble(
    message: Message,
    incoming: Boolean,
    presentation: ReceiverPresentation?,
    ttsState: MessageTtsState?,
    isTtsInstalledForLanguage: Boolean,
    onRetry: () -> Unit,
    onPlayTts: () -> Unit,
    onStopTts: () -> Unit,
    onAcknowledgeSos: () -> Unit,
    playbackProgress: PlaybackProgress? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VokieTheme.colors
    val isPlaying = ttsState == MessageTtsState.PLAYING
    val isSos = message.messageType == MessageType.SOS
    val timeFormat = java.text.SimpleDateFormat("HH:mm", java.util.Locale.US)
    val timeStr = timeFormat.format(java.util.Date(message.timestamp))

    // Card Colors from iTantra.dc.html
    val cardBg = when {
        isSos -> colors.alert.copy(alpha = 0.12f)
        incoming -> VokiePalette.Surface
        else -> VokiePalette.OutgoingSurface
    }
    val cardBorder = when {
        isSos -> colors.alert
        incoming -> VokiePalette.Border
        else -> VokiePalette.OutgoingBorder
    }
    val leftAccent = when {
        isSos -> colors.alert
        incoming -> colors.warning
        else -> colors.accent
    }

    Surface(
        color = cardBg,
        shape = RoundedCornerShape(DesignSpec.panelRadius),
        border = BorderStroke(1.dp, cardBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
    ) {
        Row(Modifier.fillMaxWidth()) {
            // 3dp Left Accent Border
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(leftAccent, RoundedCornerShape(topStart = DesignSpec.panelRadius, bottomStart = DesignSpec.panelRadius)),
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(14.dp, 16.dp),
            ) {
                if (isPlaying) {
                    // Active Playing Layout
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // 44dp Solid Amber Pause Button
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(onClick = onStopTts),
                            shape = CircleShape,
                            color = colors.warning,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = VokiePalette.BgBase,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (incoming) "PEER · PLAYING" else "YOU · PLAYING",
                                    style = DesignSpec.messageTag,
                                    color = if (incoming) colors.warning else colors.accent,
                                )
                                val posMs = playbackProgress?.positionMs ?: 2000L
                                val durMs = (playbackProgress?.durationMs ?: 5000L).coerceAtLeast(1000L)
                                val posStr = String.format(java.util.Locale.US, "%d:%02d", (posMs / 1000) / 60, (posMs / 1000) % 60)
                                val durStr = String.format(java.util.Locale.US, "%d:%02d", (durMs / 1000) / 60, (durMs / 1000) % 60)
                                Text(
                                    text = "$posStr / $durStr",
                                    style = DesignSpec.monoSmall,
                                    color = colors.textSecondary,
                                )
                            }

                            // 10 Animated Amber Mini Voice Bars
                            Row(
                                modifier = Modifier
                                    .padding(top = 9.dp)
                                    .height(22.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                miniHeights.forEachIndexed { index, height ->
                                    val barTransition = rememberInfiniteTransition(label = "bubble_bar_$index")
                                    val scale by barTransition.animateFloat(
                                        initialValue = 0.18f,
                                        targetValue = 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(450, delayMillis = miniDelays[index], easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse,
                                        ),
                                        label = "bubble_scale_$index",
                                    )
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(height.dp)
                                            .scale(scaleX = 1f, scaleY = scale)
                                            .background(colors.warning, RoundedCornerShape(2.dp)),
                                    )
                                }
                            }
                        }
                    }

                    // Source and Translated Text
                    val sourceStr = presentation?.sourceText ?: message.text
                    // Presentation is in-memory for a live arrival; use the receiver-local
                    // Room cache after recreation so the recipient still sees the translation.
                    val transStr = presentation?.displayText ?: message.translatedText

                    // The recipient's language is the primary presentation. The source text
                    // remains authoritative in storage, but an English-only recipient must not
                    // have to interpret the Hindi transcript to understand the message.
                    Text(
                        text = if (incoming) transStr ?: sourceStr else sourceStr,
                        style = DesignSpec.sourceText,
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                } else {
                    // Idle Card: 44dp Circular Play Button + Header Tag + 25-Bar Waveform
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        // 44dp Circular Play Button
                        Surface(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable(onClick = onPlayTts),
                            shape = CircleShape,
                            color = if (incoming) VokiePalette.SelectedAmber else VokiePalette.OutgoingPlayBg,
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play voice note",
                                    tint = if (incoming) colors.warning else colors.accent,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            // Header Row: Party Tag + Timestamp + Check Icon
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = if (isSos) "EMERGENCY SOS" else if (incoming) "PEER" else "YOU",
                                    style = DesignSpec.messageTag,
                                    color = if (isSos) colors.alert else if (incoming) colors.warning else colors.accent,
                                )
                                Text(
                                    text = "$timeStr · 0:04",
                                    style = DesignSpec.monoSmall,
                                    color = colors.textSecondary,
                                )
                                message.semanticMetadata?.displayLabel()?.let { label ->
                                    Text(
                                        text = label,
                                        style = DesignSpec.monoSmall,
                                        color = if (message.semanticMetadata.urgency.name in setOf("URGENT", "CRITICAL")) colors.warning else colors.textMuted,
                                    )
                                }
                                if (!incoming) {
                                    when (message.deliveryState) {
                                        DeliveryState.QUEUED -> Text("PENDING", style = DesignSpec.monoSmall, color = colors.warning)
                                        DeliveryState.TRANSMITTING -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp, color = colors.accent)
                                        DeliveryState.RETRYING -> Text("RETRYING", style = DesignSpec.monoSmall, color = colors.warning)
                                        DeliveryState.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Refresh, "Retry", tint = colors.alert, modifier = Modifier.size(14.dp).clickable(onClick = onRetry))
                                            Text(" FAILED", style = DesignSpec.monoSmall, color = colors.alert)
                                        }
                                        else -> Icon(Icons.Default.DoneAll, "Delivered", tint = colors.success, modifier = Modifier.size(15.dp))
                                    }
                                }
                            }

                            // 25 Discrete Waveform Bars
                            val waveHeights = if (incoming) peerWaveHeights else selfWaveHeights
                            val waveColor = if (incoming) VokiePalette.IncomingWave else VokiePalette.OutgoingWave

                            Row(
                                modifier = Modifier
                                    .padding(top = 9.dp)
                                    .height(22.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                waveHeights.forEach { h ->
                                    Box(
                                        Modifier
                                            .width(3.dp)
                                            .height(h.dp)
                                            .background(waveColor, RoundedCornerShape(2.dp)),
                                    )
                                }
                            }
                        }
                    }

                    // Receiver messages lead with the translated presentation. Fall back to
                    // source only while translation is unavailable; sender messages remain source.
                    val translated = presentation?.displayText ?: message.translatedText
                    val visibleText = if (incoming) translated ?: message.text else message.text
                    if (visibleText.isNotBlank()) {
                        Text(
                            text = visibleText,
                            style = DesignSpec.translatedText,
                            color = colors.textPrimary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }
    }
}
