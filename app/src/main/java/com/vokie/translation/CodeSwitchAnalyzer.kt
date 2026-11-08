package com.vokie.translation

import com.vokie.domain.model.VokieLanguage

/**
 * Deterministic, local script/span analysis for EN, HI, and TA transcript text.
 *
 * This is not a general-purpose language identifier. A configured primary language is
 * authoritative except for unambiguous Tamil or Devanagari script, or a small allowlist
 * of isolated Latin-script English terms inside an otherwise Indic-script utterance.
 * No model, Android API, persistence, or network is used.
 */
enum class CodeSwitchAnalysisState { NO_SWITCH, SWITCH_DETECTED, LOW_CONFIDENCE, FALLBACK }
enum class SpanConfidence { HIGH, MEDIUM, LOW }
enum class SpanEvidence {
    CONFIGURED_LANGUAGE,
    TAMIL_SCRIPT,
    DEVANAGARI_SCRIPT,
    LATIN_RESCUE_TERM,
    LATIN_AMBIGUOUS,
    UNKNOWN_SCRIPT,
    MIXED_SCRIPT,
}

data class LanguageSpan(
    val text: String,
    /** A source-language candidate, always bounded to Vokie's configured language set. */
    val language: VokieLanguage,
    val confidence: SpanConfidence,
    val evidence: SpanEvidence,
)

data class UtteranceAnalysis(
    val originalText: String,
    val primaryLanguage: VokieLanguage,
    val segments: List<LanguageSpan>,
    val state: CodeSwitchAnalysisState,
) {
    init {
        require(segments.joinToString(separator = "") { it.text } == originalText) {
            "Language spans must reconstruct original text exactly"
        }
    }
}

class CodeSwitchAnalyzer(private val maximumSpans: Int = DEFAULT_MAXIMUM_SPANS) {
    init { require(maximumSpans > 0) }

    fun analyze(text: String, primaryLanguage: VokieLanguage): UtteranceAnalysis {
        if (text.isEmpty()) return UtteranceAnalysis(text, primaryLanguage, emptyList(), CodeSwitchAnalysisState.NO_SWITCH)

        val runs = scriptRuns(text)
        val containsIndicScript = runs.any { it.kind == ScriptKind.TAMIL || it.kind == ScriptKind.DEVANAGARI }
        val classified = runs.map { run -> classify(run, primaryLanguage, containsIndicScript) }
        val merged = mergeAdjacent(classified)
        if (merged.size > maximumSpans) return fallback(text, primaryLanguage)

        val hasSwitch = merged.any { it.language != primaryLanguage && it.confidence != SpanConfidence.LOW }
        val hasLowEvidence = merged.any { it.confidence == SpanConfidence.LOW }
        val state = when {
            hasSwitch -> CodeSwitchAnalysisState.SWITCH_DETECTED
            hasLowEvidence -> CodeSwitchAnalysisState.LOW_CONFIDENCE
            else -> CodeSwitchAnalysisState.NO_SWITCH
        }
        return UtteranceAnalysis(text, primaryLanguage, merged, state)
    }

    private fun fallback(text: String, primary: VokieLanguage) = UtteranceAnalysis(
        originalText = text,
        primaryLanguage = primary,
        segments = listOf(LanguageSpan(text, primary, SpanConfidence.LOW, SpanEvidence.CONFIGURED_LANGUAGE)),
        state = CodeSwitchAnalysisState.FALLBACK,
    )

