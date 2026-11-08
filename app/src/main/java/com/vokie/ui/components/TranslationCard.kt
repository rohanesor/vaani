package com.vokie.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.vokie.domain.model.Message
import com.vokie.domain.model.VokieLanguage
import com.vokie.translation.ReceiverPresentation
import com.vokie.translation.ReceiverPresentationState
import com.vokie.tts.MessageTtsState
import com.vokie.ui.theme.VokieTheme

@Composable
fun TranslationCard(
    message: Message,
    incoming: Boolean,
    presentation: ReceiverPresentation?,
    ttsState: MessageTtsState?,
    isTtsInstalledForLanguage: Boolean,
    onPlayTts: () -> Unit,
    onStopTts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val srcLang = VokieLanguage.fromCode(message.language)?.displayName ?: message.language

    Column(modifier = modifier.fillMaxWidth()) {
        // ORIGINAL TEXT
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 4.dp),
        ) {
            Text(
                text = "ORIGINAL · $srcLang",
                style = VokieTheme.typography.labelSmall,
                color = VokieTheme.colors.textSecondary,
            )
        }

        Text(
            text = message.text,
            style = VokieTheme.typography.bodyLarge,
            color = VokieTheme.colors.textPrimary,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        // If incoming, show translation & TTS section
        if (incoming) {
            HorizontalDivider(
                color = VokieTheme.colors.border.copy(alpha = 0.5f),
                modifier = Modifier.padding(vertical = 8.dp),
            )

            val targetLang = presentation?.targetLanguage?.displayName
                ?: presentation?.sourceLanguage?.displayName
                ?: "Understanding language"

            val isTranslated = presentation?.state == ReceiverPresentationState.TRANSLATED &&
                !presentation.displayText.isNullOrBlank()

            if (isTranslated) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 4.dp),
                ) {
                    Text(
                        text = "TRANSLATED · $targetLang",
                        style = VokieTheme.typography.labelSmall,
                        color = VokieTheme.colors.accent,
                    )
                }

                Text(
                    text = presentation?.displayText.orEmpty(),
                    style = VokieTheme.typography.bodyLarge,
                    color = VokieTheme.colors.textPrimary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                presentation?.codeSwitchIndicator?.let { indicator ->
                    Text(
                        text = indicator,
                        style = VokieTheme.typography.caption,
                        color = VokieTheme.colors.textMuted,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            } else if (presentation?.state == ReceiverPresentationState.TRANSLATION_UNAVAILABLE ||
                presentation?.state == ReceiverPresentationState.TRANSLATION_FAILED) {
                Text(
                    text = "Translation unavailable offline",
                    style = VokieTheme.typography.caption,
                    color = VokieTheme.colors.warning,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            // TTS playback row
            Spacer(Modifier.height(4.dp))
            if (isTtsInstalledForLanguage) {
                val isPlaying = ttsState in setOf(MessageTtsState.SYNTHESIZING, MessageTtsState.PLAYING)
                OutlinedButton(
                    onClick = if (isPlaying) onStopTts else onPlayTts,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = if (isPlaying) VokieTheme.colors.alert else VokieTheme.colors.accent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = if (isPlaying) "Stop voice playback" else "Play voice",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isPlaying) "STOP VOICE" else "SPEAK",
                        style = VokieTheme.typography.labelSmall,
                    )
                }
            } else {
                Text(
                    text = "Voice unavailable offline",
                    style = VokieTheme.typography.caption,
                    color = VokieTheme.colors.textMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
