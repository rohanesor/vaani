package com.vokie.translation

import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** N3 regression tests: mixed-language presentations remain message/peer local. */
class CodeSwitchSessionIsolationTest {
    private class Fake : TranslationEngine {
        val calls = mutableListOf<Triple<String, VokieLanguage, VokieLanguage>>()
        override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
            calls += Triple(text, sourceLanguage, targetLanguage)
            return TranslationResult(text, sourceLanguage, targetLanguage, "[$sourceLanguage>$targetLanguage:$text]", TranslationStatus.TRANSLATED)
        }
    }

    @Test fun `different peer messages retain independent source and TTS target`() = runBlocking {
        val fake = Fake(); val coordinator = ReceiverTranslationCoordinator(fake)
        val hiPeer = coordinator.present("peer-hi-message", "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA)
        val taPeer = coordinator.present("peer-ta-message", "எனக்கு help வேண்டும்", VokieLanguage.TA, VokieLanguage.HI)

        assertEquals(VokieLanguage.HI, hiPeer.sourceLanguage)
        assertEquals(VokieLanguage.TA, taPeer.sourceLanguage)
        assertEquals(VokieLanguage.TA, hiPeer.ttsHandoff()?.language)
        assertEquals(VokieLanguage.HI, taPeer.ttsHandoff()?.language)
        assertEquals(CodeSwitchTranslationRoute.SEGMENTED, hiPeer.codeSwitchRoute)
        assertEquals(CodeSwitchTranslationRoute.SEGMENTED, taPeer.codeSwitchRoute)
    }

    @Test fun `concurrent duplicate delivery creates one receiver presentation`() = runBlocking {
        val fake = Fake(); val coordinator = ReceiverTranslationCoordinator(fake)
        val outcomes = listOf(
            async { coordinator.presentOnce("same-packet", "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA) },
            async { coordinator.presentOnce("same-packet", "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA) },
        ).awaitAll()

        assertTrue(outcomes.count { it.isNew } == 1)
        assertTrue(outcomes.count { !it.isNew } == 1)
        // Three direct source-to-target spans only once; duplicate packet gets cache hit.
        assertEquals(3, fake.calls.size)
    }

    @Test fun `N2 retry identity reuses presentation and cannot duplicate TTS handoff`() = runBlocking {
        val fake = Fake(); val coordinator = ReceiverTranslationCoordinator(fake)
        val first = coordinator.presentOnce("stable-message-id", "எனக்கு help வேண்டும்", VokieLanguage.TA, VokieLanguage.HI)
        val retry = coordinator.presentOnce("stable-message-id", "எனக்கு help வேண்டும்", VokieLanguage.TA, VokieLanguage.HI)

        assertTrue(first.isNew)
        assertFalse(retry.isNew)
        assertEquals(first.presentation.ttsHandoff(), retry.presentation.ttsHandoff())
    }
}
