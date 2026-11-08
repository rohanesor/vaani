package com.vokie.stt

import android.content.Context
import android.os.SystemClock
import com.vokie.BuildConfig
import com.vokie.communication.VokieLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicBoolean

class WhisperNative {
    init { System.loadLibrary("vokie_whisper") }
    external fun nativeInit(modelPath: String): Long
    external fun nativeTranscribe(context: Long, samples: FloatArray, language: String, threads: Int): String
    external fun nativeAbort()
    external fun nativeFree(context: Long)
}

/** Native STT is optional: an unsupported ABI/page-size must not crash application startup. */
private fun loadWhisperNative(): WhisperNative? = runCatching { WhisperNative() }.getOrNull()

/** One application-scoped whisper.cpp context. Audio and inference never leave the device. */
class WhisperSttEngine(
    private val context: Context,
    val model: SttModel = WhisperTinyMultilingualQ5_1,
    private val native: WhisperNative? = loadWhisperNative(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    vadEngine: VadEngine = EnergyVadEngine(),
) : SttEngine {
    private val modelStore = SttModelStore(context, model)
    private val recorder = MicrophoneAudioRecorder(context, scope, vadEngine)
    private val stateMachine = SttStateMachine()
    private val inferenceMutex = Mutex()
    @Volatile private var inferenceStartListener: ((turnId: String?) -> Unit)? = null
    @Volatile private var inferenceTurnId: String? = null
    private val initializationMutex = Mutex()
    private val processing = AtomicBoolean(false)
    // Set synchronously by VAD/manual stop before inference is dispatched.
    // This closes the VAD-finalization versus stop() race.
    private val finalizationPending = AtomicBoolean(false)
    private val _status = MutableStateFlow(SttStatus(SttState.UNINITIALIZED))
    override val status: StateFlow<SttStatus> = _status.asStateFlow()
    private var nativeContext = 0L
    private var activeLanguage = SttLanguage.ENGLISH
    private var activePreferredLanguage = UserLanguageProfile.same(com.vokie.domain.model.VokieLanguage.EN)
    // Debug harness only: an in-memory copy permits identical-PCM replays.
    // It is never persisted and is cleared with the native context.
    private var debugLastCapture: CapturedAudio? = null

    override suspend fun initialize() = initializationMutex.withLock {
        if (nativeContext != 0L) {
            if (stateMachine.state in setOf(SttState.READY, SttState.RESULT)) return@withLock
            if (stateMachine.state == SttState.ERROR) {
                moveTo(SttState.INITIALIZING)
                moveTo(SttState.READY)
                return@withLock
            }
            // Compose may request initialization during an active voice operation.
            // The native context is valid; reinitializing would violate the state machine.
            if (stateMachine.state in setOf(SttState.PROCESSING, SttState.LISTENING)) return@withLock
        }
        moveTo(SttState.INITIALIZING)
        if (!modelStore.isInstalled()) {
            moveTo(SttState.MODEL_MISSING, failure = SttFailure(SttErrorCode.MODEL_MISSING, "Bundled STT assets could not be prepared."))
            return@withLock
        }
        val modelFile = model.localFile(context)
        val size = modelFile.length()
        val started = SystemClock.elapsedRealtime()
        try {
            val backend = native ?: error("Whisper native backend is unavailable on this device ABI")
            val loaded = withContext(Dispatchers.IO) { backend.nativeInit(modelFile.absolutePath) }
            check(loaded != 0L) { "whisper.cpp returned an empty context" }
            nativeContext = loaded
            val loadTime = SystemClock.elapsedRealtime() - started
            moveTo(SttState.READY, modelLoadTimeMs = loadTime, installedModelBytes = size)
            VokieLog.stt("Model loaded in ${loadTime}ms: ${model.id}")
        } catch (error: Throwable) {
            fail(mapSttFailure(error, SttErrorCode.MODEL_LOAD_FAILED, "The local STT model could not be loaded."))
        }
    }

    override suspend fun start(language: SttLanguage, preferredLanguage: UserLanguageProfile, finalizeOnVad: Boolean) {
        if (language !in model.supportedLanguages) {
            fail(SttFailure(SttErrorCode.UNSUPPORTED_LANGUAGE, "The selected language is not supported by this model.")); return
        }
        if (nativeContext == 0L) {
            initialize()
            if (nativeContext == 0L) return
        }
        if (stateMachine.state !in setOf(SttState.READY, SttState.RESULT, SttState.ERROR)) return
        activeLanguage = language
        activePreferredLanguage = preferredLanguage
        processing.set(false)
        finalizationPending.set(false)
        moveTo(SttState.LISTENING, vadState = VadState.WAITING_FOR_SPEECH)
        try {
            recorder.start(
                onVadState = { vad -> _status.value = _status.value.copy(vadState = vad) },
                finalizeOnVad = finalizeOnVad,
                onFinalized = { audio ->
                    if (finalizationPending.compareAndSet(false, true)) {
                        scope.launch { processCaptured(audio, activeLanguage, activePreferredLanguage) }
                    }
                },
                onFailure = { failure -> scope.launch { fail(failure) } },
            )
            VokieLog.stt("Listening started: ${language.whisperCode}")
        } catch (error: Throwable) {
            fail(mapSttFailure(error, SttErrorCode.AUDIO_CAPTURE_FAILED, "Audio capture could not start."))
        }
    }

    override suspend fun stop() {
        // VAD may have finalized on another thread immediately before button release.
        if (finalizationPending.get() || processing.get()) return
        val audio = recorder.stop()
        if (audio != null) {
            if (finalizationPending.compareAndSet(false, true)) processCaptured(audio, activeLanguage, activePreferredLanguage)
            return
        }
        if (stateMachine.state == SttState.LISTENING) {
            // Let an already-dispatched VAD callback publish its pending marker.
            kotlinx.coroutines.yield()
            if (finalizationPending.get() || processing.get()) return
            moveTo(SttState.READY, vadState = VadState.WAITING_FOR_SPEECH)
            VokieLog.stt("PTT_NO_SPEECH_READY stt_engine=whisper")
        }
    }

    override fun setInferenceStartListener(listener: ((turnId: String?) -> Unit)?) { inferenceStartListener = listener }
    override fun setInferenceTurnId(turnId: String?) { inferenceTurnId = turnId }

    override suspend fun transcribe(audio: FloatArray, language: SttLanguage, audioDurationMs: Long): SttResult {
        require(audio.isNotEmpty() && audio.size <= WHISPER_SAMPLE_RATE * 30) { "Audio must be between 1 sample and 30 seconds" }
        require(audioDurationMs > 0) { "Audio duration must be positive" }
        if (language !in model.supportedLanguages) throw SttException(SttErrorCode.UNSUPPORTED_LANGUAGE, "Unsupported STT language")
        val handle = nativeContext
        if (handle == 0L) throw SttException(SttErrorCode.STT_INITIALIZATION_FAILED, "STT is not initialized")
        return inferenceMutex.withLock {
            val started = SystemClock.elapsedRealtime()
            inferenceStartListener?.invoke(inferenceTurnId)
            VokieLog.stt("whisperStartTimestamp=$started requestedLanguage=${language.whisperCode} inputSamples=${audio.size} inputDurationMs=$audioDurationMs")
            val timedOut = AtomicBoolean(false)
            val watchdog = scope.launch(Dispatchers.Default) {
                kotlinx.coroutines.delay(TRANSCRIPTION_TIMEOUT_MS)
                timedOut.set(true)
                VokieLog.stt("Transcription timeout — requesting cooperative native abort")
                native?.nativeAbort()
            }
            try {
                val text = withContext(Dispatchers.Default) {
                    (native ?: error("Whisper native backend is unavailable on this device ABI")).nativeTranscribe(handle, audio, language.whisperCode, Runtime.getRuntime().availableProcessors().coerceIn(1, 4)).trim()
                }
                val whisperEnd = SystemClock.elapsedRealtime()
                val processingTime = whisperEnd - started
                VokieLog.stt("whisperEndTimestamp=$whisperEnd nativeTextLength=${text.length} processingMs=$processingTime")
                if (text.isBlank()) throw SttException(SttErrorCode.NO_SPEECH, "No speech was recognized. Try speaking more clearly.")
                // Explicit user-profile language only: never invoke Whisper language identification.
                VokieLog.stt("messageLanguage=${language.whisperCode} resultDeliveredTimestamp=${SystemClock.elapsedRealtime()}")
                SttResult(text, language, confidence = null, processingTimeMs = processingTime, audioDurationMs = audioDurationMs, timestamp = System.currentTimeMillis())
            } catch (error: Throwable) {
                if (error is kotlinx.coroutines.CancellationException && !timedOut.get()) throw error
                if (timedOut.get()) throw SttException(SttErrorCode.STT_INFERENCE_FAILED, "Transcription timed out after 60 seconds.", error)
                throw error
            } finally {
                watchdog.cancel()
                // Avoid a canceled watchdog aborting the next mutex-protected inference.
                watchdog.join()
            }
        }
    }

    /** Re-runs the exact last PTT/Tap capture; Debug APK benchmark harness only. */
    suspend fun replayLastCaptureForBenchmark(language: SttLanguage, preferredLanguage: UserLanguageProfile) {
        check(BuildConfig.DEBUG) { "PCM replay is available only in Debug builds" }
        val captured = checkNotNull(debugLastCapture) { "Capture speech once before replaying the benchmark." }
        activePreferredLanguage = preferredLanguage
        val result = transcribe(captured.samples, language, captured.durationMs)
        _status.value = _status.value.copy(state = SttState.RESULT, result = result, failure = null)
        VokieLog.stt("Benchmark replay result: audio=${result.audioDurationMs}ms processing=${result.processingTimeMs}ms rtf=${result.realTimeFactor}")
    }

    override fun release() {
        recorder.release()
        debugLastCapture = null
        releaseContext()
        processing.set(false)
        finalizationPending.set(false)
        if (stateMachine.state != SttState.UNINITIALIZED) moveTo(SttState.UNINITIALIZED)
    }

    private suspend fun processCaptured(audio: CapturedAudio, language: SttLanguage, preferredLanguage: UserLanguageProfile) {
        if (!processing.compareAndSet(false, true)) return
        try {
            if (stateMachine.state == SttState.LISTENING) moveTo(SttState.PROCESSING, vadState = VadState.TRANSCRIBING)
            if (BuildConfig.DEBUG) {
                debugLastCapture = audio.copy(samples = audio.samples.copyOf())
                saveDebugValidationPcm(audio.samples)
            }
            activePreferredLanguage = preferredLanguage
            val result = transcribe(audio.samples, language, audio.durationMs)
            moveTo(SttState.RESULT, vadState = VadState.TEXT_READY, result = result)
            VokieLog.stt("Result ready: audio=${result.audioDurationMs}ms processing=${result.processingTimeMs}ms rtf=${result.realTimeFactor}")
            kotlinx.coroutines.delay(50)
            moveTo(SttState.READY, vadState = VadState.WAITING_FOR_SPEECH)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            fail(mapSttFailure(error, SttErrorCode.STT_INFERENCE_FAILED, "Local speech recognition failed."))
        } finally {
            processing.set(false)
            finalizationPending.set(false)
        }
    }

    private fun moveTo(
        state: SttState,
        vadState: VadState = _status.value.vadState,
        result: SttResult? = null,
        failure: SttFailure? = null,
        modelLoadTimeMs: Long? = _status.value.modelLoadTimeMs,
        installedModelBytes: Long = _status.value.installedModelBytes,
    ) {
        stateMachine.moveTo(state)
        _status.value = SttStatus(state, vadState, result, failure, modelLoadTimeMs, installedModelBytes)
    }

    private fun fail(failure: SttFailure) {
        if (stateMachine.state != SttState.ERROR) moveTo(SttState.ERROR, failure = failure)
        else _status.value = _status.value.copy(failure = failure)
        VokieLog.stt("${failure.code}: ${failure.userMessage}")
    }

    private companion object {
        const val TRANSCRIPTION_TIMEOUT_MS = 60_000L
    }

    private fun saveDebugValidationPcm(samples: FloatArray) {
        // Consent-gated Phase 3D.4 helper: Debug only, app-private cache, overwritten
        // on each real capture. Test audio is never placed in the repository or uploaded.
        if (!BuildConfig.DEBUG) return
        val file = File(context.cacheDir, "stt-validation/last.pcm")
        file.parentFile?.mkdirs()
        FileOutputStream(file).use { output ->
            val bytes = ByteArray(samples.size * 2)
            samples.forEachIndexed { index, sample ->
                val value = (sample.coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort().toInt()
                bytes[index * 2] = (value and 0xff).toByte()
                bytes[index * 2 + 1] = ((value ushr 8) and 0xff).toByte()
            }
            output.write(bytes)
        }
        VokieLog.stt("Debug validation PCM saved locally: samples=${samples.size}")
    }

    private fun releaseContext() {
        val handle = nativeContext
        nativeContext = 0
        if (handle != 0L) runCatching { native?.nativeFree(handle) }
    }

}
