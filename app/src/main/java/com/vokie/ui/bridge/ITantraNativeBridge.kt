package com.vokie.ui.bridge

import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.vokie.ai.AiOperation
import com.vokie.domain.model.PeerConnectionState
import com.vokie.domain.model.PeerTransportType
import com.vokie.domain.model.VoiceMessageState
import com.vokie.semantic.SemanticUrgency
import com.vokie.ui.communication.CommunicationViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ITantraNativeBridge(
    private val vm: CommunicationViewModel,
    private val webViewProvider: () -> WebView?,
    private val scope: CoroutineScope,
) {

    @JavascriptInterface
    fun onPttDown() {
        scope.launch(Dispatchers.Main) {
            vm.startVoice()
        }
    }

    @JavascriptInterface
    fun onPttUp() {
        scope.launch(Dispatchers.Main) {
            vm.stopVoice()
        }
    }

    @JavascriptInterface
    fun onSendMessage(peerId: String, text: String) {
        scope.launch(Dispatchers.Main) {
            vm.selectPeer(peerId)
            vm.send(text)
        }
    }

    @JavascriptInterface
    fun onRequestAi(operation: String, prompt: String, targetLanguage: String?) {
        val parsed = runCatching { AiOperation.valueOf(operation) }.getOrNull() ?: return
        scope.launch(Dispatchers.Main) { vm.requestAi(parsed, prompt, targetLanguage) }
    }

    @JavascriptInterface
    fun onCancelAiRequest() {
        scope.launch(Dispatchers.Main) { vm.cancelAiRequest() }
    }

    @JavascriptInterface
    fun onSelectPeer(peerId: String) {
        scope.launch(Dispatchers.Main) {
            vm.selectPeer(peerId)
        }
    }

    @JavascriptInterface
    fun startDiscovery() {
        scope.launch(Dispatchers.Main) {
            vm.startDiscovery()
        }
    }

    @JavascriptInterface
    fun stopDiscovery() {
        scope.launch(Dispatchers.Main) {
            vm.stopDiscovery()
        }
    }

    @JavascriptInterface
    fun connectPeer(peerId: String) {
        scope.launch(Dispatchers.Main) {
            vm.connectPeer(peerId)
        }
    }

    @JavascriptInterface
    fun onDiscoverWifiDirect() {
        scope.launch(Dispatchers.Main) { vm.discoverWifiDirect() }
    }

    @JavascriptInterface
    fun onConnectWifiDirect(address: String) {
        scope.launch(Dispatchers.Main) { vm.connectWifiDirect(address) }
    }

    @JavascriptInterface
    fun sendPairRequest(peerId: String) {
        scope.launch(Dispatchers.Main) {
            vm.sendPairRequest(peerId)
        }
    }

    @JavascriptInterface
    fun acceptPair(requestId: String) {
        scope.launch(Dispatchers.Main) {
            vm.acceptPairRequest(requestId)
        }
    }

    @JavascriptInterface
    fun declinePair(requestId: String) {
        scope.launch(Dispatchers.Main) {
            vm.declinePairRequest(requestId)
        }
    }

    @JavascriptInterface
    fun onStartCall(peerId: String) {
        scope.launch(Dispatchers.Main) { vm.startCall(peerId) }
    }

    @JavascriptInterface
    fun onAcceptCall(callId: String) {
        scope.launch(Dispatchers.Main) { vm.acceptCall(callId) }
    }

    @JavascriptInterface
    fun onRejectCall(callId: String) {
        scope.launch(Dispatchers.Main) { vm.rejectCall(callId) }
    }

    @JavascriptInterface
    fun onCancelCall(callId: String) {
        scope.launch(Dispatchers.Main) { vm.cancelCall(callId) }
    }

    @JavascriptInterface
    fun onEndCall(callId: String) {
        scope.launch(Dispatchers.Main) { vm.endCall(callId) }
    }

    @JavascriptInterface
    fun onSetCallMuted(muted: Boolean) {
        vm.setCallMuted(muted)
    }

    @JavascriptInterface
    fun onSetCallSpeakerphone(enabled: Boolean) {
        vm.setCallSpeakerphone(enabled)
    }

    @JavascriptInterface
    fun playMessageAudio(messageId: String, text: String, languageCode: String) {
        scope.launch(Dispatchers.Main) { vm.playVoiceNote(messageId, text, languageCode) }
    }

    @JavascriptInterface
    fun stopMessageAudio(messageId: String) {
        scope.launch(Dispatchers.Main) { vm.stopVoiceNote(messageId) }
    }

    @JavascriptInterface
    fun onUpdateDisplayName(name: String) {
        scope.launch(Dispatchers.Main) { vm.updateDisplayName(name) }
    }

    @JavascriptInterface
    fun onSelectLanguage(code: String) {
        scope.launch(Dispatchers.Main) { vm.selectLanguage(code) }
    }

    @JavascriptInterface
    fun onSetTtsSpeed(speed: Float) {
        scope.launch(Dispatchers.Main) { vm.setTtsSpeed(speed) }
    }

    @JavascriptInterface
    fun onMarkAsRead(peerId: String) {
        scope.launch(Dispatchers.Main) { vm.markConversationAsRead(peerId) }
    }

    @JavascriptInterface
    fun onDismissNotification(peerId: String) {
        scope.launch(Dispatchers.Main) { vm.markConversationAsRead(peerId) }
    }

    @JavascriptInterface
    fun cloudLogin(email: String, password: String) {
        vm.cloudLogin(email, password) { success, message ->
            webViewProvider()?.evaluateJavascript(
                "window.onCloudAuthResult && window.onCloudAuthResult(${success.toString()}, ${JSONObject.quote(message ?: "")});",
                null,
            )
            pushStateUpdate()
        }
    }

    @JavascriptInterface
    fun cloudLogout() {
        vm.cloudLogout { _, _ -> pushStateUpdate() }
    }

    @JavascriptInterface
    fun setCloudSyncEnabled(enabled: Boolean) {
        vm.setCloudSyncEnabled(enabled)
        pushStateUpdate()
    }

    @JavascriptInterface
    fun syncCloudNow() {
        vm.syncCloudNow()
    }

    @JavascriptInterface
    fun linkCloudConversation(localConversationId: String, cloudConversationId: Long) {
        vm.linkCloudConversation(localConversationId, cloudConversationId)
    }

    /** Opens the real chat after a notification PendingIntent is handled. */
    fun navigateToChat(peerId: String) {
        scope.launch(Dispatchers.Main) {
            webViewProvider()?.evaluateJavascript(
                "window.openNativeChat && window.openNativeChat(${JSONObject.quote(peerId)});",
                null,
            )
        }
    }

    @JavascriptInterface
    fun getInitialState(): String {
        return buildStateJson()
    }

    fun pushStateUpdate() {
        scope.launch(Dispatchers.Main) {
            val json = buildStateJson()
            webViewProvider()?.evaluateJavascript(
                "window.onAppStateUpdate && window.onAppStateUpdate($json);",
                null
            )
        }
    }

    private fun buildStateJson(): String {
        val root = JSONObject()
        val speak = vm.speakUiState.value
        val peers = vm.peersUiState.value
        val pairing = vm.pairingUiState.value
        val messages = vm.messagesUiState.value
        val call = vm.callUiState.value
        val identity = vm.settingsRepository.identity.value
        root.put("identity", JSONObject().apply {
            put("peerId", identity.deviceId)
            put("name", identity.displayName)
            put("initial", identity.avatarInitial)
        })
        val cloud = vm.cloudUser.value
        root.put("cloud", JSONObject().apply {
            put("signedIn", cloud != null && vm.cloudCredentialsAvailable())
            put("displayName", cloud?.displayName ?: JSONObject.NULL)
            put("email", cloud?.email ?: JSONObject.NULL)
            put("syncEnabled", vm.cloudSyncEnabled.value)
            put("syncState", vm.cloudSyncState.value.javaClass.simpleName)
        })
        root.put("ai", JSONObject().apply {
            val ai = vm.aiAssistant.value
            put("loading", ai.isLoading)
            put("error", ai.error ?: JSONObject.NULL)
            put("text", ai.response?.text ?: JSONObject.NULL)
            put("actionsRequireConfirmation", ai.response?.actions?.all { it.requiresConfirmation } ?: true)
        })
        // User-configured language and TTS speed.
        root.put("language", vm.settingsRepository.languagePreference.value.speakLanguage.code)
        root.put("ttsSpeed", vm.ttsSpeed.value)
        root.put("call", JSONObject().apply {
            put("callId", call.callId ?: JSONObject.NULL)
            put("state", call.callState.name)
            put("direction", call.direction?.name ?: JSONObject.NULL)
            put("peerId", call.peer?.peerId ?: JSONObject.NULL)
            put("peerName", call.peer?.displayName ?: call.peer?.peerId ?: JSONObject.NULL)
            put("peerInitial", call.peer?.initial?.ifBlank { call.peer?.displayName?.take(1)?.uppercase() } ?: JSONObject.NULL)
            put("failureReason", call.failureReason ?: JSONObject.NULL)
        })

        val activeList = peers.discoveredPeers.filter { it.connectionState == PeerConnectionState.ACTIVE }
        val activeCount = activeList.size

        // Strip text
        root.put("stripDot", if (activeCount > 0) "green" else "grey")
        root.put(
            "stripText",
            if (activeCount > 0) {
                "$activeCount active nearby · Wi-Fi preferred, Bluetooth fallback"
            } else {
                "No peers active · Offline"
            }
        )

        // Selected peer (include offline status)
        val selected = speak.selectedPeer
        if (selected != null) {
            val isActive = selected.connectionState == PeerConnectionState.ACTIVE
            val peerObj = JSONObject()
            peerObj.put("id", selected.peerId)
            peerObj.put("name", selected.displayName)
            peerObj.put("initial", selected.initial.ifBlank { selected.displayName.take(1) })
            peerObj.put("transport", when {
                !isActive -> "offline"
                selected.transport == PeerTransportType.WIFI -> "wifi"
                else -> "bluetooth"
            })
            peerObj.put("connected", isActive)
            root.put("selectedPeer", peerObj)
        } else {
            root.put("selectedPeer", JSONObject.NULL)
        }

        // PTT state is rendered from Android's real STT/turn state.
        val pttState = when (speak.pttState) {
            VoiceMessageState.LISTENING -> "listening"
            VoiceMessageState.PROCESSING_STT -> "understanding"
            VoiceMessageState.SENDING -> "sending"
            VoiceMessageState.DELIVERED -> "delivered"
            VoiceMessageState.PLAYING_TTS -> "speaking"
            VoiceMessageState.FAILED -> "failed"
            else -> "idle"
        }
        root.put("pttState", pttState)
        root.put("pttLabel", when (pttState) {
            "listening" -> "Listening…"
            "understanding" -> "Understanding…"
            "sending" -> "Sending…"
            "delivered" -> "Delivered"
            "speaking" -> "Speaking there"
            "failed" -> "Voice message failed"
            else -> "Hold to speak"
        })

        // Active peers
        val activeArray = JSONArray()
        activeList.forEach { p ->
            val obj = JSONObject()
            obj.put("id", p.peerId)
            obj.put("name", p.displayName)
            obj.put("initial", p.initial.ifBlank { p.displayName.take(1) })
            obj.put("transport", if (p.transport == PeerTransportType.WIFI) "wifi" else "bluetooth")
            obj.put("connected", true)
            activeArray.put(obj)
        }
        root.put("activePeers", activeArray)

        // Nearby, not paired peers (discovered but not active and not trusted)
        val discoveredArray = JSONArray()
        peers.discoveredPeers.filter { it.connectionState != PeerConnectionState.ACTIVE }.forEach { dp ->
            val obj = JSONObject()
            obj.put("id", dp.peerId)
            obj.put("name", dp.displayName)
            obj.put("initial", dp.initial.ifBlank { dp.displayName.take(1) })
            obj.put("transport", if (dp.transport == PeerTransportType.WIFI) "Wi-Fi Direct" else "Bluetooth")
            obj.put("connected", false)
            discoveredArray.put(obj)
        }
        root.put("discoveredPeers", discoveredArray)

        // Trusted peers
        val trustedArray = JSONArray()
        peers.trustedPeers.forEach { tp ->
            val obj = JSONObject()
            obj.put("id", tp.peerId)
            obj.put("name", tp.displayName)
            obj.put("initial", tp.displayName.take(1).uppercase())
            obj.put("status", tp.status.name)
            trustedArray.put(obj)
        }
        root.put("trustedPeers", trustedArray)

        val wifiPeers = JSONArray()
        vm.wifiDirectPeers.value.forEach { peer ->
            wifiPeers.put(JSONObject().apply { put("address", peer.address); put("name", peer.name); put("available", peer.available) })
        }
        root.put("wifiDirectPeers", wifiPeers)

        // Incoming pair request
        val pendingReq = peers.pendingRequests.firstOrNull { it.receiverPeerId == vm.deviceId }
        if (pendingReq != null) {
            val reqObj = JSONObject()
            reqObj.put("requestId", pendingReq.requestId)
            reqObj.put("senderPeerId", pendingReq.senderPeerId)
            val sender = peers.discoveredPeers.find { it.peerId == pendingReq.senderPeerId }
            reqObj.put("displayName", sender?.displayName ?: "iTantra Peer")
            reqObj.put("initial", (sender?.displayName ?: "A").take(1).uppercase())
            root.put("incomingPairRequest", reqObj)
        } else {
            root.put("incomingPairRequest", JSONObject.NULL)
        }

        // Conversations
        val convArray = JSONArray()
        messages.conversations.forEach { conv ->
            val peer = peers.discoveredPeers.find { it.peerId == conv.peerId }
            val trusted = peers.trustedPeers.find { it.peerId == conv.peerId }
            val name = peer?.displayName ?: trusted?.displayName ?: conv.peerId
            val lastMsg = messages.lastMessages[conv.peerId]
            val obj = JSONObject()
            obj.put("id", conv.conversationId)
            obj.put("peerId", conv.peerId)
            obj.put("name", name)
            obj.put("initial", name.take(1).uppercase())
            obj.put("lastMessage", lastMsg?.text ?: "")
            obj.put("time", lastMsg?.timestamp ?: conv.updatedAt)
            obj.put("unreadCount", conv.unreadCount)
            convArray.put(obj)
        }
        root.put("conversations", convArray)

        // Chat messages for active conversation
        val chat = vm.chatUiState.value
        val chatArray = JSONArray()
        chat.messages.forEach { m ->
            val obj = JSONObject()
            obj.put("id", m.id)
            obj.put("text", m.text)
            obj.put("senderId", m.senderId)
            obj.put("isOutgoing", m.senderId == vm.deviceId)
            obj.put("time", m.timestamp)
            obj.put("status", m.deliveryState.name)
            obj.put("type", m.messageType.name)
            obj.put("language", m.language)
            val isUrgent = m.semanticMetadata?.urgency in setOf(SemanticUrgency.URGENT, SemanticUrgency.CRITICAL) || m.priority >= 2
            obj.put("urgent", isUrgent)
            obj.put("semanticLabel", m.semanticMetadata?.displayLabel() ?: JSONObject.NULL)
            chatArray.put(obj)
        }
        root.put("chatMessages", chatArray)

        return root.toString()
    }
}
