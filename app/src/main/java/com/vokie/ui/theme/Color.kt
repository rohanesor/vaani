package com.vokie.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * iTantra exact design palette.
 * The handoff uses vermilion for primary actions; SOS retains its explicit label and icon.
 */
object VokiePalette {
    val BgBase = Color(0xFF111111)
    val Surface = Color(0xFF1C1C1C)
    val Border = Color(0xFF2F343C)
    val Divider = Color(0xFF232830)
    val TextMuted = Color(0xFF4E4C4B)
    val TextPrimary = Color(0xFFDBDBDB)
    val TextSecondary = Color(0xFFB3B3B3)
    val LightBg = Color(0xFFD5D5D5)
    val Alert = Color(0xFFE8402B)
    val Success = Color(0xFF3FA66E)
    val Warning = Color(0xFFF2A33A)

    // Specific component tints from iTantra.dc.html
    val SelectedRed = Color(0xFF2A1A17)
    val SelectedAmber = Color(0xFF2A2117)
    val OutgoingSurface = Color(0xFF191412)
    val OutgoingBorder = Color(0xFF3A201B)
    val OutgoingPlayBg = Color(0xFF2B1C19)
    val OutgoingWave = Color(0xFF4A2620)
    val IncomingWave = Color(0xFF3A2C1B)

    // Light-mode derived tokens (manually selectable)
    val LightSurface = Color(0xFFECECEC)
    val LightBorder = Color(0xFFBEBEBE)
    val LightTextPrimary = Color(0xFF161616)
    val LightTextSecondary = Color(0xFF3B3B3B)
    val LightTextMuted = Color(0xFF7A7A7A)
}
