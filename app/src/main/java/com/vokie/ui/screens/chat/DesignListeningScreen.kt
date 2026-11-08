package com.vokie.ui.screens.chat

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokieTheme

/**
 * Screen 11: Listening — matches iTantra.dc.html exactly.
 * 12 animated vermilion voice bars (heights 26..104dp), top LISTENING pill with pulsing red dot,
 * live transcript quote, and 64dp circular cancel button.
 */
@Composable
fun DesignListeningScreen(
    onCancel: () -> Unit,
    transcript: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = VokieTheme.colors

    // Pulse animation for the 8dp red dot inside LISTENING badge
    val transition = rememberInfiniteTransition(label = "listening_dot")
    val dotAlpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "red_dot_alpha",
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        // Main content area
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // Top Pill: LISTENING badge with pulsing 8dp red dot
            Surface(
                color = colors.surface,
                shape = RoundedCornerShape(999.dp),
                border = BorderStroke(1.dp, colors.border),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .alpha(dotAlpha)
                            .background(colors.accent, CircleShape),
                    )
                    Text(
                        text = "LISTENING",
                        style = DesignSpec.listeningBadge,
                        color = colors.textPrimary,
                    )
                }
            }

            Spacer(Modifier.height(38.dp))

            Text(
                text = "Speak normally",
                style = DesignSpec.listeningTitle,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "iTantra stops on its own when you pause.",
                style = DesignSpec.listeningSubtitle,
                color = colors.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(44.dp))

            // 12 Animated Vermilion Voice Bars with exact handoff heights & staggered delays
            val barHeights = listOf(26, 48, 80, 104, 66, 92, 38, 72, 100, 54, 84, 30)
            val barDelays = listOf(0, 80, 160, 240, 320, 400, 480, 560, 640, 720, 800, 880)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                barHeights.forEachIndexed { index, height ->
                    val barTransition = rememberInfiniteTransition(label = "itBar_$index")
                    val scale by barTransition.animateFloat(
                        initialValue = 0.18f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(500, delayMillis = barDelays[index], easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse,
                        ),
                        label = "bar_scale_$index",
                    )
                    Box(
                        modifier = Modifier
                            .width(4.dp)
                            .height(height.dp)
                            .scale(scaleX = 1f, scaleY = scale)
                            .background(colors.accent, RoundedCornerShape(3.dp)),
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            // Live transcript quote area (min height 54dp)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 54.dp),
                contentAlignment = Alignment.Center,
            ) {
                val displayQuote = if (!transcript.isNullOrBlank()) {
                    "\"$transcript\""
                } else {
                    "\"Meet me at the north gate in ten minutes\""
                }
                Text(
                    text = displayQuote,
                    style = DesignSpec.sourceTextLarge,
                    color = colors.textPrimary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Bottom Cancel Section: 64dp circle + CANCEL label
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .padding(bottom = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(64.dp)
                    .clickable(onClick = onCancel),
                shape = CircleShape,
                color = colors.surface,
                border = BorderStroke(1.dp, colors.border),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Cancel listening",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Text(
                text = "CANCEL",
                style = DesignSpec.cancelLabel,
                color = colors.textMuted,
            )
        }
    }
}
