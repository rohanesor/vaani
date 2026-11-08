package com.vokie.domain.model

import com.vokie.semantic.SemanticMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

enum class TransportType { BLUETOOTH, WIFI_DIRECT, ULTRASONIC }
enum class DeliveryState { QUEUED, TRANSMITTING, RETRYING, RECEIVED_BY_PEER, RELAYED, DELIVERED, FAILED }
// LOCATION is appended to preserve existing PacketV2 type ordinals for TEXT/SOS/CHECK_IN/SYSTEM.
enum class MessageType {
    TEXT,
    SOS,
    CHECK_IN,
    SYSTEM,
    LOCATION,
    VOICE_TEXT,
    EMERGENCY,
    CALL_SIGNAL,
    /** Fire-and-forget PCM call-media frame; appended to preserve prior wire ordinals. */
    CALL_AUDIO;

    companion object {
        fun fromWire(ordinal: Int): MessageType = entries.getOrElse(ordinal) { TEXT }
    }
}
enum class CommunicationStatus { ONLINE, OFFLINE, BLUETOOTH_READY, WIFI_READY, SEARCHING, CONNECTED, TRANSMITTING, RECEIVING, QUEUED, FAILED }
enum class VokieLanguage(val code: String, val displayName: String) {
    HI("HI", "Hindi"), GU("GU", "Gujarati"), MR("MR", "Marathi"), KN("KN", "Kannada"),
    ML("ML", "Malayalam"), TA("TA", "Tamil"), TE("TE", "Telugu"), OR("OR", "Odia"),
    BN("BN", "Bengali"), EN("EN", "English");
    companion object {
        fun fromCode(code: String): VokieLanguage? = entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
        fun isSupported(code: String) = fromCode(code) != null
    }
}

enum class MessageDeliveryStatus { PENDING, SENDING, DELIVERED, READ, FAILED }

enum class MessagePriority(val level: Int) {
    NORMAL(0),
    URGENT(1),
    EMERGENCY(2);

    companion object {
        fun fromLevel(level: Int): MessagePriority = when {
            level >= 2 -> EMERGENCY
            level == 1 -> URGENT
            else -> NORMAL
        }
    }
}

data class Message(
    val id: String,
    val senderId: String,
    val timestamp: Long,
    val text: String,
    val language: String = VokieLanguage.EN.code,
    val messageType: MessageType = MessageType.TEXT,
    val deliveryState: DeliveryState = DeliveryState.QUEUED,
    val transport: TransportType? = null,
    val hopCount: Int = 0,
    val receiverId: String? = null,
    val retryCount: Int = 0,
    val requiresAck: Boolean = true,
    val lastError: String? = null,
    val sequenceNumber: Long = 0,
    val ttlMs: Long = 5 * 60 * 1_000L,
    val priority: Int = 0,
    val checksum: Long = 0,
    val nextRetryAt: Long? = null,
    /** Optional N5 hint; text remains the authoritative communication content. */
    val semanticMetadata: SemanticMetadata? = null,
    /** Receiver-local translation, never serialized in PacketV2 or used as source text. */
    val translatedText: String? = null,
    /** Language of [translatedText], when it is available. */
    val translatedLanguage: String? = null,
    val conversationId: String? = null,
    val status: MessageDeliveryStatus = when (deliveryState) {
        DeliveryState.QUEUED, DeliveryState.RETRYING -> MessageDeliveryStatus.PENDING
        DeliveryState.TRANSMITTING -> MessageDeliveryStatus.SENDING
        DeliveryState.DELIVERED, DeliveryState.RECEIVED_BY_PEER -> MessageDeliveryStatus.DELIVERED
        DeliveryState.FAILED -> MessageDeliveryStatus.FAILED
        else -> MessageDeliveryStatus.PENDING
    },
) {
    val messageId: String get() = id
    val peerId: String get() = receiverId ?: senderId
    val senderPeerId: String get() = senderId
    val receiverPeerId: String? get() = receiverId
    val messagePriority: MessagePriority get() = MessagePriority.fromLevel(priority)
}

enum class PeerConnectionState {
    AVAILABLE,
    DISCOVERED,
    CONNECTING,
    ACTIVE,
    OFFLINE,
}

enum class PeerTransportType {
    NONE,
    WIFI,
    BLUETOOTH;

    companion object {
        fun fromTransportType(type: TransportType?): PeerTransportType = when (type) {
            TransportType.BLUETOOTH -> BLUETOOTH
            TransportType.WIFI_DIRECT -> WIFI
            else -> NONE
        }
    }
}

data class Peer(
    val id: String,
    val name: String,
    val address: String = id,
    val bonded: Boolean = false,
    val rssi: Int? = null,
    val connectionState: PeerConnectionState = PeerConnectionState.DISCOVERED,
    val transport: PeerTransportType = PeerTransportType.NONE,
    val lastSeen: Long? = null,
    val inputLanguage: String? = null,
    val outputLanguage: String? = null,
    val capabilities: Set<String> = emptySet(),
) {
    val peerId: String get() = id
    val displayName: String get() = name
    val initial: String get() = (name.firstOrNull()?.uppercase() ?: "?")
}

enum class TrustedPeerStatus { ACTIVE, BLOCKED, REMOVED }

data class TrustedPeer(
    val peerId: String,
    val displayName: String,
    val createdAt: Long,
    val acceptedAt: Long? = null,
    val lastConnectedAt: Long? = null,
    val status: TrustedPeerStatus = TrustedPeerStatus.ACTIVE,
)

enum class PairRequestStatus { PENDING, ACCEPTED, DECLINED, EXPIRED, CANCELLED }

