package com.vokie.communication

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import com.vokie.domain.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/** Real classic Bluetooth RFCOMM transport. UI only sees the Transport contract. */
class BluetoothTransport(private val context: Context, private val scope: kotlinx.coroutines.CoroutineScope) : Transport {
    override val type = TransportType.BLUETOOTH
    // BluetoothAdapter is obtained from BluetoothManager; it is not itself a system service.
    private val adapter: BluetoothAdapter? = context.getSystemService(android.bluetooth.BluetoothManager::class.java)?.adapter
    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    override val peers: StateFlow<List<Peer>> = _peers.asStateFlow()
    private val _state = MutableStateFlow(initialState())
    override val connectionState: StateFlow<TransportConnectionState> = _state.asStateFlow()
    private val _connectedPeerId = MutableStateFlow<String?>(null)
    override val connectedPeerId: StateFlow<String?> = _connectedPeerId.asStateFlow()
    private var socket: BluetoothSocket? = null
    private var serverSocket: android.bluetooth.BluetoothServerSocket? = null
    private var acceptJob: Job? = null
    private var connectionJob: Job? = null
    private var reconnectJob: Job? = null
    private var manuallyDisconnected = false
    @Volatile private var reconnectPeerId: String? = null
    @Volatile private var reconnectGeneration = 0L
    private val reconnectPolicy = ReconnectPolicy.BLUETOOTH
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null
    private var receiverRegistered = false
    private val received = MutableSharedFlow<Message>(extraBufferCapacity = 32)
    private val receivedPackets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    private val ackTracker = AckTracker()
    private val reassembler = PacketReassembler()
    /** Called exactly once when a live RFCOMM session ends unexpectedly. */
    var onDisconnected: ((peerId: String?) -> Unit)? = null
    /** Resolves a stale hardware address to the currently bonded address for the same peer. */
    var reconnectAddressResolver: (suspend (staleAddress: String) -> String?)? = null
    private val discoveredCandidates = BluetoothPeerCandidates()

    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    if (!BluetoothPermission.hasDiscovery(ctx) || !BluetoothPermission.hasConnection(ctx)) return
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    // Do not expose every inquiry result as an iTantra peer. A classic
                    // inquiry has no application identity; bonded phone/computer candidates
                    // may be shown while SDP is resolved, and the service UUID/HELLO path
                    // remains authoritative for an unpaired candidate.
                    if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        retainCandidate(
                            device,
                            intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).takeIf { it != Short.MIN_VALUE }?.toInt(),
                            intent.getStringExtra(BluetoothDevice.EXTRA_NAME),
                        )
                    }
                    device.fetchUuidsWithSdp()
                }
                BluetoothDevice.ACTION_UUID -> {
                    if (!BluetoothPermission.hasDiscovery(ctx) || !BluetoothPermission.hasConnection(ctx)) return
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    val uuids = IntentCompat.getParcelableArrayExtra(intent, BluetoothDevice.EXTRA_UUID, android.os.ParcelUuid::class.java)?.mapNotNull { (it as? android.os.ParcelUuid)?.uuid } ?: emptyList()
                    if (VokieProtocol.SERVICE_UUID in uuids) retainCandidate(device, null)
                }
                BluetoothDevice.ACTION_NAME_CHANGED -> {
                    if (!BluetoothPermission.hasConnection(ctx)) return
                    val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
                    if (device.bondState == BluetoothDevice.BOND_BONDED || discoveredCandidates.contains(device.address)) {
                        retainCandidate(device, null, intent.getStringExtra(BluetoothDevice.EXTRA_NAME))
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> if (_state.value == TransportConnectionState.SEARCHING) _state.value = TransportConnectionState.IDLE
                BluetoothAdapter.ACTION_STATE_CHANGED -> {
                    when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                        BluetoothAdapter.STATE_OFF -> {
                            val lostPeer = _connectedPeerId.value
                            reconnectJob?.cancel(); reconnectJob = null
                            closeSocket(); closeServer(); _connectedPeerId.value = null
                            _state.value = TransportConnectionState.BLUETOOTH_DISABLED
                            // Radio-off is a real disconnect, not merely a visual state.
                            onDisconnected?.invoke(lostPeer)
                        }
                        BluetoothAdapter.STATE_ON -> {
                            if (BluetoothPermission.hasConnection(context)) {
                                adapter?.let(::startServer)
                                _state.value = TransportConnectionState.IDLE
                                if (!manuallyDisconnected) reconnectPeerId?.let(::scheduleReconnect)
                            } else {
                                _state.value = TransportConnectionState.PERMISSION_REQUIRED
                            }
                        }
                    }
                }
            }
        }
    }

    init { registerReceiver() }

    @SuppressLint("MissingPermission")
    override suspend fun startListening() = withContext(Dispatchers.IO) {
        check(BluetoothPermission.hasConnection(context)) { "Nearby Devices permission is required." }
        val bt = adapter ?: error("Bluetooth is not available on this phone")
        if (!bt.isEnabled) error("Turn on Bluetooth to communicate.")
        registerReceiver(); startServer(bt)
        if (_state.value != TransportConnectionState.CONNECTED) _state.value = TransportConnectionState.IDLE
    }

    @SuppressLint("MissingPermission")
    override suspend fun discoverPeers() = withContext(Dispatchers.IO) {
        check(BluetoothPermission.hasDiscovery(context)) { "Nearby Devices permission is required." }
        val bt = adapter ?: error("Bluetooth is not available on this phone")
        if (!bt.isEnabled) error("Turn on Bluetooth to communicate.")
        registerReceiver()
        discoveredCandidates.clear()
        _peers.value = emptyList()
        bt.bondedDevices.forEach { it.fetchUuidsWithSdp() }
        startServer(bt)
        if (bt.isDiscovering) bt.cancelDiscovery()
        _state.value = TransportConnectionState.SEARCHING
        check(bt.startDiscovery()) { "Bluetooth discovery could not start." }
        VokieLog.bt("Discovery started")
    }

    @SuppressLint("MissingPermission")
    private fun retainCandidate(device: BluetoothDevice, rssi: Int?, discoveredName: String? = null) {
        // Inquiry reports every nearby accessory. Only phone/computer/network
        // classes are plausible iTantra hosts; HELLO remains authoritative before
        // a candidate becomes ACTIVE.
        val major = device.bluetoothClass?.majorDeviceClass
        if (major == BluetoothClass.Device.Major.AUDIO_VIDEO ||
            major == BluetoothClass.Device.Major.WEARABLE ||
            major == BluetoothClass.Device.Major.PERIPHERAL ||
            major == BluetoothClass.Device.Major.HEALTH) return
        discoveredCandidates.retain(
            address = device.address,
            name = discoveredName ?: device.name,
            bonded = device.bondState == BluetoothDevice.BOND_BONDED,
            rssi = rssi,
            discoveredAtMs = System.currentTimeMillis(),
        )?.let { _peers.value = it }
    }

    @SuppressLint("MissingPermission")
    override suspend fun stopDiscovery() = withContext(Dispatchers.IO) {
        if (BluetoothPermission.hasDiscovery(context) && adapter?.isDiscovering == true) adapter.cancelDiscovery()
        if (_state.value == TransportConnectionState.SEARCHING) _state.value = TransportConnectionState.IDLE
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(peerId: String) = withContext(Dispatchers.IO) {
        manuallyDisconnected = false
        check(BluetoothPermission.hasConnection(context)) { "Nearby Devices permission is required." }
        val bt = adapter ?: error("Bluetooth is not available on this phone")
        if (!bt.isEnabled) error("Turn on Bluetooth to communicate.")
        val device = bt.getRemoteDevice(peerId)
        reconnectPeerId = peerId
        _state.value = TransportConnectionState.CONNECTING
        if (bt.isDiscovering) bt.cancelDiscovery()
        closeSocket()
        try {
            val candidate = withTimeout(15.seconds) { device.createRfcommSocketToServiceRecord(VokieProtocol.SERVICE_UUID).also { it.connect() } }
            socket = candidate; input = DataInputStream(candidate.inputStream); output = DataOutputStream(candidate.outputStream)
            _connectedPeerId.value = peerId
            _state.value = TransportConnectionState.CONNECTED
            reconnectGeneration++ // invalidate stale reconnect loops
            VokieLog.bt("Connection established: $peerId")
            listenForFrames()
        } catch (t: Throwable) { closeSocket(); _state.value = TransportConnectionState.FAILED; VokieLog.bt("Connection failed: ${t.message}"); scheduleReconnect(peerId); throw IOException("Connection failed", t) }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) { manuallyDisconnected = true; reconnectPeerId = null; reconnectJob?.cancel(); reconnectJob = null; closeSocket(); closeServer(); _connectedPeerId.value = null; _state.value = TransportConnectionState.DISCONNECTED; VokieLog.bt("Disconnected") }

    override suspend fun send(message: Message): SendResult = withContext(Dispatchers.IO) {
        val stream = output ?: return@withContext SendResult(message.id, false, error = "No connected iTantra device")
        val waiter = ackTracker.register(message.id)
        try {
            val frames = PacketV2.fromMessage(message)
            synchronized(stream) { frames.forEach { frame -> stream.writeInt(frame.size); stream.write(frame) }; stream.flush() }
            VokieLog.msg("Transmission started: ${message.id}")
            val started = android.os.SystemClock.elapsedRealtime()
            val ack = withTimeout(8.seconds) { waiter.await() }
            if (ack) { val latency = android.os.SystemClock.elapsedRealtime() - started; VokieLog.msg("ACK received: ${message.id}"); SendResult(message.id, true, latency) } else SendResult(message.id, false, error = "Peer rejected message")
        } catch (t: Throwable) { VokieLog.msg("Transmission failed: ${message.id}: ${t.message}"); SendResult(message.id, false, error = "No acknowledgement received") } finally { ackTracker.remove(message.id) }
    }

    override fun observeMessages(): Flow<Message> = received.asSharedFlow()
    fun observePackets(): Flow<ByteArray> = receivedPackets.asSharedFlow()
    suspend fun sendPacket(packet: ByteArray) = withContext(Dispatchers.IO) {
        val stream = output ?: throw IOException("No connected iTantra device")
        require(packet.isNotEmpty() && packet.size <= PacketV2.MAX_FRAME_BYTES)
        synchronized(stream) { stream.writeInt(packet.size); stream.write(packet); stream.flush() }
    }

    override suspend fun acknowledge(messageId: String) = withContext(Dispatchers.IO) {
        val localId = context.getSharedPreferences("vokie_identity", Context.MODE_PRIVATE).getString("device_id", null) ?: error("Device identity unavailable")
        sendAck(messageId, localId)
    }

    private fun listenForFrames() {
        connectionJob?.cancel()
        connectionJob = scope.launch(Dispatchers.IO) {
            try {
                while (connectionState.value == TransportConnectionState.CONNECTED) {
                    val size = input?.readInt() ?: break
                    if (size <= 0 || size > 64 * 1024) throw IOException("Invalid iTantra frame size")
                    val bytes = ByteArray(size); input!!.readFully(bytes)
                    receivedPackets.tryEmit(bytes)
                    when (val frame = PacketV2.decode(bytes)) {
                        is PacketV2.Decoded.Ack -> if (!ackTracker.acknowledge(frame.messageId)) VokieLog.msg("Unknown ACK ignored: ${frame.messageId}")
                        is PacketV2.Decoded.MessagePacket -> {
                            // SYSTEM is dispatched by TransportManager; CALL_AUDIO is a
                            // high-rate ephemeral stream. Neither belongs to legacy Room reassembly.
                            if (frame.messageType !in setOf(MessageType.SYSTEM, MessageType.CALL_AUDIO)) {
                                reassembler.add(frame)?.let { received.emit(it) }
                            }
                        }
                    }
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (t: Throwable) { if (connectionState.value == TransportConnectionState.CONNECTED) { val lostPeer = _connectedPeerId.value; closeSocket(); _connectedPeerId.value = null; _state.value = TransportConnectionState.DISCONNECTED; VokieLog.bt("Connection lost: ${t.message}"); onDisconnected?.invoke(lostPeer); reconnectPeerId?.let(::scheduleReconnect) } }
        }
    }

    private fun sendAck(messageId: String, receiverId: String) { val stream = output ?: return; val frame = VokieProtocol.encodeAck(messageId, receiverId, System.currentTimeMillis()); synchronized(stream) { stream.writeInt(frame.size); stream.write(frame); stream.flush() } }
    @SuppressLint("MissingPermission") private fun closeSocket() { connectionJob?.cancel(); connectionJob = null; runCatching { input?.close() }; runCatching { output?.close() }; runCatching { socket?.close() }; input = null; output = null; socket = null }
    @SuppressLint("MissingPermission") private fun closeServer() { acceptJob?.cancel(); acceptJob = null; runCatching { serverSocket?.close() }; serverSocket = null }
    @SuppressLint("MissingPermission")
    private fun startServer(bt: BluetoothAdapter) {
        if (acceptJob?.isActive == true) return
        acceptJob = scope.launch(Dispatchers.IO) {
            runCatching {
                serverSocket = bt.listenUsingRfcommWithServiceRecord(VokieProtocol.SERVICE_NAME, VokieProtocol.SERVICE_UUID)
                while (true) {
                    val accepted = serverSocket?.accept() ?: break
                    reconnectJob?.cancel(); reconnectJob = null
                    reconnectGeneration++ // invalidate any stale outbound reconnect
                    closeSocket()
                    socket = accepted; input = DataInputStream(accepted.inputStream); output = DataOutputStream(accepted.outputStream)
                    manuallyDisconnected = false
                    reconnectPeerId = null
                    _connectedPeerId.value = accepted.remoteDevice.address
                    _state.value = TransportConnectionState.CONNECTED
                    VokieLog.bt("Incoming iTantra connection established")
                    listenForFrames()
                }
            }.onFailure { if (_state.value != TransportConnectionState.CONNECTED) VokieLog.bt("Server stopped: ${it.message}") }
        }
    }

    private fun scheduleReconnect(peerId: String) {
        if (manuallyDisconnected || reconnectJob?.isActive == true) return
        val generation = ++reconnectGeneration
        VokieLog.rescue("RECONNECT_STARTED transport=BLUETOOTH peer=$peerId generation=$generation")
        reconnectJob = scope.launch(Dispatchers.IO) {
            repeat(reconnectPolicy.maxAttempts) { attempt ->
                // Abort if superseded by a newer generation, user disconnected, or already reconnected.
                if (reconnectGeneration != generation || manuallyDisconnected) {
                    VokieLog.rescue("RECONNECT_CANCELLED transport=BLUETOOTH reason=${if (manuallyDisconnected) "intentional" else "stale"} attempt=$attempt")
                    return@launch
                }
                if (_state.value == TransportConnectionState.CONNECTED) {
                    VokieLog.rescue("RECONNECT_CANCELLED transport=BLUETOOTH reason=already_connected attempt=$attempt")
                    return@launch
                }
                val backoff = reconnectPolicy.delayMs(attempt)
                VokieLog.rescue("RECONNECT_BACKOFF transport=BLUETOOTH attempt=$attempt delay=${backoff}ms peer=$peerId")
                delay(backoff)
                // Re-check after delay.
                if (reconnectGeneration != generation || manuallyDisconnected || adapter?.isEnabled != true) return@launch
                if (_state.value == TransportConnectionState.CONNECTED) return@launch
                val resolvedPeerId = reconnectAddressResolver?.invoke(peerId)?.takeIf { it.isNotBlank() } ?: peerId
                if (resolvedPeerId != peerId) {
                    VokieLog.rescue("RECONNECT_ADDRESS_RESOLVED transport=BLUETOOTH stale=$peerId resolved=$resolvedPeerId")
                }
                VokieLog.rescue("RECONNECT_ATTEMPT transport=BLUETOOTH attempt=$attempt peer=$resolvedPeerId")
                val success = runCatching { connect(resolvedPeerId) }.isSuccess
                if (success) {
                    VokieLog.rescue("RECONNECT_CONNECTED transport=BLUETOOTH attempt=$attempt peer=$peerId")
                    return@launch
                }
                VokieLog.rescue("RECONNECT_FAILED transport=BLUETOOTH attempt=$attempt peer=$peerId")
            }
            VokieLog.rescue("RECONNECT_EXHAUSTED transport=BLUETOOTH peer=$peerId attempts=${reconnectPolicy.maxAttempts}")
        }
    }

    private fun registerReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(context, discoveryReceiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND); addAction(BluetoothDevice.ACTION_UUID); addAction(BluetoothDevice.ACTION_NAME_CHANGED); addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED); addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }, ContextCompat.RECEIVER_EXPORTED)
        receiverRegistered = true
    }

    private fun initialState(): TransportConnectionState = when {
        adapter == null -> TransportConnectionState.UNAVAILABLE
        !BluetoothPermission.hasConnection(context) -> TransportConnectionState.PERMISSION_REQUIRED
        !adapter.isEnabled -> TransportConnectionState.BLUETOOTH_DISABLED
        else -> TransportConnectionState.IDLE
    }
}

object VokieLog {
    fun bt(message: String) = debug("VOKIE][BT", message)
    fun msg(message: String) = debug("VOKIE][MSG", message)
    fun stt(message: String) = debug("VOKIE][STT", message)
    fun tts(message: String) = debug("VOKIE][TTS", message)
    fun translation(message: String) = debug("VOKIE][TRANSLATION", message)
    fun timing(message: String) = debug("VOKIE_TIMING", message)
    fun rescue(message: String) = debug("VOKIE_RESCUE", message)
    private fun debug(tag: String, message: String) { if (com.vokie.BuildConfig.DEBUG) runCatching { android.util.Log.i(tag, message) } }
}
