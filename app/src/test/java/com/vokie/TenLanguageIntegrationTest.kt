package com.vokie

import com.vokie.domain.model.VokieLanguage
import com.vokie.stt.SttLanguage
import com.vokie.stt.UserLanguageProfile
import com.vokie.tts.TtsLanguage
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive test for SIH 10-language integration.
 * Verifies that all language enums and mappings are complete.
 */
class TenLanguageIntegrationTest {

    /** Expected language configuration for SIH target. */
    private val sihLanguages = listOf(
        LanguageSpec("EN", "English", "en", "eng"),
        LanguageSpec("HI", "Hindi", "hi", "hin"),
        LanguageSpec("GU", "Gujarati", "gu", "guj"),
        LanguageSpec("MR", "Marathi", "mr", "mar"),
        LanguageSpec("KN", "Kannada", "kn", "kan"),
        LanguageSpec("ML", "Malayalam", "ml", "mal"),
        LanguageSpec("TA", "Tamil", "ta", "tam"),
        LanguageSpec("TE", "Telugu", "te", "tel"),
        LanguageSpec("OR", "Odia", "or", "ory"),
        LanguageSpec("BN", "Bengali", "bn", "ben"),
    )

    data class LanguageSpec(
        val vokieCode: String,
        val displayName: String,
        val whisperCode: String,
        val iso6393: String,
    )

    @Test
    fun vokieLanguageHasAllTenEntries() {
        assertEquals("VokieLanguage must have exactly 10 entries", 10, VokieLanguage.entries.size)
        sihLanguages.forEach { spec ->
            val language = VokieLanguage.fromCode(spec.vokieCode)
            assertNotNull("VokieLanguage missing: ${spec.vokieCode}", language)
            assertEquals(spec.displayName, language!!.displayName)
        }
    }

    @Test
    fun sttLanguageHasAllTenEntries() {
        assertEquals("SttLanguage must have exactly 10 entries", 10, SttLanguage.entries.size)
        sihLanguages.forEach { spec ->
            val stt = SttLanguage.fromWhisperCode(spec.whisperCode)
            assertNotNull("SttLanguage missing whisper code: ${spec.whisperCode}", stt)
            assertEquals(spec.vokieCode, stt!!.messageLanguage.code)
            
            val fromMessage = SttLanguage.fromMessageCode(spec.vokieCode)
            assertNotNull("SttLanguage missing message code: ${spec.vokieCode}", fromMessage)
            assertEquals(stt, fromMessage)
        }
    }

    @Test
    fun ttsLanguageHasAllTenEntries() {
        assertEquals("TtsLanguage must have exactly 10 entries", 10, TtsLanguage.entries.size)
        sihLanguages.forEach { spec ->
            val tts = TtsLanguage.fromIso6393(spec.iso6393)
            assertNotNull("TtsLanguage missing ISO 639-3: ${spec.iso6393}", tts)
            assertEquals(spec.vokieCode, tts!!.messageLanguage.code)
            
            val fromMessage = TtsLanguage.fromMessageCode(spec.vokieCode)
            assertNotNull("TtsLanguage missing message code: ${spec.vokieCode}", fromMessage)
            assertEquals(tts, fromMessage)
        }
    }

    @Test
    fun userLanguageProfileSupportsAllTenLanguages() {
        assertEquals("supportedLanguages must have 10 entries", 10, UserLanguageProfile.supportedLanguages.size)
        assertEquals("onboardingOptions must have 10 entries", 10, UserLanguageProfile.onboardingOptions.size)

        VokieLanguage.entries.forEach { language ->
            assertTrue("${language.code} must be in supportedLanguages", 
                language in UserLanguageProfile.supportedLanguages)
            
            val profile = UserLanguageProfile.fromCodes(language.code, language.code)
            assertNotNull("UserLanguageProfile.fromCodes failed for ${language.code}", profile)
            assertEquals(language, profile!!.preferredInputLanguage)
            assertEquals(language, profile.preferredOutputLanguage)
        }
    }

