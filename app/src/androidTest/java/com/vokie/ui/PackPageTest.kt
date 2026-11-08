package com.vokie.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vokie.tts.TtsLanguage
import com.vokie.ui.screens.onboarding.PackPage
import com.vokie.ui.screens.onboarding.PackRow
import com.vokie.ui.theme.VokieTheme
import org.junit.Rule
import org.junit.Test

/** UI fixtures only: no downloader or production model is touched. */
class PackPageTest {
    @get:Rule val compose = createComposeRule()
    private val rows = listOf(PackRow(TtsLanguage.ENGLISH, 114017251, 114017251), PackRow(TtsLanguage.HINDI, 0, 63146118))

    @Test fun downloadUsesFullPageAndRealSuppliedProgress() {
        compose.setContent { VokieTheme {
            PackPage(rows, TtsLanguage.HINDI, TtsLanguage.HINDI, 68, false, false, true, {}, {}, {})
        } }
        compose.onNodeWithText("68%").assertExists()
        compose.onNodeWithText("Continue in background").assertExists()
        compose.onNodeWithText("Cancel download").assertDoesNotExist()
    }

    @Test fun failureHasRetryWithoutInventedResumePromise() {
        compose.setContent { VokieTheme {
            PackPage(rows, TtsLanguage.HINDI, TtsLanguage.HINDI, null, true, false, false, {}, {}, {})
        } }
        compose.onNodeWithText("Download stopped").assertExists()
        compose.onNodeWithText("Retry download").assertExists()
        compose.onNodeWithText("resumes where it stopped").assertDoesNotExist()
    }

    @Test fun installedStateHasContinue() {
        compose.setContent { VokieTheme {
            PackPage(rows, TtsLanguage.HINDI, TtsLanguage.HINDI, 100, false, true, false, {}, {}, {})
        } }
        compose.onNodeWithText("You're ready").assertExists()
        compose.onNodeWithText("Continue").assertExists()
    }
}
