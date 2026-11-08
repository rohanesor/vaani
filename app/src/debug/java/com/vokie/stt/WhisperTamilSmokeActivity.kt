package com.vokie.stt

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.vokie.VokieApplication
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Debug-only physical microphone/Whisper validation. It never sends a transcript. */
class WhisperTamilSmokeActivity : Activity() {
    private val scope = MainScope()
    private lateinit var view: TextView
    private val app get() = application as VokieApplication
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        view = TextView(this).apply { textSize = 18f; setPadding(30, 48, 30, 48); text = "WHISPER TAMIL STT\n\nActivity started" }
        setContentView(view)
        log("onCreate source=TA explicit")
        scope.launch { app.speechToText.status.collectLatest { status -> log("STATUS state=${status.state} vad=${status.vadState} result=${status.result?.text} language=${status.result?.language} loadMs=${status.modelLoadTimeMs} failure=${status.failure?.code}") } }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            log("MIC_PERMISSION requesting"); ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 41); status("Microphone permission required; grant it and relaunch")
        } else startCapture()
    }
    private fun startCapture() = scope.launch {
        try {
            val profile = UserLanguageProfile(VokieLanguage.TA, VokieLanguage.TA)
            status("Loading Whisper model...\nThen speak Tamil for 12 seconds")
            val loadStart = SystemClock.elapsedRealtime(); app.speechToText.initialize()
            log("MODEL_READY loadMs=${SystemClock.elapsedRealtime() - loadStart} pssKb=${pssKb()}")
            val utterances = listOf(
                "எனக்கு உதவி தேவை.", "தயவுசெய்து எனக்கு உதவுங்கள்.",
                "நீங்கள் எங்கே இருக்கிறீர்கள்?", "எனக்கு தண்ணீர் வேண்டும்.",
                "நான் பாதுகாப்பாக இருக்கிறேன்."
            )
            utterances.forEachIndexed { index, expected ->
                log("CAPTURE_BEGIN run=${index + 1} source=TA expected=$expected"); status("RECORDING ${index + 1}/5\nSpeak the displayed Tamil phrase")
                app.speechToText.start(SttLanguage.TAMIL, profile, finalizeOnVad = false)
                delay(10_000)
                log("CAPTURE_STOP run=${index + 1}"); app.speechToText.stop(); delay(6_000)
                val result = app.speechToText.status.value.result
                if (result != null) log("RESULT run=${index + 1} vad=${app.speechToText.status.value.vadState} transcript=${result.text} language=${result.language} transcribeMs=${result.processingTimeMs} audioMs=${result.audioDurationMs} pssKb=${pssKb()}")
                else { val failure=app.speechToText.status.value.failure; log("FAIL run=${index + 1} no result failure=${failure?.code}: ${failure?.userMessage}") }
            }
            status("COMPLETE\n5 controlled Tamil capture windows")
        } catch (e: Throwable) { Log.e(TAG, "FAILURE ${e::class.java.name}: ${e.message}", e); status("FAIL\n${e::class.java.simpleName}: ${e.message}") }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) { super.onRequestPermissionsResult(requestCode, permissions, results); if (requestCode == 41 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startCapture() }
    private fun pssKb(): Int { val info=Debug.MemoryInfo(); Debug.getMemoryInfo(info); return info.totalPss }
    private fun status(value:String)=runOnUiThread { view.text="WHISPER TAMIL STT\n\n$value" }; private fun log(value:String)=Log.i(TAG,value)
    companion object { const val TAG="VOKIE_WHISPER_TAMIL" }
}
