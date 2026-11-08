package com.vokie.translation

import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class ReceiverPresentationState { UNTRANSLATED, TRANSLATING, TRANSLATED, TRANSLATION_UNAVAILABLE, TRANSLATION_FAILED, INVALID_SOURCE_LANGUAGE, INVALID_TARGET_LANGUAGE }
data class ReceiverPresentationOutcome(val presentation: ReceiverPresentation, val isNew: Boolean)

data class ReceiverPresentation(
    val messageId: String,
    val sourceText: String,
    val sourceLanguage: VokieLanguage?,
    val targetLanguage: VokieLanguage?,
    val displayText: String? = null,
    val state: ReceiverPresentationState,
    val error: String? = null,
    /** Receiver-local code-switch routing detail; never persisted or transmitted. */
    val codeSwitchRoute: CodeSwitchTranslationRoute? = null,
    val isPartial: Boolean = false,
    val fallbackUsed: Boolean = false,
) {
    val ttsText: String? get() = displayText
    val ttsLanguage: VokieLanguage? get() = targetLanguage
    val codeSwitchIndicator: String? get() = when {
        isPartial -> "Partially translated"
        fallbackUsed -> "Using fallback translation"
        codeSwitchRoute == CodeSwitchTranslationRoute.SEGMENTED -> "Mixed language normalized"
        else -> null
    }

    /** The only text/language pair permitted to enter the existing TTS queue. */
    fun ttsHandoff(): ReceiverTtsHandoff? = displayText
        ?.takeIf { state == ReceiverPresentationState.TRANSLATED && it.isNotBlank() }
        ?.let { text -> targetLanguage?.let { ReceiverTtsHandoff(text, it) } }
}

data class ReceiverTtsHandoff(val text: String, val language: VokieLanguage)

/** Receiver-local cache boundary. Source packets and source text are never mutated. */
class ReceiverTranslationCoordinator(private val engine: TranslationEngine) {
    // Cache and StateFlow publication are one receiver-local critical section. This
    // complements the CT2 engine mutex and prevents concurrent peer arrivals from
    // translating/enqueuing the same message presentation twice.
    private val presentationLock = Mutex()
    private val codeSwitchTranslation = CodeSwitchTranslationCoordinator(engine)
    private val cache = mutableMapOf<Key, ReceiverPresentation>()
    private val _presentations = MutableStateFlow<Map<String, ReceiverPresentation>>(emptyMap())
    val presentations: StateFlow<Map<String, ReceiverPresentation>> = _presentations.asStateFlow()
    private data class Key(val messageId: String, val target: VokieLanguage)

    suspend fun presentOnce(messageId: String, sourceText: String, sourceLanguage: VokieLanguage?, targetLanguage: VokieLanguage?): ReceiverPresentationOutcome = presentationLock.withLock {
        if (messageId.isBlank() || sourceText.isBlank() || sourceLanguage == null) return@withLock ReceiverPresentationOutcome(ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, state = ReceiverPresentationState.INVALID_SOURCE_LANGUAGE), true)
        if (targetLanguage == null) return@withLock ReceiverPresentationOutcome(ReceiverPresentation(messageId, sourceText, sourceLanguage, null, state = ReceiverPresentationState.INVALID_TARGET_LANGUAGE), true)
        val key = Key(messageId, targetLanguage)
        cache[key]?.let { return@withLock ReceiverPresentationOutcome(it, false) }
        val result = codeSwitchTranslation.translate(sourceText, sourceLanguage, targetLanguage)
        val presentation = when (result.status) {
            TranslationStatus.TRANSLATED, TranslationStatus.PASSTHROUGH -> ReceiverPresentation(
                messageId, sourceText, sourceLanguage, targetLanguage, result.text, ReceiverPresentationState.TRANSLATED, result.error,
                codeSwitchRoute = result.route.takeIf { result.usedCodeSwitching }, isPartial = result.isPartial, fallbackUsed = result.fallbackUsed,
            )
            TranslationStatus.UNAVAILABLE -> ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, state = ReceiverPresentationState.TRANSLATION_UNAVAILABLE, error = result.error)
            TranslationStatus.FAILED -> ReceiverPresentation(messageId, sourceText, sourceLanguage, targetLanguage, state = ReceiverPresentationState.TRANSLATION_FAILED, error = result.error)
        }
        when {
            presentation.isPartial -> com.vokie.communication.VokieLog.translation("PRESENTATION_PARTIAL messageId=$messageId target=${targetLanguage.code}")
            presentation.fallbackUsed -> com.vokie.communication.VokieLog.translation("PRESENTATION_FALLBACK messageId=$messageId target=${targetLanguage.code}")
            presentation.codeSwitchRoute == CodeSwitchTranslationRoute.SEGMENTED -> com.vokie.communication.VokieLog.translation("PRESENTATION_CODE_SWITCH messageId=$messageId target=${targetLanguage.code}")
        }
        cache[key] = presentation
        _presentations.value = _presentations.value + ("${messageId}|${targetLanguage.code}" to presentation)
        ReceiverPresentationOutcome(presentation, true)
    }

    suspend fun present(messageId: String, sourceText: String, sourceLanguage: VokieLanguage?, targetLanguage: VokieLanguage?) = presentOnce(messageId, sourceText, sourceLanguage, targetLanguage).presentation

    suspend fun translate(messageId: String, sourceText: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
        val presentation = present(messageId, sourceText, sourceLanguage, targetLanguage)
        return when (presentation.state) {
            ReceiverPresentationState.TRANSLATED -> TranslationResult(sourceText, sourceLanguage, targetLanguage, presentation.displayText, if (sourceLanguage == targetLanguage && presentation.codeSwitchRoute == null) TranslationStatus.PASSTHROUGH else TranslationStatus.TRANSLATED)
            ReceiverPresentationState.TRANSLATION_UNAVAILABLE -> TranslationResult(sourceText, sourceLanguage, targetLanguage, status = TranslationStatus.UNAVAILABLE, error = presentation.error)
            else -> TranslationResult(sourceText, sourceLanguage, targetLanguage, status = TranslationStatus.FAILED, error = presentation.error)
        }
    }
}
