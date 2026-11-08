package com.vokie.translation

import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSwitchTranslationCoordinatorTest {
    private data class Call(val text: String, val source: VokieLanguage, val target: VokieLanguage)
    private class Fake(private val fail: (Call) -> Boolean = { false }) : TranslationEngine {
        val calls = mutableListOf<Call>()
        override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
            val call = Call(text, sourceLanguage, targetLanguage); calls += call
            return if (fail(call)) TranslationResult(text, sourceLanguage, targetLanguage, status = TranslationStatus.FAILED, error = "planned")
            else TranslationResult(text, sourceLanguage, targetLanguage, "[$sourceLanguage>$targetLanguage:$text]", TranslationStatus.TRANSLATED)
        }
    }
    private fun run(fake: Fake, text: String, source: VokieLanguage, target: VokieLanguage) = runBlocking {
        CodeSwitchTranslationCoordinator(fake).translate(text, source, target)
    }

    // Existing whole-message baseline routes.
    @Test fun enToHiOrdinaryUsesWholeMessage() { val f = Fake(); val r = run(f, "help needed", VokieLanguage.EN, VokieLanguage.HI); assertEquals(CodeSwitchTranslationRoute.WHOLE_MESSAGE, r.route); assertEquals(1, r.translationCalls); assertEquals(listOf(Call("help needed", VokieLanguage.EN, VokieLanguage.HI)), f.calls) }
    @Test fun enToTaOrdinaryUsesWholeMessage() { val f = Fake(); val r = run(f, "help needed", VokieLanguage.EN, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.WHOLE_MESSAGE, r.route); assertEquals(1, f.calls.size) }
    @Test fun hiToTaOrdinaryUsesDirectWholeMessage() { val f = Fake(); run(f, "मुझे मदद चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals(listOf(Call("मुझे मदद चाहिए", VokieLanguage.HI, VokieLanguage.TA)), f.calls) }
    @Test fun taToHiOrdinaryUsesDirectWholeMessage() { val f = Fake(); run(f, "எனக்கு உதவி வேண்டும்", VokieLanguage.TA, VokieLanguage.HI); assertEquals(listOf(Call("எனக்கு உதவி வேண்டும்", VokieLanguage.TA, VokieLanguage.HI)), f.calls) }
    @Test fun hiToHiOrdinaryBypassesEngine() { val f = Fake(); val r = run(f, "मुझे मदद चाहिए", VokieLanguage.HI, VokieLanguage.HI); assertEquals(CodeSwitchTranslationRoute.WHOLE_MESSAGE, r.route); assertEquals(TranslationStatus.PASSTHROUGH, r.status); assertEquals(0, r.translationCalls); assertTrue(f.calls.isEmpty()) }
    @Test fun taToTaOrdinaryBypassesEngine() { val f = Fake(); run(f, "எனக்கு உதவி வேண்டும்", VokieLanguage.TA, VokieLanguage.TA); assertTrue(f.calls.isEmpty()) }

    // Segmented paths; fake output makes direct source/target calls explicit.
    @Test fun hiEnglishToTaTranslatesEachConfidentSourceDirectly() { val f = Fake(); val r = run(f, "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.SEGMENTED, r.route); assertEquals(listOf(Call("मुझे", VokieLanguage.HI, VokieLanguage.TA), Call("help", VokieLanguage.EN, VokieLanguage.TA), Call("चाहिए", VokieLanguage.HI, VokieLanguage.TA)), f.calls); assertEquals("[HI>TA:मुझे] [EN>TA:help] [HI>TA:चाहिए]", r.text) }
    @Test fun taEnglishToHiUsesDirectCalls() { val f = Fake(); val r = run(f, "நான் help வேண்டும்", VokieLanguage.TA, VokieLanguage.HI); assertEquals(CodeSwitchTranslationRoute.SEGMENTED, r.route); assertEquals(listOf(VokieLanguage.TA, VokieLanguage.EN, VokieLanguage.TA), f.calls.map { it.source }); assertTrue(f.calls.all { it.target == VokieLanguage.HI }) }
    @Test fun hiTamilToEnglishUsesNoPivot() { val f = Fake(); run(f, "मुझे உதவி चाहिए", VokieLanguage.HI, VokieLanguage.EN); assertEquals(listOf(VokieLanguage.HI, VokieLanguage.TA, VokieLanguage.HI), f.calls.map { it.source }); assertTrue(f.calls.all { it.target == VokieLanguage.EN }) }
    @Test fun hiTamilEnglishToTaPreservesTargetTamilSpan() { val f = Fake(); val r = run(f, "मुझे help வேண்டும்", VokieLanguage.HI, VokieLanguage.TA); assertEquals(listOf(Call("मुझे", VokieLanguage.HI, VokieLanguage.TA), Call("help", VokieLanguage.EN, VokieLanguage.TA)), f.calls); assertEquals("[HI>TA:मुझे] [EN>TA:help] வேண்டும்", r.text) }
    @Test fun targetLanguageSpanIsNotTranslated() { val f = Fake(); run(f, "முதல் मदद", VokieLanguage.TA, VokieLanguage.TA); assertEquals(listOf(Call("मदद", VokieLanguage.HI, VokieLanguage.TA)), f.calls) }

    // Conservative low-confidence handling.
    @Test fun ambiguousLatinUsesExistingWholeMessagePath() { val f = Fake(); val r = run(f, "please help", VokieLanguage.HI, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.WHOLE_MESSAGE, r.route); assertEquals(listOf(Call("please help", VokieLanguage.HI, VokieLanguage.TA)), f.calls) }
    @Test fun romanizedHindiUsesConfiguredWholeMessageFallback() { val f = Fake(); run(f, "mujhe help chahiye", VokieLanguage.HI, VokieLanguage.TA); assertEquals(listOf(Call("mujhe help chahiye", VokieLanguage.HI, VokieLanguage.TA)), f.calls) }
    @Test fun romanizedTamilUsesConfiguredWholeMessageFallback() { val f = Fake(); run(f, "naan help venum", VokieLanguage.TA, VokieLanguage.HI); assertEquals(listOf(Call("naan help venum", VokieLanguage.TA, VokieLanguage.HI)), f.calls) }
    @Test fun rescueTermInsideIndicTextActivatesSegmentedPath() { val f = Fake(); val r = run(f, "oxygen चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.SEGMENTED, r.route); assertEquals(2, r.translationCalls) }

    // Text ordering and grouping/bounds.
    @Test fun punctuationAndWhitespaceArePreservedAroundTranslation() { val f = Fake(); val r = run(f, "मदद, help! चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals("[HI>TA:मदद,] [EN>TA:help!] [HI>TA:चाहिए]", r.text) }
    @Test fun whitespaceIsNotCollapsed() { val f = Fake(); val r = run(f, "मदद  help   चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals("[HI>TA:मदद]  [EN>TA:help]   [HI>TA:चाहिए]", r.text) }
    @Test fun adjacentRescueWordsAreGroupedIntoOneCall() { val f = Fake(); run(f, "rescue team வேண்டும்", VokieLanguage.TA, VokieLanguage.HI); assertEquals(listOf(Call("rescue team", VokieLanguage.EN, VokieLanguage.HI), Call("வேண்டும்", VokieLanguage.TA, VokieLanguage.HI)), f.calls) }
    @Test fun repeatedSwitchesRemainBoundedAndOrdered() { val f = Fake(); val r = run(f, "अ help அ rescue अ", VokieLanguage.HI, VokieLanguage.EN); assertEquals(3, r.translationCalls); assertEquals(listOf(VokieLanguage.HI, VokieLanguage.TA, VokieLanguage.HI), f.calls.map { it.source }) }
    @Test fun excessiveSegmentationUsesWholeMessageFallback() { val f = Fake(); val text = (1..25).joinToString("") { if (it % 2 == 0) "अ" else "அ" }; val r = run(f, text, VokieLanguage.HI, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.WHOLE_MESSAGE, r.route); assertEquals(listOf(Call(text, VokieLanguage.HI, VokieLanguage.TA)), f.calls) }

    // Failure safety.
    @Test fun oneSpanFailurePreservesOriginalSpanAndMarksPartial() { val f = Fake { it.source == VokieLanguage.EN }; val r = run(f, "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.SEGMENTED, r.route); assertTrue(r.isPartial); assertEquals("[HI>TA:मुझे] help [HI>TA:चाहिए]", r.text) }
    @Test fun partialResultKeepsSuccessfulOutput() { val f = Fake { it.text == "चाहिए" }; val r = run(f, "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertTrue(r.isPartial); assertTrue(r.text!!.contains("[EN>TA:help]")); assertTrue(r.text!!.endsWith("चाहिए")) }
    @Test fun completeSegmentFailureUsesWholeMessageFallback() { val f = Fake { true }; val r = run(f, "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.WHOLE_MESSAGE_FALLBACK, r.route); assertTrue(r.fallbackUsed); assertEquals(4, r.translationCalls); assertEquals("मुझे help चाहिए", r.text) }
    @Test fun emptyMessageDoesNotInvokeEngine() { val f = Fake(); val r = run(f, "", VokieLanguage.HI, VokieLanguage.TA); assertEquals(CodeSwitchTranslationRoute.PRESERVED, r.route); assertTrue(f.calls.isEmpty()) }
    @Test fun repeatedExecutionIsDeterministic() { val f1 = Fake(); val f2 = Fake(); val first = run(f1, "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA); val second = run(f2, "मुझे help चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals(first.copy(translationCalls = 0), second.copy(translationCalls = 0)); assertEquals(f1.calls, f2.calls) }
    @Test fun lowConfidenceSpanIsNeverSentAsEnglish() { val f = Fake(); run(f, "मुझे please चाहिए", VokieLanguage.HI, VokieLanguage.TA); assertEquals(listOf(Call("मुझे please चाहिए", VokieLanguage.HI, VokieLanguage.TA)), f.calls) }
}
