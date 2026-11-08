package com.vokie.ui.communication

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.vokie.BuildConfig
import com.vokie.ai.*
import com.vokie.VokieApplication
import com.vokie.communication.CommunicationPreferences
import com.vokie.communication.PeerSessionState
import com.vokie.communication.VokieLog
import com.vokie.domain.model.*
import com.vokie.models.DownloadState
import com.vokie.location.LocationPacket
import com.vokie.location.LocationAvailability
import com.vokie.stt.SttLanguage
import com.vokie.stt.SttState
import com.vokie.stt.TurnEvent
import com.vokie.stt.TurnMode
import com.vokie.stt.SttStatus
import com.vokie.stt.UserLanguageProfile
import com.vokie.stt.resolveProductionSttLanguage
import com.vokie.tts.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID

data class AiAssistantUiState(
    val isLoading: Boolean = false,
    val response: AiResponse? = null,
    val error: String? = null,
)

class CommunicationViewModel(application: Application) : AndroidViewModel(application) {
    private val instanceId = UUID.randomUUID().toString()
    private val app = application as VokieApplication
    val deviceId: String get() = app.deviceId
    private val repository = app.messageRepository
    private val manager = app.transportManager
    private val speechToText = app.speechToText
    private val turnManager = app.continuousTurnManager
    private val textToSpeech = app.textToSpeech
    private val communicationPreferences = app.communicationPreferences
    val cloudUser = app.database.cloudUser().observe()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val cloudSyncEnabled = app.cloudSyncPreferences.enabled
    val cloudSyncState = app.cloudSyncCoordinator.syncState

