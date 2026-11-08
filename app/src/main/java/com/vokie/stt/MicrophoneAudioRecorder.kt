package com.vokie.stt

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import com.vokie.communication.VokieLog
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal data class CapturedAudio(val samples: FloatArray, val durationMs: Long)

/** Native Android mono PCM16 capture with one reusable read buffer and a fixed 30-second ceiling. */
internal class MicrophoneAudioRecorder(
    private val context: Context,
    private val scope: CoroutineScope,
    private val vad: VadEngine,
) {
    private val lock = Any()
    private val recording = AtomicBoolean(false)
    private val finalized = AtomicBoolean(false)
    private val focusLost = AtomicBoolean(false)
    private val captured = ShortArray(MAX_SAMPLES)
    private var capturedCount = 0
    private var audioRecord: AudioRecord? = null
    private var captureJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change < 0) {
            focusLost.set(true)
            recording.set(false)
            runCatching { audioRecord?.stop() }
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun start(
        onVadState: (VadState) -> Unit,
        finalizeOnVad: Boolean = true,
        onFinalized: suspend (CapturedAudio) -> Unit,
        onFailure: (SttFailure) -> Unit,
    ) {
        check(!recording.get()) { "Microphone capture is already active" }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            throw SttException(SttErrorCode.MIC_PERMISSION_DENIED, "Microphone permission required for voice messaging.")
        }
        if (!requestAudioFocus()) throw SttException(SttErrorCode.AUDIO_CAPTURE_FAILED, "Audio focus is unavailable. Close other recording apps and retry.")

        val minimum = AudioRecord.getMinBufferSize(WHISPER_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minimum <= 0) {
            abandonAudioFocus()
            throw SttException(SttErrorCode.MIC_UNAVAILABLE, "The microphone is unavailable on this device.")
        }
        val bufferBytes = maxOf(minimum, FRAME_SAMPLES * Short.SIZE_BYTES * 2)
        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(WHISPER_SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(bufferBytes)
                .build()
        } catch (error: Throwable) {
            abandonAudioFocus()
            throw SttException(SttErrorCode.MIC_UNAVAILABLE, "The microphone could not be initialized.", error)
        }
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release(); abandonAudioFocus()
            throw SttException(SttErrorCode.MIC_UNAVAILABLE, "The microphone could not be initialized.")
        }

        vad.reset(); capturedCount = 0; finalized.set(false); focusLost.set(false)
        try {
            recorder.startRecording()
        } catch (error: Throwable) {
            recorder.release(); abandonAudioFocus()
            throw SttException(SttErrorCode.AUDIO_CAPTURE_FAILED, "Audio capture could not start.", error)
        }
        if (recorder.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            recorder.release(); abandonAudioFocus()
            throw SttException(SttErrorCode.AUDIO_CAPTURE_FAILED, "Audio capture could not start.")
        }
        audioRecord = recorder
        recording.set(true)
        VokieLog.stt("recordingStartTimestamp=${android.os.SystemClock.elapsedRealtime()}")
        captureJob = scope.launch(Dispatchers.IO) {
            val frame = ShortArray(FRAME_SAMPLES)
            try {
                while (recording.get()) {
                    val count = recorder.read(frame, 0, frame.size, AudioRecord.READ_BLOCKING)
                    if (count < 0) throw IllegalStateException("AudioRecord read failed: $count")
                    if (count == 0) continue
                    val copied = synchronized(lock) {
                        val available = captured.size - capturedCount
                        val amount = minOf(count, available)
                        frame.copyInto(captured, capturedCount, 0, amount)
                        capturedCount += amount
                        amount
                    }
                    val decision = vad.process(frame, copied)
                    onVadState(decision.state)
                    if ((finalizeOnVad && decision.finalizeUtterance) || capturedCount >= captured.size) {
                        recording.set(false)
                        if (vad.hasSpeech && finalized.compareAndSet(false, true)) onFinalized(snapshot())
                    }
                }
            } catch (error: Throwable) {
                if (recording.getAndSet(false) || focusLost.getAndSet(false)) onFailure(SttFailure(SttErrorCode.AUDIO_CAPTURE_FAILED, "Audio capture was interrupted.", error))
            } finally {
                runCatching { if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop() }
                recorder.release()
                if (audioRecord === recorder) audioRecord = null
                abandonAudioFocus()
            }
        }
    }

    suspend fun stop(): CapturedAudio? {
        VokieLog.stt("recordingStopRequested timestamp=${android.os.SystemClock.elapsedRealtime()}")
        recording.set(false)
        runCatching { audioRecord?.stop() }
        val job = captureJob
        if (job != null && job !== kotlinx.coroutines.currentCoroutineContext()[Job]) job.cancelAndJoin()
        captureJob = null
        VokieLog.stt("recordingStopped timestamp=${android.os.SystemClock.elapsedRealtime()} capturedSampleCount=$capturedCount capturedAudioDurationMs=${audioDurationMs(capturedCount)} vadHasSpeech=${vad.hasSpeech}")
        // PTT release is authoritative: submit any usable capture, regardless of VAD state.
        return if (finalized.compareAndSet(false, true) && capturedCount >= MIN_CAPTURE_SAMPLES) snapshot() else null
    }

    fun release() {
        recording.set(false)
        runCatching { audioRecord?.stop() }
        runCatching { audioRecord?.release() }
        audioRecord = null
        captureJob?.cancel(); captureJob = null
        abandonAudioFocus()
    }

    private fun snapshot(): CapturedAudio = synchronized(lock) {
        val floats = FloatArray(capturedCount) { captured[it] / 32768.0f }
        CapturedAudio(floats, audioDurationMs(capturedCount))
    }

    @Suppress("DEPRECATION")
    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setOnAudioFocusChangeListener(focusListener)
                .build().also { focusRequest = it }
                .let(manager::requestAudioFocus)
        } else manager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
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
        const val FRAME_SAMPLES = 1_600 // 100 ms at 16 kHz
        const val MAX_UTTERANCE_SECONDS = 30
        const val MAX_SAMPLES = WHISPER_SAMPLE_RATE * MAX_UTTERANCE_SECONDS
        const val MIN_CAPTURE_SAMPLES = WHISPER_SAMPLE_RATE * 300 / 1_000
    }
}
