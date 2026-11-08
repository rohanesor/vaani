package com.vokie.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.location.CardinalDirection
import com.vokie.location.EmergencyGuidanceState
import com.vokie.location.LocationFreshness
import com.vokie.location.RelativeDirection
import com.vokie.ui.theme.VokieTheme
import java.util.Locale

@Composable
fun directionInstruction(relative: RelativeDirection, cardinal: CardinalDirection?): String {
    return when (relative) {
        RelativeDirection.AHEAD -> "Ahead • Move forward"
        RelativeDirection.SLIGHT_RIGHT -> "Bear slight right"
        RelativeDirection.RIGHT -> "Turn right"
        RelativeDirection.SHARP_RIGHT -> "Turn sharp right"
        RelativeDirection.BEHIND -> "Turn around • Walk back"
        RelativeDirection.SHARP_LEFT -> "Turn sharp left"
        RelativeDirection.LEFT -> "Turn left"
        RelativeDirection.SLIGHT_LEFT -> "Bear slight left"
        RelativeDirection.UNKNOWN -> cardinal?.let { "Move ${it.name.lowercase()}" } ?: "Searching for directional heading…"
    }
}

fun relativeRotationDegrees(relative: RelativeDirection): Float {
    return when (relative) {
        RelativeDirection.AHEAD -> 0f
        RelativeDirection.SLIGHT_RIGHT -> 45f
        RelativeDirection.RIGHT -> 90f
        RelativeDirection.SHARP_RIGHT -> 135f
        RelativeDirection.BEHIND -> 180f
        RelativeDirection.SHARP_LEFT -> 225f
        RelativeDirection.LEFT -> 270f
        RelativeDirection.SLIGHT_LEFT -> 315f
        RelativeDirection.UNKNOWN -> 0f
    }
}

@Composable
fun LocationGuidanceCard(
    guidanceState: EmergencyGuidanceState,
    modifier: Modifier = Modifier,
) {
    val guidance = guidanceState.location
    val distance = guidanceState.displayedDistanceMeters ?: guidance.distanceMeters
    val cardinal = guidance.cardinalDirection
    val relative = guidance.relativeDirection
    val isStale = guidance.freshness == LocationFreshness.STALE
    val accuracy = guidanceState.combinedAccuracyMeters
    val uncertain = com.vokie.location.DistancePresentation.isUncertain(distance, accuracy)
    val bucket = com.vokie.location.DistancePresentation.bucket(distance)

    val targetRotation = relativeRotationDegrees(relative)
    val animatedRotation by animateFloatAsState(
        targetValue = targetRotation,
        animationSpec = tween(400),
        label = "compass_rotation",
    )

    VokiePanel(modifier = modifier.fillMaxWidth()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        ) {
            // Large Directional Compass / Arrow Ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(VokieTheme.colors.surface)
                    .border(2.dp, VokieTheme.colors.accent.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Direction arrow pointing ${cardinal?.name ?: "forward"}",
                    tint = if (isStale) VokieTheme.colors.warning else VokieTheme.colors.accent,
                    modifier = Modifier
                        .size(68.dp)
                        .rotate(animatedRotation),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Cardinal direction text
            Text(
                text = cardinal?.name ?: "DIRECTION",
                style = VokieTheme.typography.header,
                color = VokieTheme.colors.accent,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )

            // Approximate Distance
            val distanceText = when {
                distance == null -> "Location uncertain"
                uncertain -> com.vokie.location.DistancePresentation.label(bucket)
                else -> String.format(Locale.US, "≈ %.0f m away", distance)
            }

            Text(
                text = distanceText,
                style = VokieTheme.typography.headerSmall,
                color = VokieTheme.colors.textPrimary,
                modifier = Modifier.padding(top = 6.dp),
            )

            // Combined local and peer uncertainty; never present a precise range when it dominates.
            if (accuracy != null) {
                Text(
                    text = if (uncertain) "GPS accuracy is low (±${String.format(Locale.US, "%.0f", accuracy)} m)" else String.format(Locale.US, "Accuracy ±%.0f m", accuracy),
                    style = VokieTheme.typography.caption,
                    color = VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            HorizontalDivider(
                color = VokieTheme.colors.border,
                modifier = Modifier.padding(vertical = 14.dp),
            )

            // Actionable Movement Guidance
            Text(
                text = directionInstruction(relative, cardinal),
                style = VokieTheme.typography.label,
                color = VokieTheme.colors.textPrimary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(10.dp))

            Text(
                text = when (guidance.freshness) {
                    LocationFreshness.CURRENT -> guidanceState.senderAgeMs?.let { if (it < 1_000) "Updated just now" else "Updated ${it / 1_000}s ago" } ?: "Updated just now"
                    LocationFreshness.STALE -> "Location stale"
                    LocationFreshness.UNAVAILABLE -> "Location unavailable"
                },
                style = VokieTheme.typography.caption,
                color = VokieTheme.colors.textSecondary,
                modifier = Modifier.padding(bottom = 10.dp),
            )

            // Signal Trend Status
            SignalStatusBadge(
                proximityState = guidanceState.proximity?.state,
                rssiTrend = guidanceState.rssiTrend,
                hasLocation = distance != null,
            )
        }
    }
}
