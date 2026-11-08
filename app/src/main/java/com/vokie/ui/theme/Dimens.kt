package com.vokie.ui.theme

import androidx.compose.ui.unit.dp

/** Spacing + sizing tokens. Accessibility-driven minimum touch targets. */
object VokieDimens {
    val space2 = 2.dp
    val space4 = 4.dp
    val space8 = 8.dp
    val space12 = 12.dp
    val space16 = 16.dp
    val space20 = 20.dp
    val space24 = 24.dp
    val space32 = 32.dp
    val space48 = 48.dp

    /** Absolute minimum interactive target. */
    val minTouchTarget = 48.dp

    /** Critical actions (SOS, push-to-talk). */
    val criticalTouchTarget = 64.dp
    val criticalTouchTargetSmall = 56.dp

    val cardCorner = 14.dp
    val buttonCorner = 12.dp
    val borderWidth = 1.dp

    val pushToTalkSize = 200.dp
    val pttHeight = 88.dp
    val pttCorner = 16.dp
    val sosButtonHeight = 72.dp
}
