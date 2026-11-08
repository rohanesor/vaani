package com.vokie.stt

import kotlin.math.sqrt

data class VadDecision(val state: VadState, val finalizeUtterance: Boolean)

interface VadEngine {
    val hasSpeech: Boolean
    fun reset()
    fun process(samples: ShortArray, count: Int): VadDecision
}

data class EnergyVadConfig(
    val speechRmsThreshold: Double = 0.015,
    val adaptiveNoiseMultiplier: Double = 2.2,
    val minimumSpeechMs: Long = 200,
    val finalizeSilenceMs: Long = 750,
    val sampleRate: Int = WHISPER_SAMPLE_RATE,
)

/** Bounded, replaceable RMS-energy VAD. It never invokes Whisper for individual audio frames. */
class EnergyVadEngine(private val config: EnergyVadConfig = EnergyVadConfig()) : VadEngine {
    private var consecutiveSpeechMs = 0L
    private var consecutiveSilenceMs = 0L
    private var calibrationMs = 0L
    private var ambientSquares = 0.0
    private var ambientSamples = 0L
    private var activeThreshold = 0.025
    override var hasSpeech = false
        private set

    override fun reset() {
        consecutiveSpeechMs = 0
        consecutiveSilenceMs = 0
        hasSpeech = false
        calibrationMs = 0
        ambientSquares = 0.0
        ambientSamples = 0
        activeThreshold = maxOf(config.speechRmsThreshold, 0.025)
    }

    override fun process(samples: ShortArray, count: Int): VadDecision {
        require(count in 0..samples.size)
        if (count == 0) return VadDecision(if (hasSpeech) VadState.RECORDING else VadState.WAITING_FOR_SPEECH, false)
        var sumSquares = 0.0
        for (index in 0 until count) {
            val normalized = samples[index] / 32768.0
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        val frameMs = audioDurationMs(count, config.sampleRate).coerceAtLeast(1)
        if (!hasSpeech && calibrationMs < 200) {
            ambientSquares += sumSquares
            ambientSamples += count
            calibrationMs += frameMs
            if (rms >= config.speechRmsThreshold) consecutiveSpeechMs += frameMs
            if (calibrationMs < 200) return VadDecision(VadState.WAITING_FOR_SPEECH, false)
            val ambientRms = if (ambientSamples > 0) sqrt(ambientSquares / ambientSamples) else 0.0
            activeThreshold = maxOf(config.speechRmsThreshold, ambientRms * config.adaptiveNoiseMultiplier, 0.025)
            if (consecutiveSpeechMs >= config.minimumSpeechMs) hasSpeech = true
            if (hasSpeech) return VadDecision(VadState.SPEECH_DETECTED, false)
        }
        if (rms >= activeThreshold) {
            consecutiveSpeechMs += frameMs
            consecutiveSilenceMs = 0
            if (consecutiveSpeechMs >= config.minimumSpeechMs) hasSpeech = true
            return VadDecision(if (hasSpeech) VadState.SPEECH_DETECTED else VadState.WAITING_FOR_SPEECH, false)
        }
        consecutiveSpeechMs = 0
        if (!hasSpeech) return VadDecision(VadState.WAITING_FOR_SPEECH, false)
        consecutiveSilenceMs += frameMs
        val finalize = consecutiveSilenceMs >= config.finalizeSilenceMs
        return VadDecision(if (finalize) VadState.SILENCE_DETECTED else VadState.RECORDING, finalize)
    }
}
