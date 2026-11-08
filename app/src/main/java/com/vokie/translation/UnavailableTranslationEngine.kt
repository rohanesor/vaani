package com.vokie.translation

import com.vokie.domain.model.VokieLanguage

/** Explicit legal/model gate; it never substitutes cloud or English translation. */
class UnavailableTranslationEngine : TranslationEngine {
    override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage) =
        TranslationResult(text, sourceLanguage, targetLanguage, status = TranslationStatus.UNAVAILABLE,
            error = "No approved offline translation backend is installed.")
}
