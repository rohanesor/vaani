package com.vokie.communication

import com.vokie.domain.model.VokieLanguage
import com.vokie.translation.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests ensuring TTS and translation contexts are isolated per-peer.
 */
class MultiPeerTtsIsolationTest {
    
    private lateinit var translationEngine: StubTranslationEngine
    private lateinit var coordinator: ReceiverTranslationCoordinator
    
    @Before
    fun setUp() {
        translationEngine = StubTranslationEngine()
        coordinator = ReceiverTranslationCoordinator(translationEngine)
    }

    @Test
    fun `01 - translation for different messages are independent`() = runBlocking {
        val resultA = coordinator.present("msg-a", "Tamil text", VokieLanguage.TA, VokieLanguage.EN)
        val resultB = coordinator.present("msg-b", "Hindi text", VokieLanguage.HI, VokieLanguage.EN)
        
        assertNotEquals(resultA.messageId, resultB.messageId)
        assertNotNull(resultA.displayText)
        assertNotNull(resultB.displayText)
    }

    @Test
    fun `02 - tts handoff has correct language`() = runBlocking {
        val result = coordinator.present("msg-a", "Tamil text", VokieLanguage.TA, VokieLanguage.EN)
        
        val handoff = result.ttsHandoff()
        assertNotNull(handoff)
        assertEquals(VokieLanguage.EN, handoff?.language)
    }

    @Test
    fun `03 - passthrough for same language`() = runBlocking {
        val result = coordinator.present("msg-en", "English text", VokieLanguage.EN, VokieLanguage.EN)
        
        assertEquals(ReceiverPresentationState.TRANSLATED, result.state)
        assertEquals("English text", result.displayText)
    }

    @Test
    fun `04 - cache hit for same message`() = runBlocking {
        val first = coordinator.presentOnce("msg-1", "Test", VokieLanguage.TA, VokieLanguage.EN)
        assertTrue(first.isNew)
        
        val second = coordinator.presentOnce("msg-1", "Test", VokieLanguage.TA, VokieLanguage.EN)
        assertFalse(second.isNew)
    }

    @Test
    fun `05 - failed translation has no tts handoff`() = runBlocking {
        translationEngine.forceFailure = true
        
        val result = coordinator.present("msg-fail", "test", VokieLanguage.TA, VokieLanguage.EN)
        
        assertEquals(ReceiverPresentationState.TRANSLATION_FAILED, result.state)
        assertNull(result.ttsHandoff())
    }

    class StubTranslationEngine : TranslationEngine {
        var forceFailure = false

        override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
            if (forceFailure) {
                return TranslationResult(text, sourceLanguage, targetLanguage, status = TranslationStatus.FAILED, error = "Forced failure")
            }
            if (sourceLanguage == targetLanguage) {
                return TranslationResult(text, sourceLanguage, targetLanguage, text, TranslationStatus.PASSTHROUGH)
            }
            return TranslationResult(text, sourceLanguage, targetLanguage, "[${targetLanguage.code}] $text", TranslationStatus.TRANSLATED)
        }
    }
}
