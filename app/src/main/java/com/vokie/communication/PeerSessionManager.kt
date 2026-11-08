package com.vokie.communication

import com.vokie.data.local.PeerDao
import com.vokie.data.local.PeerEntity
import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory, peer-scoped session state. Transport ownership and wire protocol remain
 * in their existing components; this class only records peer-facing lifecycle events.
 */
data class PeerSessionState(
    val peerId: String,
    val displayName: String? = null,
    val connectionState: TransportConnectionState = TransportConnectionState.IDLE,
    val transport: TransportType? = null,
    val lastSeen: Long? = null,
    val lastMessageTimestamp: Long? = null,
    val lastMessagePreview: String? = null,
    val pendingMessageCount: Int = 0,
    val unacknowledgedMessageCount: Int = 0,
    val unreadCount: Int = 0,
    val sourceLanguage: String? = null,
    val targetLanguage: String? = null,
    val priority: Int = 0,
    val sessionStatus: SessionStatus = SessionStatus.ACTIVE,
    /** Bluetooth MAC address if known (distinct from app-level peerId UUID). */
    val bluetoothAddress: String? = null,
)

enum class SessionStatus { ACTIVE, ARCHIVED, BLOCKED }

/**
 * Session bookkeeping is deliberately explicit: events for an unregistered peer are
 * ignored rather than silently creating state or being assigned to another peer.
 */
