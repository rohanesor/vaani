package com.vokie.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.ui.theme.DesignSpec
import com.vokie.ui.theme.VokieTheme

@Composable
fun DesignAction(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, secondary: Boolean = false, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(DesignSpec.ctaRadius),
        color = if (secondary || !enabled) VokieTheme.colors.surface else VokieTheme.colors.accent,
        border = if (secondary || !enabled) BorderStroke(1.dp, VokieTheme.colors.border) else null,
        modifier = modifier.fillMaxWidth().heightIn(min = DesignSpec.ctaHeight)) {
        Box(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
            Text(text, style = DesignSpec.action, color = if (secondary || !enabled) VokieTheme.colors.textSecondary else Color.White)
        }
    }
}

@Composable
fun SetupProgress(step: Int) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) { i -> Box(Modifier.weight(1f).height(3.dp).background(if (i < step) VokieTheme.colors.accent else VokieTheme.colors.border, CircleShape)) }
        }
        Text("STEP $step OF 3", fontSize = 11.sp, letterSpacing = 1.4.sp,
            color = VokieTheme.colors.textSecondary, modifier = Modifier.padding(top = 14.dp))
    }
}

/** Decorative activity indicator, not a measurement of microphone amplitude. */
@Composable
fun DesignVoiceBars(color: Color, modifier: Modifier = Modifier, compact: Boolean = false) {
    val transition = rememberInfiniteTransition(label = "voice_activity")
    val phase by transition.animateFloat(.18f, 1f,
        infiniteRepeatable(tween(if (compact) 450 else 500), RepeatMode.Reverse), label = "voice_scale")
    val heights = if (compact) listOf(9, 16, 24, 13, 26, 19, 11, 22, 15, 8) else listOf(26, 48, 80, 104, 66, 92, 38, 72, 100, 54, 84, 30)
    Row(modifier.height(if (compact) 26.dp else 110.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { index, height ->
            Box(Modifier.width(if (compact) 3.dp else 4.dp).height(height.dp)
                .scale(1f, if (index % 2 == 0) phase else 1.18f - phase).background(color, CircleShape))
        }
    }
}
