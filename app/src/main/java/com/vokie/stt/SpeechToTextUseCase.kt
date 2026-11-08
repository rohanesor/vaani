package com.vokie.stt

import kotlinx.coroutines.flow.StateFlow

/** UI-facing offline STT boundary. Transport remains text-only and absent here. */
class SpeechToTextUseCase(
    private val engine: SttEngine,
    @Suppress("unused") private val languagePreferences: SttLanguagePreferences,
) {
    val status: StateFlow<SttStatus> = engine.status

    suspend fun initialize() = engine.initialize()
    suspend fun start(language: SttLanguage, preferredLanguage: UserLanguageProfile, finalizeOnVad: Boolean = true) =
        engine.start(language, preferredLanguage, finalizeOnVad)
    suspend fun stop() = engine.stop()
}
