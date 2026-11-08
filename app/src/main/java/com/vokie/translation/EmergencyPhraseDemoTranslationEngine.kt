package com.vokie.translation

import com.vokie.domain.model.VokieLanguage

/**
 * Deterministic, offline demonstrator for a deliberately small emergency phrase set.
 * It is not an ML translation model and unknown input is explicitly unavailable.
 */
class EmergencyPhraseDemoTranslationEngine : TranslationEngine {
    override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
        if (sourceLanguage == targetLanguage) return TranslationResult(text, sourceLanguage, targetLanguage, text, TranslationStatus.PASSTHROUGH)
        val translated = phrases[Key(normalize(text), sourceLanguage, targetLanguage)]
        return if (translated == null) TranslationResult(text, sourceLanguage, targetLanguage, status = TranslationStatus.UNAVAILABLE,
            error = "Offline Emergency Phrase Demo has no translation for this message.")
        else TranslationResult(text, sourceLanguage, targetLanguage, translated, TranslationStatus.TRANSLATED)
    }

    private fun normalize(text: String) = text.trim().removeSuffix(".").removeSuffix("।").uppercase()
    private data class Key(val text: String, val source: VokieLanguage, val target: VokieLanguage)
    private companion object {
        val phrases = listOf(
            Triple("I NEED HELP", "मुझे मदद चाहिए", "எனக்கு உதவி தேவை"),
            Triple("I AM LOST", "मैं रास्ता भटक गया हूँ", "நான் வழி தவறிவிட்டேன்"),
            Triple("WHERE ARE YOU", "आप कहाँ हैं", "நீங்கள் எங்கே இருக்கிறீர்கள்"),
            Triple("I AM INJURED", "मैं घायल हूँ", "எனக்கு காயம் ஏற்பட்டுள்ளது"),
            Triple("SEND HELP", "मदद भेजिए", "உதவி அனுப்புங்கள்"),
            Triple("I NEED WATER", "मुझे पानी चाहिए", "எனக்கு தண்ணீர் தேவை"),
        ).flatMap { (en, hi, ta) -> listOf(
            Key(en, VokieLanguage.EN, VokieLanguage.HI) to hi, Key(en, VokieLanguage.EN, VokieLanguage.TA) to ta,
            Key(hi, VokieLanguage.HI, VokieLanguage.EN) to en, Key(hi, VokieLanguage.HI, VokieLanguage.TA) to ta,
            Key(ta, VokieLanguage.TA, VokieLanguage.EN) to en, Key(ta, VokieLanguage.TA, VokieLanguage.HI) to hi,
        ) }.toMap()
    }
}