class PeerSessionManager(
    private val peerDao: PeerDao? = null,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private data class MessageKey(val messageId: String, val sequenceNumber: Long)
    private data class PeerPending(
        val messageIds: MutableSet<String> = mutableSetOf(),
        val acknowledgements: MutableSet<MessageKey> = mutableSetOf(),
    )

    private val _sessions = MutableStateFlow<Map<String, PeerSessionState>>(emptyMap())
    val sessions: StateFlow<Map<String, PeerSessionState>> = _sessions.asStateFlow()
    private val pending = mutableMapOf<String, PeerPending>()

    @Synchronized
    fun registerPeer(peerId: String, displayName: String? = null, transport: TransportType? = null): PeerSessionState? {
        if (peerId.isBlank()) return null
        val existing = _sessions.value[peerId]
        val state = existing?.copy(
            displayName = displayName ?: existing.displayName,
            transport = transport ?: existing.transport,
        ) ?: PeerSessionState(peerId, displayName, transport = transport)
        _sessions.value = _sessions.value + (peerId to state)
        pending.getOrPut(peerId) { PeerPending() }
        if (existing == null) VokieLog.rescue("SESSION_CREATED peer=$peerId")
        return state
    }

    /**
     * Restore peer sessions from Room after process death.
     * Connection state is always set to IDLE — a peer that was CONNECTED before
     * process death cannot genuinely still be connected.
     */
    suspend fun restoreFromPersistence() {
        val dao = peerDao ?: return
        val entities = dao.getAll()
        if (entities.isEmpty()) return
        synchronized(this) {
            entities.forEach { entity ->
                val transport = runCatching { TransportType.valueOf(entity.transport) }.getOrNull()
                // deviceAddress contains the Bluetooth MAC if different from the UUID id
                val btAddress = entity.deviceAddress.takeIf { it != entity.id }
                val state = PeerSessionState(
                    peerId = entity.id,
                    displayName = entity.deviceName,
                    connectionState = TransportConnectionState.IDLE, // never restore as CONNECTED
                    transport = transport,
                    lastSeen = entity.lastSeen,
                    sourceLanguage = entity.sourceLanguage,
                    targetLanguage = entity.targetLanguage,
                    priority = entity.priority,
                    bluetoothAddress = btAddress,
                )
                _sessions.value = _sessions.value + (entity.id to state)
                pending.getOrPut(entity.id) { PeerPending() }
            }
        }
        VokieLog.rescue("SESSIONS_RESTORED count=${entities.size}")
    }

    /** Persist current session metadata to Room. Called after significant state changes. */
    suspend fun persistSession(peerId: String) {
        val dao = peerDao ?: return
        val session = getSession(peerId) ?: return
        dao.upsert(PeerEntity(
            id = session.peerId,
            deviceAddress = session.bluetoothAddress ?: session.peerId,
            deviceName = session.displayName ?: session.peerId,
            protocolVersion = VokieProtocol.VERSION,
            lastSeen = session.lastSeen ?: now(),
            connectionState = session.connectionState.name,
            transport = (session.transport ?: TransportType.BLUETOOTH).name,
            isTrusted = false,
            sourceLanguage = session.sourceLanguage,
            targetLanguage = session.targetLanguage,
            priority = session.priority,
        ))
    }

    @Synchronized
    fun removePeer(peerId: String): Boolean {
        if (peerId !in _sessions.value) return false
        _sessions.value = _sessions.value - peerId
        pending.remove(peerId)
        VokieLog.rescue("SESSION_REMOVED peer=$peerId")
        return true
    }

    @Synchronized
    fun updateConnectionState(peerId: String, state: TransportConnectionState): Boolean = update(peerId) {
        VokieLog.rescue("CONNECTION_STATE peer=$peerId state=$state")
        it.copy(connectionState = state, lastSeen = now())
    }

    /** Records only a message whose sender is the explicitly supplied peer. */
    @Synchronized
    fun recordIncomingMessage(peerId: String, message: Message, incrementUnread: Boolean = true): Boolean {
        if (message.senderId != peerId) return false
        return update(peerId) {
            it.copy(
                lastSeen = now(),
                lastMessageTimestamp = maxTimestamp(it.lastMessageTimestamp, message.timestamp),
                lastMessagePreview = message.text.take(100),
                sourceLanguage = message.language,
                priority = message.priority,
                unreadCount = if (incrementUnread) it.unreadCount + 1 else it.unreadCount,
            ).also { VokieLog.rescue("MESSAGE_IN peer=$peerId message=${message.id} unread=${it.unreadCount}") }
        }
    }

    /** Mark all messages from a peer as read when viewing their conversation. */
    @Synchronized
    fun markAsRead(peerId: String): Boolean = update(peerId) {
        it.copy(unreadCount = 0).also { VokieLog.rescue("MARK_READ peer=$peerId") }
    }

    /** Get total unread count across all peers. */
    @Synchronized
    fun totalUnreadCount(): Int = _sessions.value.values.sumOf { it.unreadCount }

    /** Records only a message explicitly addressed to the supplied peer. */
    @Synchronized
    fun recordOutgoingMessage(peerId: String, message: Message): Boolean {
        if (message.receiverId != peerId) return false
        return update(peerId) {
            val peerPending = pending.getOrPut(peerId) { PeerPending() }
            if (message.requiresAck) {
                peerPending.messageIds += message.id
                peerPending.acknowledgements += MessageKey(message.id, message.sequenceNumber)
            }
            // Outgoing messages do not overwrite peer priority or sourceLanguage;
            // those are properties of the remote peer's incoming communication.
            it.copy(
                lastSeen = now(),
                lastMessageTimestamp = maxTimestamp(it.lastMessageTimestamp, message.timestamp),
                pendingMessageCount = peerPending.messageIds.size,
                unacknowledgedMessageCount = peerPending.acknowledgements.size,
            ).also { VokieLog.rescue("MESSAGE_OUT peer=$peerId message=${message.id}") }
        }
    }

    /** ACK association is peer-explicit and cannot resolve another peer's message. */
    @Synchronized
    fun recordAck(peerId: String, messageId: String, sequenceNumber: Long): Boolean {
        val session = _sessions.value[peerId] ?: return false
        val peerPending = pending[peerId] ?: return false
        val key = MessageKey(messageId, sequenceNumber)
        if (!peerPending.acknowledgements.remove(key)) return false
        peerPending.messageIds.remove(messageId)
        _sessions.value = _sessions.value + (peerId to session.copy(
            lastSeen = now(),
            pendingMessageCount = peerPending.messageIds.size,
            unacknowledgedMessageCount = peerPending.acknowledgements.size,
        ))
        VokieLog.rescue("ACK peer=$peerId message=$messageId")
        return true
    }

    @Synchronized
    fun getSession(peerId: String): PeerSessionState? = _sessions.value[peerId]

    @Synchronized
    fun getSessions(): List<PeerSessionState> = _sessions.value.values.toList()

    /**
     * Associate a Bluetooth MAC address with a peer UUID. Called when receiving a message
     * from a peer over Bluetooth so that outbound routing can resolve the MAC→UUID mapping.
     */
    @Synchronized
    fun associateBluetoothAddress(peerId: String, bluetoothAddress: String): Boolean {
        if (peerId.isBlank() || bluetoothAddress.isBlank()) return false
        // If there's a stale MAC-keyed session, remove it; the UUID session is authoritative.
        if (_sessions.value.containsKey(bluetoothAddress) && bluetoothAddress != peerId) {
            _sessions.value = _sessions.value - bluetoothAddress
            VokieLog.rescue("BT_MAC_SESSION_MERGED mac=$bluetoothAddress uuid=$peerId")
        }
        return update(peerId) {
            it.copy(bluetoothAddress = bluetoothAddress).also {
                VokieLog.rescue("BT_ADDRESS_ASSOCIATED peer=$peerId address=$bluetoothAddress")
            }
        }
    }

    /**
     * Mark both the UUID session and any associated MAC session as disconnected.
     * Called when transport link drops to ensure immediate UI state sync.
     */
    @Synchronized
    fun markDisconnected(peerId: String) {
        val session = _sessions.value[peerId]
        val btAddress = session?.bluetoothAddress
        update(peerId) { it.copy(connectionState = TransportConnectionState.DISCONNECTED) }
        if (btAddress != null && _sessions.value.containsKey(btAddress)) {
            update(btAddress) { it.copy(connectionState = TransportConnectionState.DISCONNECTED) }
        }
        VokieLog.rescue("PEER_MARKED_DISCONNECTED peer=$peerId btAddress=$btAddress")
    }

    /**
     * Find peer UUID by Bluetooth MAC address. Used by outbound routing to check if the
     * currently connected Bluetooth device (by MAC) is the same as a queued message's
     * destination (by UUID).
     */
    @Synchronized
    fun findPeerByBluetoothAddress(bluetoothAddress: String?): String? {
        if (bluetoothAddress.isNullOrBlank()) return null
        return _sessions.value.values.find { it.bluetoothAddress.equals(bluetoothAddress, ignoreCase = true) }?.peerId
    }

    /**
     * Check if a peer UUID is associated with a Bluetooth address that matches the given address.
     */
    @Synchronized
    fun isPeerAtBluetoothAddress(peerId: String?, bluetoothAddress: String?): Boolean {
        if (peerId.isNullOrBlank() || bluetoothAddress.isNullOrBlank()) return false
        val session = _sessions.value[peerId] ?: return false
        return session.bluetoothAddress.equals(bluetoothAddress, ignoreCase = true)
    }

    private fun update(peerId: String, transform: (PeerSessionState) -> PeerSessionState): Boolean {
        val current = _sessions.value[peerId] ?: return false
        _sessions.value = _sessions.value + (peerId to transform(current))
        return true
    }

    private fun maxTimestamp(existing: Long?, candidate: Long): Long = maxOf(existing ?: candidate, candidate)
}