    @Test
    fun inputSttLanguageResolvesForAllProfiles() {
        VokieLanguage.entries.forEach { language ->
            val profile = UserLanguageProfile(language, language)
            // This must not throw
            val stt = profile.inputSttLanguage
            assertEquals(language, stt.messageLanguage)
        }
    }

    @Test
    fun outputSttLanguageResolvesForAllProfiles() {
        VokieLanguage.entries.forEach { language ->
            val profile = UserLanguageProfile(language, language)
            // This must not throw
            val stt = profile.outputSttLanguage
            assertEquals(language, stt.messageLanguage)
        }
    }

    @Test
    fun crossLanguageProfilesWork() {
        // Test cross-language profiles (e.g., Hindi speaker understanding English)
        val profile = UserLanguageProfile(VokieLanguage.HI, VokieLanguage.EN)
        assertEquals(SttLanguage.HINDI, profile.inputSttLanguage)
        assertEquals(SttLanguage.ENGLISH, profile.outputSttLanguage)
    }

    @Test
    fun vokieLanguageCodeIsUppercase() {
        VokieLanguage.entries.forEach { language ->
            assertEquals(language.code, language.code.uppercase())
        }
    }

    @Test
    fun sttWhisperCodeIsLowercase() {
        SttLanguage.entries.forEach { language ->
            assertEquals(language.whisperCode, language.whisperCode.lowercase())
        }
    }

    @Test
    fun ttsIso6393IsLowercase() {
        TtsLanguage.entries.forEach { language ->
            assertEquals(language.iso6393, language.iso6393.lowercase())
        }
    }

    @Test
    fun allLanguagesHaveNativeName() {
        SttLanguage.entries.forEach { language ->
            assertTrue("SttLanguage ${language.name} nativeName is blank", language.nativeName.isNotBlank())
        }
        TtsLanguage.entries.forEach { language ->
            assertTrue("TtsLanguage ${language.name} nativeName is blank", language.nativeName.isNotBlank())
        }
    }

    @Test
    fun languageMappingsAreConsistent() {
        // Every VokieLanguage maps to exactly one SttLanguage and one TtsLanguage
        VokieLanguage.entries.forEach { vokie ->
            val stt = SttLanguage.fromMessageCode(vokie.code)
            val tts = TtsLanguage.fromMessageCode(vokie.code)
            assertNotNull("SttLanguage missing for ${vokie.code}", stt)
            assertNotNull("TtsLanguage missing for ${vokie.code}", tts)
            assertEquals("SttLanguage.messageLanguage mismatch for ${vokie.code}", vokie, stt!!.messageLanguage)
            assertEquals("TtsLanguage.messageLanguage mismatch for ${vokie.code}", vokie, tts!!.messageLanguage)
        }
    }

    @Test
    fun existingEnHiTaRoutingStillWorks() {
        // Regression test: existing EN/HI/TA paths must continue to work
        assertEquals(SttLanguage.ENGLISH, SttLanguage.fromWhisperCode("en"))
        assertEquals(SttLanguage.HINDI, SttLanguage.fromWhisperCode("hi"))
        assertEquals(SttLanguage.TAMIL, SttLanguage.fromWhisperCode("ta"))
        
        assertEquals(TtsLanguage.ENGLISH, TtsLanguage.fromIso6393("eng"))
        assertEquals(TtsLanguage.HINDI, TtsLanguage.fromIso6393("hin"))
        assertEquals(TtsLanguage.TAMIL, TtsLanguage.fromIso6393("tam"))
        
        assertEquals(VokieLanguage.EN, VokieLanguage.fromCode("EN"))
        assertEquals(VokieLanguage.HI, VokieLanguage.fromCode("HI"))
        assertEquals(VokieLanguage.TA, VokieLanguage.fromCode("TA"))
    }
}
