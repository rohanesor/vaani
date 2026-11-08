package com.vokie.translation

import com.vokie.domain.model.VokieLanguage

interface TranslationEngine {
    suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult
}
