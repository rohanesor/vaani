package com.vokie.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vokie.ui.theme.VokieDimens
import com.vokie.ui.theme.VokieTheme

@Composable
fun VokiePanel(
    modifier: Modifier = Modifier,
    backgroundColor: Color = VokieTheme.colors.surface,
    borderColor: Color = VokieTheme.colors.border,
    shape: Shape = RoundedCornerShape(VokieDimens.cardCorner),
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        color = backgroundColor,
        shape = shape,
        border = BorderStroke(VokieDimens.borderWidth, borderColor),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}
