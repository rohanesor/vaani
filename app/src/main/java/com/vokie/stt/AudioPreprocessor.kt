package com.vokie.stt

import kotlin.math.sqrt

/**
 * N4 lightweight offline candidate. It is intentionally not enabled by production
 * capture until a real SenseVoice baseline/candidate benchmark demonstrates benefit.
 *
 * The stateful DC blocker is useful to evaluate microphone bias / very-low-frequency
 * energy that can inflate an RMS VAD floor. It is not a general denoiser and makes no
 * claim to remove crowd, wind, vehicle, music, or reverberant noise.
 */
enum class AudioPreprocessingMode { BYPASS, DC_BLOCKER }

data class AudioPreprocessingResult(
    val samples: FloatArray,
    val inputRms: Double,
    val outputRms: Double,
)

class LightweightAudioPreprocessor(
    private val mode: AudioPreprocessingMode = AudioPreprocessingMode.BYPASS,
    private val dcBlockPole: Float = DEFAULT_DC_BLOCK_POLE,
) {
    init { require(dcBlockPole in 0f..0.9999f) }

    private var previousInput = 0f
    private var previousOutput = 0f

    fun reset() {
        previousInput = 0f
        previousOutput = 0f
    }

    /** Input/output use the existing SenseVoice float PCM convention (-1..1). */
    fun process(input: FloatArray): AudioPreprocessingResult {
        if (input.isEmpty()) return AudioPreprocessingResult(FloatArray(0), 0.0, 0.0)
        val output = FloatArray(input.size)
        var inputSquares = 0.0
        var outputSquares = 0.0
        input.forEachIndexed { index, raw ->
            // A malformed audio buffer must never introduce NaN into VAD/STT.
            val sample = raw.takeIf { it.isFinite() }?.coerceIn(-1f, 1f) ?: 0f
            val filtered = when (mode) {
                AudioPreprocessingMode.BYPASS -> sample
                AudioPreprocessingMode.DC_BLOCKER -> {
                    val value = (sample - previousInput + dcBlockPole * previousOutput).coerceIn(-1f, 1f)
                    previousInput = sample
                    previousOutput = value
                    value
                }
            }
            output[index] = filtered
            inputSquares += sample * sample
            outputSquares += filtered * filtered
        }
        return AudioPreprocessingResult(
            samples = output,
            inputRms = sqrt(inputSquares / input.size),
            outputRms = sqrt(outputSquares / output.size),
        )
    }

    private companion object { const val DEFAULT_DC_BLOCK_POLE = 0.995f }
}
