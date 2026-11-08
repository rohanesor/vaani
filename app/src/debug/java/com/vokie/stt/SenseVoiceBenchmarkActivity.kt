package com.vokie.stt

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.ScrollView
import android.widget.TextView
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineSenseVoiceModelConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.abs

/**
 * Debug-only physical benchmark: SenseVoice-Small STT via sherpa-onnx OfflineRecognizer.
 * Compares against the existing Whisper-tiny baseline.
 */
class SenseVoiceBenchmarkActivity : Activity() {
    private lateinit var output: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val sv = ScrollView(this)
        output = TextView(this).apply { textSize = 12f; setPadding(24, 40, 24, 40); setTextIsSelectable(true) }
        sv.addView(output); setContentView(sv)
        status("SenseVoice Benchmark\nStarting...")
        Thread({ runBenchmark() }, "sv-bench").start()
    }

    private fun runBenchmark() { try {
        val results = JSONObject()
        results.put("device", android.os.Build.MODEL)
        results.put("android", android.os.Build.VERSION.RELEASE)
        results.put("api", android.os.Build.VERSION.SDK_INT)
        results.put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown")
        results.put("sherpa_onnx", "1.13.7")
        results.put("timestamp", System.currentTimeMillis())

        val modelDir = File(filesDir, "models/stt/sensevoice")
        val modelFile = File(modelDir, "model.int8.onnx")
        val tokensFile = File(modelDir, "tokens.txt")

        if (!modelFile.exists() || !tokensFile.exists()) {
            status("ERROR: Model not staged.\nExpected:\n${modelFile.absolutePath}\n${tokensFile.absolutePath}")
            log("MODEL_MISSING model=${modelFile.exists()} tokens=${tokensFile.exists()}")
            return@runBenchmark
        }

        results.put("model_file", modelFile.name)
        results.put("model_bytes", modelFile.length())
        results.put("tokens_file", tokensFile.name)

        val baselinePss = pssKb()
        results.put("baseline_pss_kb", baselinePss)
        log("BASELINE pssKb=$baselinePss nativeKb=${nativeKb()}")

        // === LOAD MODEL ===
        status("Loading SenseVoice model...")
        val loadStart = SystemClock.elapsedRealtime()
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
        // Pass null AssetManager to use newFromFile with absolute paths
        val recognizer = OfflineRecognizer(null, config)
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        val postLoadPss = pssKb()
        results.put("cold_load_ms", loadMs)
        results.put("post_load_pss_kb", postLoadPss)
        results.put("post_load_native_kb", nativeKb())
        log("MODEL_LOADED ms=$loadMs pssKb=$postLoadPss nativeKb=${nativeKb()}")

        // === USE EXISTING WHISPER CAPTURED PCM IF AVAILABLE ===
        // The last PTT capture is saved at cache/stt-validation/last.pcm
        val pcmFile = File(cacheDir, "stt-validation/last.pcm")
        val testSamples: FloatArray
        val audioDurationMs: Long
        if (pcmFile.exists() && pcmFile.length() > 100) {
            val bytes = pcmFile.readBytes()
            val shortCount = bytes.size / 2
            testSamples = FloatArray(shortCount) { i ->
                val lo = bytes[i * 2].toInt() and 0xFF
                val hi = bytes[i * 2 + 1].toInt()
                ((hi shl 8) or lo).toShort().toFloat() / 32768f
            }
            audioDurationMs = (shortCount.toLong() * 1000L) / 16000L
            log("USING_CAPTURED_PCM samples=$shortCount durationMs=$audioDurationMs")
        } else {
            // Generate a 1-second silent test signal if no capture available
            testSamples = FloatArray(16000) { 0.001f * (it % 2 * 2 - 1) }
            audioDurationMs = 1000L
            log("USING_SYNTHETIC_AUDIO samples=16000 durationMs=1000")
        }
        results.put("audio_duration_ms", audioDurationMs)
        results.put("audio_samples", testSamples.size)
        results.put("audio_source", if (pcmFile.exists()) "captured_pcm" else "synthetic")

        // === FIRST INFERENCE ===
        status("Running first inference...")
        val firstStart = SystemClock.elapsedRealtime()
        val firstText = recognize(recognizer, testSamples)
        val firstMs = SystemClock.elapsedRealtime() - firstStart
        results.put("first_inference_ms", firstMs)
        results.put("first_transcript", firstText)
        log("FIRST_INFERENCE ms=$firstMs transcript=$firstText")

        // === WARM RUNS ===
        status("Running warm inferences...")
        val warmTimes = mutableListOf<Long>()
        val warmTranscripts = mutableListOf<String>()
        for (i in 1..10) {
            val ws = SystemClock.elapsedRealtime()
            val text = recognize(recognizer, testSamples)
            val wt = SystemClock.elapsedRealtime() - ws
            warmTimes += wt
            warmTranscripts += text
            log("WARM_RUN $i ms=$wt transcript=$text")
            status("Warm run $i/10: ${wt}ms — $text")
        }
        val sorted = warmTimes.sorted()
        results.put("warm_times_ms", JSONArray(warmTimes))
        results.put("warm_median_ms", sorted[sorted.size / 2])
        results.put("warm_p95_ms", sorted[(sorted.size * 0.95).toInt().coerceAtMost(sorted.size - 1)])
        results.put("warm_min_ms", sorted.first())
        results.put("warm_max_ms", sorted.last())
        results.put("warm_transcripts", JSONArray(warmTranscripts))

        val finalPss = pssKb()
        val finalNative = nativeKb()
        results.put("final_pss_kb", finalPss)
        results.put("final_native_kb", finalNative)

        // === THERMAL ===
        results.put("thermal_status", thermalStatus())

        // === RTF ===
        if (audioDurationMs > 0) {
            results.put("warm_median_rtf", sorted[sorted.size / 2].toDouble() / audioDurationMs)
        }

        recognizer.release()

        log("RESULTS_JSON ${results.toString(2)}")
        status("COMPLETE\n\n${results.toString(2)}")
    } catch (e: Throwable) {
        Log.e(TAG, "BENCHMARK FAILED", e)
        log("BENCHMARK_FAILED ${e.javaClass.simpleName}: ${e.message}")
        status("FAILED\n${e.javaClass.simpleName}: ${e.message}\n${e.stackTraceToString()}")
    } }

    private fun recognize(recognizer: OfflineRecognizer, samples: FloatArray): String {
        val stream = recognizer.createStream()
        stream.acceptWaveform(samples, 16000)
        recognizer.decode(stream)
        return recognizer.getResult(stream).text.trim()
    }

    private fun pssKb(): Int { val m = Debug.MemoryInfo(); Debug.getMemoryInfo(m); return m.totalPss }
    private fun nativeKb() = (Debug.getNativeHeapAllocatedSize() / 1024).toInt()
    private fun thermalStatus(): Int = try {
        val pm = getSystemService(android.os.PowerManager::class.java)
        pm?.currentThermalStatus ?: -1
    } catch (_: Throwable) { -1 }
    private fun status(s: String) = runOnUiThread { output.text = s }
    private fun log(s: String) = Log.i(TAG, s)

    companion object {
        const val TAG = "VOKIE_SV_BENCH"
    }
}
