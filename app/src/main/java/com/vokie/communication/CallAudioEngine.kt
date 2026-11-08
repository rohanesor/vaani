package com.vokie.communication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.os.Build
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Real-time 16kHz mono PCM call engine. It owns microphone/audio focus only while a
 * call is live, uses Android's communication route, and never persists media frames.
 */
internal class CallAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onFrame: suspend (sequence: Long, pcm: ByteArray) -> Unit,
    private val onFailure: (String) -> Unit,
) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val running = AtomicBoolean(false)
    private val jitter = CallJitterBuffer(frameBytes = FRAME_BYTES)
    private var record: AudioRecord? = null
    private var track: AudioTrack? = null
    private var captureJob: Job? = null
    private var playbackJob: Job? = null
    private var focusRequest: AudioFocusRequest? = null
    private var aec: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    @Volatile private var muted = false

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (!running.compareAndSet(false, true)) return false
        return try {
            check(ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) { "Microphone permission required for calls" }
            check(requestAudioFocus()) { "Audio focus unavailable" }
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            // Emergency/offline calls default to loudspeaker; Android 12+ ignores the
            // deprecated isSpeakerphoneOn route in communication mode.
            setSpeakerphone(true)

            val minRecord = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val minTrack = AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minRecord > 0 && minTrack > 0) { "Voice audio is unavailable" }
            val recorder = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
                .setBufferSizeInBytes(maxOf(minRecord, FRAME_BYTES * 4))
                .build()
            check(recorder.state == AudioRecord.STATE_INITIALIZED) { "Microphone could not be initialized" }
            val player = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(SAMPLE_RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setBufferSizeInBytes(maxOf(minTrack, FRAME_BYTES * 4))
                .build()
            check(player.state == AudioTrack.STATE_INITIALIZED) { "Speaker could not be initialized" }
            record = recorder
            track = player
            if (AcousticEchoCanceler.isAvailable()) aec = AcousticEchoCanceler.create(recorder.audioSessionId)?.also { it.enabled = true }
            if (NoiseSuppressor.isAvailable()) noiseSuppressor = NoiseSuppressor.create(recorder.audioSessionId)?.also { it.enabled = true }
            player.play()
            recorder.startRecording()
            check(recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Microphone could not start" }
            startLoops(recorder, player)
            VokieLog.rescue("CALL_AUDIO_STARTED aec=${aec != null} ns=${noiseSuppressor != null}")
            true
        } catch (error: Throwable) {
            releaseInternal()
            onFailure(error.message ?: "Call audio could not start")
            false
        }
    }

    fun receive(sequence: Long, pcm: ByteArray) = jitter.offer(sequence, pcm)
    fun setMuted(value: Boolean) { muted = value }
    @Suppress("DEPRECATION") fun setSpeakerphone(enabled: Boolean) {
        val manager = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (enabled) {
                val speaker = manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) manager.setCommunicationDevice(speaker)
            } else {
                manager.clearCommunicationDevice()
            }
        } else {
            manager.isSpeakerphoneOn = enabled
        }
    }

    suspend fun stop() {
        if (!running.getAndSet(false)) return
        captureJob?.cancelAndJoin(); playbackJob?.cancelAndJoin()
        captureJob = null; playbackJob = null
        releaseInternal()
        VokieLog.rescue("CALL_AUDIO_STOPPED")
    }

    private fun startLoops(recorder: AudioRecord, player: AudioTrack) {
        captureJob = scope.launch(Dispatchers.IO) {
            val samples = ShortArray(FRAME_SAMPLES)
            var sequence = 0L
            try {
                while (running.get()) {
                    val read = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                    if (read != FRAME_SAMPLES) {
                        if (read < 0) error("Microphone read failed: $read")
                        continue
                    }
                    if (!muted) {
                        val pcm = ByteBuffer.allocate(FRAME_BYTES).order(ByteOrder.LITTLE_ENDIAN).also { b -> samples.forEach(b::putShort) }.array()
                        onFrame(sequence, pcm)
                        if (sequence % 50L == 0L) VokieLog.rescue("CALL_AUDIO_TX seq=$sequence bytes=${pcm.size}")
                    }
                    sequence++
                }
            } catch (error: Throwable) {
                if (running.get()) onFailure(error.message ?: "Call microphone interrupted")
            }
        }
        playbackJob = scope.launch(Dispatchers.IO) {
            try {
                while (running.get()) {
                    jitter.poll()?.let { pcm ->
                        val wrote = player.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                        if (wrote < 0) error("Speaker write failed: $wrote")
                    } ?: delay(FRAME_MS.toLong())
                }
            } catch (error: Throwable) {
                if (running.get()) onFailure(error.message ?: "Call speaker interrupted")
            }
        }
    }

    private fun releaseInternal() {
        jitter.clear()
        runCatching { record?.stop() }; runCatching { track?.stop() }
        aec?.release(); aec = null; noiseSuppressor?.release(); noiseSuppressor = null
        record?.release(); record = null; track?.release(); track = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) audioManager?.clearCommunicationDevice()
        @Suppress("DEPRECATION") run { audioManager?.isSpeakerphoneOn = false }
        audioManager?.mode = AudioManager.MODE_NORMAL
        abandonAudioFocus()
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE).setAudioAttributes(attributes).build()
        return manager.requestAudioFocus(focusRequest!!) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }
    private fun abandonAudioFocus() { focusRequest?.let { audioManager?.abandonAudioFocusRequest(it) }; focusRequest = null }

    companion object {
        const val SAMPLE_RATE = 16_000
        const val FRAME_MS = 20
        const val FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1_000
        const val FRAME_BYTES = FRAME_SAMPLES * Short.SIZE_BYTES
    }
}
