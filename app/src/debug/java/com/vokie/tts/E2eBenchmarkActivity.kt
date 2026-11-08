package com.vokie.tts

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import com.vokie.domain.model.VokieLanguage
import com.vokie.translation.Ctranslate2TranslationEngine
import com.vokie.translation.TranslationStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ceil

/**
 * Debug-only physical ARM64 benchmark: measures translation (CT2 NLLB) and
 * TTS (sherpa-onnx) independently and combined. Single-device only —
 * STT and transport are NOT measured.
 */
class E2eBenchmarkActivity : Activity() {
    private lateinit var output: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val sv = ScrollView(this)
        output = TextView(this).apply { textSize = 13f; setPadding(24, 40, 24, 40); setTextIsSelectable(true) }
        sv.addView(output); setContentView(sv)
        status("E2E BENCHMARK\nStarting...")
        Thread({ runBenchmark() }, "e2e-bench").start()
    }

    private fun runBenchmark() = try {
        val results = JSONObject()
        results.put("device", android.os.Build.MODEL)
        results.put("android", android.os.Build.VERSION.RELEASE)
        results.put("api", android.os.Build.VERSION.SDK_INT)
        results.put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        results.put("sherpa_onnx", SHERPA_ONNX_VERSION)
        results.put("timestamp", System.currentTimeMillis())

        val baselinePss = pssKb()
        results.put("baseline_pss_kb", baselinePss)
        log("baseline pssKb=$baselinePss nativeHeapKb=${nativeKb()}")

        // === TRANSLATION BENCHMARK ===
        status("Loading CT2 translation model...")
        val ct2 = Ctranslate2TranslationEngine(applicationContext)
        try {
            val translationResults = JSONArray()
            val directions = listOf(
                Triple(VokieLanguage.EN, VokieLanguage.HI, "I need help urgently."),
                Triple(VokieLanguage.HI, VokieLanguage.EN, "मुझे तुरंत मदद चाहिए।"),
                Triple(VokieLanguage.EN, VokieLanguage.TA, "I need help urgently."),
                Triple(VokieLanguage.TA, VokieLanguage.EN, "எனக்கு உடனடியாக உதவி தேவை."),
                Triple(VokieLanguage.HI, VokieLanguage.TA, "मुझे तुरंत मदद चाहिए।"),
                Triple(VokieLanguage.TA, VokieLanguage.HI, "எனக்கு உடனடியாக உதவி தேவை."),
            )
            for ((src, tgt, text) in directions) {
                status("Translation ${src.code}→${tgt.code}...")
                val dirObj = JSONObject()
                dirObj.put("source", src.code); dirObj.put("target", tgt.code); dirObj.put("input", text)
                // Cold (first for this direction — model may already be loaded after first pair)
                val coldStart = SystemClock.elapsedRealtime()
                val coldResult = runBlocking { ct2.translate(text, src, tgt) }
                val coldMs = SystemClock.elapsedRealtime() - coldStart
                dirObj.put("cold_ms", coldMs)
                dirObj.put("cold_output", coldResult.translatedText ?: "")
                dirObj.put("cold_status", coldResult.status.name)
                log("TRANS ${src.code}→${tgt.code} cold=${coldMs}ms status=${coldResult.status} output=${coldResult.translatedText}")
                if (coldResult.status != TranslationStatus.TRANSLATED) {
                    dirObj.put("warm_median_ms", JSONObject.NULL); dirObj.put("error", coldResult.error ?: "unknown")
                    translationResults.put(dirObj); continue
                }
                // Warm runs
                val warmTimes = mutableListOf<Long>()
                for (i in 1..5) {
                    val ws = SystemClock.elapsedRealtime()
                    val wr = runBlocking { ct2.translate(text, src, tgt) }
                    val wt = SystemClock.elapsedRealtime() - ws
                    warmTimes += wt
                    log("TRANS_WARM ${src.code}→${tgt.code}/$i ms=$wt status=${wr.status}")
                }
                val sorted = warmTimes.sorted()
                dirObj.put("warm_times_ms", JSONArray(warmTimes))
                dirObj.put("warm_median_ms", sorted[(sorted.size - 1) / 2])
                dirObj.put("warm_p95_ms", sorted[ceil(sorted.size * 0.95).toInt() - 1])
                dirObj.put("warm_min_ms", sorted.first())
                dirObj.put("warm_max_ms", sorted.last())
                translationResults.put(dirObj)
            }
            results.put("translation", translationResults)
            results.put("post_translation_pss_kb", pssKb())
            log("POST_TRANSLATION pssKb=${pssKb()} nativeHeapKb=${nativeKb()}")
        } finally { ct2.release() }

        // === TTS BENCHMARK ===
        val models = TtsModelManager(applicationContext)
        repeat(90) {
            if (models.isInstalled(TtsLanguage.ENGLISH) && models.isInstalled(TtsLanguage.TAMIL)) return@repeat
            Thread.sleep(500)
        }
        val ttsLanguages = listOf(
            Triple(TtsLanguage.ENGLISH, "I need help urgently.", 0),
            Triple(TtsLanguage.HINDI, "मुझे तुरंत मदद चाहिए।", 22050),
            Triple(TtsLanguage.TAMIL, "எனக்கு உடனடியாக உதவி தேவை.", 16000),
            Triple(TtsLanguage.GUJARATI, "મને મદદની જરૂર છે", 16000),
        )
        val ttsResults = JSONArray()
        for ((lang, text, expectedRate) in ttsLanguages) {
            status("TTS ${lang.iso6393}...")
            val langObj = JSONObject()
            langObj.put("language", lang.iso6393)
            langObj.put("input", text)
            if (!models.isInstalled(lang)) {
                langObj.put("status", "NOT_INSTALLED"); ttsResults.put(langObj); continue
            }
            val engine = SherpaOnnxTtsEngine(models, VokieAudioPlayer(applicationContext))
            try {
                // Cold load
                val loadStart = SystemClock.elapsedRealtime()
                runBlocking { engine.initialize(lang) }
                val loadMs = SystemClock.elapsedRealtime() - loadStart
                langObj.put("cold_load_ms", loadMs)
                langObj.put("post_load_pss_kb", pssKb())
                log("TTS_LOAD ${lang.iso6393} ms=$loadMs pssKb=${pssKb()}")
                if (engine.status.value.state != TtsState.READY) {
                    langObj.put("status", "LOAD_FAILED"); ttsResults.put(langObj); continue
                }
                // First synthesis
                val (audio1, first) = runBlocking { engine.synthesize(text, lang) }
                val rate = audio1.sampleRate
                langObj.put("sample_rate", rate)
                langObj.put("first_synth_ms", first.synthesisTimeMs)
                langObj.put("first_audio_ms", audio1.durationMs)
                langObj.put("first_rtf", first.realTimeFactor)
                langObj.put("first_samples", audio1.samples.size)
                langObj.put("first_peak", audio1.samples.maxOf { abs(it) }.toDouble())
                langObj.put("first_finite", audio1.samples.all { it.isFinite() })
                log("TTS_FIRST ${lang.iso6393} synthMs=${first.synthesisTimeMs} audioMs=${audio1.durationMs} rtf=${first.realTimeFactor} rate=$rate peak=${audio1.samples.maxOf { abs(it) }}")
                // Playback
                runBlocking { engine.play(audio1, emergency = false) }
                log("TTS_PLAY ${lang.iso6393} PASS")
                // Warm runs
                val warmTimes = mutableListOf<Long>()
                for (i in 1..5) {
                    val (wa, wr) = runBlocking { engine.synthesize(text, lang) }
                    warmTimes += wr.synthesisTimeMs
                    runBlocking { engine.play(wa, emergency = false) }
                    log("TTS_WARM ${lang.iso6393}/$i synthMs=${wr.synthesisTimeMs} audioMs=${wa.durationMs} rtf=${wr.realTimeFactor}")
                }
                val sorted = warmTimes.sorted()
                langObj.put("warm_times_ms", JSONArray(warmTimes))
                langObj.put("warm_median_ms", sorted[(sorted.size - 1) / 2])
                langObj.put("warm_p95_ms", sorted[ceil(sorted.size * 0.95).toInt() - 1])
                langObj.put("warm_min_ms", sorted.first())
                langObj.put("warm_max_ms", sorted.last())
                langObj.put("post_tts_pss_kb", pssKb())
                langObj.put("status", "PASS")
            } catch (e: Throwable) {
                langObj.put("status", "FAILED")
                langObj.put("error", "${e.javaClass.simpleName}: ${e.message}")
                log("TTS_FAIL ${lang.iso6393} ${e.javaClass.simpleName}: ${e.message}")
            } finally { engine.release() }
            ttsResults.put(langObj)
        }
        results.put("tts", ttsResults)

        // === COMBINED TRANSLATION+TTS ===
        status("Combined translation+TTS...")
        val ct2b = Ctranslate2TranslationEngine(applicationContext)
        val combinedResults = JSONArray()
        val combinedPairs = listOf(
            Triple(VokieLanguage.EN, VokieLanguage.HI, "I need help urgently."),
            Triple(VokieLanguage.HI, VokieLanguage.EN, "मुझे तुरंत मदद चाहिए।"),
            Triple(VokieLanguage.EN, VokieLanguage.TA, "I need help urgently."),
        )
        for ((src, tgt, text) in combinedPairs) {
            val cObj = JSONObject()
            cObj.put("source", src.code); cObj.put("target", tgt.code)
            val ttsLang = TtsLanguage.fromMessageCode(tgt.code)
            if (ttsLang == null || !models.isInstalled(ttsLang)) {
                cObj.put("status", "TTS_UNAVAILABLE"); combinedResults.put(cObj); continue
            }
            val engine = SherpaOnnxTtsEngine(models, VokieAudioPlayer(applicationContext))
            try {
                runBlocking { engine.initialize(ttsLang) }
                val e2eStart = SystemClock.elapsedRealtime()
                val tr = runBlocking { ct2b.translate(text, src, tgt) }
                val transMs = SystemClock.elapsedRealtime() - e2eStart
                if (tr.status != TranslationStatus.TRANSLATED || tr.translatedText.isNullOrBlank()) {
                    cObj.put("status", "TRANSLATION_FAILED"); combinedResults.put(cObj); continue
                }
                val synthStart = SystemClock.elapsedRealtime()
                val (audio, ttsResult) = runBlocking { engine.synthesize(tr.translatedText!!, ttsLang) }
                val synthMs = SystemClock.elapsedRealtime() - synthStart
                val playStart = SystemClock.elapsedRealtime()
                runBlocking { engine.play(audio, emergency = false) }
                val playMs = SystemClock.elapsedRealtime() - playStart
                val e2eMs = SystemClock.elapsedRealtime() - e2eStart
                cObj.put("translation_ms", transMs)
                cObj.put("tts_ms", synthMs)
                cObj.put("playback_ms", playMs)
                cObj.put("e2e_ms", e2eMs)
                cObj.put("translated_text", tr.translatedText)
                cObj.put("audio_ms", audio.durationMs)
                cObj.put("status", "PASS")
                log("COMBINED ${src.code}→${tgt.code} transMs=$transMs synthMs=$synthMs playMs=$playMs e2eMs=$e2eMs")
            } catch (e: Throwable) {
                cObj.put("status", "FAILED"); cObj.put("error", e.message)
            } finally { engine.release() }
            combinedResults.put(cObj)
        }
        ct2b.release()
        results.put("combined", combinedResults)

        // === FINAL ===
        results.put("final_pss_kb", pssKb())
        results.put("final_native_heap_kb", nativeKb())
        log("FINAL pssKb=${pssKb()} nativeHeapKb=${nativeKb()}")
        log("RESULTS_JSON ${results.toString(2)}")
        status("COMPLETE\n\n${results.toString(2)}")
    } catch (e: Throwable) {
        Log.e(TAG, "BENCHMARK FAILED", e)
        status("FAILED\n${e.javaClass.simpleName}: ${e.message}")
    }

    private fun pssKb(): Int { val m = Debug.MemoryInfo(); Debug.getMemoryInfo(m); return m.totalPss }
    private fun nativeKb() = (Debug.getNativeHeapAllocatedSize() / 1024).toInt()
    private fun status(s: String) = runOnUiThread { output.text = s }
    private fun log(s: String) = Log.i(TAG, s)
    companion object { const val TAG = "VOKIE_E2E_BENCH" }
}
