package com.vokie

import android.content.Context
import android.os.Environment
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.vokie.domain.model.VokieLanguage
import com.vokie.stt.SttLanguage
import com.vokie.stt.UserLanguageProfile
import com.vokie.stt.SenseVoiceSttEngine
import com.vokie.translation.Ctranslate2TranslationEngine
import com.vokie.translation.TranslationStatus
import com.vokie.tts.SherpaOnnxTtsEngine
import com.vokie.tts.TtsLanguage
import com.vokie.tts.TtsModelManager
import com.vokie.tts.VokieAudioPlayer
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.Locale

/**
 * Validation-only corpus runner. It uses production engines directly; it is not a
 * production feature and does not change the released application's UI or routing.
 * Corpus files are supplied by adb under this test package's external files directory.
 */
@RunWith(AndroidJUnit4::class)
class CorpusProductionPipelineTest {
    private val target: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext: Context get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun runAllCorpusSamples() {
        runBlocking {
        // Corpus is copied into the target app's private files by the validation deployment step.
        val root = File(target.filesDir, "corpus")
        check(root.isDirectory) { "Corpus not staged at ${root.absolutePath}" }
        val metadata = File(root, "metadata.csv")
        check(metadata.isFile) { "Missing ${metadata.absolutePath}" }
        val requested = InstrumentationRegistry.getArguments().getString("only")
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)?.toSet().orEmpty()
        val rows = metadata.readLines(Charsets.UTF_8).drop(1).filter { it.isNotBlank() }.map(::parseCsv)
            .filter { requested.isEmpty() || it.getOrNull(0) in requested }
        check(rows.isNotEmpty()) { "No requested corpus rows selected" }
        val output = StringBuilder()
        output.append("id,language,reference,predicted,stt_ms,translation_target,translation_output,ct2_ms,tts_ms,audio_ms,status,error\n")
        run {
            val out = output
            val stt = SenseVoiceSttEngine(target)
            val ct2 = Ctranslate2TranslationEngine(target)
            val models = TtsModelManager(target)
            try {
                stt.initialize()
                val languageOrder = listOf(VokieLanguage.EN, VokieLanguage.HI, VokieLanguage.TA, VokieLanguage.GU, VokieLanguage.MR, VokieLanguage.KN, VokieLanguage.ML, VokieLanguage.TE, VokieLanguage.OR, VokieLanguage.BN)
                for ((index, row) in rows.withIndex()) {
                    val id = row.getOrNull(0) ?: ""
                    val iso = row.getOrNull(3)?.lowercase(Locale.ROOT) ?: ""
                    val reference = row.getOrNull(11) ?: ""
                    val audio = File(root, row.getOrNull(13) ?: row.getOrNull(12) ?: "")
                    var predicted = ""; var targetLanguage = ""; var translated = ""
                    var sttMs: Long? = null; var ct2Ms: Long? = null; var ttsMs: Long? = null; var audioMs: Long? = null
                    var status = "FAILED"; var error = ""
                    try {
                        check(audio.isFile) { "missing audio ${audio.path}" }
                        if (iso == "or") {
                            status = "ODIA_STT_UNAVAILABLE"
                            error = "Odia STT is runtime unavailable; audio is Hindi TTS fallback"
                        } else {
                            val language = SttLanguage.fromWhisperCode(iso) ?: error("unsupported corpus language $iso")
                            val pcm = readPcm16Wav(audio)
                            val started = SystemClock.elapsedRealtime()
                            val result = stt.transcribe(pcm, language, pcm.size * 1000L / 16000L)
                            sttMs = SystemClock.elapsedRealtime() - started
                            predicted = result.text
                            val src = language.messageLanguage
                            val tgt = languageOrder[(languageOrder.indexOf(src) + 1) % languageOrder.size]
                            targetLanguage = tgt.code
                            val transStarted = SystemClock.elapsedRealtime()
                            val translation = ct2.translate(predicted, src, tgt)
                            ct2Ms = SystemClock.elapsedRealtime() - transStarted
                            check(translation.status == TranslationStatus.TRANSLATED && !translation.translatedText.isNullOrBlank()) {
                                "CT2 ${translation.status}: ${translation.error ?: "empty output"}"
                            }
                            translated = translation.translatedText!!
                            val ttsLanguage = TtsLanguage.fromMessageCode(tgt.code) ?: error("no TTS route ${tgt.code}")
                            val engine = SherpaOnnxTtsEngine(models, VokieAudioPlayer(target))
                            try {
                                val ttsStarted = SystemClock.elapsedRealtime()
                                val generated = engine.synthesize(translated, ttsLanguage)
                                ttsMs = SystemClock.elapsedRealtime() - ttsStarted
                                audioMs = generated.first.durationMs
                                check(generated.first.samples.isNotEmpty()) { "empty TTS audio" }
                            } finally { engine.release() }
                            status = "PASS"
                        }
                    } catch (e: Throwable) { error = "${e.javaClass.simpleName}: ${e.message}" }
                    out.append(csv(listOf(id, iso, reference, predicted, sttMs, targetLanguage, translated, ct2Ms, ttsMs, audioMs, status, error))).append('\n')
                    val predictedB64 = android.util.Base64.encodeToString(predicted.toByteArray(Charsets.UTF_8), android.util.Base64.NO_WRAP)
                    android.util.Log.i(TAG, "CORPUS ${index + 1}/500 id=$id lang=$iso status=$status sttMs=$sttMs ct2Ms=$ct2Ms ttsMs=$ttsMs predictedB64=$predictedB64 error=$error")
                }
            } finally { stt.release(); ct2.release() }
        }
        }
    }

    private fun readPcm16Wav(file: File): FloatArray {
        val bytes = FileInputStream(file).use { it.readBytes() }
        require(bytes.size >= 44 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF" && String(bytes, 8, 4, Charsets.US_ASCII) == "WAVE") { "invalid WAV" }
        val channels = u16(bytes, 22); val rate = u32(bytes, 24); val bits = u16(bytes, 34)
        require(channels == 1 && rate == 16000 && bits == 16) { "expected mono 16k PCM16, got channels=$channels rate=$rate bits=$bits" }
        var p = 12; var start = -1; var size = 0
        while (p + 8 <= bytes.size) { val id = String(bytes, p, 4, Charsets.US_ASCII); val n = u32(bytes, p + 4); if (id == "data") { start = p + 8; size = n; break }; p += 8 + n + (n and 1) }
        require(start >= 0 && start + size <= bytes.size) { "WAV data chunk missing" }
        return FloatArray(size / 2) { i -> ((bytes[start + 2 * i].toInt() and 255) or (bytes[start + 2 * i + 1].toInt() shl 8)).toShort().toFloat() / 32768f }
    }
    private fun u16(b: ByteArray, p: Int) = (b[p].toInt() and 255) or ((b[p + 1].toInt() and 255) shl 8)
    private fun u32(b: ByteArray, p: Int) = u16(b, p) or (u16(b, p + 2) shl 16)
    private fun parseCsv(line: String): List<String> { val r = mutableListOf<String>(); val s = StringBuilder(); var q = false; var i = 0; while (i < line.length) { val c=line[i]; if (c == '"') { if (q && i + 1 < line.length && line[i + 1] == '"') { s.append('"'); i++ } else q=!q } else if (c == ',' && !q) { r += s.toString(); s.clear() } else s.append(c); i++ }; r += s.toString(); return r }
    private fun csv(values: List<Any?>) = values.joinToString(",") { "\"${it?.toString()?.replace("\"", "\"\"") ?: ""}\"" }
    companion object { private const val TAG = "VOKIE_CORPUS_PIPELINE" }
}
