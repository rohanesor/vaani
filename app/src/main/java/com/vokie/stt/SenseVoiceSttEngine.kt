package com.vokie.stt

import android.content.Context
import android.os.SystemClock
import com.vokie.BuildConfig
import com.vokie.communication.VokieLog
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
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
import java.util.concurrent.atomic.AtomicBoolean

/**
 * SenseVoice-Small STT engine using sherpa-onnx OfflineRecognizer.
 * Shares the same [SttEngine] interface, microphone recorder, and VAD as Whisper.
 * The recognizer is loaded once and reused for all inference calls.
 */
class SenseVoiceSttEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    vadEngine: VadEngine = EnergyVadEngine(),
) : SttEngine {
    private val recorder = MicrophoneAudioRecorder(context, scope, vadEngine)
    private val stateMachine = SttStateMachine()
    private val inferenceMutex = Mutex()
    @Volatile private var inferenceStartListener: ((turnId: String?) -> Unit)? = null
    @Volatile private var inferenceTurnId: String? = null
    private val processing = AtomicBoolean(false)
    private val finalizationPending = AtomicBoolean(false)
    private val _status = MutableStateFlow(SttStatus(SttState.UNINITIALIZED))
    override val status: StateFlow<SttStatus> = _status.asStateFlow()
    private var recognizer: OfflineRecognizer? = null
    private var activeLanguage: SttLanguage = SttLanguage.ENGLISH
    private var activePreferredLanguage: UserLanguageProfile = UserLanguageProfile.same(com.vokie.domain.model.VokieLanguage.EN)
    @Volatile private var debugLastCapture: CapturedAudio? = null

    private val modelDir get() = File(context.filesDir, "models/stt/sensevoice")
    private val modelFile get() = File(modelDir, "model.int8.onnx")
    private val tokensFile get() = File(modelDir, "tokens.txt")

    val isAvailable: Boolean get() = modelFile.exists() && tokensFile.exists()

    override suspend fun initialize() {
        if (!isAvailable) {
            VokieLog.stt("SenseVoice model not staged: ${modelDir.absolutePath}")
            if (stateMachine.state != SttState.MODEL_MISSING) moveTo(SttState.MODEL_MISSING)
            return
        }
        if (recognizer != null && stateMachine.state in setOf(SttState.READY, SttState.RESULT, SttState.LISTENING, SttState.PROCESSING)) return
        try {
            if (stateMachine.state !in setOf(SttState.INITIALIZING)) moveTo(SttState.INITIALIZING)
            val started = SystemClock.elapsedRealtime()
            val config = OfflineRecognizerConfig(
                modelConfig = OfflineModelConfig(
                    senseVoice = OfflineSenseVoiceModelConfig(
                        model = modelFile.absolutePath,
                        language = "",
                        useInverseTextNormalization = false,
                    ),
                    tokens = tokensFile.absolutePath,
                    numThreads = 4,
                    debug = false,
                ),
                decodingMethod = "greedy_search",
            )
            recognizer = withContext(Dispatchers.IO) { OfflineRecognizer(null, config) }
            val loadMs = SystemClock.elapsedRealtime() - started
            moveTo(SttState.READY, modelLoadTimeMs = loadMs)
            VokieLog.stt("SenseVoice loaded in ${loadMs}ms stt_engine=sensevoice")
        } catch (e: Throwable) {
            VokieLog.stt("SenseVoice init failed: ${e.message}")
            fail(mapSttFailure(e, SttErrorCode.MODEL_LOAD_FAILED, "SenseVoice model could not be loaded."))
        }
    }

    override suspend fun start(language: SttLanguage, preferredLanguage: UserLanguageProfile, finalizeOnVad: Boolean) {
        if (recognizer == null) {
            initialize()
            if (recognizer == null) return
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
            VokieLog.stt("Listening started: ${language.whisperCode} stt_engine=sensevoice")
        } catch (e: Throwable) {
            fail(mapSttFailure(e, SttErrorCode.AUDIO_CAPTURE_FAILED, "Audio capture could not start."))
        }
    }

    override suspend fun stop() {
        if (finalizationPending.get() || processing.get()) return
        val audio = recorder.stop()
        if (audio != null) {
            if (finalizationPending.compareAndSet(false, true)) processCaptured(audio, activeLanguage, activePreferredLanguage)
            return
        }
        if (stateMachine.state == SttState.LISTENING) {
            kotlinx.coroutines.yield()
            if (finalizationPending.get() || processing.get()) return
            // A short tap without usable audio is a normal PTT cancellation, not a
            // terminal engine error. Return directly to READY for the next press.
            moveTo(SttState.READY, vadState = VadState.WAITING_FOR_SPEECH)
            VokieLog.stt("PTT_NO_SPEECH_READY stt_engine=sensevoice")
        }
    }

    override suspend fun transcribe(audio: FloatArray, language: SttLanguage, audioDurationMs: Long): SttResult {
        require(audio.isNotEmpty() && audio.size <= WHISPER_SAMPLE_RATE * 30)
        require(audioDurationMs > 0)
        val rec = recognizer ?: throw SttException(SttErrorCode.STT_INITIALIZATION_FAILED, "SenseVoice is not initialized")
        return inferenceMutex.withLock {
            val started = SystemClock.elapsedRealtime()
            inferenceStartListener?.invoke(inferenceTurnId)
            VokieLog.stt("senseVoiceStart stt_engine=sensevoice language=${language.whisperCode} samples=${audio.size} durationMs=$audioDurationMs")
            val text = withContext(Dispatchers.Default) {
                val stream = rec.createStream()
                stream.acceptWaveform(audio, WHISPER_SAMPLE_RATE)
                rec.decode(stream)
                rec.getResult(stream).text.trim()
            }
            val processingMs = SystemClock.elapsedRealtime() - started
            VokieLog.stt("senseVoiceEnd stt_engine=sensevoice processingMs=$processingMs textLength=${text.length}")
            if (text.isBlank()) throw SttException(SttErrorCode.NO_SPEECH, "No speech was recognized.")
            SttResult(text, language, confidence = null, processingTimeMs = processingMs, audioDurationMs = audioDurationMs, timestamp = System.currentTimeMillis())
        }
    }

    override fun setInferenceStartListener(listener: ((turnId: String?) -> Unit)?) { inferenceStartListener = listener }
    override fun setInferenceTurnId(turnId: String?) { inferenceTurnId = turnId }

    override fun release() {
        recorder.release()
        debugLastCapture = null
        recognizer?.release()
        recognizer = null
        processing.set(false)
        finalizationPending.set(false)
        if (stateMachine.state != SttState.UNINITIALIZED) moveTo(SttState.UNINITIALIZED)
    }

    private suspend fun processCaptured(audio: CapturedAudio, language: SttLanguage, preferredLanguage: UserLanguageProfile) {
        if (!processing.compareAndSet(false, true)) return
        try {
            if (stateMachine.state == SttState.LISTENING) moveTo(SttState.PROCESSING, vadState = VadState.TRANSCRIBING)
            if (BuildConfig.DEBUG) debugLastCapture = audio.copy(samples = audio.samples.copyOf())
            activePreferredLanguage = preferredLanguage
            val result = transcribe(audio.samples, language, audio.durationMs)
            moveTo(SttState.RESULT, vadState = VadState.TEXT_READY, result = result)
            VokieLog.stt("Result ready: audio=${result.audioDurationMs}ms processing=${result.processingTimeMs}ms rtf=${result.realTimeFactor} stt_engine=sensevoice")
            // Give turn observers a bounded chance to consume RESULT, then make the
            // engine immediately reusable rather than leaving the UI in a stale result.
            kotlinx.coroutines.delay(50)
            moveTo(SttState.READY, vadState = VadState.WAITING_FOR_SPEECH)
        } catch (e: Throwable) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            fail(mapSttFailure(e, SttErrorCode.STT_INFERENCE_FAILED, "SenseVoice recognition failed."))
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
    ) {
        stateMachine.moveTo(state)
        _status.value = SttStatus(state, vadState, result, failure, modelLoadTimeMs)
    }

    private fun fail(failure: SttFailure) {
        if (stateMachine.state != SttState.ERROR) moveTo(SttState.ERROR, failure = failure)
        else _status.value = _status.value.copy(failure = failure)
        VokieLog.stt("${failure.code}: ${failure.userMessage} stt_engine=sensevoice")
    }
}
