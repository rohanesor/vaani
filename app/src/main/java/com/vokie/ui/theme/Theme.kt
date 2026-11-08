package com.vokie.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

@Composable
fun VokieTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val colors = if (darkTheme) DarkVokieColors else LightVokieColors
    val scheme = if (darkTheme) {
        darkColorScheme(
            background = colors.background,
            surface = colors.surface,
            primary = colors.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            outline = colors.border,
            error = colors.alert,
        )
    } else {
        lightColorScheme(
            background = colors.background,
            surface = colors.surface,
            primary = colors.accent,
            onPrimary = androidx.compose.ui.graphics.Color.White,
            onBackground = colors.textPrimary,
            onSurface = colors.textPrimary,
            outline = colors.border,
            error = colors.alert,
        )
    }
    CompositionLocalProvider(
        LocalVokieColors provides colors,
        LocalVokieTypography provides defaultVokieTypography,
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

object VokieTheme {
    val colors @Composable get() = LocalVokieColors.current
    val typography @Composable get() = LocalVokieTypography.current
}
