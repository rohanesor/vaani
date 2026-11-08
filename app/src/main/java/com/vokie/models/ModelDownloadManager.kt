package com.vokie.models

import android.content.Context
import android.net.ConnectivityManager
import android.os.StatFs
import com.vokie.BuildConfig
import com.vokie.tts.TtsLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface DownloadState {
    data object Idle : DownloadState
    data class Downloading(val language: TtsLanguage, val percent: Int, val downloadedBytes: Long = 0, val totalBytes: Long = 0) : DownloadState
    data class Cancelled(val language: TtsLanguage, val downloadedBytes: Long, val totalBytes: Long) : DownloadState
    data class Complete(val language: TtsLanguage) : DownloadState
    data class Error(val language: TtsLanguage, val message: String, val downloadedBytes: Long = 0, val totalBytes: Long = 0) : DownloadState
}

/** User-confirmed one-time language pack delivery. Runtime speech never uses the network. */
class ModelDownloadManager(private val context: Context, private val bundled: BundledModelStore) {
    private val _state = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val state: StateFlow<DownloadState> = _state.asStateFlow()

    private val transferMutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)
    private val activeConnection = AtomicReference<HttpURLConnection?>(null)

    /** Called on IO: disconnect unblocks a pending socket read. Never deletes partial files. */
    suspend fun cancel() = withContext(Dispatchers.IO) {
        cancelRequested.set(true)
        activeConnection.get()?.disconnect()
    }

    suspend fun download(language: TtsLanguage) = withContext(Dispatchers.IO) {
        check(transferMutex.tryLock()) { "A language download is already in progress." }
        cancelRequested.set(false)
        val root = File(context.filesDir, "models/tts")
        val target = File(root, language.iso6393)
        val temporary = File(root, ".${language.iso6393}.downloading")
        var total = 0L
        fun retained() = listOf("model.onnx", "tokens.txt").sumOf { File(temporary, it).length() }.coerceIn(0, total)
        try {
            require(language != TtsLanguage.ENGLISH) { "English is included in iTantra." }
            val model = bundled.spec("tts/${language.iso6393}/model.onnx")
            val tokens = bundled.spec("tts/${language.iso6393}/tokens.txt")
            total = model.sizeBytes + tokens.sizeBytes
            // Publish before connectivity/space checks so every failure has a selected language.
            _state.value = DownloadState.Downloading(language, downloadPercent(retained(), total), retained(), total)
            requireFreeSpace((total - retained()).coerceAtLeast(0) + SAFETY_BYTES)
            root.mkdirs(); temporary.mkdirs()
            downloadFile(language, "model.onnx", model, File(temporary, "model.onnx"), total, 0)
            downloadFile(language, "tokens.txt", tokens, File(temporary, "tokens.txt"), total, model.sizeBytes)
            verify(File(temporary, "model.onnx"), model)
            verify(File(temporary, "tokens.txt"), tokens)
            checkCancelled()
            // Keep the previously installed pack intact if atomic directory replacement fails.
            val previous = File(root, ".${language.iso6393}.previous")
            check(!previous.exists()) { "A previous installation needs recovery before downloading again." }
            if (target.exists()) check(target.renameTo(previous)) { "Could not preserve installed pack." }
            if (!temporary.renameTo(target)) {
                if (previous.exists()) check(previous.renameTo(target)) { "Could not restore installed pack." }
                error("Could not finalize downloaded ${language.nativeName} pack")
            }
            previous.deleteRecursively()
            _state.value = DownloadState.Complete(language)
        } catch (error: Throwable) {
            // Retain incomplete bytes on network failure/cancel for Range resume on retry.
            if (cancelRequested.get() || error is CancellationException) {
                _state.value = DownloadState.Cancelled(language, retained(), total)
                throw CancellationException("Language download cancelled").also { it.initCause(error) }
            }
            _state.value = DownloadState.Error(language, error.message ?: "Language pack download failed", retained(), total)
            throw error
        } finally {
            activeConnection.getAndSet(null)?.disconnect()
            transferMutex.unlock()
        }
    }

    private fun checkCancelled() {
        if (cancelRequested.get()) throw CancellationException("Language download cancelled")
    }

    private fun downloadFile(language: TtsLanguage, name: String, spec: BundledModelStore.ModelFile, target: File, total: Long, completedBytes: Long) {
        checkCancelled()
        if (target.length() > spec.sizeBytes) check(target.delete()) { "Could not remove invalid partial pack" }
        if (target.isFile && target.length() == spec.sizeBytes) {
            try { verify(target, spec); return } catch (_: IllegalStateException) { check(target.delete()) }
        }
        val existing = target.takeIf(File::isFile)?.length() ?: 0L
        val url = URL("${BuildConfig.MODEL_CDN_BASE_URL}/models/v1.0.0/tts/vits-mms-${language.iso6393}/$name")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS; readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept-Encoding", "identity")
            if (existing > 0) setRequestProperty("Range", "bytes=$existing-")
        }
        activeConnection.set(connection)
        try {
            checkCancelled()
            val code = connection.responseCode
            check(code == HttpURLConnection.HTTP_OK || code == HttpURLConnection.HTTP_PARTIAL) { "Download failed: HTTP $code" }
            val append = code == HttpURLConnection.HTTP_PARTIAL
            if (append) check(validResumeRange(connection.getHeaderField("Content-Range"), existing, spec.sizeBytes)) { "Server returned an invalid resume range" }
            // A server ignoring Range returns 200: truncate safely, do not append duplicate bytes.
            connection.inputStream.use { input -> FileOutputStream(target, append).use { output ->
                val buffer = ByteArray(BUFFER)
                var downloaded = if (append) existing else 0L
                _state.value = DownloadState.Downloading(language, downloadPercent(completedBytes + downloaded, total), completedBytes + downloaded, total)
                while (true) {
                    checkCancelled()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(downloaded + count <= spec.sizeBytes) { "Downloaded $name exceeds its verified size" }
                    output.write(buffer, 0, count)
                    downloaded += count
                    _state.value = DownloadState.Downloading(language, downloadPercent(completedBytes + downloaded, total), completedBytes + downloaded, total)
                }
            } }
            checkCancelled()
            if (target.length() == spec.sizeBytes) {
                try { verify(target, spec) } catch (error: IllegalStateException) { target.delete(); throw error }
            } else error("Download interrupted; partial progress retained")
        } finally {
            activeConnection.compareAndSet(connection, null)
            connection.disconnect()
        }
    }

    private fun verify(file: File, spec: BundledModelStore.ModelFile) {
        check(file.length() == spec.sizeBytes) { "Downloaded file has an unexpected size" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input -> val buffer = ByteArray(BUFFER); while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) } }
        check(digest.digest().joinToString("") { "%02x".format(it) } == spec.sha256) { "Downloaded file checksum does not match release manifest" }
    }
    private fun requireFreeSpace(required: Long) {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        check(cm.activeNetwork != null && !cm.isActiveNetworkMetered) { "Connect to Wi-Fi to download this offline language pack." }
        check(StatFs(context.filesDir.path).availableBytes >= required) { "At least 150 MB of free storage is required." }
    }
    private companion object { const val BUFFER = 64 * 1024; const val SAFETY_BYTES = 16L * 1024 * 1024; const val CONNECT_TIMEOUT_MS = 15_000; const val READ_TIMEOUT_MS = 30_000 }
}