    private fun classify(run: Run, primary: VokieLanguage, containsIndicScript: Boolean): LanguageSpan = when (run.kind) {
        ScriptKind.TAMIL -> LanguageSpan(run.text, VokieLanguage.TA, SpanConfidence.HIGH, SpanEvidence.TAMIL_SCRIPT)
        ScriptKind.DEVANAGARI -> LanguageSpan(run.text, VokieLanguage.HI, SpanConfidence.HIGH, SpanEvidence.DEVANAGARI_SCRIPT)
        ScriptKind.LATIN -> {
            // Latin text is ambiguous when it is the whole utterance: it can be English,
            // Romanized Hindi, or Romanized Tamil. Only a small set of common English
            // communication/emergency terms embedded in Indic-script utterance are candidates.
            if (containsIndicScript && run.text.trim().trim { !it.isLetter() }.lowercase() in RESCUE_TERMS) {
                LanguageSpan(run.text, VokieLanguage.EN, SpanConfidence.MEDIUM, SpanEvidence.LATIN_RESCUE_TERM)
            } else {
                LanguageSpan(run.text, primary, SpanConfidence.LOW, SpanEvidence.LATIN_AMBIGUOUS)
            }
        }
        ScriptKind.OTHER -> LanguageSpan(run.text, primary, SpanConfidence.LOW, SpanEvidence.UNKNOWN_SCRIPT)
        ScriptKind.SEPARATOR -> LanguageSpan(run.text, primary, SpanConfidence.LOW, SpanEvidence.CONFIGURED_LANGUAGE)
    }

    /**
     * Makes runs only at Unicode-script transitions; separators remain with the preceding
     * run when possible. Thus concatenating output is byte-for-byte equal to input.
     */
    private fun scriptRuns(text: String): List<Run> {
        val raw = mutableListOf<Run>()
        val builder = StringBuilder()
        var active: ScriptKind? = null
        fun flush() {
            if (builder.isNotEmpty()) {
                raw += Run(builder.toString(), requireNotNull(active))
                builder.clear()
            }
        }
        text.forEach { character ->
            val kind = kindOf(character)
            if (active != null && kind != active) flush()
            active = kind
            builder.append(character)
        }
        flush()

        // Whitespace and punctuation do not create a language boundary of their own.
        val attached = mutableListOf<Run>()
        raw.forEach { run ->
            if (run.kind == ScriptKind.SEPARATOR && attached.isNotEmpty()) {
                val prior = attached.removeLast()
                attached += prior.copy(text = prior.text + run.text)
            } else {
                attached += run
            }
        }
        return attached
    }

    private fun mergeAdjacent(spans: List<LanguageSpan>): List<LanguageSpan> {
        val merged = mutableListOf<LanguageSpan>()
        spans.forEach { span ->
            val prior = merged.lastOrNull()
            if (prior != null && prior.language == span.language && prior.confidence == span.confidence && prior.evidence == span.evidence) {
                merged[merged.lastIndex] = prior.copy(text = prior.text + span.text)
            } else {
                merged += span
            }
        }
        return merged
    }

    private fun kindOf(character: Char): ScriptKind = when {
        character in '\u0B80'..'\u0BFF' -> ScriptKind.TAMIL
        character in '\u0900'..'\u097F' -> ScriptKind.DEVANAGARI
        character.isLetter() && character.code <= 0x007F -> ScriptKind.LATIN
        character.isWhitespace() || character.isPunctuation() || character.isDigit() -> ScriptKind.SEPARATOR
        else -> ScriptKind.OTHER
    }

    private data class Run(val text: String, val kind: ScriptKind)
    private enum class ScriptKind { TAMIL, DEVANAGARI, LATIN, SEPARATOR, OTHER }

    private companion object {
        const val DEFAULT_MAXIMUM_SPANS = 24
        // Deliberately narrow, testable contextual vocabulary; this is not general
        // English or Romanized-language detection. It covers common offline-communication,
        // technical, medical, location, and emergency words used inside Indic text.
        val RESCUE_TERMS = setOf(
            "help", "water", "phone", "location", "message", "call", "device",
            "battery", "charger", "signal", "network", "emergency", "assistance",
            "oxygen", "ambulance", "hospital", "medical", "rescue", "team",
        )
    }
}

private fun Char.isPunctuation(): Boolean = when (Character.getType(this)) {
    Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt() -> true
    else -> false
}
