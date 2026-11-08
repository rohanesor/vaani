package com.vokie.communication

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import com.vokie.domain.model.TransportType
import com.vokie.domain.model.TransportConnectionState
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException

/** Byte-oriented transport boundary. PacketV2 serialization is outside this interface. */
interface PacketTransport {
    val type: TransportType
    val state: StateFlow<PacketTransportState>
    val peers: StateFlow<List<WifiPeer>>
    suspend fun discover()
    suspend fun connect(deviceAddress: String)
    suspend fun send(packet: ByteArray)
    suspend fun disconnect()
    fun observePackets(): Flow<ByteArray>
}

enum class PacketTransportState { IDLE, DISCOVERING, CONNECTING, CONNECTED, DISCONNECTING, FAILED }
data class WifiPeer(val address: String, val name: String, val available: Boolean)

/** Raw-byte RFCOMM adapter; legacy Message APIs remain only for compatibility. */
class BluetoothPacketTransport(private val legacy: BluetoothTransport, scope: CoroutineScope) : PacketTransport {
    override val type = TransportType.BLUETOOTH
    override val state: StateFlow<PacketTransportState> = legacy.connectionState.map { when (it) {
        TransportConnectionState.CONNECTED -> PacketTransportState.CONNECTED
        TransportConnectionState.SEARCHING -> PacketTransportState.DISCOVERING
        TransportConnectionState.CONNECTING -> PacketTransportState.CONNECTING
        TransportConnectionState.FAILED -> PacketTransportState.FAILED
        else -> PacketTransportState.IDLE
    }}.stateIn(scope, SharingStarted.Eagerly, PacketTransportState.IDLE)
    override val peers: StateFlow<List<WifiPeer>> = legacy.peers.map { list -> list.map { WifiPeer(it.address, it.name, it.bonded) }}.stateIn(scope, SharingStarted.Eagerly, emptyList())
    override suspend fun discover() = legacy.discoverPeers()
    override suspend fun connect(deviceAddress: String) = legacy.connect(deviceAddress)
    override suspend fun send(packet: ByteArray) = legacy.sendPacket(packet)
    override suspend fun disconnect() = legacy.disconnect()
    override fun observePackets(): Flow<ByteArray> = legacy.observePackets()
}

/** Shared TCP framing used by Wi-Fi Direct. It handles short reads and rejects bad lengths. */
object LengthPrefixedFrames {
    const val MAX_PACKET_BYTES = PacketV2.MAX_FRAME_BYTES
    suspend fun write(output: DataOutputStream, packet: ByteArray) = withContext(Dispatchers.IO) {
        require(packet.isNotEmpty() && packet.size <= MAX_PACKET_BYTES)
        synchronized(output) { output.writeInt(packet.size); output.write(packet); output.flush() }
    }
    suspend fun read(input: DataInputStream): ByteArray = withContext(Dispatchers.IO) {
        VokieLog.bt("WIFI_RX_READ_START")
        val length = input.readInt()
        require(length in 1..MAX_PACKET_BYTES) { "Invalid packet length" }
        ByteArray(length).also {
            input.readFully(it)
            VokieLog.bt("WIFI_RX_BYTES length=$length")
        }
    }
}

