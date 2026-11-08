package com.vokie.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vokie.stt.UserLanguageProfile
import com.vokie.ui.theme.VokieTheme

@Composable
fun CompactLanguagePair(
    profile: UserLanguageProfile?,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val speakLang = profile?.inputSttLanguage?.nativeName ?: "Select"
    val understandLang = profile?.preferredOutputLanguage?.displayName ?: "Select"

    val baseModifier = if (onClick != null) {
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    } else {
        modifier.padding(horizontal = 8.dp, vertical = 4.dp)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = baseModifier,
    ) {
        Text(
            text = speakLang,
            style = VokieTheme.typography.labelSmall,
            color = VokieTheme.colors.textPrimary,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = "translates to",
            tint = VokieTheme.colors.accent,
            modifier = Modifier.padding(top = 1.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = understandLang,
            style = VokieTheme.typography.labelSmall,
            color = VokieTheme.colors.accent,
        )
    }
}

@Composable
fun FullLanguagePairCard(
    profile: UserLanguageProfile?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val speakLang = profile?.inputSttLanguage?.nativeName ?: "Not selected"
    val understandLang = profile?.preferredOutputLanguage?.displayName ?: "Not selected"

    VokiePanel(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.padding(vertical = 4.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "YOU SPEAK",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                )
                Text(
                    text = speakLang,
                    style = VokieTheme.typography.headerSmall,
                    color = VokieTheme.colors.textPrimary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "translates to",
                tint = VokieTheme.colors.accent,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text(
                    text = "YOU UNDERSTAND",
                    style = VokieTheme.typography.labelSmall,
                    color = VokieTheme.colors.textSecondary,
                )
                Text(
                    text = understandLang,
                    style = VokieTheme.typography.headerSmall,
                    color = VokieTheme.colors.accent,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}
