package com.vokie.translation

import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class CodeSwitchPresentationHandoffTest {
    private data class Call(val text: String, val source: VokieLanguage, val target: VokieLanguage)
    private class Fake(private val response: (Call) -> TranslationResult) : TranslationEngine {
        val calls = mutableListOf<Call>()
        override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
            val call = Call(text, sourceLanguage, targetLanguage); calls += call
            return response(call)
        }
    }
    private class FakeTtsSink { val handoffs = mutableListOf<ReceiverTtsHandoff>(); fun enqueue(handoff: ReceiverTtsHandoff) { handoffs += handoff } }
    private fun successful(call: Call) = TranslationResult(call.text, call.source, call.target, "[${call.source}>${call.target}:${call.text}]", TranslationStatus.TRANSLATED)
    private fun present(fake: Fake, id: String, text: String, source: VokieLanguage, target: VokieLanguage) = runBlocking {
        ReceiverTranslationCoordinator(fake).present(id, text, source, target)
    }

    @Test fun ordinaryPresentationRemainsNormalAndHandsFinalTextToTts() {
        val fake = Fake(::successful); val p = present(fake, "normal", "help needed", VokieLanguage.EN, VokieLanguage.HI)
        assertNull(p.codeSwitchIndicator)
        assertEquals("[EN>HI:help needed]", p.displayText)
        assertEquals(ReceiverTtsHandoff("[EN>HI:help needed]", VokieLanguage.HI), p.ttsHandoff())
    }

    @Test fun successfulCodeSwitchPresentationIsTruthfullyMarked() {
        val fake = Fake(::successful); val p = present(fake, "mixed", "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA)
        assertEquals(CodeSwitchTranslationRoute.SEGMENTED, p.codeSwitchRoute)
        assertFalse(p.isPartial); assertFalse(p.fallbackUsed)
        assertEquals("Mixed language normalized", p.codeSwitchIndicator)
        assertEquals("[HI>TA:मुझे] [EN>TA:help] [HI>TA:चाहिए]", p.displayText)
    }

    @Test fun successfulSegmentedFinalTextReachesOneTargetLanguageTtsHandoff() {
        val fake = Fake(::successful); val p = present(fake, "mixed-tts", "முதல் help வேண்டும்", VokieLanguage.TA, VokieLanguage.HI)
        val sink = FakeTtsSink(); p.ttsHandoff()?.let(sink::enqueue)
        assertEquals(1, sink.handoffs.size)
        assertEquals(p.displayText, sink.handoffs.single().text)
        assertEquals(VokieLanguage.HI, sink.handoffs.single().language)
        assertEquals(3, fake.calls.size) // Translation calls may be segmented; TTS is not.
    }

    @Test fun partialPresentationKeepsFinalMergedTextForTts() {
        val fake = Fake { call -> if (call.source == VokieLanguage.EN) TranslationResult(call.text, call.source, call.target, status = TranslationStatus.FAILED, error = "planned") else successful(call) }
        val p = present(fake, "partial", "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA)
        assertTrue(p.isPartial); assertEquals("Partially translated", p.codeSwitchIndicator)
        assertEquals("[HI>TA:मुझे] help [HI>TA:चाहिए]", p.displayText)
        assertEquals(ReceiverTtsHandoff(p.displayText!!, VokieLanguage.TA), p.ttsHandoff())
    }

    @Test fun wholeMessageFallbackPresentationReachesTts() {
        val original = "मुझे help चाहिए"
        val fake = Fake { call ->
            if (call.text == original) successful(call)
            else TranslationResult(call.text, call.source, call.target, status = TranslationStatus.FAILED, error = "planned")
        }
        val p = present(fake, "fallback", original, VokieLanguage.HI, VokieLanguage.TA)
        assertTrue(p.fallbackUsed); assertEquals("Using fallback translation", p.codeSwitchIndicator)
        assertEquals("[HI>TA:$original]", p.displayText)
        assertEquals(ReceiverTtsHandoff(p.displayText!!, VokieLanguage.TA), p.ttsHandoff())
    }

    @Test fun sameLanguageCodeSwitchIsMarkedTranslatedNotPassthrough() = runBlocking {
        val fake = Fake(::successful)
        val result = ReceiverTranslationCoordinator(fake).translate("same-mixed", "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.HI)
        assertEquals(TranslationStatus.TRANSLATED, result.status)
        assertEquals("मुझे [EN>HI:help] चाहिए", result.translatedText)
    }

    @Test fun sameLanguagePresentationHandsOriginalTextToTargetVoice() {
        val fake = Fake(::successful); val p = present(fake, "same", "मुझे मदद चाहिए", VokieLanguage.HI, VokieLanguage.HI)
        assertEquals("मुझे मदद चाहिए", p.displayText)
        assertEquals(ReceiverTtsHandoff("मुझे मदद चाहिए", VokieLanguage.HI), p.ttsHandoff())
        assertTrue(fake.calls.isEmpty())
    }

    @Test fun failedOrEmptyPresentationNeverProducesTtsHandoff() {
        val failed = ReceiverPresentation("f", "text", VokieLanguage.EN, VokieLanguage.TA, state = ReceiverPresentationState.TRANSLATION_FAILED)
        val empty = ReceiverPresentation("e", "text", VokieLanguage.EN, VokieLanguage.TA, "", ReceiverPresentationState.TRANSLATED)
        assertNull(failed.ttsHandoff()); assertNull(empty.ttsHandoff())
    }

    @Test fun noPerSpanTtsInvocationOccursInContract() {
        val fake = Fake(::successful); val p = present(fake, "one-handoff", "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA)
        val sink = FakeTtsSink(); p.ttsHandoff()?.let(sink::enqueue)
        assertEquals(3, fake.calls.size)
        assertEquals(1, sink.handoffs.size)
        assertEquals(p.displayText, sink.handoffs.single().text)
    }

    @Test fun presentationIndicatorsNeverExposeConfidenceOrLanguageLists() {
        val normal = ReceiverPresentation("n", "x", VokieLanguage.EN, VokieLanguage.HI, "y", ReceiverPresentationState.TRANSLATED)
        assertNull(normal.codeSwitchIndicator)
        assertEquals("Mixed language normalized", ReceiverPresentation("m", "x", VokieLanguage.HI, VokieLanguage.TA, "y", ReceiverPresentationState.TRANSLATED, codeSwitchRoute = CodeSwitchTranslationRoute.SEGMENTED).codeSwitchIndicator)
    }
}
