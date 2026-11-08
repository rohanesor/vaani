package com.vokie.tts

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import com.vokie.VokieApplication
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.math.ceil

/** Debug-only physical profiler. Uses the application's production TTS engine instance. */
class TtsProfilingActivity : Activity() {
    private lateinit var output: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = TextView(this).apply { textSize = 13f; setPadding(24, 40, 24, 40); text = "TTS profiling starting…" }
        setContentView(output)
        Thread({ runProfile() }, "tts-profile").start()
    }

    private fun runProfile() = try {
        val engine = (application as VokieApplication).ttsEngine
        val language = TtsLanguage.ENGLISH
        val result = JSONObject()
            .put("device", android.os.Build.MODEL)
            .put("android", android.os.Build.VERSION.RELEASE)
            .put("api", android.os.Build.VERSION.SDK_INT)
            .put("abi", android.os.Build.SUPPORTED_ABIS.firstOrNull())
            .put("sherpa_onnx", SHERPA_ONNX_VERSION)
            .put("threads", SherpaOnnxTtsEngine.DEFAULT_THREADS)
            .put("pre_load_pss_kb", pssKb())
            .put("pre_load_native_heap_kb", nativeKb())

        val loadStart = SystemClock.elapsedRealtime()
        runBlocking { engine.initialize(language) }
        val loadMs = SystemClock.elapsedRealtime() - loadStart
        check(engine.status.value.state == TtsState.READY) { "English model load failed: ${engine.status.value}" }
        result.put("cold_load_ms", loadMs)
            .put("post_load_pss_kb", pssKb())
            .put("post_load_native_heap_kb", nativeKb())

        val shortText = "Help me now."
        val repeated = JSONArray()
        repeat(5) { index ->
            val (audio, tts) = runBlocking { engine.synthesize(shortText, language) }
            repeated.put(measurement(index + 1, shortText, tts, audio, play = false, engine = engine))
        }
        result.put("same_short_five", repeated)
        val warm = (1 until repeated.length()).map { repeated.getJSONObject(it).getLong("synthesis_ms") }.sorted()
        result.put("warm_median_ms", warm[(warm.size - 1) / 2])
            .put("warm_p95_ms", warm[ceil(warm.size * 0.95).toInt() - 1])

        val texts = listOf(
            "Help me.",
            "Please send rescue now.",
            "Please send rescue. I am trapped under the roof.",
            "I am trapped under the collapsed roof and cannot move. Please send medical rescue to my location immediately.",
            "I am trapped beneath a collapsed roof and cannot move my left leg. Please send rescue workers and medical assistance to my current location as soon as possible.",
        )
        val scaling = JSONArray()
        texts.forEachIndexed { index, text ->
            val (audio, tts) = runBlocking { engine.synthesize(text, language) }
            scaling.put(measurement(index + 1, text, tts, audio, play = true, engine = engine))
        }
        result.put("length_scaling", scaling)
            .put("final_pss_kb", pssKb())
            .put("final_native_heap_kb", nativeKb())
            .put("token_count", JSONObject.NULL)
            .put("token_count_reason", "sherpa-onnx OfflineTts.generate does not expose frontend tokens")
            .put("native_pipeline_scope", "frontend/tokenization + inference + waveform/postprocessing")

        File(cacheDir, "tts_profile_results.json").writeText(result.toString(2))
        Log.i(TAG, "RESULTS_JSON ${result}")
        runOnUiThread { output.text = "COMPLETE\n\n${result.toString(2)}" }
    } catch (error: Throwable) {
        Log.e(TAG, "FAILED ${error.javaClass.simpleName}: ${error.message}", error)
        runOnUiThread { output.text = "FAILED\n${error.javaClass.simpleName}: ${error.message}" }
    }

    private fun measurement(index: Int, text: String, tts: TtsResult, audio: AudioBuffer, play: Boolean, engine: TtsEngine): JSONObject {
        val beforePss = pssKb()
        var playbackMs: Long? = null
        if (play) {
            val started = SystemClock.elapsedRealtime()
            runBlocking { engine.play(audio) }
            playbackMs = SystemClock.elapsedRealtime() - started
        }
        return JSONObject()
            .put("run", index)
            .put("text", text)
            .put("text_length", text.length)
            .put("token_count", JSONObject.NULL)
            .put("synthesis_ms", tts.synthesisTimeMs)
            .put("native_pipeline_ms", tts.nativePipelineTimeMs)
            .put("preprocessing_ms", tts.preprocessingTimeMs)
            .put("audio_buffer_ms", tts.audioBufferTimeMs)
            .put("generated_audio_ms", audio.durationMs)
            .put("rtf", tts.realTimeFactor)
            .put("playback_ms", playbackMs ?: JSONObject.NULL)
            .put("pss_kb", beforePss)
            .put("native_heap_kb", nativeKb())
            .put("model_instance_id", tts.modelInstanceId)
    }

    private fun pssKb(): Int = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }.totalPss
    private fun nativeKb(): Long = Debug.getNativeHeapAllocatedSize() / 1024

    companion object { const val TAG = "VOKIE_TTS_PROFILE" }
}
