package com.vokie.tts

import com.vokie.domain.model.MessageType
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.flow.StateFlow

const val SHERPA_ONNX_VERSION = "1.13.7"
const val DEFAULT_TTS_SPEED = 1.0f
const val MIN_TTS_SPEED = 0.75f
const val MAX_TTS_SPEED = 1.5f

/** SIH 10-language TTS support. All MMS/VITS models are available (bundled or downloadable). */
enum class TtsLanguage(
    val iso6393: String,
    val messageLanguage: VokieLanguage,
    val nativeName: String,
) {
    ENGLISH("eng", VokieLanguage.EN, "English"),
    HINDI("hin", VokieLanguage.HI, "हिन्दी"),
    GUJARATI("guj", VokieLanguage.GU, "ગુજરાતી"),
    MARATHI("mar", VokieLanguage.MR, "मराठी"),
    KANNADA("kan", VokieLanguage.KN, "ಕನ್ನಡ"),
    MALAYALAM("mal", VokieLanguage.ML, "മലയാളം"),
    TAMIL("tam", VokieLanguage.TA, "தமிழ்"),
    TELUGU("tel", VokieLanguage.TE, "తెలుగు"),
    ODIA("ory", VokieLanguage.OR, "ଓଡ଼ିଆ"),
    BENGALI("ben", VokieLanguage.BN, "বাংলা");

    companion object {
        fun fromMessageCode(code: String): TtsLanguage? = entries.firstOrNull { it.messageLanguage.code == code.uppercase() }
        fun fromIso6393(code: String): TtsLanguage? = entries.firstOrNull { it.iso6393 == code.lowercase() }
    }
}

enum class TtsState { UNINITIALIZED, MODEL_MISSING, IMPORTING, VALIDATING, INITIALIZING, READY, SYNTHESIZING, PLAYING, COMPLETED, ERROR, MODEL_LOAD_FAILED }
enum class TtsErrorCode { MODEL_MISSING, MODEL_LOAD_FAILED, MODEL_INVALID, UNSUPPORTED_LANGUAGE, SYNTHESIS_FAILED, AUDIO_OUTPUT_FAILED, AUDIO_FOCUS_FAILED, OUT_OF_MEMORY }
enum class MessageTtsState { QUEUED, SYNTHESIZING, PLAYING, COMPLETED, FAILED }

data class TtsFailure(val code: TtsErrorCode, val userMessage: String, val cause: Throwable? = null)
class TtsException(val code: TtsErrorCode, message: String, cause: Throwable? = null) : Exception(message, cause)

data class AudioBuffer(val samples: FloatArray, val sampleRate: Int) {
    val durationMs: Long get() = if (sampleRate > 0) samples.size.toLong() * 1_000L / sampleRate else 0
}

data class TtsResult(
    val textLength: Int,
    val language: TtsLanguage,
    val synthesisTimeMs: Long,
    val audioDurationMs: Long,
    val timestamp: Long,
    val firstSynthesisForModel: Boolean,
    // sherpa-onnx exposes generate() as one native frontend+inference+waveform call;
    // tokenization/inference/postprocessing cannot be separated without changing runtime.
    val preprocessingTimeMs: Double = 0.0,
    val nativePipelineTimeMs: Long = synthesisTimeMs,
    val audioBufferTimeMs: Double = 0.0,
    val modelLoadTimeMs: Long = 0L,
    val tokenCount: Int? = null,
    val modelInstanceId: Long? = null,
) {
    val realTimeFactor: Double? get() = calculateTtsRealTimeFactor(synthesisTimeMs, audioDurationMs)
}

data class TtsStatus(
    val state: TtsState,
    val activeLanguage: TtsLanguage? = null,
    val result: TtsResult? = null,
    val failure: TtsFailure? = null,
    val modelLoadTimeMs: Long? = null,
    val installedModelBytes: Long = 0,
)

fun calculateTtsRealTimeFactor(synthesisTimeMs: Long, audioDurationMs: Long): Double? =
    if (synthesisTimeMs < 0 || audioDurationMs <= 0) null else synthesisTimeMs.toDouble() / audioDurationMs

fun validateTtsSpeed(speed: Float): Float {
    require(speed in MIN_TTS_SPEED..MAX_TTS_SPEED) { "Speech speed must be between ${MIN_TTS_SPEED}x and ${MAX_TTS_SPEED}x" }
    return speed
}

interface TtsEngine {
    val status: StateFlow<TtsStatus>
    suspend fun initialize(language: TtsLanguage)
    suspend fun synthesize(text: String, language: TtsLanguage, speed: Float = DEFAULT_TTS_SPEED): Pair<AudioBuffer, TtsResult>
    suspend fun play(audio: AudioBuffer, emergency: Boolean = false)
    suspend fun stop()
    fun release()
}

internal class TtsStateMachine(initial: TtsState = TtsState.UNINITIALIZED) {
    var state = initial
        private set

    fun moveTo(next: TtsState) {
        val allowed = when (state) {
            TtsState.UNINITIALIZED -> setOf(TtsState.INITIALIZING, TtsState.MODEL_MISSING)
            TtsState.MODEL_MISSING -> setOf(TtsState.IMPORTING, TtsState.INITIALIZING, TtsState.UNINITIALIZED)
            TtsState.IMPORTING -> setOf(TtsState.VALIDATING, TtsState.ERROR, TtsState.MODEL_MISSING)
            TtsState.VALIDATING -> setOf(TtsState.INITIALIZING, TtsState.ERROR, TtsState.MODEL_MISSING)
            TtsState.INITIALIZING -> setOf(TtsState.READY, TtsState.MODEL_MISSING, TtsState.MODEL_LOAD_FAILED, TtsState.ERROR, TtsState.UNINITIALIZED)
            TtsState.READY -> setOf(TtsState.SYNTHESIZING, TtsState.PLAYING, TtsState.INITIALIZING, TtsState.UNINITIALIZED)
            TtsState.SYNTHESIZING -> setOf(TtsState.PLAYING, TtsState.READY, TtsState.ERROR, TtsState.UNINITIALIZED)
            TtsState.PLAYING -> setOf(TtsState.COMPLETED, TtsState.ERROR, TtsState.READY, TtsState.UNINITIALIZED)
            TtsState.COMPLETED -> setOf(TtsState.SYNTHESIZING, TtsState.INITIALIZING, TtsState.READY, TtsState.UNINITIALIZED)
            TtsState.ERROR -> setOf(TtsState.IMPORTING, TtsState.INITIALIZING, TtsState.SYNTHESIZING, TtsState.READY, TtsState.UNINITIALIZED)
            TtsState.MODEL_LOAD_FAILED -> setOf(TtsState.IMPORTING, TtsState.INITIALIZING, TtsState.UNINITIALIZED)
        }
        check(next in allowed) { "Invalid TTS transition: $state -> $next" }
        state = next
    }
}

fun mapTtsFailure(error: Throwable, fallback: TtsErrorCode, message: String): TtsFailure = when (error) {
    is TtsException -> TtsFailure(error.code, error.message ?: message, error)
    is OutOfMemoryError -> TtsFailure(TtsErrorCode.OUT_OF_MEMORY, "Not enough memory to synthesize speech locally.", error)
    is UnsatisfiedLinkError -> TtsFailure(TtsErrorCode.MODEL_LOAD_FAILED, "The sherpa-onnx native engine is unavailable.", error)
    else -> TtsFailure(fallback, message, error)
}

data class TtsQueueItem(val messageId: String, val text: String, val language: TtsLanguage, val messageType: MessageType)
