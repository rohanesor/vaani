package com.vokie.stt

import android.content.Context
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.flow.StateFlow
import java.io.File

const val WHISPER_SAMPLE_RATE = 16_000

/** SIH 10-language support. SenseVoice/Whisper models support all listed languages. */
enum class SttLanguage(
    val whisperCode: String,
    val messageLanguage: VokieLanguage,
    val nativeName: String,
    val displayName: String,
) {
    ENGLISH("en", VokieLanguage.EN, "English", "English"),
    HINDI("hi", VokieLanguage.HI, "हिन्दी", "Hindi"),
    GUJARATI("gu", VokieLanguage.GU, "ગુજરાતી", "Gujarati"),
    MARATHI("mr", VokieLanguage.MR, "मराठी", "Marathi"),
    KANNADA("kn", VokieLanguage.KN, "ಕನ್ನಡ", "Kannada"),
    MALAYALAM("ml", VokieLanguage.ML, "മലയാളം", "Malayalam"),
    TAMIL("ta", VokieLanguage.TA, "தமிழ்", "Tamil"),
    TELUGU("te", VokieLanguage.TE, "తెలుగు", "Telugu"),
    ODIA("or", VokieLanguage.OR, "ଓଡ଼ିଆ", "Odia"),
    BENGALI("bn", VokieLanguage.BN, "বাংলা", "Bengali");

    companion object {
        fun fromWhisperCode(code: String): SttLanguage? = entries.firstOrNull { it.whisperCode == code.lowercase() }
        fun fromMessageCode(code: String): SttLanguage? = entries.firstOrNull { it.messageLanguage.code == code.uppercase() }
    }
}

interface SttModel {
    val id: String
    val displayName: String
    val fileName: String
    val format: String
    val quantization: String
    val approximateSizeBytes: Long
    val approximateRamBytes: Long
    val sha256: String
    val supportedLanguages: Set<SttLanguage>
    fun localFile(context: Context): File
}

object WhisperTinyMultilingualQ5_1 : SttModel {
    override val id = "whisper-tiny-multilingual-q5_1"
    override val displayName = "Whisper tiny multilingual Q5_1"
    override val fileName = "ggml-tiny-q5_1.bin"
    override val format = "whisper.cpp GGML"
    override val quantization = "Q5_1"
    override val approximateSizeBytes = 32L * 1024 * 1024
    override val approximateRamBytes = 273L * 1024 * 1024
    override val sha256 = "818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7"
    override val supportedLanguages = SttLanguage.entries.toSet()
    override fun localFile(context: Context) = File(File(context.filesDir, "models/stt"), fileName)
}

enum class SttState { UNINITIALIZED, MODEL_MISSING, IMPORTING, VALIDATING, INITIALIZING, READY, LISTENING, PROCESSING, RESULT, ERROR }
enum class VadState { WAITING_FOR_SPEECH, SPEECH_DETECTED, RECORDING, SILENCE_DETECTED, TRANSCRIBING, TEXT_READY }
enum class SttErrorCode {
    MODEL_MISSING, MODEL_LOAD_FAILED, MIC_PERMISSION_DENIED, MIC_UNAVAILABLE, AUDIO_CAPTURE_FAILED,
    STT_INITIALIZATION_FAILED, STT_INFERENCE_FAILED, UNSUPPORTED_LANGUAGE, OUT_OF_MEMORY, NO_SPEECH
}

data class SttFailure(val code: SttErrorCode, val userMessage: String, val cause: Throwable? = null)
class SttException(val code: SttErrorCode, message: String, cause: Throwable? = null) : Exception(message, cause)

fun mapSttFailure(error: Throwable, fallback: SttErrorCode, message: String): SttFailure = when (error) {
    is SttException -> SttFailure(error.code, error.message ?: message, error)
    is OutOfMemoryError -> SttFailure(SttErrorCode.OUT_OF_MEMORY, "Not enough memory to run local speech recognition.", error)
    is UnsatisfiedLinkError -> SttFailure(SttErrorCode.STT_INITIALIZATION_FAILED, "The local whisper.cpp engine is unavailable.", error)
    else -> SttFailure(fallback, message, error)
}