    val pushToTalkEnabled: StateFlow<Boolean> = communicationPreferences.pushToTalkEnabled
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val alertsFullVolume: StateFlow<Boolean> = communicationPreferences.alertsFullVolume
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val messages: StateFlow<List<Message>> = repository.observeMessages()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val peers = manager.peers
    val connectionState = manager.connectionState
    val connectedPeerId = manager.connectedPeerId
    val sttStatus: StateFlow<SttStatus> = speechToText.status
    val preferredLanguage: StateFlow<UserLanguageProfile?> = app.userLanguageProfilePreferences.profile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val _debugFastSttEnabled = MutableStateFlow(false)
    /** Debug-only product experiment; Release always uses the selected STT mode. */
    val debugFastSttEnabled: StateFlow<Boolean> = _debugFastSttEnabled.asStateFlow()
    val ttsStatus: StateFlow<TtsStatus> = textToSpeech.status
    val messageTtsStates: StateFlow<Map<String, MessageTtsState>> = textToSpeech.messageStates
    val installedTtsLanguages: StateFlow<Set<TtsLanguage>> = textToSpeech.installedLanguages
    val ttsSpeed: StateFlow<Float> = textToSpeech.speed.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_TTS_SPEED)
    val modelDownloadState: StateFlow<DownloadState> = app.modelDownloads.state
    val telemetryLocation = app.locationProvider.location
    val telemetryHeading = app.headingProvider.reading
    val telemetryRssi = app.rssiTelemetry.state
    val telemetryGuidance = app.emergencyGuidance.state
    val proximityMeasurements = app.proximityMeasurements.latest
    val localizationStates = app.localizationEngine.states
    val receiverPresentations = app.receiverPresentations
    // Wi-Fi Direct remains a Debug-only physical-validation path until its lifecycle is proven.
    val wifiDirectState = app.wifiDirectTransport.state
    val wifiDirectPeers = app.wifiDirectTransport.peers
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _aiAssistant = MutableStateFlow(AiAssistantUiState())
    val aiAssistant: StateFlow<AiAssistantUiState> = _aiAssistant.asStateFlow()
    private var aiRequestJob: Job? = null
    private val _locationShareState = MutableStateFlow("IDLE")
    private enum class VoiceCommand { START, STOP }
    private val voiceCommands = Channel<VoiceCommand>(Channel.UNLIMITED)
    val locationShareState: StateFlow<String> = _locationShareState.asStateFlow()

    /** Peer-scoped session state from the PeerSessionManager. */
    val peerSessions: StateFlow<Map<String, PeerSessionState>> = app.peerSessionManager.sessions

    /** The currently selected peer for conversation filtering. Defaults to connectedPeerId. */
    private val _selectedPeerId = MutableStateFlow<String?>(null)
    val selectedPeerId: StateFlow<String?> = _selectedPeerId.asStateFlow()

    /**
     * The effective peer for message filtering: explicit selection takes priority,
     * falling back to the transport-level connected peer for backward compatibility.
     */
    val effectivePeerId: StateFlow<String?> = combine(_selectedPeerId, connectedPeerId, peerSessions) { selected, connected, sessions ->
        val raw = selected ?: connected
        // Bluetooth transport exposes a MAC while packets/conversations use app UUIDs.
        sessions.values.firstOrNull { it.bluetoothAddress.equals(raw, ignoreCase = true) }?.peerId ?: raw
    }.stateIn(viewModelScope, SharingStarted.Eagerly, connectedPeerId.value)

    /**
     * Resolve a peer identifier (which may be a Bluetooth MAC address) to the set of
     * peer UUIDs that should be included in message filtering. This handles the case
     * where connectedPeerId is a MAC address but messages use app-level UUIDs.
     */
    private fun resolvePeerIds(peerId: String?, sessions: Map<String, PeerSessionState>): Set<String> {
        if (peerId == null) return emptySet()
        val result = mutableSetOf(peerId)
        // If peerId is a Bluetooth MAC address, find the peer UUID associated with it
        sessions.values.find { it.bluetoothAddress == peerId }?.let { result.add(it.peerId) }
        // If peerId is a UUID, also include its Bluetooth address for matching
        sessions[peerId]?.bluetoothAddress?.let { result.add(it) }
        return result
    }

    /** Messages filtered to the currently effective peer. */
    val selectedPeerMessages: StateFlow<List<Message>> = combine(messages, effectivePeerId, peerSessions) { allMessages, peerId, sessions ->
        if (peerId == null) allMessages
        else {
            val peerIds = resolvePeerIds(peerId, sessions)
            allMessages.filter { msg ->
                peerIds.any { id -> msg.senderId == id || msg.receiverId == id }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun cloudCredentialsAvailable(): Boolean = app.cloudCredentials.token() != null

    fun cloudLogin(email: String, password: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            runCatching {
                val result = app.cloudAuthClient.login(email.trim(), password)
                app.cloudSyncRepository.saveAuthState(
                    result.userId, result.email ?: email.trim(), result.displayName,
                    result.preferredLanguage, result.avatarHue, result.token,
                    System.currentTimeMillis() + 14L * 24 * 60 * 60 * 1000,
                )
                app.cloudSyncRepository.migrateLegacyCredential()
                app.cloudSyncCoordinator.cancelAllSync()
                result
            }.onSuccess { onResult(true, it.displayName) }
                .onFailure { onResult(false, it.message ?: "Cloud login failed") }
        }
    }

    fun cloudLogout(onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        viewModelScope.launch {
            val token = app.cloudCredentials.token()
            runCatching { if (token != null) app.cloudAuthClient.logout(token) }
                .onFailure { /* local logout still proceeds; do not retain credentials */ }
            app.cloudSyncCoordinator.cancelAllSync()
            app.cloudSyncRepository.logout()
            onResult(true, null)
        }
    }

    fun setCloudSyncEnabled(enabled: Boolean) {
        app.cloudSyncPreferences.setEnabled(enabled)
        if (enabled) {
            app.cloudSyncCoordinator.scheduleSyncOnce()
            app.cloudSyncCoordinator.startPeriodicSync()
        } else app.cloudSyncCoordinator.cancelAllSync()
    }

    fun syncCloudNow() {
        viewModelScope.launch { runCatching { app.cloudSyncCoordinator.syncNow() } }
    }

    fun linkCloudConversation(localConversationId: String, cloudConversationId: Long) {
        viewModelScope.launch {
            app.cloudSyncRepository.linkLocalConversation(cloudConversationId, localConversationId)
            if (app.cloudSyncPreferences.enabled.value) app.cloudSyncCoordinator.scheduleSyncOnce()
        }
    }

    fun requestAi(operation: AiOperation, prompt: String = "", targetLanguage: String? = null) {
        val peerId = effectivePeerId.value
        if (peerId.isNullOrBlank()) {
            _aiAssistant.value = AiAssistantUiState(error = "Choose a conversation before requesting assistance.")
            return
        }
        aiRequestJob?.cancel()
        aiRequestJob = viewModelScope.launch {
            _aiAssistant.value = AiAssistantUiState(isLoading = true)
            runCatching {
                val local = selectedPeerMessages.value.map { AiMessage(it.id, it.senderId, it.text, it.timestamp) }
                val relevant = ConversationContextRetriever().relevant(local, "$prompt ${operation.name}")
                app.aiProvider.assist(AiRequest(operation, peerId, deviceId, prompt, relevant, targetLanguage))
            }.onSuccess { response -> _aiAssistant.value = AiAssistantUiState(response = response) }
                .onFailure { _aiAssistant.value = AiAssistantUiState(error = it.message ?: "AI assistance is unavailable.") }
        }
    }

    fun cancelAiRequest() {
        aiRequestJob?.cancel()
        _aiAssistant.value = AiAssistantUiState(error = "AI request cancelled.")
    }

    fun selectPeer(peerId: String?) {
        val canonical = peerId?.let { raw ->
            peerSessions.value.values.firstOrNull { it.bluetoothAddress.equals(raw, ignoreCase = true) }?.peerId ?: raw
        }
        _selectedPeerId.value = canonical
        if (canonical != null) {
            app.peerSessionManager.markAsRead(canonical)
            app.notificationManager.cancelNotificationForPeer(canonical)
        }
        VokieLog.rescue("SELECTED_PEER peer=${canonical ?: "NONE"}")
    }

    /** Total unread messages across all peers (for badge display). */
    val totalUnreadCount: StateFlow<Int> = peerSessions.map { sessions ->
        sessions.values.sumOf { it.unreadCount }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** Get unread count for a specific peer. */
    fun unreadCountFor(peerId: String): Int = peerSessions.value[peerId]?.unreadCount ?: 0

    val peerRepository = app.peerRepository
    val pairingRepository = app.pairingRepository
    val conversationRepository = app.conversationRepository
    val settingsRepository = app.settingsRepository

    private val turnAndStt: StateFlow<Pair<com.vokie.stt.TurnState, SttStatus>> = combine(turnManager.state, sttStatus) { turn, stt -> turn to stt }
        .stateIn(viewModelScope, SharingStarted.Eagerly, turnManager.state.value to sttStatus.value)

    // 1. SpeakUiState
    val speakUiState: StateFlow<SpeakUiState> = combine(
        effectivePeerId,
        peerRepository.observePeers(),
        connectionState,
        turnAndStt,
        _error,
    ) { peerId: String?, peersList: List<Peer>, conn: TransportConnectionState, state: Pair<com.vokie.stt.TurnState, SttStatus>, err: String? ->
        val (turnState, stt) = state
        val currentPeer = peersList.find { it.peerId == peerId }
            ?: peersList.firstOrNull { it.connectionState == PeerConnectionState.ACTIVE }
        // Turn lifecycle wins over stale engine LISTENING/RESULT after PTT release.
        val ptt = when (turnState) {
            com.vokie.stt.TurnState.LISTENING -> VoiceMessageState.LISTENING
            com.vokie.stt.TurnState.PROCESSING, com.vokie.stt.TurnState.SENTENCE_READY -> VoiceMessageState.PROCESSING_STT
            com.vokie.stt.TurnState.ERROR -> VoiceMessageState.FAILED
            else -> if (stt.state == SttState.ERROR) VoiceMessageState.FAILED else VoiceMessageState.IDLE
        }
        SpeakUiState(
            selectedPeer = currentPeer,
            connectionState = currentPeer?.connectionState ?: PeerConnectionState.AVAILABLE,
            transport = currentPeer?.transport ?: PeerTransportType.NONE,
            pttState = ptt,
            recognizedText = stt.result?.text,
            outgoingMessage = null,
            deliveryState = null,
            remoteSpeaking = textToSpeech.status.value.state == TtsState.PLAYING,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpeakUiState())

    // 2. MessagesUiState
    val messagesUiState: StateFlow<MessagesUiState> = combine(
        conversationRepository.observeConversations(),
        totalUnreadCount,
        peerRepository.observePeers(),
        messages,
        peerSessions,
    ) { convos: List<Conversation>, unread: Int, peersList: List<Peer>, allMsgs: List<Message>, sessions: Map<String, PeerSessionState> ->
        fun canonical(raw: String): String = sessions.values.firstOrNull { it.bluetoothAddress.equals(raw, ignoreCase = true) }?.peerId ?: raw
        val lastMsgsMap = allMsgs.groupBy { message ->
            canonical(if (message.senderId == deviceId) message.receiverId ?: message.senderId else message.senderId)
        }.mapValues { entry -> entry.value.maxByOrNull { m: Message -> m.timestamp } ?: entry.value.first() }
        val activePeers = peersList.filter { it.connectionState == PeerConnectionState.ACTIVE }
        // A legacy MAC row and current UUID row describe one handset. Collapse them for UI;
        // Room data remains untouched so no message history is lost.
        val collapsed = convos.groupBy { canonical(it.peerId) }.map { (peerId, rows) ->
            val newest = rows.maxByOrNull { it.updatedAt }!!
            newest.copy(peerId = peerId, unreadCount = rows.sumOf { it.unreadCount })
        }
        val knownPeerIds = collapsed.mapTo(mutableSetOf()) { it.peerId }
        val synthesized = activePeers.filter { it.peerId !in knownPeerIds }.map { peer ->
            Conversation("active-${peer.peerId}", peer.peerId, System.currentTimeMillis(), System.currentTimeMillis(), 0)
        }
        MessagesUiState(
            conversations = (collapsed + synthesized).sortedByDescending { it.updatedAt },
            unreadCount = maxOf(unread, collapsed.sumOf { it.unreadCount }),
            activePeers = activePeers,
            lastMessages = lastMsgsMap,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState())

    // 3. ChatUiState
    val chatUiState: StateFlow<ChatUiState> = combine(
        effectivePeerId,
        peerRepository.observePeers(),
        selectedPeerMessages,
        sttStatus
    ) { peerId: String?, peersList: List<Peer>, msgs: List<Message>, stt: SttStatus ->
        val currentPeer = peersList.find { it.peerId == peerId }
        val ptt = when (stt.state) {
            SttState.LISTENING -> VoiceMessageState.LISTENING
            SttState.PROCESSING -> VoiceMessageState.PROCESSING_STT
            SttState.ERROR -> VoiceMessageState.FAILED
            else -> VoiceMessageState.IDLE
        }
        ChatUiState(
            peer = currentPeer,
            connectionState = currentPeer?.connectionState ?: PeerConnectionState.OFFLINE,
            messages = msgs,
            inputText = "",
            sendingState = msgs.lastOrNull { it.senderId == app.deviceId }?.status,
            voiceState = ptt,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChatUiState())

    // 4. PeersUiState
    val peersUiState: StateFlow<PeersUiState> = combine(
        peerRepository.observeDiscoveredPeers(),
        peerRepository.observeTrustedPeers(),
        pairingRepository.observePendingRequests(),
        peerSessions
    ) { discovered: List<Peer>, trusted: List<TrustedPeer>, requests: List<PairRequest>, sessions: Map<String, PeerSessionState> ->
        val activeSessionsList = sessions.values.map { s: PeerSessionState ->
            ConnectionSession(
                sessionId = "sess-${s.peerId}",
                peerId = s.peerId,
                transport = PeerTransportType.fromTransportType(s.transport),
                state = when (s.connectionState) {
                    TransportConnectionState.CONNECTED -> ConnectionSessionState.ACTIVE
                    TransportConnectionState.CONNECTING -> ConnectionSessionState.CONNECTING
                    TransportConnectionState.DISCONNECTED -> ConnectionSessionState.DISCONNECTED
                    TransportConnectionState.FAILED -> ConnectionSessionState.FAILED
                    else -> ConnectionSessionState.DISCONNECTED
                },
                startedAt = s.lastSeen ?: System.currentTimeMillis(),
                lastActivityAt = s.lastSeen ?: System.currentTimeMillis(),
            )
        }
        PeersUiState(
            discoveredPeers = discovered,
            trustedPeers = trusted,
            pendingRequests = requests,
            activeSessions = activeSessionsList,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeersUiState())

    // 5. PairingUiState
    val pairingUiState: StateFlow<PairingUiState> = combine(
        effectivePeerId,
        peerRepository.observePeers(),
        pairingRepository.observePendingRequests(),
        _error
    ) { peerId: String?, peersList: List<Peer>, pendingReqs: List<PairRequest>, err: String? ->
        val currentPeer = peersList.find { it.peerId == peerId }
        val req = pendingReqs.find { it.receiverPeerId == peerId || it.senderPeerId == peerId }
        PairingUiState(
            selectedPeer = currentPeer,
            requestState = req?.status,
            error = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PairingUiState())

    // 6. SettingsUiState
    val settingsUiState: StateFlow<SettingsUiState> = combine(
        settingsRepository.identity,
        settingsRepository.languagePreference,
        wifiDirectState,
        connectionState
    ) { identity: UserIdentity, langPref: LanguagePreference, wifiState: com.vokie.communication.PacketTransportState, connState: TransportConnectionState ->
        SettingsUiState(
            identity = identity,
            speakLanguage = langPref.speakLanguage,
            understandLanguage = langPref.understandLanguage,
            wifiState = wifiState != com.vokie.communication.PacketTransportState.FAILED,
            bluetoothState = connState != TransportConnectionState.BLUETOOTH_DISABLED,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState(
        identity = settingsRepository.identity.value,
        speakLanguage = settingsRepository.languagePreference.value.speakLanguage,
        understandLanguage = settingsRepository.languagePreference.value.understandLanguage,
    ))

    // 7. Real offline call signaling and media state.
    val callUiState: StateFlow<CallUiState> = combine(app.callManager.uiState, peerRepository.observePeers()) { call, peers ->
        call.copy(peer = call.callId?.let { id -> app.callManager.session.value?.peerId }?.let { peerId -> peers.find { it.peerId == peerId } })
    }.stateIn(viewModelScope, SharingStarted.Eagerly, CallUiState())

    fun startCall(peerId: String) = action { app.callManager.start(peerId) }
    fun acceptCall(callId: String) = action { app.callManager.accept(callId) }
    fun rejectCall(callId: String) = action { app.callManager.reject(callId) }
    fun cancelCall(callId: String) = action { app.callManager.cancel(callId) }
    fun endCall(callId: String) = action { app.callManager.end(callId) }
    fun setCallMuted(muted: Boolean) = app.callManager.setMuted(muted)
    fun setCallSpeakerphone(enabled: Boolean) = app.callManager.setSpeakerphone(enabled)

    fun sendPairRequest(receiverPeerId: String) = action {
        app.peerProtocolCoordinator.sendPairRequest(receiverPeerId)
    }

    fun acceptPairRequest(requestId: String) = action {
        app.peerProtocolCoordinator.acceptPairRequest(requestId)
    }

    fun declinePairRequest(requestId: String) = action {
        app.peerProtocolCoordinator.declinePairRequest(requestId)
    }

    fun cancelPairRequest(requestId: String) = action {
        pairingRepository.cancelPairRequest(requestId)
    }

    fun updateDisplayName(name: String) = action {
        settingsRepository.updateDisplayName(name)
    }

    fun updateLanguages(speak: VokieLanguage, understand: VokieLanguage) = action {
        settingsRepository.updateLanguages(speak, understand)
    }

    fun selectLanguage(code: String) = action {
        val lang = VokieLanguage.fromCode(code) ?: VokieLanguage.EN
        settingsRepository.updateLanguages(lang, lang)
        app.userLanguageProfilePreferences.select(UserLanguageProfile.same(lang))
    }

    fun markConversationAsRead(peerId: String) = action {
        val canonical = peerSessions.value.values.firstOrNull { it.bluetoothAddress.equals(peerId, ignoreCase = true) }?.peerId ?: peerId
        conversationRepository.markReadForPeer(canonical)
        app.peerSessionManager.markAsRead(canonical)
        app.notificationManager.cancelNotificationForPeer(canonical)
    }

    init {
        // A newly connected peer is immediately usable from Speak; users should not need
        // to visit People merely to enable PTT after a successful handshake.
        viewModelScope.launch {
            peerRepository.observePeers().collect { peersList ->
                if (_selectedPeerId.value == null) {
                    peersList.firstOrNull { it.connectionState == PeerConnectionState.ACTIVE }?.let { peer ->
                        selectPeer(peer.peerId)
                    }
                }
            }
        }
        VokieLog.msg("VOICE_OWNER_CREATED owner=$instanceId source=CommunicationViewModel")
        // Serialize PTT commands so a quick release cannot execute STOP before START.
        viewModelScope.launch {
            for (command in voiceCommands) {
                runCatching {
                    when (command) {
                        VoiceCommand.START -> {
                            // Settings is synchronously available; profile DataStore may still be
                            // loading on first launch. Never leave PTT disabled for that race.
                            val preferred = preferredLanguage.value
                                ?: UserLanguageProfile.same(settingsRepository.languagePreference.value.speakLanguage)
                            turnManager.start(if (pushToTalkEnabled.value) TurnMode.PUSH_TO_TALK else TurnMode.CONTINUOUS, resolveProductionSttLanguage(preferred), preferred)
                        }
                        VoiceCommand.STOP -> turnManager.stop()
                    }
                }.onFailure { _error.value = it.message ?: "Voice operation failed" }
            }
        }
        // C2 is the sole production owner of microphone/STT turns. Sentence events replace
        // ChatScreen's former raw SttStatus RESULT enqueue observer.
        viewModelScope.launch {
            turnManager.events.collect { event ->
                if (event is TurnEvent.Sentence) {
                    val claimed = turnManager.claimSubmission(event.turnId, event.index)
                    VokieLog.msg("TURN_SUBMIT_CLAIM turn_id=${event.turnId} sentence_index=${event.index} owner=$instanceId source=turn_event_sentence claimed=$claimed")
                    if (!claimed) return@collect
                    runCatching {
                        // A completed PTT/STT turn is a voice note with a text transcript,
                        // never a plain TEXT message. The Web UI renders VOICE_TEXT as a player.
                        val message = repository.createMessage(event.text, app.deviceId, effectivePeerId.value, event.language, MessageType.VOICE_TEXT)
                        app.turnTiming.associateMessage(event.turnId, message.id)
                        VokieLog.msg("TURN_SUBMIT turn_id=${event.turnId} message_id=${message.id} owner=$instanceId source=turn_event_sentence")
                    }.onFailure { _error.value = it.message ?: "Voice message could not be queued" }
                }
            }
        }
    }

    fun startListening() = action { manager.startBluetoothListener() }
    fun discover() = action { manager.discoverBluetooth() }
    fun stopDiscovery() = action {
        manager.stopDiscovery()
    }
    fun startDiscovery() = action {
        manager.startBluetoothListener()
        manager.discoverBluetooth()
        runCatching { manager.discoverWifiDirect() }
    }
    fun connect(peerId: String) = action { manager.connectBluetooth(peerId) }
    fun disconnect() = action { manager.disconnect() }
    fun discoverWifiDirect() = action { manager.discoverWifiDirect() }
    fun connectWifiDirect(address: String) = action { manager.connectWifiDirect(address) }
    fun disconnectWifiDirect() = action { manager.disconnectWifiDirect() }
    fun connectPeer(peerId: String) = action {
        val session = app.peerSessionManager.getSession(peerId)
        val btAddress = session?.bluetoothAddress
        // UUIDs identify an iTantra peer; they are not Wi-Fi P2P MAC addresses.
        // Wi-Fi-only connections are made only from the real WifiP2pDevice list.
        if (!btAddress.isNullOrBlank()) manager.connectBluetooth(btAddress)
        else if (peerId.matches(Regex("^([0-9A-Fa-f]{2}:){5}[0-9A-Fa-f]{2}$"))) manager.connectBluetooth(peerId)
        else throw IllegalStateException("Discover a real Bluetooth or Wi-Fi Direct device address first.")
    }

    fun send(text: String, language: VokieLanguage? = null, type: MessageType = MessageType.TEXT, onQueued: () -> Unit = {}) {
        if (text.isBlank()) { _error.value = "Enter a message before sending."; return }
        val resolvedLanguage = language ?: sttStatus.value.result?.language?.messageLanguage
            ?: settingsRepository.languagePreference.value.speakLanguage
            ?: if (type == MessageType.SOS) VokieLanguage.EN else VokieLanguage.HI
        if (resolvedLanguage == null) { _error.value = "Choose the language you speak before sending a message."; return }
        // Use the effective peer (explicit selection or transport-level connected peer).
        val targetPeer = effectivePeerId.value
        viewModelScope.launch {
            runCatching { repository.createMessage(text, app.deviceId, targetPeer, resolvedLanguage, type) }
                .onSuccess { _error.value = null; onQueued() }
                .onFailure { _error.value = it.message ?: "Message could not be queued" }
        }
    }

    fun selectPreferredLanguage(language: UserLanguageProfile) = action {
        app.userLanguageProfilePreferences.select(language)
    }
    fun enqueueWhisperResult(result: com.vokie.stt.SttResult) = viewModelScope.launch {
        runCatching { app.enqueueWhisperTranscript(result, effectivePeerId.value) }
            .onSuccess { _error.value = null }
            .onFailure { _error.value = it.message ?: "Voice message could not be queued" }
    }
    fun initializeStt() = action { speechToText.initialize() }
    fun resetOnboardingForDebug() {
        if (BuildConfig.DEBUG) action { app.userLanguageProfilePreferences.clear() }
    }
    fun setDebugFastStt(enabled: Boolean) {
        if (BuildConfig.DEBUG) _debugFastSttEnabled.value = enabled
    }
    fun startVoice() { voiceCommands.trySend(VoiceCommand.START) }
    fun stopVoice() { voiceCommands.trySend(VoiceCommand.STOP) }
    fun replayLastPcmBenchmark() = action {
        val preferred = preferredLanguage.value ?: throw IllegalStateException("Select a preferred language before replaying.")
        (app.sttEngine as? com.vokie.stt.WhisperSttEngine)?.replayLastCaptureForBenchmark(resolveProductionSttLanguage(preferred), preferred)
            ?: throw IllegalStateException("PCM replay is only available with the Whisper STT engine.")
    }
    fun setTtsSpeed(speed: Float) = action { textToSpeech.setSpeed(speed) }
    fun playbackSnapshot(): com.vokie.tts.PlaybackProgress? =
        (app.ttsEngine as? com.vokie.tts.SherpaOnnxTtsEngine)?.playbackSnapshot()
    fun cancelLanguageDownload() = action { app.modelDownloads.cancel() }
    fun downloadTtsLanguage(language: TtsLanguage) = action {
        app.modelDownloads.download(language)
        textToSpeech.refreshDownloadedLanguage(language)
    }
    fun playMessage(message: Message) = action { textToSpeech.play(message) }
    fun stopMessage(messageId: String) = action { textToSpeech.stop(messageId) }

    /** Play a voice note message via TTS. Called from WebView bridge. */
    fun playVoiceNote(messageId: String, text: String, languageCode: String) = action {
        val language = com.vokie.tts.TtsLanguage.fromMessageCode(languageCode) ?: com.vokie.tts.TtsLanguage.ENGLISH
        val msg = Message(
            id = messageId,
            senderId = "voice-note",
            timestamp = System.currentTimeMillis(),
            text = text,
            language = languageCode,
            messageType = MessageType.VOICE_TEXT,
        )
        textToSpeech.play(msg)
    }

    /** Stop a specific voice note playback. */
    fun stopVoiceNote(messageId: String) = action {
        textToSpeech.stop(messageId)
    }
    fun stopTts() = action { textToSpeech.stop() }
    fun acknowledgeSos(messageId: String) = action { textToSpeech.acknowledgeSos(messageId) }
    fun isIncoming(message: Message) = message.senderId != app.deviceId
    fun setPushToTalk(enabled: Boolean) = action { communicationPreferences.setPushToTalk(enabled) }
    fun setAlertsFullVolume(enabled: Boolean) = action { communicationPreferences.setAlertsFullVolume(enabled) }
    fun startTelemetryLocation() = app.locationProvider.start()
    fun stopTelemetryLocation() = app.locationProvider.stop()
    fun shareCurrentLocation() = viewModelScope.launch {
        _locationShareState.value = "SHARING"
        runCatching {
            val fix = app.locationProvider.location.value
            check(fix.availability == LocationAvailability.AVAILABLE) { "Your location unavailable." }
            val transmission = LocationPacket.encode(app.deviceId, fix)
            transmission.frames.forEach { frame ->
                val result = manager.sendPacket(frame, transmission.messageId, transmission.sequenceNumber, effectivePeerId.value, requireAck = true)
                check(result.acknowledged) { result.error ?: "Location could not be sent." }
            }
        }.onSuccess {
            _locationShareState.value = "SENT"
            _error.value = null
        }.onFailure {
            _locationShareState.value = "UNAVAILABLE"
            _error.value = it.message ?: "Location could not be sent."
        }
    }
    fun startTelemetryHeading() = app.headingProvider.start()
    fun stopTelemetryHeading() = app.headingProvider.stop()
    fun startTelemetryRssi() = action {
        manager.discoverBluetooth()
        peers.value.forEach { app.rssiTelemetry.recordDiscovery(it, System.currentTimeMillis()) }
    }
    fun stopTelemetryRssi() = Unit
    fun refreshTelemetryRssi() { app.rssiTelemetry.refresh(System.currentTimeMillis()) }
    fun retry(messageId: String) = action { repository.retry(messageId) }
    fun clearError() { _error.value = null }
    fun reportError(message: String) { _error.value = message }
    fun discoverabilityRequest() = manager.discoverabilityRequest()

    /**
     * DEBUG-ONLY: populate simulated peer sessions for UI testing.
     * These peers are clearly labelled SIMULATED and never mixed with real transport peers.
     */
    fun addSimulatedPeers() {
        if (!BuildConfig.DEBUG) return
        val sm = app.peerSessionManager
        data class SimPeer(val id: String, val name: String, val lang: String, val priority: Int, val connected: Boolean)
        val peers = listOf(
            SimPeer("SIM-CIV-001", "SIMULATED \u2014 CIV-001", "TA", 150, true),
            SimPeer("SIM-CIV-002", "SIMULATED \u2014 CIV-002", "HI", 10, true),
            SimPeer("SIM-CIV-003", "SIMULATED \u2014 CIV-003", "EN", 50, false),
        )
        peers.forEach { peer ->
            sm.registerPeer(peer.id, peer.name)
            // Inject a deterministic incoming message so session metadata is populated.
            sm.recordIncomingMessage(peer.id, Message(
                "sim-msg-${peer.id}", peer.id, System.currentTimeMillis(),
                "Test message from ${peer.name}", peer.lang, priority = peer.priority,
            ))
            if (peer.connected) {
                sm.updateConnectionState(peer.id, com.vokie.domain.model.TransportConnectionState.CONNECTED)
            }
        }
        VokieLog.rescue("DEBUG_SIMULATED_PEERS_ADDED count=${peers.size}")
    }

    /** DEBUG-ONLY: remove simulated peers and clean up selection. */
    fun removeSimulatedPeers() {
        if (!BuildConfig.DEBUG) return
        val simIds = listOf("SIM-CIV-001", "SIM-CIV-002", "SIM-CIV-003")
        simIds.forEach { app.peerSessionManager.removePeer(it) }
        // If the currently selected peer was simulated, clear the selection.
        if (_selectedPeerId.value in simIds) _selectedPeerId.value = null
        VokieLog.rescue("DEBUG_SIMULATED_PEERS_REMOVED")
    }

    override fun onCleared() {
        VokieLog.msg("VOICE_OWNER_CLEARED owner=$instanceId source=CommunicationViewModel")
        super.onCleared()
    }

    private fun action(block: suspend () -> Unit) { viewModelScope.launch { runCatching { block() }.onSuccess { _error.value = null }.onFailure { _error.value = it.message ?: "Communication failed" } } }
}
