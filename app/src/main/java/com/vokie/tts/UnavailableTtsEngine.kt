package com.vokie.tts

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Production-safe placeholder while no legally approved bundled TTS artifact exists. */
class UnavailableTtsEngine : TtsEngine, UnifiedTtsEngine {
    private val _status = MutableStateFlow(TtsStatus(TtsState.MODEL_MISSING))
    override val status: StateFlow<TtsStatus> = _status.asStateFlow()

    private fun unavailable(language: TtsLanguage): Nothing = throw TtsException(
        TtsErrorCode.UNSUPPORTED_LANGUAGE,
        "Offline speech is unavailable for ${language.nativeName}. Install an approved bundled voice package."
    )

    override suspend fun initialize(language: TtsLanguage) { unavailable(language) }
    override suspend fun synthesize(text: String, language: TtsLanguage, speed: Float): Pair<AudioBuffer, TtsResult> = unavailable(language)
    override suspend fun play(audio: AudioBuffer, emergency: Boolean) = throw TtsException(TtsErrorCode.MODEL_MISSING, "No approved offline TTS backend is installed.")
    override suspend fun stop() = Unit
    override fun release() = Unit

    override suspend fun synthesize(text: String, languageCode: String, voice: String?): UnifiedTtsSynthesis =
        UnifiedTtsSynthesis.Failure(TtsFailure(TtsErrorCode.UNSUPPORTED_LANGUAGE, "No approved offline TTS backend is installed for $languageCode."))
}
