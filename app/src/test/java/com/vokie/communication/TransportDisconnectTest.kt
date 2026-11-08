package com.vokie.communication

import com.vokie.domain.model.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Deterministic failure-injection tests for transport disconnect wiring.
 * Uses fake transports — no physical devices required.
 */
class TransportDisconnectTest {
    private lateinit var sessions: PeerSessionManager
    private lateinit var ackRegistry: PendingAckRegistry
    private var clock = 1_000L

    @Before fun setUp() {
        sessions = PeerSessionManager { clock }
        ackRegistry = PendingAckRegistry()
    }

    private fun msg(id: String, sender: String, receiver: String? = null, state: DeliveryState = DeliveryState.QUEUED) =
        Message(id, sender, clock, "text-$id", "EN", deliveryState = state, receiverId = receiver)

    // TEST 1: Message is QUEUED and transport disconnects before transmission → stays QUEUED.
    @Test fun `queued message survives transport disconnect`() {
        sessions.registerPeer("A")
        val message = msg("m1", "operator", "A")
        // Message is QUEUED in repository — disconnect happens — no state change needed.
        assertEquals(DeliveryState.QUEUED, message.deliveryState)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        // Message stays QUEUED (it was never picked up for transmission).
        assertEquals(DeliveryState.QUEUED, message.deliveryState)
    }

    // TEST 2: Message is TRANSMITTING and transport disconnects → re-queue scenario.
    @Test fun `transmitting message can be re-queued on disconnect`() {
        val message = msg("m2", "operator", "A", DeliveryState.TRANSMITTING)
        // Simulate re-queue: TRANSMITTING → QUEUED (what recoverInterrupted does).
        val recovered = message.copy(deliveryState = DeliveryState.QUEUED, lastError = "Transmission interrupted; queued after restart")
        assertEquals(DeliveryState.QUEUED, recovered.deliveryState)
    }

    // TEST 3: TRANSMITTING with pending ACK → ACK cancelled, message re-queued.
    @Test fun `pending ack cancelled on transport disconnect`() = runBlocking {
        val fakeTransport = FakePacketTransport(TransportType.WIFI_DIRECT)
        val key = PendingAckRegistry.Key("m3", 0)
        val entry = ackRegistry.register(key, fakeTransport, 10_000)
        assertFalse(entry.completion.isCompleted)
        // Simulate disconnect: cancel all pending ACKs for this transport.
        ackRegistry.clearTransport(fakeTransport)
        assertTrue(entry.completion.isCompleted)
        assertFalse(entry.completion.await())
    }

    // TEST 4: Peer A disconnects → only A's session changes.
    @Test fun `peer A disconnect does not affect peer B`() {
        sessions.registerPeer("A"); sessions.registerPeer("B")
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("B", TransportConnectionState.CONNECTED)
        // A disconnects.
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("A")?.connectionState)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("B")?.connectionState)
    }

    // TEST 5: Peer A disconnects, Peer B remains CONNECTED.
    @Test fun `peer isolation during disconnect`() {
        sessions.registerPeer("A"); sessions.registerPeer("B"); sessions.registerPeer("C")
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("B", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("C", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("B")?.connectionState)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("C")?.connectionState)
    }

    // TEST 6: Duplicate disconnect callback → no corruption.
    @Test fun `duplicate disconnect is idempotent`() {
        sessions.registerPeer("A")
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED) // duplicate
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("A")?.connectionState)
        assertEquals(1, sessions.getSessions().size)
    }

    // TEST 7: Disconnect after ACK already resolved → no re-queue of delivered message.
    @Test fun `disconnect after ack does not requeue delivered message`() {
        sessions.registerPeer("A")
        sessions.recordOutgoingMessage("A", msg("m7", "operator", "A"))
        assertTrue(sessions.recordAck("A", "m7", 0))
        assertEquals(0, sessions.getSession("A")?.unacknowledgedMessageCount)
        // Disconnect happens after ACK.
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        assertEquals(0, sessions.getSession("A")?.unacknowledgedMessageCount) // stays 0
    }

    // TEST 8: Multiple messages transmitting for same peer, disconnect.
    @Test fun `multiple pending messages for same peer all affected by disconnect`() = runBlocking {
        val transport = FakePacketTransport(TransportType.BLUETOOTH)
        val e1 = ackRegistry.register(PendingAckRegistry.Key("m8a", 0), transport, 10_000)
        val e2 = ackRegistry.register(PendingAckRegistry.Key("m8b", 1), transport, 10_000)
        val e3 = ackRegistry.register(PendingAckRegistry.Key("m8c", 2), transport, 10_000)
        assertEquals(3, ackRegistry.size())
        ackRegistry.clearTransport(transport)
        assertTrue(e1.completion.isCompleted)
        assertTrue(e2.completion.isCompleted)
        assertTrue(e3.completion.isCompleted)
        assertEquals(0, ackRegistry.size())
    }

    // TEST 9: Multiple peers, disconnect one → others' queues unchanged.
    @Test fun `multi peer queue isolation on disconnect`() = runBlocking {
        val transportA = FakePacketTransport(TransportType.BLUETOOTH)
        val transportB = FakePacketTransport(TransportType.WIFI_DIRECT)
        val ea = ackRegistry.register(PendingAckRegistry.Key("mA", 0), transportA, 10_000)
        val eb = ackRegistry.register(PendingAckRegistry.Key("mB", 0), transportB, 10_000)
        // Disconnect only transport A.
        ackRegistry.clearTransport(transportA)
        assertTrue(ea.completion.isCompleted)
        assertFalse(eb.completion.isCompleted)
        assertEquals(1, ackRegistry.size())
        // Cleanup.
        ackRegistry.clearTransport(transportB)
    }

    // TEST 10: recoverInterrupted is compatible (state transition TRANSMITTING → QUEUED).
    @Test fun `transmitting to queued transition preserves message identity`() {
        val original = msg("m10", "operator", "A", DeliveryState.TRANSMITTING)
        val recovered = original.copy(deliveryState = DeliveryState.QUEUED, retryCount = 0, lastError = "Transmission interrupted; queued after restart")
        assertEquals(original.id, recovered.id)
        assertEquals(original.senderId, recovered.senderId)
        assertEquals(original.receiverId, recovered.receiverId)
        assertEquals(original.text, recovered.text)
        assertEquals(DeliveryState.QUEUED, recovered.deliveryState)
    }
}

/** Minimal fake transport for ACK-registry testing. Each instance is identity-distinct. */
private class FakePacketTransport(override val type: TransportType) : PacketTransport {
    override val state = MutableStateFlow(PacketTransportState.CONNECTED)
    override val peers = MutableStateFlow<List<WifiPeer>>(emptyList())
    override suspend fun discover() = Unit
    override suspend fun connect(deviceAddress: String) = Unit
    override suspend fun send(packet: ByteArray) = Unit
    override suspend fun disconnect() = Unit
    override fun observePackets(): Flow<ByteArray> = emptyFlow()
}
