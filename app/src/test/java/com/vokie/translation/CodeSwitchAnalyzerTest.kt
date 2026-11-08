package com.vokie.translation

import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CodeSwitchAnalyzerTest {
    private val analyzer = CodeSwitchAnalyzer()

    private fun analyze(text: String, primary: VokieLanguage) = analyzer.analyze(text, primary)
    private fun assertAnalysis(
        text: String,
        primary: VokieLanguage,
        state: CodeSwitchAnalysisState,
        vararg expected: Triple<String, VokieLanguage, Pair<SpanConfidence, SpanEvidence>>,
    ) {
        val result = analyze(text, primary)
        assertEquals(state, result.state)
        assertEquals(primary, result.primaryLanguage)
        assertEquals(expected.map { it.first }, result.segments.map { it.text })
        assertEquals(expected.map { it.second }, result.segments.map { it.language })
        assertEquals(expected.map { it.third.first }, result.segments.map { it.confidence })
        assertEquals(expected.map { it.third.second }, result.segments.map { it.evidence })
        assertEquals(text, result.segments.joinToString("") { it.text })
    }
    private fun span(text: String, language: VokieLanguage, confidence: SpanConfidence, evidence: SpanEvidence) = Triple(text, language, confidence to evidence)

    // A. Single-language
    @Test fun englishOnlyUsesConfiguredFallback() = assertAnalysis("help needed", VokieLanguage.EN, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("help needed", VokieLanguage.EN, SpanConfidence.LOW, SpanEvidence.LATIN_AMBIGUOUS))
    @Test fun hindiOnlyIsHighScriptEvidence() = assertAnalysis("मुझे मदद चाहिए", VokieLanguage.HI, CodeSwitchAnalysisState.NO_SWITCH,
        span("मुझे मदद चाहिए", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT))
    @Test fun tamilOnlyIsHighScriptEvidence() = assertAnalysis("எனக்கு உதவி வேண்டும்", VokieLanguage.TA, CodeSwitchAnalysisState.NO_SWITCH,
        span("எனக்கு உதவி வேண்டும்", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))

    // B. Mixed
    @Test fun hindiAndEnglishRescueTerm() = assertAnalysis("मुझे help चाहिए", VokieLanguage.HI, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("मुझे ", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT),
        span("help ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("चाहिए", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT))
    @Test fun tamilAndEnglishRescueTerm() = assertAnalysis("நான் help வேண்டும்", VokieLanguage.TA, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("நான் ", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT),
        span("help ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("வேண்டும்", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))
    @Test fun hindiAndTamilSwitchesFromConfiguredHindi() = assertAnalysis("मुझे உதவி चाहिए", VokieLanguage.HI, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("मुझे ", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT),
        span("உதவி ", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT),
        span("चाहिए", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT))
    @Test fun hindiTamilEnglishUsesOnlyKnownEnglishCandidate() = assertAnalysis("मुझे help வேண்டும்", VokieLanguage.HI, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("मुझे ", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT),
        span("help ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("வேண்டும்", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))
    @Test fun multipleScriptSwitchesRemainOrdered() = assertAnalysis("முதல் मदद Tamil உதவி", VokieLanguage.TA, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("முதல் ", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT),
        span("मदद ", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT),
        span("Tamil ", VokieLanguage.TA, SpanConfidence.LOW, SpanEvidence.LATIN_AMBIGUOUS),
        span("உதவி", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))

    // C. Scripts
    @Test fun tamilScriptDetection() = assertAnalysis("தமிழ்", VokieLanguage.EN, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("தமிழ்", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))
    @Test fun devanagariDetection() = assertAnalysis("मदद", VokieLanguage.EN, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("मदद", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT))
    @Test fun latinIsAmbiguousWithoutIndicContext() = assertAnalysis("please help", VokieLanguage.HI, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("please help", VokieLanguage.HI, SpanConfidence.LOW, SpanEvidence.LATIN_AMBIGUOUS))
    @Test fun unknownScriptUsesConfiguredFallback() = assertAnalysis("你好", VokieLanguage.HI, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("你好", VokieLanguage.HI, SpanConfidence.LOW, SpanEvidence.UNKNOWN_SCRIPT))

    // D. Romanized text
    @Test fun romanizedHindiStaysHindi() = assertAnalysis("mujhe help chahiye", VokieLanguage.HI, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("mujhe help chahiye", VokieLanguage.HI, SpanConfidence.LOW, SpanEvidence.LATIN_AMBIGUOUS))
    @Test fun romanizedTamilStaysTamil() = assertAnalysis("naan help venum", VokieLanguage.TA, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("naan help venum", VokieLanguage.TA, SpanConfidence.LOW, SpanEvidence.LATIN_AMBIGUOUS))
    @Test fun romanizedMixedStaysConfiguredLanguage() = assertAnalysis("mujhe rescue team chahiye", VokieLanguage.HI, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("mujhe rescue team chahiye", VokieLanguage.HI, SpanConfidence.LOW, SpanEvidence.LATIN_AMBIGUOUS))

    // E. Narrow rescue-term policy
    @Test fun oxygenInHindiIsCandidate() = assertAnalysis("oxygen चाहिए", VokieLanguage.HI, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("oxygen ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("चाहिए", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT))
    @Test fun helpInTamilIsCandidate() = assertAnalysis("help வேண்டும்", VokieLanguage.TA, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("help ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("வேண்டும்", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))
    @Test fun rescueTeamIsOneCandidateSpan() = assertAnalysis("rescue team வந்துட்டாங்க", VokieLanguage.TA, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("rescue team ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("வந்துட்டாங்க", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))
    @Test fun batteryInTamilIsCandidate() = assertAnalysis("battery வேண்டும்", VokieLanguage.TA, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("battery ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("வேண்டும்", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))
    @Test fun phoneInHindiDoesNotReclassifyTheHindiSentence() = assertAnalysis("मेरा phone काम नहीं कर रहा है", VokieLanguage.HI, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("मेरा ", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT),
        span("phone ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("काम नहीं कर रहा है", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT))
    @Test fun emergencyAssistanceIsOneEnglishPhraseInTamilContext() = assertAnalysis("எனக்கு emergency assistance வேண்டும்", VokieLanguage.TA, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("எனக்கு ", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT),
        span("emergency assistance ", VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM),
        span("வேண்டும்", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))

    // F. Safety
    @Test fun emptyTextHasNoSpans() = assertAnalysis("", VokieLanguage.HI, CodeSwitchAnalysisState.NO_SWITCH)
    @Test fun whitespaceOnlyIsPreserved() = assertAnalysis("   ", VokieLanguage.HI, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("   ", VokieLanguage.HI, SpanConfidence.LOW, SpanEvidence.CONFIGURED_LANGUAGE))
    @Test fun punctuationOnlyIsPreserved() = assertAnalysis("!?…", VokieLanguage.TA, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("!?…", VokieLanguage.TA, SpanConfidence.LOW, SpanEvidence.CONFIGURED_LANGUAGE))
    @Test fun excessiveSpansFallsBackWithoutLoss() {
        val text = (1..25).joinToString("") { if (it % 2 == 0) "अ" else "அ" }
        assertAnalysis(text, VokieLanguage.HI, CodeSwitchAnalysisState.FALLBACK,
            span(text, VokieLanguage.HI, SpanConfidence.LOW, SpanEvidence.CONFIGURED_LANGUAGE))
    }
    @Test fun emojiUsesConfiguredFallback() = assertAnalysis("🆘", VokieLanguage.HI, CodeSwitchAnalysisState.LOW_CONFIDENCE,
        span("🆘", VokieLanguage.HI, SpanConfidence.LOW, SpanEvidence.UNKNOWN_SCRIPT))
    @Test fun repeatedSwitchesBelowCapAreRetained() = assertAnalysis("अஅअஅ", VokieLanguage.HI, CodeSwitchAnalysisState.SWITCH_DETECTED,
        span("अ", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT),
        span("அ", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT),
        span("अ", VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT),
        span("அ", VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT))

    // G. Explicit invariants
    @Test fun reconstructionIsExact() {
        val text = "मुझे, help! வேண்டும் 🆘"
        assertEquals(text, analyze(text, VokieLanguage.HI).segments.joinToString("") { it.text })
    }
    @Test fun spanOrderFollowsSourceOrder() {
        val result = analyze("अ help அ", VokieLanguage.HI)
        assertEquals(listOf("अ ", "help ", "அ"), result.segments.map { it.text })
    }
    @Test fun noUnknownLanguageIsFabricated() {
        val result = analyze("你好", VokieLanguage.TA)
        assertTrue(result.segments.all { it.language == VokieLanguage.TA })
        assertEquals(SpanEvidence.UNKNOWN_SCRIPT, result.segments.single().evidence)
    }
    @Test fun repeatedAnalysisIsDeterministic() {
        val first = analyze("मुझे help வேண்டும்", VokieLanguage.HI)
        assertEquals(first, analyze("मुझे help வேண்டும்", VokieLanguage.HI))
    }
    @Test fun configuredLanguageAlwaysRemainsFallbackForAmbiguousText() {
        val result = analyze("location update", VokieLanguage.TA)
        assertEquals(VokieLanguage.TA, result.segments.single().language)
        assertEquals(SpanEvidence.LATIN_AMBIGUOUS, result.segments.single().evidence)
    }
}
