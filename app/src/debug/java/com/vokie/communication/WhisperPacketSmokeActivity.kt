package com.vokie.communication

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import com.vokie.VokieApplication
import com.vokie.domain.model.VokieLanguage
import com.vokie.stt.SttLanguage
import com.vokie.stt.UserLanguageProfile
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Debug-only Whisper-to-existing-PacketV2 validation. Receiver translation/TTS are suppressed. */
class WhisperPacketSmokeActivity : Activity() {
    private val scope=MainScope(); private lateinit var view:TextView
    private val app get()=application as VokieApplication
    override fun onCreate(state:Bundle?) { super.onCreate(state); getSharedPreferences(DEBUG_PREFS, MODE_PRIVATE).edit().putBoolean(TRANSPORT_ONLY, true).apply(); view=TextView(this).apply { textSize=17f; setPadding(30,40,30,40); text="WHISPER → PACKETV2" }; setContentView(view)
        val role=intent.getStringExtra("role") ?: "RECEIVER"; log("START role=$role")
        scope.launch { app.messageRepository.observeMessages().collectLatest { messages -> messages.filter { it.senderId != app.deviceId }.forEach { log("RX_MESSAGE id=${it.id} text=${it.text} language=${it.language}") } } }
        scope.launch {
            if(role=="SENDER") sendAfterConnect() else { app.transportManager.discoverWifiDirect(); log("RECEIVER_DISCOVERY started") }
        }
    }
    private suspend fun sendAfterConnect() {
        val stale=app.messageRepository.observeOutboundQueue().first(); stale.forEach { app.messageRepository.deleteMessage(it.id) }; log("OUTBOUND_PREFLIGHT cleared=${stale.size}")
        app.transportManager.discoverWifiDirect(); log("SENDER_DISCOVERY started"); delay(8_000)
        val peer=intent.getStringExtra("peerAddress") ?: error("peerAddress required")
        val started=SystemClock.elapsedRealtime(); log("CONNECT begin peer=$peer"); app.transportManager.connectWifiDirect(peer)
        repeat(40) { if(app.wifiDirectTransport.state.value==PacketTransportState.CONNECTED) return@repeat; delay(500) }
        check(app.wifiDirectTransport.state.value==PacketTransportState.CONNECTED) { "Wi-Fi Direct not connected: ${app.wifiDirectTransport.state.value}" }
        log("CONNECTED ms=${SystemClock.elapsedRealtime()-started}")
        val profile=UserLanguageProfile(VokieLanguage.TA, VokieLanguage.TA)
        status("RECORDING\nSpeak one short Tamil phrase now")
        log("CAPTURE_BEGIN source=TA"); app.speechToText.start(SttLanguage.TAMIL, profile, finalizeOnVad=false); delay(10_000); app.speechToText.stop(); delay(7_000)
        val result=app.speechToText.status.value.result ?: error("Whisper did not produce SttResult: ${app.speechToText.status.value.failure}")
        log("STT_RESULT text=${result.text} language=${result.language} processingMs=${result.processingTimeMs} audioMs=${result.audioDurationMs}")
        val message=app.enqueueWhisperTranscript(result); log("OUTBOUND_ENQUEUED id=${message.id} text=${message.text} language=${message.language} timestamp=${message.timestamp}")
        status("SENT\n${result.text}\nWaiting for receiver ACK")
        delay(12_000); log("COMPLETE messageId=${message.id}"); status("COMPLETE\nWhisper transcript sent")
    }
    override fun onDestroy() { getSharedPreferences(DEBUG_PREFS, MODE_PRIVATE).edit().remove(TRANSPORT_ONLY).apply(); scope.cancel(); super.onDestroy() }
    private fun log(s:String){ Log.i(TAG,s); runOnUiThread { view.append("\n$s") } }; private fun status(s:String)=runOnUiThread { view.text="WHISPER → PACKETV2\n\n$s" }
    companion object { const val TAG="VOKIE_WHISPER_PACKET"; const val DEBUG_PREFS="debug_validation"; const val TRANSPORT_ONLY="transport_only" }
}
