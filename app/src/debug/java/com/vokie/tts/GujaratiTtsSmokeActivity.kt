package com.vokie.tts

import android.app.Activity
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import kotlinx.coroutines.runBlocking
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.ceil

/** Debug-only Gujarati MMS-TTS physical-validation surface. */
class GujaratiTtsSmokeActivity : Activity() {
    private lateinit var output: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = TextView(this).apply { textSize = 16f; setPadding(32, 48, 32, 48); text = "GUJARATI TTS SMOKE TEST\n\nStarting..." }
        setContentView(output); log("onCreate"); Thread({ runSmoke() }, "guj-tts-smoke").start()
    }

    private fun runSmoke() = try {
        log("worker started sherpa=$SHERPA_ONNX_VERSION")
        status("Model staging...")
        val language = TtsLanguage.GUJARATI
        val text = "\u0AAE\u0AA8\u0AC7 \u0AAE\u0AA6\u0AA6\u0AA8\u0AC0 \u0A9C\u0AB0\u0AC2\u0AB0 \u0A9B\u0AC7" // મને મદદની જરૂર છે
        val models = TtsModelManager(applicationContext)

        // Wait for bundled extraction
        repeat(90) { if (models.isInstalled(language)) return@repeat; Thread.sleep(500) }
        check(models.isInstalled(language)) { "Gujarati model not staged after 45 s" }

        val model = models.modelFile(language)
        check(model.isFile) { "model.onnx missing" }
        val modelHash = model.sha256()
        log("stage PASS path=${models.modelDirectory(language)} hash=$modelHash bytes=${model.length()}")
        status("Stage PASS. Loading model...")

        val engine = SherpaOnnxTtsEngine(models, VokieAudioPlayer(applicationContext))
        try {
            val pssBeforeLoad = pssKb()
            log("pre-load pssKb=$pssBeforeLoad nativeHeapKb=${Debug.getNativeHeapAllocatedSize() / 1024}")

            // Cold load
            val loadStart = SystemClock.elapsedRealtime()
            runBlocking { engine.initialize(language) }
            val loadMs = SystemClock.elapsedRealtime() - loadStart
            check(engine.status.value.state == TtsState.READY) { "load failed state=${engine.status.value.state}" }
            val pssAfterLoad = pssKb()
            log("COLD_LOAD ms=$loadMs pssKb=$pssAfterLoad nativeHeapKb=${Debug.getNativeHeapAllocatedSize() / 1024}")
            status("Load PASS ${loadMs}ms. First synthesis...")

            // First synthesis
            val (audio1, first) = runBlocking { engine.synthesize(text, language) }
            check(audio1.sampleRate == 16_000) { "expected 16kHz got ${audio1.sampleRate}" }
            check(audio1.samples.isNotEmpty()) { "empty audio" }
            check(audio1.samples.all { it.isFinite() }) { "non-finite samples" }
            val peak1 = audio1.samples.maxOf { abs(it) }
            log("FIRST_SYNTH synthMs=${first.synthesisTimeMs} audioMs=${audio1.durationMs} rtf=${first.realTimeFactor} samples=${audio1.samples.size} peak=$peak1 pssKb=${pssKb()}")
            status("First synth PASS ${first.synthesisTimeMs}ms RTF=${first.realTimeFactor}. Playing...")

            // Playback
            runBlocking { engine.play(audio1, emergency = false) }
            log("FIRST_PLAY PASS")

            // Warm cycles
            val warmTimes = (1..10).map { i ->
                val (audio, result) = runBlocking { engine.synthesize(text, language) }
                check(audio.sampleRate == 16_000 && audio.samples.isNotEmpty())
                check(audio.samples.all { it.isFinite() && it in -1f..1f })
                val peak = audio.samples.maxOf { abs(it) }
                runBlocking { engine.play(audio, emergency = false) }
                log("WARM/$i synthMs=${result.synthesisTimeMs} audioMs=${audio.durationMs} rtf=${result.realTimeFactor} samples=${audio.samples.size} peak=$peak pssKb=${pssKb()}")
                result.synthesisTimeMs
            }
            val sorted = warmTimes.sorted()
            val median = sorted[(sorted.size - 1) / 2]
            val p95 = sorted[ceil(sorted.size * 0.95).toInt() - 1]
            log("WARM_STATS count=${warmTimes.size} median=$median p95=$p95 min=${sorted.first()} max=${sorted.last()} pssKb=${pssKb()} nativeHeapKb=${Debug.getNativeHeapAllocatedSize() / 1024}")
            status("PASS\nLoad: ${loadMs}ms\nFirst: ${first.synthesisTimeMs}ms\nWarm median: ${median}ms\nRTF~${first.realTimeFactor}\nPSS: ${pssKb()} KB")
        } finally {
            engine.release()
            log("released pssKb=${pssKb()}")
        }
    } catch (e: Throwable) {
        Log.e(TAG, "FAIL ${e::class.java.name}: ${e.message}", e)
        status("FAIL\n${e::class.java.simpleName}: ${e.message}")
    }

    private fun File.sha256() = inputStream().use { i ->
        MessageDigest.getInstance("SHA-256").let { d ->
            val b = ByteArray(65536)
            generateSequence { i.read(b).takeIf { n -> n > 0 } }.forEach { d.update(b, 0, it) }
            d.digest().joinToString("") { "%02x".format(it) }
        }
    }

    private fun pssKb(): Int { val m = Debug.MemoryInfo(); Debug.getMemoryInfo(m); return m.totalPss }
    private fun status(s: String) = runOnUiThread { output.text = "GUJARATI TTS SMOKE TEST\n\n$s" }
    private fun log(s: String) = Log.i(TAG, s)

    companion object {
        const val TAG = "VOKIE_GUJ_TTS"
    }
}
