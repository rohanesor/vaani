package com.vokie.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Values transcribed from iTantra.dc.html. CSS px map to dp/sp at default font scale. */
object DesignSpec {
    val setupMargin = 28.dp
    val homeMargin = 22.dp
    val ctaHeight = 56.dp
    val ctaRadius = 12.dp
    val panelRadius = 14.dp
    val microphoneSize = 168.dp
    const val pressDurationMs = 160

    // Screen Titles & Headers
    val introductionTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp)
    val introductionBody = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 25.6.sp)
    val setupTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 32.5.sp)
    val setupSubtitle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp)
    val readyTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 28.sp, lineHeight = 35.sp)
    val homeTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 22.sp, lineHeight = 26.4.sp)
    val homeHint = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp)
    val listeningTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp)
    val listeningSubtitle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.5.sp)
    val incomingPeerTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 24.sp, lineHeight = 30.sp)
    val channelHeaderTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 19.2.sp)
    val channelHeaderSubtitle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 12.sp)
    val settingsHeader = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 20.sp, lineHeight = 24.sp)

    // Badges, Step Labels & Section Headers (all 11sp / 12sp uppercase)
    val stepLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 1.4.sp)
    val settingsSection = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 1.4.sp)
    val channelDate = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 1.2.sp)
    val messageTag = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 1.0.sp)
    val listeningBadge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 12.sp, letterSpacing = 1.0.sp)
    val incomingBadge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 12.sp, letterSpacing = 1.0.sp)
    val badgeReady = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 12.sp, letterSpacing = 0.8.sp)
    val tabLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 0.6.sp)
    val cancelLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 12.sp, lineHeight = 12.sp, letterSpacing = 0.8.sp)

    // Actions & Buttons
    val action = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp)
    val pttAction = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 14.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp)
    val toggleLabel = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 11.sp, lineHeight = 11.sp, letterSpacing = 0.6.sp)

    // Card & List Typography
    val optionTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 16.sp)
    val optionTitleBold = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 16.sp)
    val cardTitle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 19.2.sp)
    val cardTitleBold = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold,
        fontSize = 16.sp, lineHeight = 19.2.sp)
    val cardSubtitle = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 15.6.sp)
    val cardSubtitle13 = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 13.sp, lineHeight = 16.9.sp)

    // Speech & Conversation Text
    val sourceText = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 17.sp, lineHeight = 25.5.sp)
    val sourceTextLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 18.sp, lineHeight = 27.sp)
    val translatedText = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 21.sp)
    val translatedText15 = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 15.sp, lineHeight = 22.5.sp)

    // Monospace Elements
    val monoSmall = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 11.sp)
    val monoTimer = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 12.sp)
    val monoBody = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 14.sp)
}
