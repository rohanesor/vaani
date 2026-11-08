package com.vokie.stt

import org.junit.Assert.*
import org.junit.Test

class EnergyVadEngineTest {
    @Test fun sustainedSpeechThenConfiguredSilenceFinalizesOnceThresholdIsReached() {
        val vad = EnergyVadEngine(EnergyVadConfig(minimumSpeechMs = 200, finalizeSilenceMs = 1_200))
        val speech = ShortArray(1_600) { 8_000 }
        val silence = ShortArray(1_600)

        assertEquals(VadState.WAITING_FOR_SPEECH, vad.process(speech, speech.size).state)
        assertEquals(VadState.SPEECH_DETECTED, vad.process(speech, speech.size).state)
        assertTrue(vad.hasSpeech)
        repeat(11) { assertFalse(vad.process(silence, silence.size).finalizeUtterance) }
        val final = vad.process(silence, silence.size)
        assertEquals(VadState.SILENCE_DETECTED, final.state)
        assertTrue(final.finalizeUtterance)
    }

    @Test fun silenceWithoutSpeechNeverCreatesAnUtterance() {
        val vad = EnergyVadEngine()
        val silence = ShortArray(1_600)
        repeat(30) {
            val decision = vad.process(silence, silence.size)
            assertEquals(VadState.WAITING_FOR_SPEECH, decision.state)
            assertFalse(decision.finalizeUtterance)
        }
        assertFalse(vad.hasSpeech)
    }
}
