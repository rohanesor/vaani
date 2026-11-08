package com.vokie.communication

import com.vokie.domain.model.*
import com.vokie.location.LocationMeasurementCollector
import com.vokie.location.MeasurementTrigger
import com.vokie.ranging.RelativePeerLocalizationEngine
import com.vokie.stt.TurnTimingRecorder
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Transport coordinator. Packet methods are the transport-neutral production boundary. */
class TransportManager(
    private val bluetooth: BluetoothTransport,
    private val wifiDirect: PacketTransport? = null,
    private val measurements: LocationMeasurementCollector? = null,
    private val localization: RelativePeerLocalizationEngine? = null,
    scope: kotlinx.coroutines.CoroutineScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Default),
    private val timing: TurnTimingRecorder? = null,
    private val sessionManager: PeerSessionManager? = null,
) {
    private val bluetoothPacket = BluetoothPacketTransport(bluetooth, scope)
    val pendingAcks = PendingAckRegistry()
    private val managerScope = scope
    private val _decodedFrames = MutableSharedFlow<DecodedTransportFrame>(extraBufferCapacity = 64)
    val decodedFrames: Flow<DecodedTransportFrame> = _decodedFrames.asSharedFlow()

    sealed interface DecodedTransportFrame {
        val transport: PacketTransport
        data class Message(val packet: PacketV2.Decoded.MessagePacket, override val transport: PacketTransport) : DecodedTransportFrame
        /** `peerId` is the ACK sender (PacketV2 ACK source field), never UI selection. */
        data class Ack(val messageId: String, val sequenceNumber: Long, val peerId: String, override val transport: PacketTransport) : DecodedTransportFrame
    }
    // The queue must react to whichever transport is actually active. Previously it
    // observed Bluetooth only, so Wi-Fi Direct packets remained queued despite a live socket.
    val connectionState: StateFlow<TransportConnectionState> = wifiDirect?.state
        ?.combine(bluetooth.connectionState) { wifi, bt ->
            if (wifi == PacketTransportState.CONNECTED) TransportConnectionState.CONNECTED else bt
        }
        ?.stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, bluetooth.connectionState.value)
        ?: bluetooth.connectionState
    val peers = bluetooth.peers
    val connectedPeerId = bluetooth.connectedPeerId
    val wifiState: StateFlow<PacketTransportState>? get() = wifiDirect?.state
    val bluetoothState: StateFlow<TransportConnectionState> get() = bluetooth.connectionState

    /** Wi-Fi Direct has priority only when its lifecycle is actually CONNECTED. */
    fun activePacketTransport(): PacketTransport? = wifiDirect?.takeIf { it.state.value == PacketTransportState.CONNECTED }
        ?: bluetoothPacket.takeIf { it.state.value == PacketTransportState.CONNECTED }
    fun activeTransport(): Transport? = bluetooth.takeIf { it.connectionState.value == TransportConnectionState.CONNECTED }
    fun activeTransportType(): TransportType? = activePacketTransport()?.type

    /**
     * Prevent a queued message for Peer B from being sent over an active Bluetooth
     * link to Peer A. Wi-Fi Direct has one TCP link as well; its target identity is
     * owned by its transport lifecycle, so a connected Wi-Fi link is treated as its
     * selected link rather than falling back to Bluetooth.
     *
     * Since Bluetooth connections use MAC addresses but messages are queued with
     * app-level UUIDs, we also check if the peer UUID is associated with the
     * currently connected Bluetooth MAC address.
     */
    fun canSendTo(peerId: String?): Boolean {
        val transport = activePacketTransport() ?: return false
        if (peerId.isNullOrBlank()) return true
        return when (transport.type) {
            TransportType.BLUETOOTH -> {
                val connectedMac = bluetooth.connectedPeerId.value
                // Direct match: peerId is the MAC address
                if (connectedMac.equals(peerId, ignoreCase = true)) return true
                // Indirect match: peerId (UUID) is associated with the connected MAC
                sessionManager?.isPeerAtBluetoothAddress(peerId, connectedMac) == true
            }
            TransportType.WIFI_DIRECT -> true
            else -> false
        }
    }

    suspend fun sendPacket(packet: ByteArray, messageId: String, sequenceNumber: Long, peerId: String?, requireAck: Boolean = true): SendResult {
        val transport = activePacketTransport() ?: return SendResult(messageId, false, error = "No connected transport")
        val pending = if (requireAck) pendingAcks.register(PendingAckRegistry.Key(messageId, sequenceNumber, peerId.orEmpty()), transport, ACK_TIMEOUT_MS).also {
            VokieLog.bt("TX_REGISTER_PENDING id=$messageId")
        } else null
        return try {
            transport.send(packet)
            if (pending == null) SendResult(messageId, true) else if (pendingAcks.await(pending)) SendResult(messageId, true) else SendResult(messageId, false, error = "ACK timeout")
        } catch (error: Throwable) {
            pending?.let { pendingAcks.fail(it.key) }; pendingAcks.clearTransport(transport)
            SendResult(messageId, false, error = error.message ?: "Transport send failed")
        }
    }

    /**
     * Fire-and-forget call media. It intentionally bypasses ACK registration/retries and
     * the durable outbound queue: delayed PCM harms call latency and must be discarded.
     */
    suspend fun sendCallAudio(callId: String, sequenceNumber: Long, sourceDeviceId: String, pcm: ByteArray): Boolean {
        val transport = activePacketTransport() ?: return false
        return runCatching {
            transport.send(PacketV2.fromCallAudio(callId, sequenceNumber, sourceDeviceId, System.currentTimeMillis(), pcm))
            true
        }.getOrElse {
            VokieLog.rescue("CALL_AUDIO_TX_FAILED callId=$callId reason=${it.message}")
            false
        }
    }

    /** Single queue entry point: encode here, then only bytes cross PacketTransport. */
    suspend fun sendMessage(message: Message): SendResult {
        val frames = PacketV2.fromMessage(message)
        // T3 is emitted only after PacketV2 has actually been created.
        timing?.packetCreated(message.id, message.sequenceNumber)
        val transport = activePacketTransport()
        val peer = connectedPeerId.value ?: message.receiverId ?: "unknown"
        val started = android.os.SystemClock.elapsedRealtime()
        measurements?.record(peer, MeasurementTrigger.MESSAGE_SENT, connectionState.value, wifiDirect?.state?.value ?: PacketTransportState.IDLE, transport?.type)
        transport?.type?.let { localization?.recordMessage(peer, it) }
        VokieLog.bt("TX_CREATE id=${message.id}")
        VokieLog.bt("WIFI_PACKET_TX messageId=${message.id} frames=${frames.size}")
        var result = SendResult(message.id, true)
        frames.forEachIndexed { index, frame ->
            if (result.acknowledged) {
                if (index == 0) timing?.transportTx(message.id, message.sequenceNumber)
                result = sendPacket(frame, message.id, message.sequenceNumber, message.receiverId, index == frames.lastIndex)
            }
        }
        if (result.acknowledged) {
            val rtt = android.os.SystemClock.elapsedRealtime() - started
            measurements?.record(peer, MeasurementTrigger.ACK_RECEIVED, connectionState.value, wifiDirect?.state?.value ?: PacketTransportState.IDLE, transport?.type, rttMs = rtt, delivered = true)
            transport?.type?.let { localization?.recordAck(peer, it, rtt) }
        }
        return result
    }
    data class IncomingPacket(val transport: PacketTransport, val bytes: ByteArray)
    init {
        // Wire transport disconnect callbacks into the common lifecycle.
        bluetooth.onDisconnected = { peerId -> onTransportDisconnected(bluetoothPacket, peerId) }
        (wifiDirect as? WifiDirectTransport)?.let { wifi -> wifi.onDisconnected = { onTransportDisconnected(wifi, connectedPeerId.value) } }

        managerScope.launch {
            incomingPackets().collect { incoming ->
                val decoded = runCatching { PacketV2.decode(incoming.bytes) }.getOrElse {
                    VokieLog.bt("PACKET_DECODE_FAILURE reason=${it.message}")
                    return@collect
                }
                when (decoded) {
                    is PacketV2.Decoded.Ack -> {
                        VokieLog.bt("PACKET_DECODE type=ACK id=${decoded.messageId}")
                        VokieLog.bt("FRAME_DISPATCH type=ACK id=${decoded.messageId}")
                        VokieLog.bt("ACK_RESOLVE_ATTEMPT id=${decoded.messageId}")
                        if (pendingAcks.resolve(decoded.messageId, decoded.sequenceNumber, decoded.receiverId, incoming.transport)) {
                            VokieLog.bt("ACK_CORRELATED id=${decoded.messageId}")
                            VokieLog.bt("TX_COMPLETE id=${decoded.messageId}")
                            timing?.transportAck(decoded.messageId, decoded.sequenceNumber)
                        } else {
                            VokieLog.bt("ACK_UNKNOWN id=${decoded.messageId} pending=${pendingAcks.size()}")
                        }
                        _decodedFrames.emit(DecodedTransportFrame.Ack(decoded.messageId, decoded.sequenceNumber, decoded.receiverId, incoming.transport))
                    }
                    is PacketV2.Decoded.MessagePacket -> {
                        // CALL_AUDIO arrives 50 times/sec; avoid per-frame log allocation and
                        // logcat backpressure on the same socket thread.
                        if (decoded.messageType != MessageType.CALL_AUDIO) VokieLog.bt("PACKET_DECODE type=MESSAGE id=${decoded.packet.messageId}")
                        _decodedFrames.emit(DecodedTransportFrame.Message(decoded, incoming.transport))
                    }
                }
            }
        }
    }

    fun incomingPackets(): Flow<IncomingPacket> = kotlinx.coroutines.flow.merge(
        (wifiDirect?.observePackets() ?: kotlinx.coroutines.flow.emptyFlow()).map { IncomingPacket(wifiDirect!!, it) },
        bluetoothPacket.observePackets().map { IncomingPacket(bluetoothPacket, it) },
    )

    suspend fun sendAck(transport: PacketTransport, messageId: String, receiverId: String, sequenceNumber: Long = 0) {
        val packet = PacketV2.encodeAck(messageId, receiverId, System.currentTimeMillis(), sequenceNumber)
        VokieLog.bt("WIFI_ACK_TX messageId=$messageId length=${packet.size}")
        transport.send(packet)
    }

    /** Callback for application-level disconnect recovery (message re-queue, session update). */
    var disconnectListener: ((transport: PacketTransport, peerId: String?) -> Unit)? = null

    fun onTransportDisconnected(transport: PacketTransport, peerId: String? = null) {
        pendingAcks.clearTransport(transport)
        VokieLog.rescue("TRANSPORT_DISCONNECTED transport=${transport.type} peer=${peerId ?: "unknown"}")
        disconnectListener?.invoke(transport, peerId)
    }

    private companion object { const val ACK_TIMEOUT_MS = 8_000L }
    suspend fun discoverWifiDirect() { wifiDirect?.discover() ?: error("Wi-Fi Direct unavailable") }
    suspend fun connectWifiDirect(address: String) { wifiDirect?.connect(address) ?: error("Wi-Fi Direct unavailable") }
    suspend fun disconnectWifiDirect() { wifiDirect?.disconnect() }

    suspend fun startBluetoothListener() = bluetooth.startListening()
    suspend fun discoverBluetooth() = bluetooth.discoverPeers()
    suspend fun stopDiscovery() = bluetooth.stopDiscovery()
    suspend fun connectBluetooth(peerId: String) = bluetooth.connect(peerId)
    suspend fun disconnect() = bluetooth.disconnect()
    fun incomingMessages() = bluetooth.observeMessages()
    suspend fun acknowledge(messageId: String) = bluetooth.acknowledge(messageId)
    fun discoverabilityRequest(): android.content.Intent = android.content.Intent(android.bluetooth.BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).putExtra(android.bluetooth.BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
}
