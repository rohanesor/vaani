package com.vokie.communication

import android.app.Activity
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.widget.TextView
import com.vokie.VokieApplication
import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.domain.model.VokieLanguage
import com.vokie.stt.UserLanguageProfile
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

/** Debug-only two-device Wi-Fi Direct PacketV2 validation; no production UI path. */
class WifiDirectSmokeActivity : Activity() {
    private val scope=MainScope(); private lateinit var view:TextView
    override fun onCreate(state:Bundle?) { super.onCreate(state); view=TextView(this).apply { textSize=17f; setPadding(30,40,30,40); text="WIFI DIRECT SMOKE" }; setContentView(view)
        val role=intent.getStringExtra("role") ?: "RECEIVER"; log("START role=$role"); val app=application as VokieApplication
        intent.getStringExtra("target")?.let { code -> VokieLanguage.fromCode(code)?.let { target -> scope.launch { app.userLanguageProfilePreferences.select(UserLanguageProfile(target, target)); log("RECEIVER_TARGET=$target") } } }
        scope.launch { app.transportManager.decodedFrames.collect { frame -> if(frame is TransportManager.DecodedTransportFrame.Message) log("RX text=${frame.packet.packet.payload.decodeToString()} id=${frame.packet.packet.messageId} transport=${frame.transport.type}") } }
        scope.launch { app.receiverPresentations.collectLatest { entries -> entries.values.forEach { p -> log("PRESENTATION source=${p.sourceText} sourceLang=${p.sourceLanguage} target=${p.targetLanguage} state=${p.state} output=${p.displayText}") } } }
        scope.launch {
            app.transportManager.discoverWifiDirect(); log("DISCOVERY started")
            if(role=="SENDER") { delay(10_000); val requestedPeer=intent.getStringExtra("peerAddress"); val peer=app.wifiDirectTransport.peers.value.firstOrNull { it.available && (requestedPeer == null || it.address.equals(requestedPeer, ignoreCase=true)) } ?: error("Requested Wi-Fi Direct peer not discovered: $requestedPeer"); log("CONNECT begin peer=${peer.address} name=${peer.name}"); val start=SystemClock.elapsedRealtime(); app.transportManager.connectWifiDirect(peer.address)
                repeat(40) { if(app.wifiDirectTransport.state.value==PacketTransportState.CONNECTED) return@repeat; delay(500) }
                check(app.wifiDirectTransport.state.value==PacketTransportState.CONNECTED) { "Wi-Fi Direct not connected: ${app.wifiDirectTransport.state.value}" }; log("CONNECTED ms=${SystemClock.elapsedRealtime()-start}")
                val fixedHindi = intent.getBooleanExtra("fixed_hi", false)
                val fixedTamil = intent.getBooleanExtra("fixed_ta", false)
                val text=when { fixedHindi -> CONTROLLED_HINDI; fixedTamil -> CONTROLLED_TAMIL; else -> intent.getStringExtra("text") ?: "VOKIE_TEST_A_TO_B" }
                val source=when { fixedHindi -> "HI"; fixedTamil -> "TA"; else -> intent.getStringExtra("source") ?: "EN" }
                repeat(intent.getIntExtra("count", if (fixedTamil) 3 else 1).coerceIn(1, 10)) { index ->
                    log("TX_SOURCE run=${index+1} text=$text language=$source")
                    val id=UUID.randomUUID().toString(); val result=app.transportManager.sendMessage(Message(id,app.deviceId,System.currentTimeMillis(),text,source,MessageType.TEXT,receiverId=peer.address,sequenceNumber=index.toLong()+1))
                    log("TX run=${index+1} text=$text ack=${result.acknowledged} ackMs=${result.ackLatencyMs} error=${result.error}")
                    if (fixedTamil) delay(1_000)
                }
            }
        }.invokeOnCompletion { e -> if(e!=null) log("FAILURE ${e::class.java.name}: ${e.message}") }
    }
    private fun log(s:String) { Log.i(TAG,s); runOnUiThread { view.append("\n$s") } }
    companion object { const val TAG="VOKIE_WIFI_SMOKE"; const val CONTROLLED_HINDI="मुझे मदद चाहिए।"; const val CONTROLLED_TAMIL="எனக்கு உதவி தேவை." }
}
