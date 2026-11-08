package com.vokie.communication

import android.content.Context
import com.vokie.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.UUID

/** Offline call signaling and real-time PCM media over the active P2P transport. */
class CallManager(
    private val context: Context,
    private val localPeerId: String,
    private val transportManager: TransportManager,
    private val scope: CoroutineScope,
) {
    private var audioEngine: CallAudioEngine? = null
    private val _session = MutableStateFlow<CallSession?>(null)
    val session: StateFlow<CallSession?> = _session.asStateFlow()
    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    fun start(peerId: String) {
        if (peerId.isBlank() || active()) return
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        update(CallSession(id, peerId, CallState.CALLING, CallDirection.OUTGOING, transportManager.activeTransportType()?.toPeerTransport() ?: PeerTransportType.NONE, now))
        send(id, "CALL_INVITE", peerId)
    }

    fun accept(callId: String) {
        val current = _session.value?.takeIf { it.sessionId == callId && it.state == CallState.RINGING } ?: return
        update(current.copy(state = CallState.CONNECTING))
        startAudio(callId)
        send(callId, "CALL_ACCEPT", current.peerId)
    }

    fun reject(callId: String) {
        val current = _session.value?.takeIf { it.sessionId == callId } ?: return
        update(current.copy(state = CallState.REJECTED, endedAt = System.currentTimeMillis()))
        send(callId, "CALL_REJECT", current.peerId)
    }

    fun cancel(callId: String) = finish(callId, CallState.ENDED, "CALL_CANCEL")
    fun end(callId: String) = finish(callId, CallState.ENDED, "CALL_END")

    suspend fun handleSignal(json: JSONObject, transport: PacketTransport) {
        val action = json.optString("action")
        val callId = json.optString("callId")
        val remote = json.optString("callerPeerId").ifBlank { json.optString("peerId") }
        if (callId.isBlank() || remote.isBlank() || remote == localPeerId) return
        val current = _session.value
        when (action) {
            "CALL_INVITE" -> if (current != null && current.state !in terminal) {
                signal(callId, "CALL_BUSY", remote, transport)
            } else {
                update(CallSession(callId, remote, CallState.RINGING, CallDirection.INCOMING, transport.type.toPeerTransport(), System.currentTimeMillis()))
                VokieLog.rescue("CALL_RINGING callId=$callId peer=$remote")
            }
            "CALL_ACCEPT" -> current?.takeIf { it.sessionId == callId && it.direction == CallDirection.OUTGOING }?.let {
                update(it.copy(state = CallState.CONNECTING)); startAudio(callId)
            }
            "CALL_REJECT" -> current?.takeIf { it.sessionId == callId }?.let { finishLocal(it, CallState.REJECTED) }
            "CALL_BUSY" -> current?.takeIf { it.sessionId == callId }?.let { finishLocal(it, CallState.FAILED, "Peer is busy") }
            "CALL_CANCEL" -> current?.takeIf { it.sessionId == callId }?.let { finishLocal(it, CallState.ENDED) }
            "CALL_END" -> current?.takeIf { it.sessionId == callId }?.let { finishLocal(it, CallState.ENDED) }
        }
    }

    private fun finish(callId: String, state: CallState, action: String) {
        val current = _session.value?.takeIf { it.sessionId == callId } ?: return
        finishLocal(current, state)
        send(callId, action, current.peerId)
    }

    /** Called from transport dispatch for a CALL_AUDIO frame; media never reaches Room. */
    fun handleAudio(packet: PacketV2.Decoded.MessagePacket) {
        val current = _session.value ?: return
        if (current.sessionId != packet.packet.messageId || current.state !in setOf(CallState.CONNECTING, CallState.CONNECTED)) return
        audioEngine?.receive(packet.packet.sequenceNumber, packet.packet.payload)
        if (packet.packet.sequenceNumber % 50L == 0L) VokieLog.rescue("CALL_AUDIO_RX seq=${packet.packet.sequenceNumber} bytes=${packet.packet.payload.size}")
    }

    fun setMuted(muted: Boolean) = audioEngine?.setMuted(muted)
    fun setSpeakerphone(enabled: Boolean) = audioEngine?.setSpeakerphone(enabled)

    /** Ends media immediately when the one active P2P socket disappears. */
    fun onTransportDisconnected() {
        _session.value?.takeIf { it.state in setOf(CallState.CALLING, CallState.RINGING, CallState.CONNECTING, CallState.CONNECTED) }
            ?.let { finishLocal(it, CallState.FAILED, "Peer connection lost") }
    }

    private fun startAudio(callId: String) {
        scope.launch(Dispatchers.IO) {
            val current = _session.value?.takeIf { it.sessionId == callId } ?: return@launch
            if (audioEngine != null) return@launch
            val engine = CallAudioEngine(
                context.applicationContext,
                scope,
                onFrame = { sequence, pcm -> transportManager.sendCallAudio(callId, sequence, localPeerId, pcm) },
                onFailure = { reason ->
                    _session.value?.takeIf { it.sessionId == callId }?.let { finishLocal(it, CallState.FAILED, reason) }
                },
            )
            audioEngine = engine
            if (engine.start()) {
                _session.value?.takeIf { it.sessionId == callId }?.let { update(it.copy(state = CallState.CONNECTED)) }
            } else {
                audioEngine = null
            }
        }
    }

    private fun finishLocal(current: CallSession, state: CallState, reason: String? = null) {
        update(current.copy(state = state, endedAt = System.currentTimeMillis(), failureReason = reason))
        val engine = audioEngine
        audioEngine = null
        if (engine != null) scope.launch(Dispatchers.IO) { engine.stop() }
    }

    private fun send(callId: String, action: String, peerId: String) {
        scope.launch(Dispatchers.IO) {
            val transport = transportManager.activePacketTransport() ?: run {
                _session.value?.takeIf { it.sessionId == callId }?.let { update(it.copy(state = CallState.FAILED, endedAt = System.currentTimeMillis(), failureReason = "No connected transport")) }
                return@launch
            }
            signal(callId, action, peerId, transport)
        }
    }

    private suspend fun signal(callId: String, action: String, peerId: String, transport: PacketTransport) {
        val payload = JSONObject().apply {
            put("action", action); put("callId", callId); put("peerId", localPeerId)
            put("callerPeerId", if (_session.value?.direction == CallDirection.OUTGOING) localPeerId else peerId)
            put("calleePeerId", if (_session.value?.direction == CallDirection.OUTGOING) peerId else localPeerId)
        }.toString()
        val message = Message(UUID.randomUUID().toString(), localPeerId, System.currentTimeMillis(), payload, "EN", MessageType.SYSTEM, receiverId = peerId, requiresAck = true)
        val result = transportManager.sendMessage(message)
        if (!result.acknowledged && _session.value?.sessionId == callId) {
            update(_session.value!!.copy(state = CallState.FAILED, endedAt = System.currentTimeMillis(), failureReason = result.error ?: "Signaling acknowledgement failed"))
        }
        VokieLog.rescue("CALL_SIGNAL action=$action callId=$callId peer=$peerId acknowledged=${result.acknowledged}")
    }

    private fun update(value: CallSession) {
        _session.value = value
        _uiState.value = CallUiState(
            callState = value.state,
            direction = value.direction,
            callId = value.sessionId,
            failureReason = value.failureReason,
            // Peer metadata is resolved by CommunicationViewModel's combine; provide peerId
            // directly here for native bridge serialization while the lookup completes.
            peer = com.vokie.domain.model.Peer(id = value.peerId, name = value.peerId, address = value.peerId),
        )
    }

    private fun active() = _session.value?.state?.let { it !in terminal } == true
    private val terminal = setOf(CallState.IDLE, CallState.REJECTED, CallState.MISSED, CallState.FAILED, CallState.ENDED)
    private fun TransportType.toPeerTransport() = if (this == TransportType.WIFI_DIRECT) PeerTransportType.WIFI else PeerTransportType.BLUETOOTH
    private fun PeerTransportType.toPeerTransport() = this
}
