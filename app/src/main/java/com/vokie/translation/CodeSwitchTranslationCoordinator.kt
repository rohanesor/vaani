package com.vokie.translation

import com.vokie.communication.VokieLog
import com.vokie.domain.model.VokieLanguage

/** Routing detail is receiver-local diagnostic state; it is never persisted or transmitted. */
enum class CodeSwitchTranslationRoute { WHOLE_MESSAGE, SEGMENTED, WHOLE_MESSAGE_FALLBACK, PRESERVED }

data class CodeSwitchTranslationOutcome(
    val text: String?,
    val status: TranslationStatus,
    val route: CodeSwitchTranslationRoute,
    val usedCodeSwitching: Boolean,
    val isPartial: Boolean = false,
    val fallbackUsed: Boolean = false,
    val translationCalls: Int = 0,
    val error: String? = null,
)

/**
 * Receiver-local code-switch routing. It does not mutate a Message or wire metadata.
 * Ordinary text always delegates unchanged to [TranslationEngine]'s whole-message path.
 */
class CodeSwitchTranslationCoordinator(
    private val engine: TranslationEngine,
    private val analyzer: CodeSwitchAnalyzer = CodeSwitchAnalyzer(),
    private val maximumTranslationCalls: Int = DEFAULT_MAXIMUM_TRANSLATION_CALLS,
) {
    init { require(maximumTranslationCalls > 0) }

    suspend fun translate(text: String, primaryLanguage: VokieLanguage, targetLanguage: VokieLanguage): CodeSwitchTranslationOutcome {
        if (text.isEmpty()) return CodeSwitchTranslationOutcome(text, TranslationStatus.PASSTHROUGH, CodeSwitchTranslationRoute.PRESERVED, false)
        val analysis = analyzer.analyze(text, primaryLanguage)
        val activateSegments = analysis.state == CodeSwitchAnalysisState.SWITCH_DETECTED &&
            analysis.segments.any { it.language != primaryLanguage && it.confidence != SpanConfidence.LOW }
        VokieLog.translation("CODE_SWITCH_ANALYSIS state=${analysis.state} spans=${analysis.segments.size} segmented=$activateSegments")
        if (!activateSegments) return wholeMessage(text, primaryLanguage, targetLanguage)

        val groups = groupsFor(analysis.segments, targetLanguage)
        if (groups.count { it.translate } > maximumTranslationCalls) return wholeMessageFallback(text, primaryLanguage, targetLanguage, 0, "translation-call cap")
        if (groups.none { it.translate }) return wholeMessage(text, primaryLanguage, targetLanguage)

        var calls = 0
        var successes = 0
        var failures = 0
        val output = StringBuilder(text.length)
        groups.forEach { group ->
            if (!group.translate) {
                output.append(group.text)
                return@forEach
            }
            val (leading, core, trailing) = splitWhitespaceEnvelope(group.text)
            if (core.isEmpty()) {
                output.append(group.text)
                return@forEach
            }
            calls++
            VokieLog.translation("SEGMENT_TRANSLATION_START source=${group.language.code} target=${targetLanguage.code} index=$calls")
            when (val result = engine.translate(core, group.language, targetLanguage)) {
                else -> when (result.status) {
                    TranslationStatus.TRANSLATED, TranslationStatus.PASSTHROUGH -> {
                        output.append(leading).append(result.translatedText).append(trailing)
                        successes++
                        VokieLog.translation("SEGMENT_TRANSLATION_SUCCESS source=${group.language.code} target=${targetLanguage.code} index=$calls")
                    }
                    TranslationStatus.UNAVAILABLE, TranslationStatus.FAILED -> {
                        output.append(group.text)
                        failures++
                        VokieLog.translation("SEGMENT_TRANSLATION_PARTIAL source=${group.language.code} target=${targetLanguage.code} index=$calls status=${result.status}")
                    }
                }
            }
        }
        if (successes == 0) return wholeMessageFallback(text, primaryLanguage, targetLanguage, calls, "all segment translations failed")
        return CodeSwitchTranslationOutcome(
            text = output.toString(),
            status = TranslationStatus.TRANSLATED,
            route = CodeSwitchTranslationRoute.SEGMENTED,
            usedCodeSwitching = true,
            isPartial = failures > 0,
            translationCalls = calls,
            error = if (failures > 0) "$failures segment translation(s) failed" else null,
        )
    }

    private suspend fun wholeMessage(text: String, source: VokieLanguage, target: VokieLanguage): CodeSwitchTranslationOutcome {
        // Preserve the receiver coordinator's pre-P2 same-language bypass without even
        // invoking a custom TranslationEngine implementation.
        if (source == target) return CodeSwitchTranslationOutcome(text, TranslationStatus.PASSTHROUGH, CodeSwitchTranslationRoute.WHOLE_MESSAGE, false)
        val result = engine.translate(text, source, target)
        return CodeSwitchTranslationOutcome(result.translatedText, result.status, CodeSwitchTranslationRoute.WHOLE_MESSAGE, false, translationCalls = 1, error = result.error)
    }

    private suspend fun wholeMessageFallback(text: String, source: VokieLanguage, target: VokieLanguage, calls: Int, reason: String): CodeSwitchTranslationOutcome {
        VokieLog.translation("SEGMENT_TRANSLATION_FALLBACK reason=$reason source=${source.code} target=${target.code}")
        val result = engine.translate(text, source, target)
        return CodeSwitchTranslationOutcome(
            text = result.translatedText ?: text,
            status = result.status,
            route = CodeSwitchTranslationRoute.WHOLE_MESSAGE_FALLBACK,
            usedCodeSwitching = true,
            fallbackUsed = true,
            translationCalls = calls + 1,
            error = result.error ?: reason,
        )
    }

    private fun groupsFor(spans: List<LanguageSpan>, target: VokieLanguage): List<Group> {
        val groups = mutableListOf<Group>()
        spans.forEach { span ->
            val translate = span.language != target && span.confidence != SpanConfidence.LOW && span.text.any { !it.isWhitespace() }
            val prior = groups.lastOrNull()
            if (prior != null && prior.translate == translate && prior.language == span.language) {
                groups[groups.lastIndex] = prior.copy(text = prior.text + span.text)
            } else {
                groups += Group(span.text, span.language, translate)
            }
        }
        return groups
    }

    /** Retains original edge whitespace because the existing CT2 engine trims native output. */
    private fun splitWhitespaceEnvelope(value: String): Triple<String, String, String> {
        val first = value.indexOfFirst { !it.isWhitespace() }
        if (first == -1) return Triple(value, "", "")
        val last = value.indexOfLast { !it.isWhitespace() }
        return Triple(value.substring(0, first), value.substring(first, last + 1), value.substring(last + 1))
    }

    private data class Group(val text: String, val language: VokieLanguage, val translate: Boolean)
    private companion object { const val DEFAULT_MAXIMUM_TRANSLATION_CALLS = 12 }
}
