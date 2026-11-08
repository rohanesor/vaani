package com.vokie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vokie.ui.theme.VokieTheme

/** Native, offline wordmark from the iTantra handoff (no web font dependency). */
@Composable
fun ITantraWordmark(modifier: Modifier = Modifier, size: Int = 30) {
    Row(modifier.clearAndSetSemantics { contentDescription = "iTantra" }, verticalAlignment = Alignment.Bottom) {
        Column(Modifier.padding(bottom = (size * 0.21f).dp), verticalArrangement = Arrangement.spacedBy((size * 0.07f).dp)) {
            Box(Modifier.size((size * 0.145f).dp).background(VokieTheme.colors.accent, CircleShape))
            Box(Modifier.width((size * 0.145f).dp).height((size * 0.52f).dp).background(VokieTheme.colors.textPrimary, CircleShape))
        }
        Spacer(Modifier.width((size * 0.09f).dp))
        Text("Tantra", color = VokieTheme.colors.textPrimary, fontSize = size.sp, lineHeight = size.sp, fontWeight = FontWeight.Bold, letterSpacing = (-size * 0.02f).sp)
    }
}
