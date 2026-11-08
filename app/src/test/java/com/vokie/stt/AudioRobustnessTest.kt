package com.vokie.stt

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.*
import org.junit.Test

/**
 * Deterministic N4 engineering-validation signal tests. These validate preprocessing
 * and VAD invariants only; they are not SenseVoice WER/CER or field-noise benchmarks.
 */
class AudioRobustnessTest {
    @Test fun `clean speech bypass is sample exact`() {
        val clean = sine(1_600, amplitude = 0.20f)
        val result = LightweightAudioPreprocessor(AudioPreprocessingMode.BYPASS).process(clean)
        assertArrayEquals(clean, result.samples, 0f)
        assertEquals(result.inputRms, result.outputRms, 0.0)
    }

    @Test fun `dc blocker removes steady microphone bias`() {
        val biasedSilence = FloatArray(16_000) { 0.12f }
        val processor = LightweightAudioPreprocessor(AudioPreprocessingMode.DC_BLOCKER)
        val output = processor.process(biasedSilence).samples
        // Ignore startup transient; a DC blocker must settle near zero.
        val tailMean = output.drop(8_000).average()
        assertTrue("tailMean=$tailMean", abs(tailMean) < 0.002)
    }

    @Test fun `dc blocker preserves usable speech energy after settling`() {
        val speechWithBias = sine(16_000, amplitude = 0.18f).map { (it + 0.10f).coerceIn(-1f, 1f) }.toFloatArray()
        val output = LightweightAudioPreprocessor(AudioPreprocessingMode.DC_BLOCKER).process(speechWithBias).samples
        val speechRms = rms(output.copyOfRange(3_200, output.size))
        assertTrue("speechRms=$speechRms", speechRms > 0.08)
    }

    @Test fun `silent and malformed audio remain finite and quiet`() {
        val malformed = floatArrayOf(0f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, 0f)
        val result = LightweightAudioPreprocessor(AudioPreprocessingMode.DC_BLOCKER).process(malformed)
        assertTrue(result.samples.all { it.isFinite() })
        assertTrue(result.samples.all { it in -1f..1f })
        assertTrue(result.outputRms < 0.01)
    }

    @Test fun `deterministic background noise processing is repeatable`() {
        val noisy = mix(sine(1_600, 0.10f), deterministicNoise(1_600, 0.04f))
        val first = LightweightAudioPreprocessor(AudioPreprocessingMode.DC_BLOCKER).process(noisy).samples
        val second = LightweightAudioPreprocessor(AudioPreprocessingMode.DC_BLOCKER).process(noisy).samples
        assertArrayEquals(first, second, 0f)
    }

    @Test fun `vad still detects speech with low background noise`() {
        val vad = EnergyVadEngine()
        val noise = deterministicNoise(1_600, 0.004f).toShortPcm()
        val speechNoise = mix(sine(1_600, 0.20f), deterministicNoise(1_600, 0.01f)).toShortPcm()
        vad.process(noise, noise.size)
        vad.process(noise, noise.size) // complete 200 ms ambient calibration
        vad.process(speechNoise, speechNoise.size)
        val decision = vad.process(speechNoise, speechNoise.size)
        assertEquals(VadState.SPEECH_DETECTED, decision.state)
        assertTrue(vad.hasSpeech)
    }

    @Test fun `noise only does not finalize utterance`() {
        val vad = EnergyVadEngine()
        repeat(20) {
            val noise = deterministicNoise(1_600, 0.004f).toShortPcm()
            val decision = vad.process(noise, noise.size)
            assertFalse(decision.finalizeUtterance)
        }
        assertFalse(vad.hasSpeech)
    }

    @Test fun `preprocessor is language agnostic for N3 transcript paths`() {
        // Audio processing has no language/token input: Hindi+English and Tamil+English
        // transcripts therefore receive identical audio treatment before existing STT.
        val samples = mix(sine(1_600, 0.12f), deterministicNoise(1_600, 0.01f))
        val processor = LightweightAudioPreprocessor(AudioPreprocessingMode.DC_BLOCKER)
        val hindiEnglish = processor.process(samples).samples
        processor.reset()
        val tamilEnglish = processor.process(samples).samples
        assertArrayEquals(hindiEnglish, tamilEnglish, 0f)
    }

    private fun sine(size: Int, amplitude: Float, hz: Double = 440.0): FloatArray =
        FloatArray(size) { index -> (amplitude * sin(2.0 * PI * hz * index / WHISPER_SAMPLE_RATE)).toFloat() }

    private fun deterministicNoise(size: Int, amplitude: Float): FloatArray {
        var state = 0x13579BDF
        return FloatArray(size) {
            state = state * 1_103_515_245 + 12_345
            val unit = ((state ushr 8) and 0xFFFF) / 32767.5f - 1f
            unit * amplitude
        }
    }

    private fun mix(a: FloatArray, b: FloatArray): FloatArray {
        require(a.size == b.size)
        return FloatArray(a.size) { (a[it] + b[it]).coerceIn(-1f, 1f) }
    }

    private fun FloatArray.toShortPcm() = ShortArray(size) { (this[it].coerceIn(-1f, 1f) * 32767f).toInt().toShort() }
    private fun rms(samples: FloatArray): Double = kotlin.math.sqrt(samples.sumOf { (it * it).toDouble() } / samples.size)
}
