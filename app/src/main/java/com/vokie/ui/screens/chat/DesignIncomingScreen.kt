package com.vokie.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.vokie.tts.PlaybackProgress
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokiePalette
import com.vokie.ui.theme.VokieTheme

/**
 * Screen 12: Incoming transmission — matches iTantra.dc.html exactly.
 * Received voice note plays on arrival with full amber visualizer,
 * elapsed/total timer, source + translated text, Replay, and Hold to reply.
 */
@Composable
fun DesignIncomingScreen(
    peerName: String,
    sourceText: String,
    translatedText: String?,
    playbackProgress: PlaybackProgress?,
    onReplay: () -> Unit,
    onStartReply: () -> Unit,
    onStopReply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = VokieTheme.colors

    // Pulse animations for the 150dp amber disc
    val transition = rememberInfiniteTransition(label = "incoming_pulse")
    val pulse1Progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "ring1_progress",
    )
    val ring1Scale = 1f + pulse1Progress * 0.9f
    val ring1Alpha = (0.55f * (1f - (pulse1Progress / 0.7f).coerceIn(0f, 1f)))

    val pulse2Progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, delayMillis = 1200, easing = LinearEasing), RepeatMode.Restart),
        label = "ring2_progress",
    )
    val ring2Scale = 1f + pulse2Progress * 0.9f
    val ring2Alpha = (0.55f * (1f - (pulse2Progress / 0.7f).coerceIn(0f, 1f)))

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Top Paired Peer Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(colors.success, CircleShape),
            )
            Text(
                text = "PAIRED · ${peerName.uppercase()}",
                style = DesignSpec.stepLabel,
                color = colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.Default.BluetoothConnected,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp),
            )
        }

        HorizontalDivider(color = VokiePalette.Divider)

        // Center Content Area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // PLAYING ALOUD amber badge
            Surface(
                color = VokiePalette.SelectedAmber,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, colors.warning),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 15.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Campaign,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = "PLAYING ALOUD",
                        style = DesignSpec.incomingBadge,
                        color = colors.warning,
                    )
                }
            }

            Spacer(Modifier.height(34.dp))

            // 150dp Pulse Disc with 104dp solid amber circle & volume_up icon
            Box(
                modifier = Modifier.size(150.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Expanding Pulse Ring 1
                Box(
                    Modifier
                        .fillMaxSize()
                        .scale(ring1Scale)
                        .alpha(ring1Alpha)
                        .background(Color.Transparent, CircleShape),
                ) {
                    Surface(
                        Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(2.dp, colors.warning),
                    ) {}
                }

                // Expanding Pulse Ring 2 (staggered 1.2s)
                Box(
                    Modifier
                        .fillMaxSize()
                        .scale(ring2Scale)
                        .alpha(ring2Alpha)
                        .background(Color.Transparent, CircleShape),
                ) {
                    Surface(
                        Modifier.fillMaxSize(),
                        color = Color.Transparent,
                        shape = CircleShape,
                        border = BorderStroke(2.dp, colors.warning),
                    ) {}
                }

                // Solid 104dp Amber Circle
                Surface(
                    modifier = Modifier.size(104.dp),
                    color = colors.warning,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = colors.background,
                            modifier = Modifier.size(52.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            // Peer Name
            Text(
                text = peerName,
                style = DesignSpec.incomingPeerTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(22.dp))

            // 10 Amber Mini Voice Bars with handoff heights & staggered delays
            val miniHeights = listOf(9, 16, 24, 13, 26, 19, 11, 22, 15, 8)
            val miniDelays = listOf(0, 90, 180, 270, 360, 450, 540, 630, 720, 810)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                miniHeights.forEachIndexed { index, height ->
                    val barTrans = rememberInfiniteTransition(label = "itBar_mini_$index")
                    val scale by barTrans.animateFloat(
                        initialValue = 0.18f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(450, delayMillis = miniDelays[index], easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "mini_scale_$index",
                    )
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(height.dp)
                            .scale(scaleX = 1f, scaleY = scale)
                            .background(colors.warning, RoundedCornerShape(2.dp)),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            // Monospace Timer: Elapsed and Duration (0:02 / 0:05 or from playbackProgress)
            val elapsedMs = playbackProgress?.positionMs ?: 2000L
            val durationMs = (playbackProgress?.durationMs ?: 5000L).coerceAtLeast(1000L)
            val elapsedStr = String.format(java.util.Locale.US, "%d:%02d", (elapsedMs / 1000) / 60, (elapsedMs / 1000) % 60)
            val durationStr = String.format(java.util.Locale.US, "%d:%02d", (durationMs / 1000) / 60, (durationMs / 1000) % 60)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = elapsedStr, style = DesignSpec.monoTimer, color = colors.textSecondary)
                Text(text = durationStr, style = DesignSpec.monoTimer, color = colors.textSecondary)
            }

            Spacer(Modifier.height(28.dp))

            // Source Text
            Text(
                text = sourceText,
                style = DesignSpec.sourceTextLarge,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            // Translated Text
            if (!translatedText.isNullOrBlank() && translatedText != sourceText) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = translatedText,
                    style = DesignSpec.translatedText15,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Bottom Controls: Replay button and Hold to reply button
        HorizontalDivider(color = VokiePalette.Divider)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Replay Button (56dp square, 12dp radius)
            Surface(
                modifier = Modifier
                    .size(56.dp)
                    .clickable(onClick = onReplay),
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Replay,
                        contentDescription = "Replay audio",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Hold to reply Button (56dp height, red #E8402B)
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                onStartReply()
                                tryAwaitRelease()
                                onStopReply()
                            },
                        )
                    },
                shape = RoundedCornerShape(12.dp),
                color = colors.accent,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Mic,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(21.dp),
                    )
                    Text(
                        text = "Hold to reply",
                        style = DesignSpec.action,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
