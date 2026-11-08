package com.vokie.data

import com.vokie.communication.PeerSessionManager
import com.vokie.communication.PeerSessionState
import com.vokie.data.local.PeerDao
import com.vokie.data.local.TrustedPeerDao
import com.vokie.data.local.TrustedPeerEntity
import com.vokie.domain.model.Peer
import com.vokie.domain.model.PeerConnectionState
import com.vokie.domain.model.PeerTransportType
import com.vokie.domain.model.TrustedPeer
import com.vokie.domain.model.TrustedPeerStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

interface PeerRepository {
    fun observePeers(): Flow<List<Peer>>
    fun observeDiscoveredPeers(): Flow<List<Peer>>
    fun observeTrustedPeers(): Flow<List<TrustedPeer>>
    fun observePeer(peerId: String): Flow<Peer?>
    suspend fun getPeer(peerId: String): Peer?
    suspend fun updateConnectionState(peerId: String, state: PeerConnectionState, transport: PeerTransportType)
    suspend fun addTrustedPeer(peerId: String, displayName: String): TrustedPeer
    suspend fun removeTrustedPeer(peerId: String)
    suspend fun blockPeer(peerId: String)
}

class RoomPeerRepository(
    private val peerDao: PeerDao,
    private val trustedPeerDao: TrustedPeerDao,
    private val peerSessionManager: PeerSessionManager,
) : PeerRepository {

    override fun observePeers(): Flow<List<Peer>> =
        combine(peerDao.observeAll(), peerSessionManager.sessions) { entities, sessions ->
            val entityMap = entities.associateBy { it.id }
            // De-duplicate: if a session has a bluetoothAddress that matches an entity ID,
            // they represent the same physical device. Prefer the UUID session over the MAC entity.
            val sessionBluetoothAddresses = sessions.values.mapNotNull { it.bluetoothAddress }.toSet()
            val allPeerIds = (entities.map { it.id }.filter { id ->
                // Exclude MAC-based entities that already have a UUID session
                id !in sessionBluetoothAddresses
            } + sessions.keys).distinct()
            allPeerIds.map { id ->
                val entity = entityMap[id]
                val session = sessions[id]
                mapToPeer(id, entity, session)
            }
        }

    override fun observeDiscoveredPeers(): Flow<List<Peer>> =
        combine(observePeers(), observeTrustedPeers()) { allPeers, trusted ->
            val trustedIds = trusted.map { it.peerId }.toSet()
            // Only emit currently connected or recent actively-seen peers. Stale historical
            // entries from Room DB (154+ ancient Bluetooth devices) are excluded by checking
            // the live PeerSessionManager status. If lastSeen is more than 10 min ago and
            // the peer is not ACTIVE, treat it as stale and hide it.
            val staleThreshold = System.currentTimeMillis() - 10 * 60 * 1_000
            allPeers.filter { peer ->
                (peer.peerId !in trustedIds || peer.connectionState == PeerConnectionState.ACTIVE) &&
                (peer.connectionState == PeerConnectionState.ACTIVE || peer.connectionState == PeerConnectionState.CONNECTING || (peer.lastSeen != null && peer.lastSeen > staleThreshold))
            }
        }

    override fun observeTrustedPeers(): Flow<List<TrustedPeer>> =
        trustedPeerDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observePeer(peerId: String): Flow<Peer?> =
        observePeers().map { peers -> peers.find { it.peerId == peerId } }

    override suspend fun getPeer(peerId: String): Peer? {
        val entity = peerDao.getAll().find { it.id == peerId }
        val session = peerSessionManager.getSession(peerId)
        return if (entity != null || session != null) mapToPeer(peerId, entity, session) else null
    }

    override suspend fun updateConnectionState(peerId: String, state: PeerConnectionState, transport: PeerTransportType) {
        val connState = when (state) {
            PeerConnectionState.ACTIVE -> com.vokie.domain.model.TransportConnectionState.CONNECTED
            PeerConnectionState.CONNECTING -> com.vokie.domain.model.TransportConnectionState.CONNECTING
            PeerConnectionState.DISCOVERED -> com.vokie.domain.model.TransportConnectionState.SEARCHING
            PeerConnectionState.OFFLINE -> com.vokie.domain.model.TransportConnectionState.DISCONNECTED
            PeerConnectionState.AVAILABLE -> com.vokie.domain.model.TransportConnectionState.IDLE
        }
        peerSessionManager.updateConnectionState(peerId, connState)
        peerDao.updateConnectionState(peerId, connState.name)
    }

    override suspend fun addTrustedPeer(peerId: String, displayName: String): TrustedPeer {
        val now = System.currentTimeMillis()
        val entity = TrustedPeerEntity(
            peerId = peerId,
            displayName = displayName,
            createdAt = now,
            acceptedAt = now,
            lastConnectedAt = now,
            status = TrustedPeerStatus.ACTIVE.name,
        )
        trustedPeerDao.upsert(entity)
        return entity.toDomain()
    }

    override suspend fun removeTrustedPeer(peerId: String) {
        trustedPeerDao.delete(peerId)
    }

    override suspend fun blockPeer(peerId: String) {
        trustedPeerDao.updateStatus(peerId, TrustedPeerStatus.BLOCKED.name)
    }

    private fun mapToPeer(
        id: String,
        entity: com.vokie.data.local.PeerEntity?,
        session: PeerSessionState?,
    ): Peer {
        val name = session?.displayName ?: entity?.deviceName ?: id
        val address = session?.bluetoothAddress ?: entity?.deviceAddress ?: id
        val transport = PeerTransportType.fromTransportType(session?.transport)
        val connState = when (session?.connectionState) {
            com.vokie.domain.model.TransportConnectionState.CONNECTED -> PeerConnectionState.ACTIVE
            com.vokie.domain.model.TransportConnectionState.CONNECTING -> PeerConnectionState.CONNECTING
            com.vokie.domain.model.TransportConnectionState.SEARCHING -> PeerConnectionState.DISCOVERED
            com.vokie.domain.model.TransportConnectionState.DISCONNECTED, com.vokie.domain.model.TransportConnectionState.FAILED -> PeerConnectionState.OFFLINE
            else -> if (entity != null) PeerConnectionState.DISCOVERED else PeerConnectionState.AVAILABLE
        }
        return Peer(
            id = id,
            name = name,
            address = address,
            bonded = entity?.isTrusted ?: false,
            connectionState = connState,
            transport = transport,
            lastSeen = session?.lastSeen ?: entity?.lastSeen,
            inputLanguage = session?.sourceLanguage ?: entity?.sourceLanguage,
            outputLanguage = session?.targetLanguage ?: entity?.targetLanguage,
            capabilities = setOf("STT", "TTS", "TRANSLATION"),
        )
    }
}

fun TrustedPeerEntity.toDomain() = TrustedPeer(
    peerId = peerId,
    displayName = displayName,
    createdAt = createdAt,
    acceptedAt = acceptedAt,
    lastConnectedAt = lastConnectedAt,
    status = runCatching { TrustedPeerStatus.valueOf(status) }.getOrDefault(TrustedPeerStatus.ACTIVE),
)
