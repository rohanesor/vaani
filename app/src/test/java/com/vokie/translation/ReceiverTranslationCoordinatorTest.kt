package com.vokie.translation

import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ReceiverTranslationCoordinatorTest {
    private class Fake : TranslationEngine {
        var calls = 0
        override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
            calls++; return TranslationResult(text, sourceLanguage, targetLanguage, "[$targetLanguage] $text", TranslationStatus.TRANSLATED)
        }
    }
    @Test fun translatesAndCachesPerReceiverOutputLanguage() = runBlocking {
        val fake = Fake(); val c = ReceiverTranslationCoordinator(fake)
        val hi = c.present("m", "தமிழ்", VokieLanguage.TA, VokieLanguage.HI)
        assertEquals(ReceiverPresentationState.TRANSLATED, hi.state); assertEquals(VokieLanguage.HI, hi.ttsLanguage); assertEquals("[HI] தமிழ்", hi.ttsText)
        assertSame(hi, c.present("m", "தமிழ்", VokieLanguage.TA, VokieLanguage.HI)); assertEquals(1, fake.calls)
        c.present("m", "தமிழ்", VokieLanguage.TA, VokieLanguage.EN); assertEquals(2, fake.calls)
    }
    @Test fun sameLanguageBypassesAndUnavailableIsExplicit() = runBlocking {
        val fake = Fake(); val c = ReceiverTranslationCoordinator(fake)
        assertEquals("hello", c.present("en", "hello", VokieLanguage.EN, VokieLanguage.EN).displayText)
        assertEquals("नमस्ते", c.present("hi", "नमस्ते", VokieLanguage.HI, VokieLanguage.HI).displayText)
        assertEquals("வணக்கம்", c.present("ta", "வணக்கம்", VokieLanguage.TA, VokieLanguage.TA).displayText)
        assertEquals(0, fake.calls)
        val unavailable = ReceiverTranslationCoordinator(UnavailableTranslationEngine()).present("x", "hello", VokieLanguage.EN, VokieLanguage.TA)
        assertEquals(ReceiverPresentationState.TRANSLATION_UNAVAILABLE, unavailable.state); assertNull(unavailable.displayText)
    }
    @Test fun translationFailureIsExplicit() = runBlocking {
        val failing = object : TranslationEngine { override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage) = TranslationResult(text, sourceLanguage, targetLanguage, status = TranslationStatus.FAILED, error = "failure") }
        assertEquals(ReceiverPresentationState.TRANSLATION_FAILED, ReceiverTranslationCoordinator(failing).present("x", "hello", VokieLanguage.EN, VokieLanguage.TA).state)
    }

    @Test fun invalidLanguagesNeverFallbackToEnglish() = runBlocking {
        val c = ReceiverTranslationCoordinator(Fake())
        assertEquals(ReceiverPresentationState.INVALID_SOURCE_LANGUAGE, c.present("x", "text", null, VokieLanguage.EN).state)
        assertEquals(ReceiverPresentationState.INVALID_TARGET_LANGUAGE, c.present("x", "text", VokieLanguage.EN, null).state)
    }
}
