package com.vokie.tts

/**
 * Candidate-neutral contract for the eventual automatic multilingual TTS system.
 *
 * Phase 2B deliberately does not wire this contract into production playback or select a
 * model backend. A future implementation must route [languageCode] internally and return
 * [TtsErrorCode.UNSUPPORTED_LANGUAGE] rather than substituting another language.
 */
interface UnifiedTtsEngine {
    suspend fun synthesize(
        text: String,
        languageCode: String,
        voice: String? = null,
    ): UnifiedTtsSynthesis
}

sealed interface UnifiedTtsSynthesis {
    data class Success(
        val audio: AudioBuffer,
        val result: TtsResult,
        val backendId: String,
    ) : UnifiedTtsSynthesis

    data class Failure(val failure: TtsFailure) : UnifiedTtsSynthesis
}
