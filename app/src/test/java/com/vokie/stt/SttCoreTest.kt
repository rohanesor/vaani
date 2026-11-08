package com.vokie.stt

import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.*
import org.junit.Test

class SttCoreTest {
    @Test fun allTenSihLanguagesMapToWhisperAndMessageCodes() {
        val expected = mapOf(
            "en" to VokieLanguage.EN,
            "hi" to VokieLanguage.HI,
            "gu" to VokieLanguage.GU,
            "mr" to VokieLanguage.MR,
            "kn" to VokieLanguage.KN,
            "ml" to VokieLanguage.ML,
            "ta" to VokieLanguage.TA,
            "te" to VokieLanguage.TE,
            "or" to VokieLanguage.OR,
            "bn" to VokieLanguage.BN,
        )
        assertEquals(10, SttLanguage.entries.size)
        assertEquals(expected.size, SttLanguage.entries.size)
        assertNull(SttLanguage.fromWhisperCode("auto"))
        expected.forEach { (whisper, message) ->
            val language = requireNotNull(SttLanguage.fromWhisperCode(whisper)) { "SttLanguage missing for $whisper" }
            assertEquals(message, language.messageLanguage)
            assertEquals(language, SttLanguage.fromMessageCode(message.code))
            assertTrue(language.nativeName.isNotBlank())
        }
        assertNull(SttLanguage.fromWhisperCode("unsupported"))
        assertEquals("Q5_1", WhisperTinyMultilingualQ5_1.quantization)
        assertEquals(64, WhisperTinyMultilingualQ5_1.sha256.length)
        assertEquals(SttLanguage.entries.toSet(), WhisperTinyMultilingualQ5_1.supportedLanguages)
    }

    @Test fun stateMachineAllowsRealPipelineAndRejectsFabricatedResult() {
        val machine = SttStateMachine()
        machine.moveTo(SttState.MODEL_MISSING)
        machine.moveTo(SttState.IMPORTING)
        machine.moveTo(SttState.VALIDATING)
        machine.moveTo(SttState.INITIALIZING)
        machine.moveTo(SttState.READY)
        machine.moveTo(SttState.LISTENING)
        machine.moveTo(SttState.PROCESSING)
        machine.moveTo(SttState.RESULT)
        assertEquals(SttState.RESULT, machine.state)

        val invalid = SttStateMachine()
        try {
            invalid.moveTo(SttState.RESULT)
            fail("A result without initialization/listening/processing must be rejected")
        } catch (_: IllegalStateException) { }
    }

    @Test fun durationAndRealTimeFactorUseMeasuredValues() {
        assertEquals(4_000L, audioDurationMs(64_000))
        assertEquals(0.5, calculateRealTimeFactor(2_000, 4_000)!!, 0.0001)
        assertNull(calculateRealTimeFactor(10, 0))
        assertNull(calculateRealTimeFactor(-1, 10))
    }

    @Test fun sttResultCarriesLanguageIntoExistingMessageModel() {
        val result = SttResult("எனக்கு உதவி தேவை", SttLanguage.TAMIL, null, 1_900, 3_200, 1234)
        assertEquals(VokieLanguage.TA, result.language.messageLanguage)
        assertEquals("எனக்கு உதவி தேவை", result.text)
        assertEquals(1_900.0 / 3_200.0, result.realTimeFactor!!, 0.0001)
        assertNull(result.confidence)
    }

    @Test fun errorsMapWithoutInventingSuccess() {
        assertEquals(SttErrorCode.MIC_PERMISSION_DENIED, mapSttFailure(SttException(SttErrorCode.MIC_PERMISSION_DENIED, "permission"), SttErrorCode.STT_INFERENCE_FAILED, "fallback").code)
        assertEquals(SttErrorCode.OUT_OF_MEMORY, mapSttFailure(OutOfMemoryError(), SttErrorCode.STT_INFERENCE_FAILED, "fallback").code)
        assertEquals(SttErrorCode.STT_INITIALIZATION_FAILED, mapSttFailure(UnsatisfiedLinkError(), SttErrorCode.STT_INFERENCE_FAILED, "fallback").code)
        assertEquals(SttErrorCode.STT_INFERENCE_FAILED, mapSttFailure(IllegalStateException(), SttErrorCode.STT_INFERENCE_FAILED, "failed").code)
    }
}
