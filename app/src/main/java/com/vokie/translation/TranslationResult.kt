package com.vokie.translation

import com.vokie.domain.model.VokieLanguage

enum class TranslationStatus { TRANSLATED, PASSTHROUGH, UNAVAILABLE, FAILED }

data class TranslationResult(
    val originalText: String,
    val sourceLanguage: VokieLanguage,
    val targetLanguage: VokieLanguage,
    val translatedText: String? = null,
    val status: TranslationStatus,
    val error: String? = null,
) {
    init {
        require(originalText.isNotBlank())
        require((status == TranslationStatus.TRANSLATED || status == TranslationStatus.PASSTHROUGH) == (translatedText != null))
    }
}
