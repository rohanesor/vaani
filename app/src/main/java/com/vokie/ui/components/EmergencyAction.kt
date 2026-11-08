package com.vokie.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

@Composable
fun EmergencyActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false,
) {
    val containerColor = if (isDestructive) VokieTheme.colors.alert else VokieTheme.colors.surface
    val contentColor = if (isDestructive) Color.White else VokieTheme.colors.textPrimary
    val borderColor = if (isDestructive) VokieTheme.colors.alert else VokieTheme.colors.border

    Surface(
        onClick = onClick,
        color = containerColor,
        shape = RoundedCornerShape(VokieDimens.cardCorner),
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 68.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) Color.White else VokieTheme.colors.accent,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = VokieTheme.typography.label,
                    color = contentColor,
                )
                Text(
                    text = subtitle,
                    style = VokieTheme.typography.caption,
                    color = if (isDestructive) Color.White.copy(alpha = 0.85f) else VokieTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
