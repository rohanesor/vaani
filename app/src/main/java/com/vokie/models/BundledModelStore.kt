package com.vokie.models

import android.content.Context
import com.vokie.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** Extracts only APK-bundled model bytes. It has no network or document-picker path. */
class BundledModelStore(private val context: Context) {
    private val root = File(context.filesDir, "models")

    suspend fun prepare(): Unit = withContext(Dispatchers.IO) {
        val manifest = manifest()
        if (isValid(manifest)) return@withContext
        val temporary = File(context.filesDir, ".models.extracting")
        temporary.deleteRecursively(); temporary.mkdirs()
        try {
            manifest.files.filterKeys { it in bundledFiles }.forEach { (relative, spec) ->
                val destination = File(temporary, relative).also { it.parentFile!!.mkdirs() }
                context.assets.open("models/$relative").use { input ->
                    FileOutputStream(destination).use { output -> input.copyTo(output, BUFFER) }
                }
                check(destination.length() == spec.sizeBytes && destination.sha256() == spec.sha256) { "Bundled model integrity check failed: $relative" }
            }
            // Piper voices require the shared phonemizer data directory. It is copied from
            // the approved sherpa-onnx Hindi bundle and shared by all Piper voices.
            copyAssetTree("models/tts/espeak-ng-data", File(temporary, "tts/espeak-ng-data"))
            check(File(temporary, "tts/espeak-ng-data/phondata").isFile) { "Bundled espeak-ng-data is incomplete" }
            FileOutputStream(File(temporary, "manifest.json")).use { it.write(manifest.raw.toByteArray()) }
            val old = File(context.filesDir, ".models.previous")
            old.deleteRecursively()
            if (root.exists() && !root.renameTo(old)) error("Unable to replace prior model extraction")
            if (!temporary.renameTo(root)) { old.renameTo(root); error("Unable to finalize model extraction") }
            old.deleteRecursively()
        } catch (e: Throwable) { temporary.deleteRecursively(); throw e }
    }

    fun sttFile() = File(root, "stt/ggml-tiny-q5_1.bin")
    fun ttsDirectory(iso6393: String) = File(root, "tts/$iso6393")
    fun spec(relative: String): ModelFile = manifest().files[relative] ?: error("Missing bundled model specification: $relative")
    fun manifest(): ModelManifest {
        val raw = context.assets.open("models/manifest.json").bufferedReader().use { it.readText() }
        val entries = JSONObject(raw).getJSONObject("files")
        val files = entries.keys().asSequence().associateWith { key ->
            val value = entries.getJSONObject(key)
            ModelFile(value.getLong("sizeBytes"), value.getString("sha256").lowercase())
        }
        return ModelManifest(raw, files)
    }
    private fun isValid(manifest: ModelManifest) = bundledFiles.all { relative ->
        val spec = manifest.files[relative] ?: return@all false
        val file = File(root, relative)
        file.isFile && file.length() == spec.sizeBytes && file.sha256() == spec.sha256
    } && File(root, "tts/espeak-ng-data/phondata").isFile
    private fun File.sha256(): String = inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(BUFFER)
        while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
    data class ModelFile(val sizeBytes: Long, val sha256: String)
    data class ModelManifest(val raw: String, val files: Map<String, ModelFile>)
    private fun copyAssetTree(assetPath: String, destination: File) {
        destination.mkdirs()
        context.assets.list(assetPath).orEmpty().forEach { child ->
            val source = "$assetPath/$child"; val target = File(destination, child)
            if (context.assets.list(source).orEmpty().isNotEmpty()) copyAssetTree(source, target)
            else context.assets.open(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output, BUFFER) } }
        }
    }
    private val bundledFiles: Set<String>
        get() = BASE_BUNDLED_FILES + if (BuildConfig.BUNDLE_ALL_TTS_MODELS) ALL_TTS_FILES else emptySet()

    private companion object {
        const val BUFFER = 64 * 1024
        val BASE_BUNDLED_FILES = setOf(
            "stt/ggml-tiny-q5_1.bin", "tts/eng/model.onnx", "tts/eng/tokens.txt", "tts/hin/model.onnx", "tts/hin/tokens.txt",
            "tts/tam/model.onnx", "tts/tam/tokens.txt", "tts/guj/model.onnx", "tts/guj/tokens.txt",
        )
        val ALL_TTS_FILES = setOf("eng", "hin", "guj", "mar", "kan", "mal", "tam", "tel", "ory", "ben")
            .flatMapTo(mutableSetOf()) { language -> setOf("tts/$language/model.onnx", "tts/$language/tokens.txt") }
    }
}
