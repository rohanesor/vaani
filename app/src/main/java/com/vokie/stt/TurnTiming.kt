package com.vokie.stt

import android.os.SystemClock
import com.vokie.communication.VokieLog
import org.json.JSONObject

/** Local-only monotonic timing record. It is never persisted or encoded in PacketV2. */
enum class TurnTimingStatus { INCOMPLETE, COMPLETE, FAILED }
enum class TurnTimingFailure { STT, TRANSPORT, TRANSLATION, TTS, PLAYBACK }

data class TurnTimingSnapshot(
    val turnId: String?, val messageId: String?,
    val t0SpeechStartNs: Long? = null, val t1EndpointNs: Long? = null,
    val t2SttCompleteNs: Long? = null, val t3PacketCreatedNs: Long? = null,
    val t4PacketReceivedNs: Long? = null, val t5TranslationCompleteNs: Long? = null,
    val t6TtsStartNs: Long? = null, val t7AudioReadyNs: Long? = null,
    val t8PlaybackStartNs: Long? = null, val failure: TurnTimingFailure? = null,
    // P1.9 additions. Existing fields and semantics remain unchanged for C2 compatibility.
    val sttStartNs: Long? = null,
    val transportTxNs: Long? = null,
    val translationStartNs: Long? = null,
    val ttsCompleteNs: Long? = null,
    val playbackCompleteNs: Long? = null,
    val transportAckNs: Long? = null,
) {
    val status get() = if (failure != null) TurnTimingStatus.FAILED else if (listOf(t0SpeechStartNs,t1EndpointNs,t2SttCompleteNs,t3PacketCreatedNs,t4PacketReceivedNs,t5TranslationCompleteNs,t6TtsStartNs,t7AudioReadyNs,t8PlaybackStartNs).all { it != null }) TurnTimingStatus.COMPLETE else TurnTimingStatus.INCOMPLETE
    private fun delta(a: Long?, b: Long?) = if (a != null && b != null && b >= a) (b-a)/1_000_000L else null
    val speechDurationMs get() = delta(t0SpeechStartNs,t1EndpointNs)
    val sttLatencyMs get() = delta(t1EndpointNs,t2SttCompleteNs)
    val packetPreparationMs get() = delta(t2SttCompleteNs,t3PacketCreatedNs)
    val transportLatencyMs get() = delta(t3PacketCreatedNs,t4PacketReceivedNs)
    val translationLatencyMs get() = delta(t4PacketReceivedNs,t5TranslationCompleteNs)
    val ttsLatencyMs get() = delta(t6TtsStartNs,t7AudioReadyNs)
    val playbackQueueDelayMs get() = delta(t7AudioReadyNs,t8PlaybackStartNs)
    val endToEndLatencyMs get() = delta(t0SpeechStartNs,t8PlaybackStartNs)
    val postSttLatencyMs get() = delta(t2SttCompleteNs,t8PlaybackStartNs)
}

/** Debug-local bounded recorder. Sender and receiver logs correlate by messageId across phones. */
class TurnTimingRecorder(private val nowNs: () -> Long = { SystemClock.elapsedRealtimeNanos() }) {
    private val byTurn = linkedMapOf<String, TurnTimingSnapshot>()
    private val turnByMessage = mutableMapOf<String, String>()