data class PairRequest(
    val requestId: String,
    val senderPeerId: String,
    val receiverPeerId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: PairRequestStatus = PairRequestStatus.PENDING,
)

data class Conversation(
    val conversationId: String,
    val peerId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val unreadCount: Int = 0,
)

data class MessageDelivery(
    val messageId: String,
    val peerId: String,
    val transport: PeerTransportType,
    val status: MessageDeliveryStatus,
    val sentAt: Long,
    val deliveredAt: Long? = null,
    val acknowledgedAt: Long? = null,
    val failureReason: String? = null,
)

enum class ConnectionSessionState { CONNECTING, ACTIVE, DISCONNECTED, FAILED }

data class ConnectionSession(
    val sessionId: String,
    val peerId: String,
    val transport: PeerTransportType,
    val state: ConnectionSessionState,
    val startedAt: Long,
    val endedAt: Long? = null,
    val lastActivityAt: Long,
)

data class LanguagePreference(
    val speakLanguage: VokieLanguage = VokieLanguage.HI,
    val understandLanguage: VokieLanguage = VokieLanguage.EN,
)

enum class VoiceMessageState {
    IDLE,
    LISTENING,
    PROCESSING_STT,
    SENDING,
    DELIVERED,
    PLAYING_TTS,
    FAILED,
}

enum class CallState {
    IDLE,
    CALLING,
    RINGING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    REJECTED,
    MISSED,
    FAILED,
    ENDED,
    UNAVAILABLE,
}

enum class CallDirection { OUTGOING, INCOMING }

data class CallSession(
    val sessionId: String,
    val peerId: String,
    val state: CallState = CallState.IDLE,
    val direction: CallDirection = CallDirection.OUTGOING,
    val transport: PeerTransportType = PeerTransportType.NONE,
    val startedAt: Long = 0,
    val endedAt: Long? = null,
    val failureReason: String? = null,
)

data class UserIdentity(
    val deviceId: String,
    val displayName: String,
    val avatarInitial: String,
)

data class SpeakUiState(
    val selectedPeer: Peer? = null,
    val connectionState: PeerConnectionState = PeerConnectionState.AVAILABLE,
    val transport: PeerTransportType = PeerTransportType.NONE,
    val pttState: VoiceMessageState = VoiceMessageState.IDLE,
    val recognizedText: String? = null,
    val outgoingMessage: Message? = null,
    val deliveryState: MessageDeliveryStatus? = null,
    val remoteSpeaking: Boolean = false,
    val error: String? = null,
)

data class MessagesUiState(
    val conversations: List<Conversation> = emptyList(),
    val unreadCount: Int = 0,
    val activePeers: List<Peer> = emptyList(),
    val lastMessages: Map<String, Message> = emptyMap(),
)

data class ChatUiState(
    val peer: Peer? = null,
    val connectionState: PeerConnectionState = PeerConnectionState.OFFLINE,
    val messages: List<Message> = emptyList(),
    val inputText: String = "",
    val sendingState: MessageDeliveryStatus? = null,
    val voiceState: VoiceMessageState = VoiceMessageState.IDLE,
)

data class PeersUiState(
    val discoveredPeers: List<Peer> = emptyList(),
    val trustedPeers: List<TrustedPeer> = emptyList(),
    val pendingRequests: List<PairRequest> = emptyList(),
    val activeSessions: List<ConnectionSession> = emptyList(),
)

data class PairingUiState(
    val selectedPeer: Peer? = null,
    val requestState: PairRequestStatus? = null,
    val error: String? = null,
)

data class SettingsUiState(
    val identity: UserIdentity,
    val speakLanguage: VokieLanguage = VokieLanguage.HI,
    val understandLanguage: VokieLanguage = VokieLanguage.EN,
    val wifiState: Boolean = true,
    val bluetoothState: Boolean = false,
)

data class CallUiState(
    val peer: Peer? = null,
    val callState: CallState = CallState.IDLE,
    val direction: CallDirection? = null,
    val callId: String? = null,
    val durationSeconds: Long = 0,
    val failureReason: String? = null,
)

enum class TransportConnectionState { UNAVAILABLE, PERMISSION_REQUIRED, BLUETOOTH_DISABLED, IDLE, SEARCHING, CONNECTING, CONNECTED, DISCONNECTED, FAILED }
data class SendResult(val messageId: String, val acknowledged: Boolean, val ackLatencyMs: Long? = null, val error: String? = null)

interface Transport {
    val type: TransportType
    val peers: StateFlow<List<Peer>>
    val connectionState: StateFlow<TransportConnectionState>
    val connectedPeerId: StateFlow<String?>
    suspend fun startListening()
    suspend fun discoverPeers()
    suspend fun stopDiscovery()
    suspend fun connect(peerId: String)
    suspend fun disconnect()
    suspend fun send(message: Message): SendResult
    suspend fun acknowledge(messageId: String)
    fun observeMessages(): Flow<Message>
}

data class Contact(val id: String, val name: String, val phone: String = "", val isEmergency: Boolean = false)
data class EmergencyAlert(val id: String, val severity: String, val title: String, val body: String, val timestamp: Long, val source: String, val status: String)
data class OfflineResource(val id: String, val category: String, val title: String, val content: String, val downloaded: Boolean = true)
data class MapRegion(val id: String, val name: String, val downloaded: Boolean, val updatedAt: Long = 0)
data class Device(val id: String, val name: String, val lastSeen: Long, val transport: TransportType)
data class AppSettings(val language: String = "EN", val walkieTalkieMode: Boolean = true, val darkMode: Boolean = true, val haptics: Boolean = true)
