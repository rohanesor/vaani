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
import kotlin.math.ceil

/** Debug-only independent Tamil MMS/VITS physical-validation surface. */
class TamilTtsSmokeActivity : Activity() {
    private lateinit var output: TextView
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        output = TextView(this).apply { textSize = 18f; setPadding(32,48,32,48); text = "TAMIL TTS SMOKE TEST\n\nActivity started" }
        setContentView(output); log("onCreate"); Thread({ runSmoke() }, "tamil-tts-smoke").start()
    }
    private fun runSmoke() = try {
        log("worker started sherpa=$SHERPA_ONNX_VERSION"); status("Model staging...")
        val language = if (intent.getStringExtra("language") == "HINDI") TtsLanguage.HINDI else TtsLanguage.TAMIL
        val text = if (language == TtsLanguage.HINDI) "मुझे मदद चाहिए।" else "எனக்கு உதவி தேவை."
        val models = TtsModelManager(applicationContext)
        repeat(60) { if (models.isInstalled(language)) return@repeat; Thread.sleep(500) }
        check(models.isInstalled(language)) { "Approved ${language.iso6393} model was not staged" }
        val model = models.modelFile(language)
        check(model.isFile && model.sha256() == SHA[language]) { "${language.iso6393} model integrity mismatch" }
        log("stage/hash PASS language=$language path=${models.modelDirectory(language)}")
        val engine = SherpaOnnxTtsEngine(models, VokieAudioPlayer(applicationContext))
        try {
            log("memory before model load pssKb=${pssKb()}")
            status("Model loading..."); val loadStart=SystemClock.elapsedRealtime(); runBlocking { engine.initialize(language) }
            check(engine.status.value.state == TtsState.READY) { "TTS load state=${engine.status.value}" }
            log("model load PASS ms=${SystemClock.elapsedRealtime()-loadStart} pssKb=${pssKb()}")
            status("First synthesis and playback...")
            val (audio, first)=runBlocking { engine.synthesize(text, language) }
            val expectedRate = if (language == TtsLanguage.HINDI) 22_050 else 16_000
            check(audio.sampleRate == expectedRate && audio.samples.isNotEmpty()) { "Expected $expectedRate Hz, got ${audio.sampleRate} Hz" }; check(audio.samples.maxOf { kotlin.math.abs(it) } <= 1f)
            log("first PASS synthMs=${first.synthesisTimeMs} audioMs=${first.audioDurationMs} rtf=${first.realTimeFactor} samples=${audio.samples.size} peak=${audio.samples.maxOf { kotlin.math.abs(it) }}")
            runBlocking { engine.play(audio, emergency=false) }; log("playback PASS")
            val ten = runCycles(engine, language, text, 10, "TEN")
            logStats("TEN", ten)
            val twenty = runCycles(engine, language, text, 20, "TWENTY")
            logStats("TWENTY", twenty)
            status("PASS\n10 + 20 synthesis/playback cycles\n${if (language == TtsLanguage.HINDI) "22.05 kHz Hindi" else "16 kHz Tamil"} audio")
        } finally { engine.release(); log("released pssKb=${pssKb()}") }
    } catch (e: Throwable) { Log.e(TAG,"FAILURE ${e::class.java.name}: ${e.message}",e); status("FAIL\n${e::class.java.simpleName}: ${e.message}") }
    private fun runCycles(engine: SherpaOnnxTtsEngine, language: TtsLanguage, text: String, count: Int, label: String): List<Long> = (1..count).map { index ->
        val (audio, result) = runBlocking { engine.synthesize(text, language) }
        val expectedRate = if (engine.status.value.activeLanguage == TtsLanguage.HINDI) 22_050 else 16_000
        check(audio.sampleRate == expectedRate && audio.samples.isNotEmpty()) { "$label/$index expected $expectedRate Hz, got ${audio.sampleRate} Hz" }
        check(audio.samples.all { it.isFinite() && it in -1f..1f }) { "$label/$index invalid PCM float" }
        val peak = audio.samples.maxOf { kotlin.math.abs(it) }
        runBlocking { engine.play(audio, emergency = false) }
        log("$label/$index synthMs=${result.synthesisTimeMs} audioMs=${audio.durationMs} rtf=${result.realTimeFactor} samples=${audio.samples.size} peak=$peak playback=PASS pssKb=${pssKb()}")
        result.synthesisTimeMs
    }
    private fun logStats(label: String, values: List<Long>) { val ordered=values.sorted(); log("$label STATS count=${values.size} first=${values.first()} median=${ordered[(values.size-1)/2]} p95=${ordered[ceil(values.size*.95).toInt()-1]} min=${ordered.first()} max=${ordered.last()} final=${values.last()} pssKb=${pssKb()}") }
    private fun File.sha256()=inputStream().use { i -> MessageDigest.getInstance("SHA-256").let { d -> val b=ByteArray(65536); generateSequence { i.read(b).takeIf { n->n>0 } }.forEach { d.update(b,0,it) }; d.digest().joinToString("") { "%02x".format(it) } } }
    private fun pssKb():Int { val m=Debug.MemoryInfo(); Debug.getMemoryInfo(m); return m.totalPss }
    private fun status(s:String)=runOnUiThread { output.text="TAMIL TTS SMOKE TEST\n\n$s" }; private fun log(s:String)=Log.i(TAG,s)
    companion object { const val TAG="VOKIE_TAMIL_TTS"; val SHA=mapOf(TtsLanguage.TAMIL to "c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88", TtsLanguage.HINDI to "8871f3e07adb6ca490f8dbcd3956a8647c53c35b5d0a1c2a8d097b3bf721a31b") }
}
