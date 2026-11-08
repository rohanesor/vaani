package com.vokie.translation

import android.content.Context
import com.vokie.communication.VokieLog
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Approved local NLLB CT2 backend. Model staging remains separate from APK assets. */
class Ctranslate2TranslationEngine(context: Context) : TranslationEngine {
    // CT2 archives are an optional, separately staged native dependency. A missing or
    // incompatible library must leave offline translation explicitly unavailable, not
    // prevent the communication application from starting.
    private val native = runCatching { Ctranslate2Native() }.getOrNull()
    private val lock = Mutex()
    // Kept outside BundledModelStore's atomically replaced assets directory.
    private val directory = File(context.filesDir, "ct2/nllb600m")
    private var handle = 0L

    override suspend fun translate(text: String, sourceLanguage: VokieLanguage, targetLanguage: VokieLanguage): TranslationResult {
        VokieLog.translation("TRANSLATION_REQUEST source=${sourceLanguage.code} target=${targetLanguage.code}")
        if (sourceLanguage == targetLanguage) return TranslationResult(text, sourceLanguage, targetLanguage, text, TranslationStatus.PASSTHROUGH)
        if (text.isBlank()) return TranslationResult(text.ifBlank { " " }, sourceLanguage, targetLanguage, status = TranslationStatus.FAILED, error = "Translation text is empty")
        return lock.withLock {
            val startedAt = System.nanoTime()
            try {
                val backend = native ?: return@withLock TranslationResult(
                    text, sourceLanguage, targetLanguage, status = TranslationStatus.UNAVAILABLE,
                    error = "Offline translation backend is unavailable on this device."
                )
                val model = File(directory, "model.bin")
                val tokenizer = File(directory, "sentencepiece.bpe.model")
                val config = File(directory, "config.json")
                val vocabulary = File(directory, "shared_vocabulary.json")
                if (model.length() != MODEL_BYTES || !tokenizer.isFile || !config.isFile || !vocabulary.isFile) {
                    VokieLog.translation("TRANSLATION_ASSET_MISSING directory=${directory.absolutePath} modelBytes=${model.length()} tokenizer=${tokenizer.isFile} config=${config.isFile} vocabulary=${vocabulary.isFile}")
                    return@withLock TranslationResult(text, sourceLanguage, targetLanguage, status = TranslationStatus.UNAVAILABLE, error = "Approved local CT2 model is not staged.")
                }
                VokieLog.translation("TRANSLATION_ASSET_FOUND modelBytes=${model.length()}")
                if (handle == 0L) {
                    VokieLog.translation("TRANSLATION_RUNTIME_INIT")
                    handle = withContext(Dispatchers.IO) { backend.nativeLoadModel(directory.absolutePath) }
                    VokieLog.translation("TRANSLATION_RUNTIME_READY")
                }
                check(handle != 0L) { "CT2 returned an empty model handle" }
                VokieLog.translation("TRANSLATION_INFER_START")
                val output = withContext(Dispatchers.Default) { backend.nativeTranslate(handle, sourceLanguage.code, targetLanguage.code, text).trim() }
                check(output.isNotBlank()) { "CT2 returned empty output" }
                VokieLog.translation("TRANSLATION_INFER_SUCCESS latencyMs=${(System.nanoTime() - startedAt) / 1_000_000}")
                TranslationResult(text, sourceLanguage, targetLanguage, output, TranslationStatus.TRANSLATED)
            } catch (error: Throwable) {
                VokieLog.translation("TRANSLATION_INFER_FAILURE type=${error.javaClass.simpleName} message=${error.message}")
                TranslationResult(text, sourceLanguage, targetLanguage, status = TranslationStatus.FAILED, error = error.message ?: "Local CT2 translation failed")
            }
        }
    }
    fun release() { val old = handle; handle = 0L; if (old != 0L) native?.nativeUnloadModel(old) }
    private companion object { const val MODEL_BYTES = 619_704_329L }
}
