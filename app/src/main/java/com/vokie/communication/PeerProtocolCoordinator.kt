package com.vokie.communication

import com.vokie.data.PairingRepository
import com.vokie.data.PeerRepository
import com.vokie.domain.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Coordinates real peer protocol over PacketV2 MessageType.SYSTEM:
 * - Handshake: HELLO / HELLO_ACK
 * - Pairing: PAIR_REQ / PAIR_ACCEPT / PAIR_DECLINE / PAIR_CANCEL
 * - Active peer state gating (RADIO ENABLED != ACTIVE)
 * - Address association (Bluetooth MAC / Wi-Fi address -> App peerId UUID)
 */
class PeerProtocolCoordinator(
    private val localDeviceId: String,
    private val localDisplayNameProvider: () -> String,
    private val transportManager: TransportManager,
    private val peerSessionManager: PeerSessionManager,
    private val peerRepository: PeerRepository,
    private val pairingRepository: PairingRepository,
    private val scope: CoroutineScope,
    private val callSignalHandler: (suspend (JSONObject, PacketTransport) -> Unit)? = null,
    private val reassembler: PacketReassembler = PacketReassembler(),
    /** Provider for local Wi-Fi Direct P2P address, used for transport upgrade handshake. */
    private val wifiP2pAddressProvider: (() -> String?)? = null,
) {
    /** Peer Wi-Fi Direct addresses learned from HELLO/HELLO_ACK, keyed by peerId. */
    private val peerWifiAddresses = mutableMapOf<String, String>()

    /**
     * Called when a physical transport establishes a socket (Wi-Fi TCP or Bluetooth RFCOMM).
     * Dispatches initial HELLO handshake.
     */
    fun onTransportConnected(transport: PacketTransport, remoteAddress: String? = null) {
        scope.launch(Dispatchers.IO) {
            VokieLog.rescue("HANDSHAKE_INIT transport=${transport.type} remoteAddress=$remoteAddress")
            val hello = JSONObject().apply {
                put("action", "HELLO")
                put("peerId", localDeviceId)
                put("displayName", localDisplayNameProvider())
                put("protocolVersion", 2)
                put("transports", JSONArray().apply { put(transport.type.name) })
                put("languages", JSONArray().apply { put("HI"); put("EN") })
                put("capabilities", JSONArray().apply {
                    put("VOICE_TEXT"); put("TEXT"); put("CALL_SIGNAL"); put("CALL_AUDIO")
                })
                // Include Wi-Fi Direct P2P address for transport upgrade
                wifiP2pAddressProvider?.invoke()?.let { put("wifiP2pAddress", it) }
            }.toString()

            val msg = Message(
                id = UUID.randomUUID().toString(),
                senderId = localDeviceId,
                timestamp = System.currentTimeMillis(),
                text = hello,
                language = "EN",
                messageType = MessageType.SYSTEM,
                receiverId = null,
                requiresAck = false
            )
            transportManager.sendMessage(msg)
            VokieLog.rescue("HANDSHAKE_SENT action=HELLO to=$remoteAddress")
        }
    }

    /**
     * Handles incoming MessageType.SYSTEM packets.
     */
    suspend fun handleSystemFrame(frame: PacketV2.Decoded.MessagePacket, transport: PacketTransport) {
        val complete = reassembler.add(frame) ?: return
        val text = complete.text
        if (text.isBlank() || !text.startsWith("{")) return

        val json = runCatching { JSONObject(text) }.getOrNull() ?: return
        val action = json.optString("action")
        val remotePeerId = json.optString("peerId").ifBlank { complete.senderId }
        val remoteName = json.optString("displayName", "iTantra Peer")

        VokieLog.rescue("SYSTEM_RX action=$action from=$remotePeerId name=$remoteName")

        when (action) {
            "HELLO" -> {
                handleHello(remotePeerId, remoteName, json, transport)
            }
            "HELLO_ACK" -> {
                handleHelloAck(remotePeerId, remoteName, json, transport)
            }
            "PAIR_REQ" -> {
                handlePairRequest(json, transport)
            }
            "PAIR_ACCEPT" -> {
                handlePairAccept(json)
            }
            "PAIR_DECLINE" -> {
                handlePairDecline(json)
            }
            "PAIR_CANCEL" -> {
                handlePairCancel(json)
            }
            "CALL_INVITE", "CALL_ACCEPT", "CALL_REJECT", "CALL_BUSY", "CALL_CANCEL", "CALL_END" -> {
                callSignalHandler?.invoke(json, transport)
            }
        }
    }

    private suspend fun handleHello(
        peerId: String,
        displayName: String,
        json: JSONObject,
        transport: PacketTransport
    ) {
        if (peerId == localDeviceId) return

        // 1. Register & associate peer
        peerSessionManager.registerPeer(peerId, displayName, transport.type)
        if (transport.type == TransportType.BLUETOOTH) {
            transportManager.connectedPeerId.value?.let { btAddress ->
                peerSessionManager.associateBluetoothAddress(peerId, btAddress)
            }
        }
        // Store peer's Wi-Fi Direct address for potential transport upgrade
        json.optString("wifiP2pAddress").takeIf { it.isNotBlank() }?.let {
            peerWifiAddresses[peerId] = it
            VokieLog.rescue("PEER_WIFI_ADDRESS peer=$peerId wifiP2pAddress=$it")
        }
        peerSessionManager.updateConnectionState(peerId, TransportConnectionState.CONNECTED)
        peerSessionManager.persistSession(peerId)

        // 2. Mark ACTIVE in PeerRepository
        val pTransport = if (transport.type == TransportType.WIFI_DIRECT) PeerTransportType.WIFI else PeerTransportType.BLUETOOTH
        peerRepository.updateConnectionState(peerId, PeerConnectionState.ACTIVE, pTransport)
        VokieLog.rescue("PEER_ACTIVE peer=$peerId name=$displayName transport=${pTransport.name}")

        // 3. Send HELLO_ACK response
        val ack = JSONObject().apply {
            put("action", "HELLO_ACK")
            put("peerId", localDeviceId)
            put("displayName", localDisplayNameProvider())
            put("protocolVersion", 2)
            put("transports", JSONArray().apply { put(transport.type.name) })
            put("languages", JSONArray().apply { put("HI"); put("EN") })
            put("capabilities", JSONArray().apply {
                put("VOICE_TEXT"); put("TEXT"); put("CALL_SIGNAL"); put("CALL_AUDIO")
            })
            wifiP2pAddressProvider?.invoke()?.let { put("wifiP2pAddress", it) }
        }.toString()

        val msg = Message(
            id = UUID.randomUUID().toString(),
            senderId = localDeviceId,
            timestamp = System.currentTimeMillis(),
            text = ack,
            language = "EN",
            messageType = MessageType.SYSTEM,
            receiverId = peerId,
            requiresAck = false
        )
        transportManager.sendMessage(msg)
        VokieLog.rescue("HANDSHAKE_SENT action=HELLO_ACK to=$peerId")
    }

    private suspend fun handleHelloAck(
        peerId: String,
        displayName: String,
        json: JSONObject,
        transport: PacketTransport
    ) {
        if (peerId == localDeviceId) return

        peerSessionManager.registerPeer(peerId, displayName, transport.type)
        if (transport.type == TransportType.BLUETOOTH) {
            transportManager.connectedPeerId.value?.let { btAddress ->
                peerSessionManager.associateBluetoothAddress(peerId, btAddress)
            }
        }
        // Store peer's Wi-Fi Direct address and attempt automatic upgrade
        json.optString("wifiP2pAddress").takeIf { it.isNotBlank() }?.let { wifiAddr ->
            peerWifiAddresses[peerId] = wifiAddr
            VokieLog.rescue("PEER_WIFI_ADDRESS peer=$peerId wifiP2pAddress=$wifiAddr")
            // Android can expose 02:00:00:00:00:00 as a privacy placeholder. It is
            // never a connectable P2P address; use the real PEERS-discovered P2P row.
            if (transport.type == TransportType.BLUETOOTH && wifiAddr != "02:00:00:00:00:00") {
                attemptWifiUpgrade(peerId, wifiAddr)
            } else if (wifiAddr == "02:00:00:00:00:00") {
                VokieLog.rescue("WIFI_UPGRADE_SKIPPED peer=$peerId reason=dummy_address")
            }
        }
        peerSessionManager.updateConnectionState(peerId, TransportConnectionState.CONNECTED)
        peerSessionManager.persistSession(peerId)

        val pTransport = if (transport.type == TransportType.WIFI_DIRECT) PeerTransportType.WIFI else PeerTransportType.BLUETOOTH
        peerRepository.updateConnectionState(peerId, PeerConnectionState.ACTIVE, pTransport)
        VokieLog.rescue("PEER_ACTIVE_FROM_ACK peer=$peerId name=$displayName transport=${pTransport.name}")
    }

    private suspend fun handlePairRequest(json: JSONObject, transport: PacketTransport) {
        val requestId = json.optString("requestId").ifBlank { UUID.randomUUID().toString() }
        val senderPeerId = json.optString("senderPeerId")
        val displayName = json.optString("displayName", "iTantra Peer")

        if (senderPeerId.isBlank() || senderPeerId == localDeviceId) return

        // Register peer if needed
        peerSessionManager.registerPeer(senderPeerId, displayName, transport.type)
        val existing = pairingRepository.getRequest(requestId)
        if (existing == null) {
            pairingRepository.sendPairRequest(senderPeerId, localDeviceId)
            VokieLog.rescue("PAIR_REQUEST_RECEIVED requestId=$requestId from=$senderPeerId name=$displayName")
        }
    }

    private suspend fun handlePairAccept(json: JSONObject) {
        val requestId = json.optString("requestId")
        val peerId = json.optString("peerId")
        val displayName = json.optString("displayName", peerId)

        if (requestId.isNotBlank()) {
            pairingRepository.acceptPairRequest(requestId)
        }
        if (peerId.isNotBlank() && peerId != localDeviceId) {
            peerRepository.addTrustedPeer(peerId, displayName)
            VokieLog.rescue("PAIR_ACCEPTED_RECEIVED requestId=$requestId peerId=$peerId")
        }
    }

    private suspend fun handlePairDecline(json: JSONObject) {
        val requestId = json.optString("requestId")
        if (requestId.isNotBlank()) {
            pairingRepository.declinePairRequest(requestId)
            VokieLog.rescue("PAIR_DECLINE_RECEIVED requestId=$requestId")
        }
    }

    private suspend fun handlePairCancel(json: JSONObject) {
        val requestId = json.optString("requestId")
        if (requestId.isNotBlank()) {
            pairingRepository.cancelPairRequest(requestId)
            VokieLog.rescue("PAIR_CANCEL_RECEIVED requestId=$requestId")
        }
    }

    /**
     * Attempts to upgrade from Bluetooth to Wi-Fi Direct for higher bandwidth.
     * Called after receiving a peer's Wi-Fi P2P address in HELLO_ACK.
     */
    private fun attemptWifiUpgrade(peerId: String, wifiAddress: String) {
        scope.launch(Dispatchers.IO) {
            VokieLog.rescue("WIFI_UPGRADE_ATTEMPT peer=$peerId wifiAddress=$wifiAddress")
            runCatching {
                transportManager.connectWifiDirect(wifiAddress)
            }.onSuccess {
                VokieLog.rescue("WIFI_UPGRADE_SUCCESS peer=$peerId")
            }.onFailure {
                VokieLog.rescue("WIFI_UPGRADE_FAILED peer=$peerId error=${it.message}")
                // Bluetooth remains active as fallback; no user-facing error needed
            }
        }
    }

    /** Get the stored Wi-Fi P2P address for a peer, if known. */
    fun getPeerWifiAddress(peerId: String): String? = peerWifiAddresses[peerId]

    /**
     * Ensures the transport is connected before sending pair request. Previous behavior
     * silently dropped the PAIR_REQ if no RFCOMM/Wi-Fi session was active, requiring
     * the user to tap multiple times before a connection established in the background.
     */
    suspend fun sendPairRequest(receiverPeerId: String): PairRequest {
        // Connect the transport first if not already active for this peer.
        if (transportManager.activePacketTransport() == null || !transportManager.canSendTo(receiverPeerId)) {
            VokieLog.rescue("PAIR_REQUEST_CONNECT_FIRST target=$receiverPeerId")
            val session = peerSessionManager.getSession(receiverPeerId)
            val btAddress = session?.bluetoothAddress ?: receiverPeerId
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { transportManager.connectBluetooth(btAddress) }
            }
        }
        val req = pairingRepository.sendPairRequest(localDeviceId, receiverPeerId)
        val payload = JSONObject().apply {
            put("action", "PAIR_REQ")
            put("requestId", req.requestId)
            put("senderPeerId", localDeviceId)
            put("displayName", localDisplayNameProvider())
            put("timestamp", req.createdAt)
        }.toString()

        val msg = Message(
            id = UUID.randomUUID().toString(),
            senderId = localDeviceId,
            timestamp = System.currentTimeMillis(),
            text = payload,
            language = "EN",
            messageType = MessageType.SYSTEM,
            receiverId = receiverPeerId,
            requiresAck = true
        )
        transportManager.sendMessage(msg)
        VokieLog.rescue("PAIR_REQUEST_SENT requestId=${req.requestId} to=$receiverPeerId")
        return req
    }

    suspend fun acceptPairRequest(requestId: String) {
        val req = pairingRepository.getRequest(requestId) ?: return
        pairingRepository.acceptPairRequest(requestId)
        val targetPeerId = if (req.senderPeerId == localDeviceId) req.receiverPeerId else req.senderPeerId
        peerRepository.addTrustedPeer(targetPeerId, targetPeerId)

        val payload = JSONObject().apply {
            put("action", "PAIR_ACCEPT")
            put("requestId", requestId)
            put("peerId", localDeviceId)
            put("displayName", localDisplayNameProvider())
        }.toString()

        val msg = Message(
            id = UUID.randomUUID().toString(),
            senderId = localDeviceId,
            timestamp = System.currentTimeMillis(),
            text = payload,
            language = "EN",
            messageType = MessageType.SYSTEM,
            receiverId = targetPeerId,
            requiresAck = true
        )
        transportManager.sendMessage(msg)
        VokieLog.rescue("PAIR_ACCEPT_SENT requestId=$requestId to=$targetPeerId")
    }

    suspend fun declinePairRequest(requestId: String) {
        val req = pairingRepository.getRequest(requestId) ?: return
        pairingRepository.declinePairRequest(requestId)
        val targetPeerId = if (req.senderPeerId == localDeviceId) req.receiverPeerId else req.senderPeerId

        val payload = JSONObject().apply {
            put("action", "PAIR_DECLINE")
            put("requestId", requestId)
            put("peerId", localDeviceId)
        }.toString()

        val msg = Message(
            id = UUID.randomUUID().toString(),
            senderId = localDeviceId,
            timestamp = System.currentTimeMillis(),
            text = payload,
            language = "EN",
            messageType = MessageType.SYSTEM,
            receiverId = targetPeerId,
            requiresAck = true
        )
        transportManager.sendMessage(msg)
        VokieLog.rescue("PAIR_DECLINE_SENT requestId=$requestId to=$targetPeerId")
    }
}