/** Native Android Wi-Fi Direct transport. It carries opaque PacketV2 bytes only. */
class WifiDirectTransport(private val context: Context, private val scope: CoroutineScope) : PacketTransport {
    override val type = TransportType.WIFI_DIRECT
    private val manager = context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    private val channel = manager?.initialize(context, context.mainLooper, null)
    private val _state = MutableStateFlow(if (manager == null) PacketTransportState.FAILED else PacketTransportState.IDLE)
    override val state = _state.asStateFlow()
    private val _peers = MutableStateFlow<List<WifiPeer>>(emptyList())
    override val peers = _peers.asStateFlow()
    @Volatile var localDeviceAddress: String? = null
        private set
    private val packets = MutableSharedFlow<ByteArray>(extraBufferCapacity = 32)
    /** Called exactly once when a live TCP session ends unexpectedly. */
    var onDisconnected: (() -> Unit)? = null
    private var socket: Socket? = null
    private var server: ServerSocket? = null
    private var ioJob: Job? = null
    private val socketLifecycleLock = Any()
    private var sessionGeneration = 0L
    private var groupFormed = false
    private var socketGeneration: Long? = null
    private val outputLock = Mutex()
    private var receiverRegistered = false
    private var reconnectJob: Job? = null
    private var lastConnectedPeerAddress: String? = null
    private var intentionalDisconnect = false
    private val reconnectPolicy = ReconnectPolicy.WIFI_DIRECT
    init {
        runCatching {
            register()
            requestLocalDeviceInfo()
            refreshPeers()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> refreshPeers()
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> refreshLocalDeviceAddress(intent)
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> requestConnectionInfo()
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> if (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, 0) == WifiP2pManager.WIFI_P2P_STATE_DISABLED) fail("Wi-Fi Direct disabled")
            }
        }
    }

    override suspend fun discover() = withContext(Dispatchers.Main) {
        ensureReady(); register(); requestLocalDeviceInfo(); _state.value = PacketTransportState.DISCOVERING
        manager!!.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                VokieLog.bt("WIFI_DISCOVERY_STARTED")
                refreshPeers()
            }
            override fun onFailure(reason: Int) {
                fail("peer discovery failed ($reason)")
            }
        })
    }

    @SuppressLint("MissingPermission")
    override suspend fun connect(deviceAddress: String) = withContext(Dispatchers.Main) {
        val normalized = deviceAddress.trim().uppercase()
        require(normalized.matches(Regex("^([0-9A-F]{2}:){5}[0-9A-F]{2}$")) && normalized != "02:00:00:00:00:00") {
            "A real discovered Wi-Fi Direct device address is required"
        }
        ensureReady(); register()
        intentionalDisconnect = false
        lastConnectedPeerAddress = normalized
        _state.value = PacketTransportState.CONNECTING
        VokieLog.bt("WIFI_CONNECT_REQUEST address=$normalized")
        manager!!.connect(channel, WifiP2pConfig().apply { normalized.also { this.deviceAddress = it } }, action("Wi-Fi Direct connection failed"))
    }

    override suspend fun send(packet: ByteArray) {
        val currentSocket = synchronized(socketLifecycleLock) { socket }
            ?: throw IOException("Wi-Fi Direct is not connected")
        val output = DataOutputStream(currentSocket.getOutputStream())
        outputLock.withLock {
            VokieLog.bt("WIFI_TX_BYTES_START length=${packet.size}")
            LengthPrefixedFrames.write(output, packet)
            VokieLog.bt("WIFI_TX_FLUSH_COMPLETE length=${packet.size}")
        }
    }

    override suspend fun disconnect() = withContext(Dispatchers.IO) {
        intentionalDisconnect = true
        reconnectJob?.cancel(); reconnectJob = null
        _state.value = PacketTransportState.DISCONNECTING
        stopApplicationSockets(invalidate = true)
        withContext(Dispatchers.Main) { channel?.let { manager?.removeGroup(it, null) } }
        _state.value = PacketTransportState.IDLE
    }

    override fun observePackets(): Flow<ByteArray> = packets.asSharedFlow()

    private fun refreshPeers() {
        manager?.requestPeers(channel) { list: WifiP2pDeviceList ->
            _peers.value = list.deviceList.map { device ->
                VokieLog.bt("WIFI_PEER_DISCOVERED name=${device.deviceName} address=${device.deviceAddress} status=${device.status}")
                WifiPeer(device.deviceAddress, device.deviceName.ifBlank { "Unnamed iTantra device" }, device.status != WifiP2pDevice.UNAVAILABLE)
            }
        }
    }
    @Suppress("DEPRECATION") private fun refreshLocalDeviceAddress(intent: Intent) {
        val device = intent.getParcelableExtra<WifiP2pDevice>(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
        device?.deviceAddress?.takeIf { it.isNotBlank() }?.let {
            localDeviceAddress = it
            VokieLog.bt("WIFI_LOCAL_DEVICE_ADDRESS address=$it")
        }
        requestLocalDeviceInfo()
    }
    private fun requestLocalDeviceInfo() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val wifiManager = manager ?: return
        val wifiChannel = channel ?: return
        wifiManager.requestDeviceInfo(wifiChannel) { device ->
            device?.deviceAddress?.takeIf { it.isNotBlank() }?.let {
                localDeviceAddress = it
                VokieLog.bt("WIFI_LOCAL_DEVICE_INFO address=$it")
            }
        }
    }
    private fun requestConnectionInfo() {
        manager?.requestConnectionInfo(channel) { info: WifiP2pInfo ->
            if (info.groupFormed) {
                val generation = synchronized(socketLifecycleLock) {
                    if (!groupFormed) {
                        groupFormed = true
                        sessionGeneration += 1
                        VokieLog.bt("WIFI_SESSION_CREATED generation=$sessionGeneration")
                    }
                    sessionGeneration
                }
                VokieLog.bt("WIFI_GROUP_FORMED generation=$generation owner=${info.isGroupOwner} ownerAddress=${info.groupOwnerAddress}")
                establishSocket(generation, info.isGroupOwner, info.groupOwnerAddress)
            } else {
                val stale = synchronized(socketLifecycleLock) {
                    // A delayed false callback is common during Android P2P callback
                    // churn. Never tear down a live TCP session from that callback.
                    groupFormed && ioJob?.isActive == true && _state.value == PacketTransportState.CONNECTED
                }
                if (stale) {
                    VokieLog.bt("WIFI_STALE_CALLBACK_IGNORED groupFormed=false generation=${sessionGeneration}")
                } else {
                    scope.launch(Dispatchers.IO) { stopApplicationSockets(invalidate = true) }
                }
            }
        }
    }
    private fun establishSocket(generation: Long, owner: Boolean, address: InetAddress?) {
        // Android may deliver the group callback more than once. Socket creation and
        // ownership are serialized so duplicate callbacks cannot bind port 39721 twice.
        synchronized(socketLifecycleLock) {
            if (ioJob?.isActive == true) return
            if (generation != sessionGeneration || !groupFormed) {
                VokieLog.bt("WIFI_STALE_CALLBACK_IGNORED generation=$generation currentGeneration=$sessionGeneration")
                return
            }
            ioJob = scope.launch(Dispatchers.IO) {
                try {
                    val s = if (owner) {
                        val listening = ServerSocket().apply {
                            reuseAddress = true
                            bind(InetSocketAddress(PORT))
                        }
                        synchronized(socketLifecycleLock) {
                            if (generation != sessionGeneration) {
                                listening.close()
                                return@launch
                            }
                            server = listening
                            socketGeneration = generation
                        }
                        VokieLog.bt("WIFI_SERVER_START generation=$generation port=$PORT")
                        listening.accept()
                    } else {
                        VokieLog.bt("WIFI_CLIENT_CONNECT port=$PORT")
                        connectToGroupOwner(address ?: error("Missing group owner address"))
                    }
                    synchronized(socketLifecycleLock) {
                        if (generation != sessionGeneration || !groupFormed) {
                            s.close()
                            return@launch
                        }
                        socket = s
                        socketGeneration = generation
                    }
                    _state.value = PacketTransportState.CONNECTED
                    if (owner) VokieLog.bt("WIFI_TCP_CONNECTED owner=true") else VokieLog.bt("WIFI_TCP_CONNECTED owner=false")
                    VokieLog.bt("WIFI_TRANSPORT_READY generation=$generation")
                    val input = DataInputStream(s.getInputStream())
                    while (isActive) {
                    val packet = LengthPrefixedFrames.read(input)
                    VokieLog.bt("WIFI_PACKET_DELIVER length=${packet.size}")
                    packets.emit(packet)
                }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Throwable) {
                    if (error !is SocketException) fail(error.message ?: "Wi-Fi Direct socket failed")
                    else VokieLog.bt("WIFI_TCP_DISCONNECTED ${error.message ?: "socket closed"}")
                } finally {
                    val current = synchronized(socketLifecycleLock) {
                        val ownsResources = socketGeneration == generation
                        if (ownsResources) {
                            runCatching { socket?.close() }
                            runCatching { server?.close() }
                            socket = null
                            server = null
                            socketGeneration = null
                        }
                        ownsResources && generation == sessionGeneration
                    }
                    if (current && _state.value == PacketTransportState.CONNECTED) {
                        _state.value = PacketTransportState.FAILED
                        VokieLog.bt("WIFI_TCP_DISCONNECTED generation=$generation")
                        onDisconnected?.invoke()
                        lastConnectedPeerAddress?.let { scheduleReconnect(it, sessionGeneration) }
                    }
                }
            }
        }
    }
    /** The group client can receive its callback just before the owner binds; retry locally. */
    private suspend fun stopApplicationSockets(invalidate: Boolean = false) {
        val job = synchronized(socketLifecycleLock) {
            val oldGeneration = sessionGeneration
            if (invalidate) {
                sessionGeneration += 1
                groupFormed = false
                VokieLog.bt("WIFI_SESSION_INVALIDATED generation=$oldGeneration")
            }
            runCatching { socket?.close() }
            runCatching { server?.close() }
            socket = null
            server = null
            socketGeneration = null
            ioJob?.also { it.cancel() }
        }
        job?.let { if (it !== currentCoroutineContext()[Job]) it.join() }
        synchronized(socketLifecycleLock) { if (ioJob === job) ioJob = null }
        VokieLog.bt("WIFI_SESSION_CLEANUP generation=$sessionGeneration")
    }

    private suspend fun connectToGroupOwner(address: InetAddress): Socket {
        var last: Throwable? = null
        repeat(10) {
            try {
                return Socket().also { it.connect(InetSocketAddress(address, PORT), 1_500) }
            } catch (error: Throwable) {
                last = error
                delay(300)
            }
        }
        throw IOException("Wi-Fi Direct owner socket was not ready", last)
    }

    @SuppressLint("MissingPermission")
    private fun scheduleReconnect(targetAddress: String, triggerGeneration: Long) {
        if (intentionalDisconnect || reconnectJob?.isActive == true) return
        if (manager == null || channel == null) return
        VokieLog.rescue("RECONNECT_STARTED transport=WIFI_DIRECT peer=$targetAddress generation=$triggerGeneration")
        reconnectJob = scope.launch(Dispatchers.IO) {
            repeat(reconnectPolicy.maxAttempts) { attempt ->
                // Abort if a newer session was already established or user disconnected.
                val stale = synchronized(socketLifecycleLock) { sessionGeneration != triggerGeneration || intentionalDisconnect }
                if (stale) {
                    VokieLog.rescue("RECONNECT_CANCELLED transport=WIFI_DIRECT reason=stale_or_intentional attempt=$attempt")
                    return@launch
                }
                // Already reconnected by an incoming connection callback.
                if (_state.value == PacketTransportState.CONNECTED) {
                    VokieLog.rescue("RECONNECT_CANCELLED transport=WIFI_DIRECT reason=already_connected attempt=$attempt")
                    return@launch
                }
                val backoff = reconnectPolicy.delayMs(attempt)
                VokieLog.rescue("RECONNECT_BACKOFF transport=WIFI_DIRECT attempt=$attempt delay=${backoff}ms peer=$targetAddress")
                delay(backoff)
                // Re-check after delay.
                val staleAfter = synchronized(socketLifecycleLock) { sessionGeneration != triggerGeneration || intentionalDisconnect }
                if (staleAfter || _state.value == PacketTransportState.CONNECTED) return@launch
                try {
                    VokieLog.rescue("RECONNECT_ATTEMPT transport=WIFI_DIRECT attempt=$attempt peer=$targetAddress")
                    // Step 1: Re-discover peers.
                    withContext(Dispatchers.Main) {
                        manager!!.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() { VokieLog.rescue("RECONNECT_DISCOVERY_STARTED attempt=$attempt") }
                            override fun onFailure(reason: Int) { VokieLog.rescue("RECONNECT_DISCOVERY_FAILED attempt=$attempt reason=$reason") }
                        })
                    }
                    // Allow discovery time, then check if the target peer appeared.
                    delay(3_000)
                    val found = _peers.value.any { it.address == targetAddress && it.available }
                    if (!found) {
                        VokieLog.rescue("RECONNECT_PEER_NOT_FOUND attempt=$attempt peer=$targetAddress")
                        return@repeat
                    }
                    VokieLog.rescue("RECONNECT_PEER_FOUND attempt=$attempt peer=$targetAddress")
                    // Step 2: Connect.
                    withContext(Dispatchers.Main) {
                        manager!!.connect(channel, WifiP2pConfig().apply { deviceAddress = targetAddress },
                            object : WifiP2pManager.ActionListener {
                                override fun onSuccess() { VokieLog.rescue("RECONNECT_CONNECT_REQUESTED attempt=$attempt") }
                                override fun onFailure(reason: Int) { VokieLog.rescue("RECONNECT_CONNECT_FAILED attempt=$attempt reason=$reason") }
                            })
                    }
                    // The connection-info callback + establishSocket will handle CONNECTED transition.
                    // Wait briefly for the connection to form.
                    delay(5_000)
                    if (_state.value == PacketTransportState.CONNECTED) {
                        VokieLog.rescue("RECONNECT_CONNECTED transport=WIFI_DIRECT attempt=$attempt peer=$targetAddress")
                        return@launch
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Throwable) {
                    VokieLog.rescue("RECONNECT_FAILED transport=WIFI_DIRECT attempt=$attempt error=${e.message}")
                }
            }
            VokieLog.rescue("RECONNECT_EXHAUSTED transport=WIFI_DIRECT peer=$targetAddress attempts=${reconnectPolicy.maxAttempts}")
        }
    }

    private fun ensureReady() { check(manager != null && channel != null) { "Wi-Fi Direct is unavailable" } }
    private fun register() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter().apply {
                addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
                addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        receiverRegistered = true
    }
    private fun fail(message: String) {
        val wasConnected = _state.value == PacketTransportState.CONNECTED
        _state.value = PacketTransportState.FAILED
        VokieLog.bt(message)
        if (wasConnected) onDisconnected?.invoke()
    }
    private fun action(message: String) = object : WifiP2pManager.ActionListener { override fun onSuccess() = Unit; override fun onFailure(reason: Int) = fail("$message ($reason)") }
    private companion object { const val PORT = 39_721 }
}
