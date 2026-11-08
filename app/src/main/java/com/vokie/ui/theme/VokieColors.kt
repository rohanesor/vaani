package com.vokie.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color tokens exposed to the UI so components never reference raw palette
 * values directly. This keeps dark (default) and light modes swappable.
 */
@Immutable
data class VokieColors(
    val background: Color,
    val surface: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accent: Color,
    val warning: Color,
    val alert: Color,
    val success: Color,
    val info: Color,
    val onAlert: Color,
    val onSuccess: Color,
    val isDark: Boolean,
)

val DarkVokieColors = VokieColors(
    background = VokiePalette.BgBase,
    surface = VokiePalette.Surface,
    border = VokiePalette.Border,
    textPrimary = VokiePalette.TextPrimary,
    textSecondary = VokiePalette.TextSecondary,
    textMuted = VokiePalette.TextMuted,
    accent = VokiePalette.Alert,
    warning = Color(0xFFF2A33A),
    alert = VokiePalette.Alert,
    success = VokiePalette.Success,
    info = Color(0xFF3B82F6),
    onAlert = Color.White,
    onSuccess = Color(0xFF06210F),
    isDark = true,
)

val LightVokieColors = VokieColors(
    background = VokiePalette.LightBg,
    surface = VokiePalette.LightSurface,
    border = VokiePalette.LightBorder,
    textPrimary = VokiePalette.LightTextPrimary,
    textSecondary = VokiePalette.LightTextSecondary,
    textMuted = VokiePalette.LightTextMuted,
    accent = VokiePalette.Alert,
    warning = Color(0xFF9B5D00),
    alert = VokiePalette.Alert,
    success = Color(0xFF2C7D50),
    info = Color(0xFF2563EB),
    onAlert = Color.White,
    onSuccess = Color.White,
    isDark = false,
)

val LocalVokieColors = staticCompositionLocalOf { DarkVokieColors }
