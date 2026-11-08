package com.vokie.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

/** Plays in-memory PCM directly through AudioTrack. No WAV or temporary playback file is created. */
class VokieAudioPlayer(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val currentTrack = AtomicReference<AudioTrack?>(null)
    private val currentCompletion = AtomicReference<CompletableDeferred<Unit>?>(null)
    private data class PositionSource(val track: AudioTrack, val frames: Int, val sampleRate: Int)
    private val positionSource = AtomicReference<PositionSource?>(null)

    /** UI pulls at most a few times/second while visible; no playback callbacks or timers added. */
    fun playbackSnapshot(): PlaybackProgress? {
        val source = positionSource.get() ?: return null
        if (currentTrack.get() !== source.track) return null
        val frames = runCatching { source.track.playbackHeadPosition.toLong() and 0xffffffffL }.getOrNull() ?: return null
        if (currentTrack.get() !== source.track) return null
        return playbackProgress(frames, source.frames, source.sampleRate)
    }

    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        val track = currentTrack.get()
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                track?.setVolume(1f)
                if (track?.playState == AudioTrack.PLAYSTATE_PAUSED) runCatching { track.play() }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> track?.setVolume(0.25f)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> runCatching { track?.pause() }
            AudioManager.AUDIOFOCUS_LOSS -> stopWithFailure(TtsException(TtsErrorCode.AUDIO_FOCUS_FAILED, "Audio focus was lost."))
        }
    }

    suspend fun play(buffer: AudioBuffer, emergency: Boolean) {
        require(buffer.sampleRate > 0 && buffer.samples.isNotEmpty())
        require(buffer.durationMs in 1..MAX_AUDIO_DURATION_MS) { "Generated audio duration is outside the playback limit" }
        stop()
        if (!requestAudioFocus(emergency)) throw TtsException(TtsErrorCode.AUDIO_FOCUS_FAILED, "Audio focus is unavailable.")
        try {
            repeat(if (emergency) SOS_REPEAT_COUNT else 1) { playOnce(buffer, emergency) }
        } finally {
            abandonAudioFocus()
        }
    }

    fun stop() {
        positionSource.set(null)
        currentCompletion.getAndSet(null)?.cancel()
        currentTrack.getAndSet(null)?.let { track ->
            runCatching { track.pause() }; runCatching { track.flush() }; runCatching { track.stop() }; runCatching { track.release() }
        }
        abandonAudioFocus()
    }

    fun release() = stop()

    private suspend fun playOnce(buffer: AudioBuffer, emergency: Boolean) {
        val preparationStartedNs = SystemClock.elapsedRealtimeNanos()
        val attributes = AudioAttributes.Builder()
            .setUsage(if (emergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setSampleRate(buffer.sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBufferBytes = AudioTrack.getMinBufferSize(
            buffer.sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBufferBytes <= 0) {
            Log.e(TAG, "AudioTrack min-buffer failure rate=${buffer.sampleRate} channel=MONO encoding=PCM_FLOAT result=$minBufferBytes")
            throw TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "PCM float audio output is unavailable (min buffer=$minBufferBytes).")
        }
        // MODE_STATIC is not reliably supported for PCM_FLOAT on Android device outputs.
        // Stream the already-normalized float PCM with a buffer that satisfies the platform
        // minimum and can hold this complete utterance.
        val selectedBufferBytes = maxOf(minBufferBytes, buffer.samples.size * Float.SIZE_BYTES)
        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(selectedBufferBytes)
                .build()
        } catch (error: Throwable) {
            Log.e(TAG, "AudioTrack construction failure rate=${buffer.sampleRate} channel=MONO encoding=PCM_FLOAT min=$minBufferBytes selected=$selectedBufferBytes samples=${buffer.samples.size}", error)
            throw TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Audio output could not be initialized.", error)
        }
        val trackBuildMs = (SystemClock.elapsedRealtimeNanos() - preparationStartedNs) / 1_000_000.0
        Log.i(TAG, "AudioTrack init rate=${buffer.sampleRate} channel=MONO encoding=PCM_FLOAT mode=STREAM min=$minBufferBytes selected=$selectedBufferBytes samples=${buffer.samples.size} state=${track.state} prepareMs=$trackBuildMs")
        if (track.state != AudioTrack.STATE_INITIALIZED) {
            track.release()
            throw TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Audio output could not be initialized (state=${track.state}).")
        }
        val completion = CompletableDeferred<Unit>()
        currentTrack.set(track); currentCompletion.set(completion)
        val source = PositionSource(track, buffer.samples.size, buffer.sampleRate)
        positionSource.set(source)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(audioTrack: AudioTrack) { completion.complete(Unit) }
            override fun onPeriodicNotification(audioTrack: AudioTrack) = Unit
        }, Handler(Looper.getMainLooper()))
        val writeStartedNs = SystemClock.elapsedRealtimeNanos()
        val written = track.write(buffer.samples, 0, buffer.samples.size, AudioTrack.WRITE_BLOCKING)
        val writeMs = (SystemClock.elapsedRealtimeNanos() - writeStartedNs) / 1_000_000.0
        Log.i(TAG, "AudioTrack write result=$written expected=${buffer.samples.size} writeMs=$writeMs")
        if (written != buffer.samples.size) {
            stopWithFailure(TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Generated speech could not be written to audio output (wrote $written/${buffer.samples.size})."))
        }
        track.notificationMarkerPosition = buffer.samples.size
        track.setVolume(1f) // Maximum application gain; this does not override the user's device volume.
        try {
            val playbackStartedNs = SystemClock.elapsedRealtimeNanos()
            track.play()
            val playCallMs = (SystemClock.elapsedRealtimeNanos() - playbackStartedNs) / 1_000_000.0
            Log.i(TAG, "AudioTrack playback started playCallMs=$playCallMs generatedAudioMs=${buffer.durationMs}")
            completion.await()
            val playbackElapsedMs = (SystemClock.elapsedRealtimeNanos() - playbackStartedNs) / 1_000_000.0
            Log.i(TAG, "AudioTrack playback completed elapsedMs=$playbackElapsedMs generatedAudioMs=${buffer.durationMs}")
        } finally {
            positionSource.compareAndSet(source, null)
            currentCompletion.compareAndSet(completion, null)
            if (currentTrack.compareAndSet(track, null)) {
                runCatching { track.stop() }; track.release()
            }
        }
    }

    private fun stopWithFailure(error: Throwable) {
        positionSource.set(null)
        currentCompletion.getAndSet(null)?.completeExceptionally(error)
        currentTrack.getAndSet(null)?.let { track -> runCatching { track.stop() }; track.release() }
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(emergency: Boolean): Boolean {
        val manager = audioManager ?: return false
        val gain = if (emergency) AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE else AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val attributes = AudioAttributes.Builder()
                .setUsage(if (emergency) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            AudioFocusRequest.Builder(gain).setAudioAttributes(attributes).setOnAudioFocusChangeListener(focusListener).build()
                .also { focusRequest = it }.let(manager::requestAudioFocus)
        } else manager.requestAudioFocus(focusListener, if (emergency) AudioManager.STREAM_ALARM else AudioManager.STREAM_MUSIC, gain)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    @Suppress("DEPRECATION")
    private fun abandonAudioFocus() {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) focusRequest?.let(manager::abandonAudioFocusRequest)
        else manager.abandonAudioFocus(focusListener)
        focusRequest = null
    }

    private companion object {
        const val MAX_AUDIO_DURATION_MS = 120_000L
        const val SOS_REPEAT_COUNT = 2
        const val TAG = "VOKIE_AUDIO"
    }
}
