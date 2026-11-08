package com.vokie.stt

import com.vokie.communication.PacketV2
import com.vokie.domain.model.Message
import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageRoutingTest {
    @Test fun selectedInputLanguageIsAlwaysTheExplicitWhisperLanguage() {
        assertEquals(SttLanguage.ENGLISH, resolveProductionSttLanguage(UserLanguageProfile(VokieLanguage.EN, VokieLanguage.HI)))
        assertEquals(SttLanguage.HINDI, resolveProductionSttLanguage(UserLanguageProfile(VokieLanguage.HI, VokieLanguage.TA)))
        assertEquals(SttLanguage.TAMIL, resolveProductionSttLanguage(UserLanguageProfile(VokieLanguage.TA, VokieLanguage.HI)))
    }

    @Test fun allTenSihLanguagesResolveForStt() {
        assertEquals(SttLanguage.ENGLISH, SttLanguage.fromWhisperCode("en"))
        assertEquals(SttLanguage.HINDI, SttLanguage.fromWhisperCode("hi"))
        assertEquals(SttLanguage.GUJARATI, SttLanguage.fromWhisperCode("gu"))
        assertEquals(SttLanguage.MARATHI, SttLanguage.fromWhisperCode("mr"))
        assertEquals(SttLanguage.KANNADA, SttLanguage.fromWhisperCode("kn"))
        assertEquals(SttLanguage.MALAYALAM, SttLanguage.fromWhisperCode("ml"))
        assertEquals(SttLanguage.TAMIL, SttLanguage.fromWhisperCode("ta"))
        assertEquals(SttLanguage.TELUGU, SttLanguage.fromWhisperCode("te"))
        assertEquals(SttLanguage.ODIA, SttLanguage.fromWhisperCode("or"))
        assertEquals(SttLanguage.BENGALI, SttLanguage.fromWhisperCode("bn"))
        assertNull(SttLanguage.fromWhisperCode("auto"))
        assertNull(SttLanguage.fromWhisperCode("fr"))
        assertEquals(10, SttLanguage.entries.size)
    }

    @Test fun allTenSihLanguagesMapFromMessageCode() {
        VokieLanguage.entries.forEach { language ->
            val stt = SttLanguage.fromMessageCode(language.code)
            assertNotNull("SttLanguage missing for ${language.code}", stt)
            assertEquals(language, stt!!.messageLanguage)
        }
    }

    @Test fun allTenSihLanguagesHaveProfiles() {
        assertEquals(10, UserLanguageProfile.supportedLanguages.size)
        assertEquals(10, UserLanguageProfile.onboardingOptions.size)
        VokieLanguage.entries.forEach { language ->
            val profile = UserLanguageProfile.fromCodes(language.code, language.code)
            assertNotNull("UserLanguageProfile missing for ${language.code}", profile)
            assertEquals(language, profile!!.preferredInputLanguage)
        }
    }

    @Test fun packetPreservesExplicitSenderLanguage() {
        val sender = Message("11111111-1111-1111-1111-111111111111", "sender", 1_700_000_000_000, "எனக்கு உதவி தேவை", VokieLanguage.TA.code)
        val decoded = PacketV2.decode(PacketV2.fromMessage(sender).single()) as PacketV2.Decoded.MessagePacket
        assertEquals(VokieLanguage.TA.code, decoded.packet.languageCode)
    }

    @Test fun profileInputSttLanguageResolvesForAllTenLanguages() {
        VokieLanguage.entries.forEach { language ->
            val profile = UserLanguageProfile(language, language)
            assertNotNull("inputSttLanguage failed for ${language.code}", profile.inputSttLanguage)
            assertEquals(language, profile.inputSttLanguage.messageLanguage)
        }
    }
}
