package com.vokie.translation

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import com.vokie.domain.model.VokieLanguage
import java.io.File
import kotlin.math.ceil

/** Debug-only, receiver-local CT2 profiler. It neither changes production routing nor uses transport. */
class Ct2SmokeActivity : Activity() {
    private lateinit var output: TextView

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = TextView(this).apply { textSize = 13f; setPadding(24, 40, 24, 40); text = "CT2 receiver profiler starting…" }
        setContentView(output)
        Thread(::runProfile, "ct2-profile").start()
    }

    private fun runProfile() = try {
        val dir = File(filesDir, "ct2/nllb600m")
        check(File(dir, "model.bin").length() == MODEL_BYTES) { "Model is not staged at ${dir.absolutePath}" }
        listOf("config.json", "shared_vocabulary.json", "sentencepiece.bpe.model").forEach { check(File(dir, it).isFile) { "Missing $it" } }
        val native = Ctranslate2Native()
        val loadStarted = SystemClock.elapsedRealtime()
        val handle = native.nativeLoadModel(dir.absolutePath)
        check(handle != 0L)
        val report = StringBuilder("CT2 receiver-local profile\nload_ms=${SystemClock.elapsedRealtime() - loadStarted}\n")
        try {
            // Warm-up is recorded separately and excluded from the five measured repetitions.
            direct(native, handle, "Help me.")
            val cases = listOf(
                "short" to "Help me.",
                "medium" to "Please send medical rescue to my location now.",
                "long" to "I am trapped under a collapsed roof and cannot move my left leg. Please send medical rescue workers to my current location immediately.",
            )
            cases.forEach { (label, text) ->
                val samples = (1..5).map { direct(native, handle, text) }
                val sorted = samples.sorted()
                report.append("$label chars=${text.length} samples_ms=${samples.joinToString()} median=${median(samples)} p95=${sorted[ceil(samples.size * .95).toInt() - 1]} min=${sorted.first()} max=${sorted.last()}\n")
            }
            report.append("pss_kb=${pssKb()} native_heap_kb=${Debug.getNativeHeapAllocatedSize() / 1024} cpu_elapsed_ms=${android.os.Process.getElapsedCpuTime()}\n")
            Log.i(TAG, report.toString())
            runOnUiThread { output.text = report.toString() }
        } finally { native.nativeUnloadModel(handle) }
    } catch (error: Throwable) {
        Log.e(TAG, "FAILED ${error.javaClass.simpleName}: ${error.message}", error)
        runOnUiThread { output.text = "FAILED: ${error.message}" }
    }

    private fun direct(native: Ctranslate2Native, handle: Long, text: String): Long {
        val start = SystemClock.elapsedRealtime()
        val output = native.nativeTranslate(handle, VokieLanguage.EN.code, VokieLanguage.HI.code, text)
        check(output.isNotBlank()) { "empty translation" }
        return SystemClock.elapsedRealtime() - start
    }
    private fun median(values: List<Long>): Long = values.sorted()[values.size / 2]
    private fun pssKb(): Int = Debug.MemoryInfo().also(Debug::getMemoryInfo).totalPss
    companion object { const val TAG = "VOKIE_CT2_PROFILE"; const val MODEL_BYTES = 619_704_329L }
}
