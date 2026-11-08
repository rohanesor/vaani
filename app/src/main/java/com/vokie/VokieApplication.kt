package com.vokie

import android.app.Application
import com.vokie.communication.*
import com.vokie.ai.*
import com.vokie.data.*
import com.vokie.data.local.*
import com.vokie.domain.model.MessageType
import com.vokie.domain.model.TransportType
import com.vokie.domain.model.TransportConnectionState
import com.vokie.map.MapPackManager
import com.vokie.models.BundledModelStore
import com.vokie.models.ModelDownloadManager
import com.vokie.location.AndroidHeadingProvider
import com.vokie.location.AndroidLocationProvider
import com.vokie.location.EmergencyGuidanceCoordinator
import com.vokie.location.LocationMeasurementCollector
import com.vokie.location.LocationPacket
import com.vokie.location.LocationAvailability
import com.vokie.location.LocationMetadata
import com.vokie.proximity.BluetoothRssiTelemetryProvider
import com.vokie.ranging.RelativePeerLocalizationEngine
import com.vokie.map.MapPreferences
import com.vokie.map.OfflineMapUseCase
import com.vokie.stt.SpeechToTextUseCase
import com.vokie.stt.ContinuousTurnManager
import com.vokie.stt.TurnTimingRecorder
import com.vokie.stt.SttLanguagePreferences
import com.vokie.stt.UserLanguageProfilePreferences
import com.vokie.stt.SttState
import com.vokie.stt.SttResult
import com.vokie.stt.SenseVoiceSttEngine
import com.vokie.stt.SttEngine
import com.vokie.stt.WhisperSttEngine
import com.vokie.tts.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import com.vokie.translation.ReceiverTranslationCoordinator
import com.vokie.translation.Ctranslate2TranslationEngine
import com.vokie.notification.AppNotificationManager
import com.vokie.semantic.SemanticUrgency
import java.util.UUID

class VokieApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lateinit var database: VokieDatabase; private set
    lateinit var cloudCredentials: com.vokie.cloud.SecureCredentialStore; private set
    lateinit var cloudSyncPreferences: com.vokie.cloud.CloudSyncPreferences; private set
    lateinit var cloudAuthClient: com.vokie.cloud.AuthClient; private set
    lateinit var cloudSyncRepository: com.vokie.cloud.CloudSyncRepository; private set
    lateinit var cloudSyncCoordinator: com.vokie.cloud.SyncCoordinator; private set
    lateinit var messageRepository: RoomMessageRepository; private set
    lateinit var peerRepository: PeerRepository; private set
    lateinit var pairingRepository: PairingRepository; private set
    lateinit var conversationRepository: ConversationRepository; private set
    lateinit var settingsRepository: SettingsRepository; private set
    lateinit var bluetoothTransport: BluetoothTransport; private set
    lateinit var wifiDirectTransport: WifiDirectTransport; private set
    lateinit var transportManager: TransportManager; private set
    lateinit var outboundProcessor: OutboundMessageProcessor; private set
    lateinit var sttEngine: SttEngine; private set
    lateinit var sttLanguagePreferences: SttLanguagePreferences; private set
    lateinit var userLanguageProfilePreferences: UserLanguageProfilePreferences; private set
    lateinit var speechToText: SpeechToTextUseCase; private set
    lateinit var continuousTurnManager: ContinuousTurnManager; private set
    lateinit var turnTiming: TurnTimingRecorder; private set
    lateinit var ttsEngine: TtsEngine; private set
    lateinit var modelDownloads: ModelDownloadManager; private set
    lateinit var textToSpeech: TextToSpeechUseCase; private set
    private lateinit var receiverTranslation: ReceiverTranslationCoordinator
    val receiverPresentations get() = receiverTranslation.presentations
    lateinit var offlineMap: OfflineMapUseCase; private set
    lateinit var locationProvider: AndroidLocationProvider; private set
    lateinit var headingProvider: AndroidHeadingProvider; private set
    lateinit var rssiTelemetry: BluetoothRssiTelemetryProvider; private set
    lateinit var emergencyGuidance: EmergencyGuidanceCoordinator; private set
    lateinit var proximityMeasurements: LocationMeasurementCollector; private set
    lateinit var localizationEngine: RelativePeerLocalizationEngine; private set
    val remoteLocation = MutableStateFlow(LocationMetadata(availability = LocationAvailability.UNAVAILABLE))
    private var remoteLocationSenderId: String? = null
    lateinit var communicationPreferences: CommunicationPreferences; private set
    lateinit var peerSessionManager: PeerSessionManager; private set
    lateinit var peerProtocolCoordinator: PeerProtocolCoordinator; private set
    lateinit var callManager: CallManager; private set
    lateinit var notificationManager: AppNotificationManager; private set
    lateinit var aiProvider: AIProvider; private set
    lateinit var deviceId: String; private set

    override fun onCreate() {
        super.onCreate()
        deviceId = getSharedPreferences("vokie_identity", MODE_PRIVATE).let { preferences ->
            preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also { preferences.edit().putString("device_id", it).apply() }
        }
        database = VokieDatabase.get(this)
        cloudCredentials = com.vokie.cloud.SecureCredentialStore(this)
        cloudSyncPreferences = com.vokie.cloud.CloudSyncPreferences(this)
        cloudAuthClient = com.vokie.cloud.AuthClient(BuildConfig.AI_BACKEND_BASE_URL)
        cloudSyncRepository = com.vokie.cloud.CloudSyncRepository(
            cloudUserDao = database.cloudUser(),
            cloudIdMappingDao = database.cloudIdMapping(),
            cloudConversationDao = database.cloudConversations(),
            cloudMemberDao = database.cloudMembers(),
            cloudMessageDao = database.cloudMessages(),
            syncQueueDao = database.syncQueue(),
            localMessageDao = database.messages(),
            apiClient = com.vokie.cloud.SyncApiClient(BuildConfig.AI_BACKEND_BASE_URL) { cloudCredentials.token() },
            credentials = cloudCredentials,
            syncPreferences = cloudSyncPreferences,
        )
        cloudSyncCoordinator = com.vokie.cloud.SyncCoordinator(this, cloudSyncRepository)
        applicationScope.launch(Dispatchers.IO) {
            cloudSyncRepository.migrateLegacyCredential()
        }
        aiProvider = if (BuildConfig.AI_BACKEND_BASE_URL.isBlank()) UnconfiguredAIProvider() else VaaniBackendProvider(BuildConfig.AI_BACKEND_BASE_URL, AiSessionTokenProvider { null })
        peerSessionManager = PeerSessionManager(database.peers())
        peerRepository = RoomPeerRepository(database.peers(), database.trustedPeers(), peerSessionManager)
        pairingRepository = RoomPairingRepository(database.pairRequests(), peerRepository)
        conversationRepository = RoomConversationRepository(database.conversations())
        messageRepository = RoomMessageRepository(
            database.messages(),
            database.messageDeliveries(),
            database.conversations(),
            onLocalMessageCreated = { message ->
                cloudSyncRepository.queueForSync(message.id, message.conversationId)
                cloudSyncCoordinator.scheduleSyncOnce()
            },
        )
        settingsRepository = SharedPreferencesSettingsRepository(applicationContext)
        notificationManager = AppNotificationManager(applicationContext)
        bluetoothTransport = BluetoothTransport(applicationContext, applicationScope)
        // Reconnect by the current bonded hardware address when a persisted UUID session
        // contains an address from an earlier handset identity. The name match is only a
        // bounded fallback; the HELLO handshake remains authoritative for ACTIVE state.
        bluetoothTransport.reconnectAddressResolver = { staleAddress ->
            runCatching {
                val persisted = database.peers().getAll().firstOrNull {
                    it.deviceAddress.equals(staleAddress, ignoreCase = true)
                } ?: return@runCatching null
                val adapter = applicationContext.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
                adapter?.bondedDevices?.firstOrNull { device ->
                    device.name?.equals(persisted.deviceName, ignoreCase = true) == true
                }?.address
            }.getOrNull()
        }
        wifiDirectTransport = WifiDirectTransport(applicationContext, applicationScope)
        proximityMeasurements = LocationMeasurementCollector()
        localizationEngine = RelativePeerLocalizationEngine(applicationContext)
        turnTiming = TurnTimingRecorder()
        transportManager = TransportManager(bluetoothTransport, wifiDirectTransport, proximityMeasurements, localizationEngine, applicationScope, turnTiming, peerSessionManager)
        callManager = CallManager(applicationContext, deviceId, transportManager, applicationScope)
        peerProtocolCoordinator = PeerProtocolCoordinator(
            localDeviceId = deviceId,
            localDisplayNameProvider = { settingsRepository.identity.value.displayName.ifBlank { "iTantra User" } },
            transportManager = transportManager,
            peerSessionManager = peerSessionManager,
            peerRepository = peerRepository,
            pairingRepository = pairingRepository,
            scope = applicationScope,
            callSignalHandler = { json, transport -> callManager.handleSignal(json, transport) },
            // Exchanged in HELLO/HELLO_ACK so a Bluetooth session can upgrade to Wi-Fi Direct.
            wifiP2pAddressProvider = { wifiDirectTransport.localDeviceAddress }
        )
        // Wire transport disconnect → re-queue TRANSMITTING messages + update peer sessions.
        transportManager.disconnectListener = { transport, peerId ->
            applicationScope.launch(Dispatchers.IO) {
                // Resolve UUID from MAC if needed for full state sync
                val resolvedUuid = peerId?.let { peerSessionManager.findPeerByBluetoothAddress(it) ?: it }
                val recovered = resolvedUuid?.takeIf { it.isNotBlank() }
                    ?.let { messageRepository.recoverInterruptedForPeer(it) }
                    ?: run { database.messages().recoverInterrupted(); 0 }
                VokieLog.rescue("MESSAGE_REQUEUED transport=${transport.type} peer=${peerId ?: "unknown"} uuid=${resolvedUuid ?: "unknown"} count=$recovered")
                callManager.onTransportDisconnected()
                // Mark both MAC and UUID sessions disconnected immediately
                if (!resolvedUuid.isNullOrBlank()) {
                    peerSessionManager.markDisconnected(resolvedUuid)
                    peerSessionManager.persistSession(resolvedUuid)
                }
                if (!peerId.isNullOrBlank() && peerId != resolvedUuid) {
                    peerSessionManager.markDisconnected(peerId)
                }
                // Radio-off callbacks may not retain a remote address. A persisted/trusted
                // entry is never proof of a live link, so clear all active sessions.
                if (peerId.isNullOrBlank()) {
                    peerSessionManager.getSessions()
                        .filter { it.connectionState in setOf(TransportConnectionState.CONNECTED, TransportConnectionState.CONNECTING) }
                        .forEach { peerSessionManager.markDisconnected(it.peerId) }
                }
            }
        }
        outboundProcessor = OutboundMessageProcessor(messageRepository, transportManager, database.transportEvents(), applicationScope, peerSessionManager)
        val inboundPackets = InboundPacketCoordinator(messageRepository, database.receivedPackets())
        val senseVoice = SenseVoiceSttEngine(applicationContext)
        val whisper = WhisperSttEngine(applicationContext)
        val hybridStt = object : SttEngine {
            val active: SttEngine get() = if (senseVoice.isAvailable) senseVoice else whisper
            override val status: StateFlow<com.vokie.stt.SttStatus> = kotlinx.coroutines.flow.combine(senseVoice.status, whisper.status) { s, w ->
                if (senseVoice.isAvailable) s else w
            }.stateIn(applicationScope, SharingStarted.Eagerly, if (senseVoice.isAvailable) senseVoice.status.value else whisper.status.value)

            override suspend fun initialize() {
                if (senseVoice.isAvailable) senseVoice.initialize()
                whisper.initialize()
            }

            override suspend fun start(language: com.vokie.stt.SttLanguage, preferredLanguage: com.vokie.stt.UserLanguageProfile, finalizeOnVad: Boolean) {
                VokieLog.stt("STT_START active=${if (senseVoice.isAvailable) "sensevoice" else "whisper"}")
                active.start(language, preferredLanguage, finalizeOnVad)
            }

            override suspend fun stop() = active.stop()

            override suspend fun transcribe(audio: FloatArray, language: com.vokie.stt.SttLanguage, audioDurationMs: Long): SttResult =
                active.transcribe(audio, language, audioDurationMs)

            override fun setInferenceStartListener(listener: ((turnId: String?) -> Unit)?) {
                senseVoice.setInferenceStartListener(listener)
                whisper.setInferenceStartListener(listener)
            }

            override fun setInferenceTurnId(turnId: String?) {
                senseVoice.setInferenceTurnId(turnId)
                whisper.setInferenceTurnId(turnId)
            }

            override fun release() {
                senseVoice.release()
                whisper.release()
            }
        }
        sttEngine = hybridStt
        sttLanguagePreferences = SttLanguagePreferences(applicationContext)
        userLanguageProfilePreferences = UserLanguageProfilePreferences(applicationContext)
        speechToText = SpeechToTextUseCase(sttEngine, sttLanguagePreferences)
        continuousTurnManager = ContinuousTurnManager(sttEngine, applicationScope, timing = turnTiming)
        val bundledModels = BundledModelStore(applicationContext)
        val ttsModels = TtsModelManager(applicationContext)
        modelDownloads = ModelDownloadManager(applicationContext, bundledModels)
        val ttsPreferences = TtsPreferences(applicationContext)
        val ttsSpeed = ttsPreferences.speed.stateIn(applicationScope, SharingStarted.Eagerly, DEFAULT_TTS_SPEED)
        // Approved local MMS/VITS assets are model-gated by TtsModelManager; unavailable
        // languages still fail explicitly instead of falling back to cloud/system TTS.
        ttsEngine = SherpaOnnxTtsEngine(ttsModels, VokieAudioPlayer(applicationContext))
        // CT2 only loads an approved app-private staged model; it has no download/fallback path.
        receiverTranslation = ReceiverTranslationCoordinator(Ctranslate2TranslationEngine(applicationContext))
        val ttsQueue = TtsPlaybackQueue(ttsEngine, ttsSpeed, applicationScope, turnTiming)
        textToSpeech = TextToSpeechUseCase(ttsEngine, ttsModels, ttsPreferences, ttsQueue).also { it.start() }
        applicationScope.launch {
            // This is a local, atomic APK-asset extraction; it never performs network I/O.
            runCatching { bundledModels.prepare() }
                .onFailure { VokieLog.stt("Bundled model preparation failed: ${it.message}") }
                .onSuccess {
                    ttsModels.refresh()
                    speechToText.initialize()
                    // TTS initialization is intentionally skipped until an approved bundled artifact exists.
                }
        }
        communicationPreferences = CommunicationPreferences(applicationContext)
        // Telemetry adapters are constructed but intentionally not started until a future active guidance feature.
        locationProvider = AndroidLocationProvider(applicationContext)
        headingProvider = AndroidHeadingProvider(applicationContext)
        rssiTelemetry = BluetoothRssiTelemetryProvider()
        emergencyGuidance = EmergencyGuidanceCoordinator()
        val mapManager = MapPackManager(applicationContext)
        val mapPreferences = MapPreferences(applicationContext)
        offlineMap = OfflineMapUseCase(applicationContext, mapManager, mapPreferences)
        applicationScope.launch { offlineMap.refresh() }
        applicationScope.launch {
            while (isActive) {
                delay(5_000L)
                localizationEngine.refresh()
            }
        }

        applicationScope.launch {
            transportManager.decodedFrames.collect { frame ->
                if (frame is com.vokie.communication.TransportManager.DecodedTransportFrame.Ack) {
                    // PacketV2 ACK carries the acknowledgement sender identity. Never
                    // associate it with whichever conversation is selected in the UI.
                    val peer = frame.peerId
                    if (peer.isNotBlank()) {
                        localizationEngine.recordAck(peer, frame.transport.type, null)
                        peerSessionManager.recordAck(peer, frame.messageId, frame.sequenceNumber)
                    }
                }
                if (frame is com.vokie.communication.TransportManager.DecodedTransportFrame.Message) {
                    runCatching {
                        val packet = frame.packet
                        val remotePeerId = packet.packet.sourceDeviceId
                        if (packet.messageType == com.vokie.domain.model.MessageType.SYSTEM) {
                            transportManager.sendAck(frame.transport, packet.packet.messageId, deviceId, packet.packet.sequenceNumber)
                            peerProtocolCoordinator.handleSystemFrame(packet, frame.transport)
                        } else if (packet.messageType == com.vokie.domain.model.MessageType.CALL_AUDIO) {
                            // Real-time call media is fire-and-forget and must never be ACKed,
                            // replay-stored, translated, or rendered as a chat message.
                            callManager.handleAudio(packet)
                        } else {
                            turnTiming.packetReceived(packet.packet.messageId, packet.packet.sequenceNumber)
                            inboundPackets.acceptDecoded(packet, frame.transport) { transport, messageId, sequenceNumber ->
                                transportManager.sendAck(transport, messageId, deviceId, sequenceNumber)
                            }
                            proximityMeasurements.record(remotePeerId, com.vokie.location.MeasurementTrigger.MESSAGE_RECEIVED, transportManager.connectionState.value, wifiDirectTransport.state.value, frame.transport.type, delivered = true)
                            localizationEngine.recordMessage(remotePeerId, frame.transport.type)
                        }
                    }.onFailure { VokieLog.msg("Incoming packet rejected: ${it.message}") }
                }
            }
        }
        applicationScope.launch {
            inboundPackets.messages.collect { message ->
                // Register the remote peer and record the inbound message against its session.
                val remotePeer = message.senderId
                if (remotePeer.isNotBlank() && remotePeer != deviceId) {
                    peerSessionManager.registerPeer(remotePeer)
                    peerSessionManager.recordIncomingMessage(remotePeer, message)
                    // Associate the Bluetooth MAC address with this peer's UUID so that outbound
                    // routing can resolve the destination even when the message was queued by UUID
                    // but the transport is connected by MAC address.
                    transportManager.connectedPeerId.value?.let { btAddress ->
                        peerSessionManager.associateBluetoothAddress(remotePeer, btAddress)
                    }
                    peerSessionManager.persistSession(remotePeer)
                    val senderName = peerRepository.getPeer(remotePeer)?.displayName?.takeIf { it.isNotBlank() } ?: "iTantra peer"
                    val urgent = message.semanticMetadata?.urgency in setOf(SemanticUrgency.URGENT, SemanticUrgency.CRITICAL) || message.priority >= 2
                    notificationManager.showIncomingMessageNotification(
                        senderId = remotePeer,
                        senderName = senderName,
                        text = message.text,
                        isVoice = message.messageType == com.vokie.domain.model.MessageType.VOICE_TEXT,
                        isUrgent = urgent,
                    )
                }
                if (message.messageType == com.vokie.domain.model.MessageType.LOCATION) {
                    LocationPacket.decode(message)?.let { location ->
                        val priorSender = remoteLocationSenderId
                        val prior = remoteLocation.value
                        if (priorSender != message.senderId || location.locationSequence > prior.locationSequence) {
                            remoteLocationSenderId = message.senderId
                            remoteLocation.value = location
                            VokieLog.bt("LOCATION_RECEIVED id=${message.id} sequence=${location.locationSequence}")
                        } else {
                            VokieLog.bt("LOCATION_REJECTED_OLD id=${message.id} sequence=${location.locationSequence}")
                        }
                    }
                    return@collect
                }
                runCatching {
                    val target = userLanguageProfilePreferences.profile.first()?.preferredOutputLanguage
                        ?: com.vokie.domain.model.VokieLanguage.fromCode(message.language)
                        ?: com.vokie.domain.model.VokieLanguage.EN
                    val source = com.vokie.domain.model.VokieLanguage.fromCode(message.language) ?: target
                    VokieLog.translation("TRANSLATION_RECEIVER_REQUEST messageId=${message.id} source=${source.code} target=${target.code}")
                    turnTiming.translationStart(message.id, source.code, target.code)
                    val outcome = receiverTranslation.presentOnce(message.id, message.text, source, target)
                    turnTiming.translationComplete(message.id)
                    outcome.presentation.displayText
                        ?.takeIf { outcome.presentation.state == com.vokie.translation.ReceiverPresentationState.TRANSLATED && it.isNotBlank() }
                        ?.let { translated ->
                            // Persist the receiver-local presentation so reopening the app does not
                            // expose an English-only recipient to the original Hindi transcript.
                            messageRepository.saveReceiverTranslation(message.id, translated, target)
                        }
                    VokieLog.translation("TRANSLATION_RECEIVER_RESULT messageId=${message.id} state=${outcome.presentation.state} new=${outcome.isNew} error=${outcome.presentation.error}")
                    // TTS is strictly for PTT voice messages, NOT typed text messages.
                    if (message.messageType == com.vokie.domain.model.MessageType.VOICE_TEXT) {
                        val handoff = outcome.presentation.ttsHandoff()
                        val textToPlay = handoff?.text ?: message.text
                        val langToPlay = handoff?.language ?: source
                        if (textToPlay.isNotBlank()) {
                            VokieLog.translation("TTS_HANDOFF messageId=${message.id} target=${langToPlay.code} chars=${textToPlay.length} state=${outcome.presentation.state}")
                            textToSpeech.enqueueReceived(message.id, textToPlay, langToPlay, message.messageType)
                        }
                    } else {
                        VokieLog.translation("TTS_SKIPPED_FOR_TYPED_TEXT messageId=${message.id} type=${message.messageType}")
                    }
                }.onFailure {
                    turnTiming.fail(null, message.id, com.vokie.stt.TurnTimingFailure.TRANSLATION)
                    VokieLog.tts("Incoming message could not be queued for speech: ${it.message}")
                }
            }
        }
        applicationScope.launch {
            combine(locationProvider.location, headingProvider.heading, rssiTelemetry.state, remoteLocation) { receiver, heading, rssi, sender ->
                emergencyGuidance.update(sender, receiver, heading, rssi.filtered, System.currentTimeMillis())
            }.collect { }
        }
        applicationScope.launch {
            bluetoothTransport.peers.collect { peers ->
                peers.forEach { peer ->
                    localizationEngine.startPeer(peer.id, TransportType.BLUETOOTH)
                    peer.rssi?.let { localizationEngine.recordRssi(peer.id, it) }
                    peerSessionManager.registerPeer(peer.id, peer.name, TransportType.BLUETOOTH)
                }
            }
        }
        applicationScope.launch {
            wifiDirectTransport.peers.collect { peers ->
                peers.filter { it.available }.forEach { peer ->
                    localizationEngine.startPeer(peer.address, TransportType.WIFI_DIRECT)
                    localizationEngine.recordWifi(peer.address)
                    peerSessionManager.registerPeer(peer.address, peer.name, TransportType.WIFI_DIRECT)
                }
            }
        }
        // Wi-Fi Direct connection lifecycle -> trigger handshake
        applicationScope.launch {
            wifiDirectTransport.state.collect { state ->
                if (state == com.vokie.communication.PacketTransportState.CONNECTED) {
                    peerProtocolCoordinator.onTransportConnected(wifiDirectTransport)
                }
            }
        }
        // Propagate Bluetooth connection state changes to PeerSessionManager & trigger handshake
        applicationScope.launch {
            combine(bluetoothTransport.connectionState, bluetoothTransport.connectedPeerId) { state, peerId -> state to peerId }.collect { (state, peerId) ->
                if (!peerId.isNullOrBlank()) {
                    peerSessionManager.updateConnectionState(peerId, state)
                    peerSessionManager.persistSession(peerId)
                    if (state == com.vokie.domain.model.TransportConnectionState.CONNECTED) {
                        transportManager.activePacketTransport()?.let { active ->
                            peerProtocolCoordinator.onTransportConnected(active, peerId)
                        }
                    }
                }
            }
        }
        applicationScope.launch(Dispatchers.IO) {
            combine(bluetoothTransport.peers, bluetoothTransport.connectionState) { peers, state -> peers to state }
                .collectLatest { (peers, state) ->
                    peers.forEach { peer ->
                        database.peers().upsert(PeerEntity(peer.id, peer.address, peer.name, VokieProtocol.VERSION, System.currentTimeMillis(), if (bluetoothTransport.connectedPeerId.value == peer.id) "CONNECTED" else state.name, TransportType.BLUETOOTH.name, peer.bonded))
                    }
                }
        }
        applicationScope.launch(Dispatchers.IO) {
            // Restore peer sessions from Room before recovering messages.
            peerSessionManager.restoreFromPersistence()
            database.messages().recoverInterrupted()
            outboundProcessor.start()
        }
    }

    /** Explicit bridge for a completed local STT result; receiver target remains receiver-local. */
    suspend fun enqueueWhisperTranscript(result: SttResult, receiverId: String? = null) =
        messageRepository.createMessage(result.text, deviceId, receiverId, result.language.messageLanguage, MessageType.VOICE_TEXT)

    override fun onLowMemory() {
        releaseIdleStt()
        super.onLowMemory()
    }

    override fun onTrimMemory(level: Int) {
        if (level >= TRIM_MEMORY_COMPLETE) releaseIdleStt()
        super.onTrimMemory(level)
    }

    private fun releaseIdleStt() {
        if (sttEngine.status.value.state in setOf(SttState.READY, SttState.RESULT, SttState.ERROR)) sttEngine.release()
        if (ttsEngine.status.value.state in setOf(TtsState.READY, TtsState.COMPLETED, TtsState.ERROR, TtsState.MODEL_MISSING, TtsState.MODEL_LOAD_FAILED)) ttsEngine.release()
    }
}
