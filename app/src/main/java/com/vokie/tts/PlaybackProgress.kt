package com.vokie.tts

/** Read-only AudioTrack position, never an inference or transport timestamp. */
data class PlaybackProgress(val positionMs: Long, val durationMs: Long)

internal fun playbackProgress(frames: Long, totalFrames: Int, sampleRate: Int): PlaybackProgress? {
    if (totalFrames <= 0 || sampleRate <= 0) return null
    return PlaybackProgress(frames.coerceIn(0, totalFrames.toLong()) * 1000 / sampleRate,
        totalFrames.toLong() * 1000 / sampleRate)
}