/** Result language is the explicit Whisper language requested from the user's input profile. */
data class SttResult(
    val text: String,
    val language: SttLanguage,
    val confidence: Float? = null,
    val processingTimeMs: Long,
    val audioDurationMs: Long,
    val timestamp: Long,
) {
    val realTimeFactor: Double? get() = calculateRealTimeFactor(processingTimeMs, audioDurationMs)
}

data class SttStatus(
    val state: SttState,
    val vadState: VadState = VadState.WAITING_FOR_SPEECH,
    val result: SttResult? = null,
    val failure: SttFailure? = null,
    val modelLoadTimeMs: Long? = null,
    val installedModelBytes: Long = 0,
)

fun resolveProductionSttLanguage(preferred: UserLanguageProfile): SttLanguage = preferred.inputSttLanguage

fun audioDurationMs(sampleCount: Int, sampleRate: Int = WHISPER_SAMPLE_RATE): Long {
    require(sampleCount >= 0 && sampleRate > 0)
    return sampleCount.toLong() * 1_000L / sampleRate
}

fun calculateRealTimeFactor(processingTimeMs: Long, audioDurationMs: Long): Double? =
    if (processingTimeMs < 0 || audioDurationMs <= 0) null else processingTimeMs.toDouble() / audioDurationMs

interface SttEngine {
    val status: StateFlow<SttStatus>
    suspend fun initialize()
    suspend fun start(language: SttLanguage, preferredLanguage: UserLanguageProfile, finalizeOnVad: Boolean = true)
    suspend fun stop()
    suspend fun transcribe(audio: FloatArray, language: SttLanguage, audioDurationMs: Long): SttResult
    /** Debug/benchmark hook at the actual inference boundary; production behavior is unchanged. */
    fun setInferenceStartListener(listener: ((turnId: String?) -> Unit)?) {}
    /** Binds the next inference to the current turn without changing STT semantics. */
    fun setInferenceTurnId(turnId: String?) {}
    fun release()
}

internal class SttStateMachine(initial: SttState = SttState.UNINITIALIZED) {
    var state: SttState = initial
        private set

    fun moveTo(next: SttState) {
        val allowed = when (state) {
            SttState.UNINITIALIZED -> setOf(SttState.INITIALIZING, SttState.MODEL_MISSING)
            SttState.MODEL_MISSING -> setOf(SttState.IMPORTING, SttState.INITIALIZING, SttState.UNINITIALIZED)
            SttState.IMPORTING -> setOf(SttState.VALIDATING, SttState.ERROR, SttState.MODEL_MISSING)
            SttState.VALIDATING -> setOf(SttState.INITIALIZING, SttState.ERROR, SttState.MODEL_MISSING)
            SttState.INITIALIZING -> setOf(SttState.READY, SttState.MODEL_MISSING, SttState.ERROR, SttState.UNINITIALIZED)
            SttState.READY -> setOf(SttState.READY, SttState.LISTENING, SttState.INITIALIZING, SttState.UNINITIALIZED)
            SttState.LISTENING -> setOf(SttState.PROCESSING, SttState.READY, SttState.ERROR, SttState.UNINITIALIZED)
            SttState.PROCESSING -> setOf(SttState.RESULT, SttState.ERROR, SttState.UNINITIALIZED)
            SttState.RESULT -> setOf(SttState.READY, SttState.LISTENING, SttState.INITIALIZING, SttState.UNINITIALIZED, SttState.ERROR)
            SttState.ERROR -> setOf(SttState.READY, SttState.IMPORTING, SttState.INITIALIZING, SttState.LISTENING, SttState.UNINITIALIZED)
        }
        check(next in allowed) { "Invalid STT transition: $state -> $next" }
        state = next
    }
}
