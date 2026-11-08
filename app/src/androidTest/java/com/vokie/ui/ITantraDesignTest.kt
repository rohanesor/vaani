package com.vokie.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.vokie.stt.SttLanguage
import com.vokie.stt.SttState
import com.vokie.stt.SttStatus
import com.vokie.ui.components.PushToTalkButton
import com.vokie.ui.screens.onboarding.OnboardingScreen
import com.vokie.ui.theme.VokieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ITantraDesignTest {
    @get:Rule val compose = createComposeRule()

    @Test fun introductionSkipsToOptionalAttribution() {
        compose.setContent { VokieTheme { OnboardingScreen(onCompleted = {}) } }
        compose.onNodeWithText("SKIP").performClick()
        compose.onNodeWithText("Where did you hear\nabout iTantra?").assertExists()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Search language").assertExists()
        compose.onNodeWithText("Search language").performTextInput("Telugu")
        compose.onNodeWithText("No supported language matches. Clear search to see available languages.").assertExists()
    }

    @Test fun tapModeStartsAndStopsOnce() {
        var starts = 0
        var stops = 0
        var state by mutableStateOf(SttState.READY)
        compose.setContent {
            VokieTheme {
                PushToTalkButton(SttStatus(state), SttLanguage.ENGLISH, true, {},
                    { starts++; state = SttState.LISTENING }, { stops++; state = SttState.READY },
                    circular = true, holdToTalk = false)
            }
        }
        compose.onNode(hasClickAction()).performTouchInput { click() }
        compose.waitForIdle()
        compose.onNode(hasClickAction()).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, starts); assertEquals(1, stops) }
    }

    @Test fun permissionIsRequestedOncePerPress() {
        var requests = 0
        compose.setContent {
            VokieTheme {
                PushToTalkButton(SttStatus(SttState.READY), SttLanguage.ENGLISH, false,
                    { requests++ }, {}, {}, circular = true)
            }
        }
        compose.onNode(hasClickAction()).performTouchInput { click() }
        compose.runOnIdle { assertEquals(1, requests) }
    }
}
