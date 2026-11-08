package com.vokie.tts

import android.os.SystemClock
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import com.vokie.communication.VokieLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Lazy, single-context MMS/VITS implementation backed by the official sherpa-onnx Android AAR. */
class SherpaOnnxTtsEngine(
    private val modelManager: TtsModelManager,
    private val audioPlayer: VokieAudioPlayer,
    private val numThreads: Int = DEFAULT_THREADS,
) : TtsEngine {
    fun playbackSnapshot(): PlaybackProgress? = audioPlayer.playbackSnapshot()

    private val stateMachine = TtsStateMachine()
    private val operationMutex = Mutex()
    private val stopRequested = AtomicBoolean(false)
    private val _status = MutableStateFlow(TtsStatus(TtsState.UNINITIALIZED))
    override val status: StateFlow<TtsStatus> = _status.asStateFlow()
    private var tts: OfflineTts? = null
    private var activeLanguage: TtsLanguage? = null
    private var firstSynthesis = true
    private val engineInstanceId = nextEngineInstanceId.incrementAndGet()
    private var nativeSessionId: Long? = null

    init {
        require(numThreads in 1..4) { "TTS inference threads must be between 1 and 4" }
        VokieLog.tts("TTS_ENGINE_CREATED engine_instance_id=$engineInstanceId threads=$numThreads")
    }

    override suspend fun initialize(language: TtsLanguage) = operationMutex.withLock {
        initializeLocked(language)
    }

    override suspend fun synthesize(text: String, language: TtsLanguage, speed: Float): Pair<AudioBuffer, TtsResult> = operationMutex.withLock {
        val requestStartNs = SystemClock.elapsedRealtimeNanos()
        val normalized = text.trim()
        val preprocessingMs = (SystemClock.elapsedRealtimeNanos() - requestStartNs) / 1_000_000.0
        if (normalized.isEmpty() || normalized.length > MAX_TEXT_CHARS) throw TtsException(TtsErrorCode.SYNTHESIS_FAILED, "Speech text must contain 1 to $MAX_TEXT_CHARS characters.")
        validateTtsSpeed(speed)
        val requiresLoad = tts == null || activeLanguage != language
        if (requiresLoad) initializeLocked(language)
        val modelLoadForRequestMs = if (requiresLoad) status.value.modelLoadTimeMs ?: 0L else 0L
        val nativeTts = tts ?: throw status.value.failure?.let { TtsException(it.code, it.userMessage, it.cause) }
            ?: TtsException(TtsErrorCode.MODEL_LOAD_FAILED, "TTS model is not ready")
        stopRequested.set(false)
        moveTo(TtsState.SYNTHESIZING, language)
        try {
            val nativeStarted = SystemClock.elapsedRealtime()
            val generated = withContext(Dispatchers.Default) { nativeTts.generate(normalized, sid = 0, speed = speed) }
            val nativePipelineMs = SystemClock.elapsedRealtime() - nativeStarted
            if (stopRequested.get()) throw TtsException(TtsErrorCode.SYNTHESIS_FAILED, "Speech synthesis was stopped.")
            val bufferStartedNs = SystemClock.elapsedRealtimeNanos()
            require(generated.sampleRate > 0 && generated.samples.isNotEmpty()) { "sherpa-onnx returned empty audio" }
            require(generated.samples.size <= generated.sampleRate * MAX_AUDIO_SECONDS) { "Generated audio exceeds the safety limit" }
            // AudioBuffer wraps sherpa's FloatArray without copying it.
            val audio = AudioBuffer(generated.samples, generated.sampleRate)
            val audioBufferMs = (SystemClock.elapsedRealtimeNanos() - bufferStartedNs) / 1_000_000.0
            val synthesisTime = nativePipelineMs
            val result = TtsResult(
                normalized.length, language, synthesisTime, audio.durationMs,
                System.currentTimeMillis(), firstSynthesis,
                preprocessingTimeMs = preprocessingMs,
                nativePipelineTimeMs = nativePipelineMs,
                audioBufferTimeMs = audioBufferMs,
                modelLoadTimeMs = modelLoadForRequestMs,
                tokenCount = null,
                modelInstanceId = nativeSessionId,
            )
            firstSynthesis = false
            moveTo(TtsState.READY, language, result = result)
            VokieLog.tts("TTS_PROFILE engine_instance_id=$engineInstanceId model_instance_id=$nativeSessionId language=${language.iso6393} text_length=${normalized.length} token_count=unavailable model_load_ms=$modelLoadForRequestMs preprocessing_ms=$preprocessingMs native_pipeline_ms=$nativePipelineMs audio_buffer_ms=$audioBufferMs generated_audio_ms=${audio.durationMs} rtf=${result.realTimeFactor}")
            VokieLog.tts("Synthesized ${normalized.length} chars: audio=${audio.durationMs}ms processing=${synthesisTime}ms rtf=${result.realTimeFactor}")
            audio to result
        } catch (error: Throwable) {
            val failure = mapTtsFailure(error, TtsErrorCode.SYNTHESIS_FAILED, "Local speech synthesis failed.")
            fail(failure)
            throw TtsException(failure.code, failure.userMessage, error)
        }
    }

    override suspend fun play(audio: AudioBuffer, emergency: Boolean) {
        if (stopRequested.get()) throw TtsException(TtsErrorCode.AUDIO_OUTPUT_FAILED, "Speech playback was stopped.")
        moveTo(TtsState.PLAYING, activeLanguage, result = _status.value.result)
        try {
            audioPlayer.play(audio, emergency)
            moveTo(TtsState.COMPLETED, activeLanguage, result = _status.value.result)
        } catch (error: Throwable) {
            val failure = mapTtsFailure(error, TtsErrorCode.AUDIO_OUTPUT_FAILED, "Speech audio could not be played.")
            fail(failure)
            throw TtsException(failure.code, failure.userMessage, error)
        }
    }

    override suspend fun stop() {
        stopRequested.set(true)
        audioPlayer.stop()
        if (stateMachine.state == TtsState.PLAYING) moveTo(TtsState.READY, activeLanguage, result = _status.value.result)
    }

    override fun release() {
        stopRequested.set(true)
        audioPlayer.release()
        releaseModel()
        modelManager.releaseUnusedModel(null)
        if (stateMachine.state != TtsState.UNINITIALIZED) moveTo(TtsState.UNINITIALIZED)
    }

    private suspend fun initializeLocked(language: TtsLanguage) {
        if (tts != null && activeLanguage == language) {
            if (stateMachine.state in setOf(TtsState.ERROR, TtsState.COMPLETED)) moveTo(TtsState.READY, language)
            return
        }
        moveTo(TtsState.INITIALIZING, language)
        if (!modelManager.isInstalled(language)) {
            moveTo(TtsState.MODEL_MISSING, language, failure = TtsFailure(TtsErrorCode.MODEL_MISSING, "Bundled TTS assets could not be prepared for ${language.nativeName}."))
            return
        }
        val started = SystemClock.elapsedRealtime()
        try {
            tts?.release(); tts = null
            activeLanguage = null
            modelManager.releaseUnusedModel(language)
            val vits = OfflineTtsVitsModelConfig(
                model = modelManager.modelFile(language).absolutePath,
                tokens = modelManager.tokensFile(language).absolutePath,
                lexicon = "",
                dataDir = if (language in MMS_CHARACTER_LANGUAGES) "" else modelManager.phonemizerDirectory().absolutePath,
                dictDir = "",
                lengthScale = 1.0f,
            )
            val modelConfig = OfflineTtsModelConfig(vits = vits, numThreads = numThreads, debug = false, provider = "cpu")
            val loaded = withContext(Dispatchers.IO) { OfflineTts(config = OfflineTtsConfig(model = modelConfig, maxNumSentences = 1)) }
            tts = loaded; activeLanguage = language; firstSynthesis = true
            nativeSessionId = nextNativeSessionId.incrementAndGet()
            val loadTime = SystemClock.elapsedRealtime() - started
            val size = modelManager.installedSizeBytes(language)
            moveTo(TtsState.READY, language, modelLoadTimeMs = loadTime, installedModelBytes = size)
            VokieLog.tts("TTS_MODEL_LOADED engine_instance_id=$engineInstanceId model_instance_id=$nativeSessionId model_instance_count=1 language=${language.iso6393} load_ms=$loadTime")
            VokieLog.tts("Loaded bundled MMS-TTS ${language.iso6393} in ${loadTime}ms")
        } catch (error: Throwable) {
            tts?.release(); tts = null; nativeSessionId = null; activeLanguage = null
            val failure = mapTtsFailure(error, TtsErrorCode.MODEL_LOAD_FAILED, "The ${language.nativeName} TTS model could not be loaded.")
            moveTo(TtsState.MODEL_LOAD_FAILED, language, failure = failure)
        }
    }

    private fun moveTo(
        state: TtsState,
        language: TtsLanguage? = _status.value.activeLanguage,
        result: TtsResult? = null,
        failure: TtsFailure? = null,
        modelLoadTimeMs: Long? = _status.value.modelLoadTimeMs,
        installedModelBytes: Long = _status.value.installedModelBytes,
    ) {
        stateMachine.moveTo(state)
        _status.value = TtsStatus(state, language, result, failure, modelLoadTimeMs, installedModelBytes)
    }

    private fun releaseModel() {
        tts?.release(); tts = null
        nativeSessionId = null
        activeLanguage = null
        firstSynthesis = true
    }

    private fun fail(failure: TtsFailure) {
        if (stateMachine.state != TtsState.ERROR) moveTo(TtsState.ERROR, activeLanguage, failure = failure)
        else _status.value = _status.value.copy(failure = failure)
        VokieLog.tts("${failure.code}: ${failure.userMessage}")
    }

    companion object {
        const val DEFAULT_THREADS = 2
        const val MAX_TEXT_CHARS = 500
        const val MAX_AUDIO_SECONDS = 120
        private val nextEngineInstanceId = AtomicLong(0)
        private val nextNativeSessionId = AtomicLong(0)
        /** MMS-TTS languages use a Unicode character frontend and do not require espeak-ng data. */
        private val MMS_CHARACTER_LANGUAGES = setOf(TtsLanguage.TAMIL, TtsLanguage.GUJARATI)
    }
}