    @Synchronized fun start(turnId: String) = updateTurn(turnId) { it.copy(t0SpeechStartNs = it.t0SpeechStartNs ?: nowNs()) }.also { event("T0_CAPTURE_START", turnId = turnId) }
    @Synchronized fun sttStart(turnId: String) = updateTurn(turnId) { it.copy(sttStartNs = it.sttStartNs ?: nowNs()) }.also { event("T1_STT_START", turnId = turnId) }
    @Synchronized fun endpoint(turnId: String) = updateTurn(turnId) { it.copy(t1EndpointNs = it.t1EndpointNs ?: nowNs()) }
    @Synchronized fun sttComplete(turnId: String, transcript: String? = null, audioDurationMs: Long? = null) {
        updateTurn(turnId) { it.copy(t2SttCompleteNs = it.t2SttCompleteNs ?: nowNs()) }
        event("T2_STT_COMPLETE", turnId = turnId, extra = mapOf("transcript" to transcript, "audio_duration_ms" to audioDurationMs))
    }
    @Synchronized fun associateMessage(turnId: String, messageId: String) {
        turnByMessage[messageId] = turnId
        updateTurn(turnId) { it.copy(messageId = messageId) }
    }
    @Synchronized fun packetCreated(messageId: String, sequenceNumber: Long? = null) {
        val turnId = turnByMessage[messageId]
        updateMessage(messageId) { it.copy(t3PacketCreatedNs = it.t3PacketCreatedNs ?: nowNs()) }
        event("T3_PACKET_CREATE", turnId, messageId, mapOf("sequence_number" to sequenceNumber))
    }
    /** Compatibility overload for existing C2 tests; production uses the post-encode overload. */
    @Synchronized fun packetCreated(turnId: String, messageId: String) {
        associateMessage(turnId, messageId)
        packetCreated(messageId, null)
    }
    @Synchronized fun transportTx(messageId: String, sequenceNumber: Long? = null) { updateMessage(messageId) { it.copy(transportTxNs=it.transportTxNs ?: nowNs()) }; event("T4_TRANSPORT_TX", messageId = messageId, extra = mapOf("sequence_number" to sequenceNumber)) }
    @Synchronized fun packetReceived(messageId: String, sequenceNumber: Long? = null) { updateMessage(messageId) { it.copy(t4PacketReceivedNs=it.t4PacketReceivedNs ?: nowNs()) }; event("T5_TRANSPORT_RX", messageId = messageId, extra = mapOf("sequence_number" to sequenceNumber)) }
    @Synchronized fun translationStart(messageId: String, source: String? = null, target: String? = null) { updateMessage(messageId) { it.copy(translationStartNs=it.translationStartNs ?: nowNs()) }; event("T6_TRANSLATION_START", messageId = messageId, extra = mapOf("source_language" to source, "target_language" to target)) }
    @Synchronized fun translationComplete(messageId: String) { updateMessage(messageId) { it.copy(t5TranslationCompleteNs=it.t5TranslationCompleteNs ?: nowNs()) }; event("T6_TRANSLATION_COMPLETE", messageId = messageId) }
    @Synchronized fun ttsStart(messageId: String) { updateMessage(messageId) { it.copy(t6TtsStartNs=it.t6TtsStartNs ?: nowNs()) }; event("TTS_START", messageId = messageId) }
    @Synchronized fun audioReady(messageId: String, audioDurationMs: Long? = null, rtf: Double? = null) { updateMessage(messageId) { it.copy(t7AudioReadyNs=it.t7AudioReadyNs ?: nowNs()) }; event("T7_TTS_COMPLETE", messageId = messageId, extra = mapOf("audio_duration_ms" to audioDurationMs, "rtf" to rtf)) }
    @Synchronized fun playbackStart(messageId: String) { updateMessage(messageId) { it.copy(t8PlaybackStartNs=it.t8PlaybackStartNs ?: nowNs()) }; event("PLAYBACK_START", messageId = messageId) }
    @Synchronized fun playbackComplete(messageId: String) { updateMessage(messageId) { it.copy(playbackCompleteNs=it.playbackCompleteNs ?: nowNs()) }; event("T8_PLAYBACK_COMPLETE", messageId = messageId) }
    @Synchronized fun transportAck(messageId: String, sequenceNumber: Long? = null) { updateMessage(messageId) { it.copy(transportAckNs=it.transportAckNs ?: nowNs()) }; event("ACK_RECEIVED", messageId = messageId, extra = mapOf("sequence_number" to sequenceNumber)) }
    @Synchronized fun fail(turnId: String?, messageId: String?, failure: TurnTimingFailure) {
        val id = turnId ?: messageId?.let { turnByMessage[it] } ?: "receiver:$messageId"
        updateTurn(id) { it.copy(messageId=messageId ?: it.messageId, failure=failure) }
        event("FAILURE", id, messageId, mapOf("failure" to failure.name))
    }
    @Synchronized fun snapshotForTurn(turnId: String) = byTurn[turnId]
    @Synchronized fun snapshotForMessage(messageId: String): TurnTimingSnapshot? = turnByMessage[messageId]?.let(byTurn::get) ?: byTurn["receiver:$messageId"]
    private fun updateMessage(messageId: String, f: (TurnTimingSnapshot)->TurnTimingSnapshot) { val id=turnByMessage[messageId] ?: "receiver:$messageId"; updateTurn(id) { f(it.copy(messageId=messageId)) } }
    private fun updateTurn(id: String, f: (TurnTimingSnapshot)->TurnTimingSnapshot) { byTurn[id]=f(byTurn[id] ?: TurnTimingSnapshot(id.takeUnless { it.startsWith("receiver:") }, null)); while(byTurn.size>128) byTurn.remove(byTurn.keys.first()) }
    private fun event(name: String, turnId: String? = null, messageId: String? = null, extra: Map<String, Any?> = emptyMap()) {
        val json = JSONObject()
        json.put("event", name)
        json.put("timestamp_ns", nowNs())
        json.put("run_id", messageId ?: turnId ?: JSONObject.NULL)
        val mappedTurn = turnId ?: messageId?.let(turnByMessage::get)
        val role = if (mappedTurn?.startsWith("receiver:") == true || (turnId == null && messageId != null && mappedTurn == null)) "receiver" else "sender"
        json.put("device_role", role)
        json.put("clock_domain", "device_elapsedRealtimeNanos")
        json.put("turn_id", turnId ?: mappedTurn ?: JSONObject.NULL)
        json.put("message_id", messageId ?: JSONObject.NULL)
        extra.forEach { (key, value) -> json.put(key, value ?: JSONObject.NULL) }
        runCatching { json.toString() }.getOrNull()?.let(VokieLog::timing)
    }
}
