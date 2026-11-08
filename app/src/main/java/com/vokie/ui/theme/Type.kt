package com.vokie.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Font abstraction.
 *
 * The brand uses "Balboa" for Display/Headers and "Arial" for Labels/Body.
 * These may be unavailable during initial development, so we abstract the family
 * here. When the Balboa/Arial font files are bundled in res/font, swap
 * [balboaFamily] / [arialFamily] to point at them without touching call sites.
 *
 * RULES:
 *  - Balboa (display) is NEVER used for body text, timestamps, dynamic info or
 *    long paragraphs.
 *  - Minimum body text size is 16sp.
 */
object VokieFonts {
    // TODO: replace with FontFamily(Font(R.font.balboa_bold), ...) once bundled.
    val balboaFamily: FontFamily = FontFamily.SansSerif
    // TODO: replace with Arial once bundled (Arial ships on most Android as sans).
    val arialFamily: FontFamily = FontFamily.SansSerif
}

@Immutable
data class VokieTypography(
    /** Display — Balboa Bold. Big emergency headlines only. */
    val display: TextStyle,
    /** Headers — Balboa. */
    val header: TextStyle,
    val headerSmall: TextStyle,
    /** Labels — Arial Bold. */
    val label: TextStyle,
    val labelSmall: TextStyle,
    /** Body — Arial Regular, minimum 16sp. */
    val body: TextStyle,
    val bodyLarge: TextStyle,
    /** Timestamps / dynamic mono-ish info — Arial, never Balboa. */
    val caption: TextStyle,
)

val defaultVokieTypography = VokieTypography(
    display = TextStyle(
        fontFamily = VokieFonts.balboaFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 44.sp,
        letterSpacing = 0.5.sp,
    ),
    header = TextStyle(
        fontFamily = VokieFonts.balboaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
    ),
    headerSmall = TextStyle(
        fontFamily = VokieFonts.balboaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    label = TextStyle(
        fontFamily = VokieFonts.arialFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = VokieFonts.arialFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.8.sp,
    ),
    body = TextStyle(
        fontFamily = VokieFonts.arialFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp, // minimum body size
        lineHeight = 22.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = VokieFonts.arialFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    ),
    caption = TextStyle(
        fontFamily = VokieFonts.arialFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    ),
)

val LocalVokieTypography = staticCompositionLocalOf { defaultVokieTypography }
