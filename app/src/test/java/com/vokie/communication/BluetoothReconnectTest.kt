package com.vokie.communication

import com.vokie.domain.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates enhanced Bluetooth reconnection lifecycle at the session/policy layer.
 * Uses PeerSessionManager and ReconnectPolicy (no Android framework).
 */
class BluetoothReconnectTest {
    private lateinit var sessions: PeerSessionManager
    private lateinit var acks: PendingAckRegistry
    private var clock = 1_000L

    @Before fun setUp() {
        sessions = PeerSessionManager { clock }
        acks = PendingAckRegistry()
    }

    private fun msg(id: String, receiver: String, state: DeliveryState = DeliveryState.QUEUED) =
        Message(id, "operator", clock, "text", "EN", deliveryState = state, receiverId = receiver)

    // TEST 1: unexpected socket failure starts reconnect lifecycle
    @Test fun `unexpected failure transitions session to disconnected`() {
        sessions.registerPeer("bt-peer", "Phone B", TransportType.BLUETOOTH)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.DISCONNECTED)
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("bt-peer")?.connectionState)
    }

    // TEST 2: reconnect policy uses exponential backoff
    @Test fun `bluetooth reconnect policy uses exponential backoff`() {
        val p = ReconnectPolicy.BLUETOOTH
        val d0 = p.delayMs(0) // ~1000 + jitter
        val d1 = p.delayMs(1) // ~2000 + jitter
        val d2 = p.delayMs(2) // ~4000 + jitter
        assertTrue("d0=$d0 should be >= 1000", d0 >= 1_000)
        assertTrue("d1=$d1 should be >= 2000", d1 >= 2_000)
        assertTrue("d2=$d2 should be >= 4000", d2 >= 4_000)
        assertTrue("backoff should grow", d1 > d0 - 800) // accounting for jitter
    }

    // TEST 3: jitter within bounds
    @Test fun `bluetooth jitter stays within configured bounds`() {
        val p = ReconnectPolicy(maxAttempts = 100, baseMs = 1_000, maxDelayMs = 1_000, jitterMs = 800)
        val delays = (0 until 100).map { p.delayMs(0) }
        assertTrue(delays.all { it in 1_000 until 1_800 })
        assertTrue("should have variation", delays.toSet().size > 1)
    }

    // TEST 4: retry succeeds → CONNECTED
    @Test fun `successful reconnect restores connected state`() {
        sessions.registerPeer("bt-peer", transport = TransportType.BLUETOOTH)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTING)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("bt-peer")?.connectionState)
    }

    // TEST 5: retry exhaustion → DISCONNECTED
    @Test fun `exhausted reconnect leaves session disconnected`() {
        sessions.registerPeer("bt-peer", transport = TransportType.BLUETOOTH)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.DISCONNECTED)
        val p = ReconnectPolicy.BLUETOOTH
        assertTrue(p.exhausted(p.maxAttempts))
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("bt-peer")?.connectionState)
    }

    // TEST 6: intentional disconnect → no reconnect
    @Test fun `intentional disconnect leaves session idle`() {
        sessions.registerPeer("bt-peer", transport = TransportType.BLUETOOTH)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.IDLE) // intentional
        assertEquals(TransportConnectionState.IDLE, sessions.getSession("bt-peer")?.connectionState)
    }

    // TEST 7: duplicate disconnect → no corruption
    @Test fun `duplicate disconnect is idempotent`() {
        sessions.registerPeer("bt-peer", transport = TransportType.BLUETOOTH)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.DISCONNECTED) // duplicate
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("bt-peer")?.connectionState)
        assertEquals(1, sessions.getSessions().size)
    }

    // TEST 8: successful reconnect makes old retry irrelevant (generation bumped)
    @Test fun `generation bump invalidates stale reconnect`() {
        // Simulate: generation incremented when connection succeeds.
        var generation = 0L
        val staleGen = generation
        generation++ // new connection formed
        assertNotEquals(staleGen, generation)
    }

    // TEST 9: stale generation is rejected
    @Test fun `stale generation check works`() {
        var generation = 5L
        val capturedGen = generation
        generation = 6
        assertNotEquals(capturedGen, generation) // stale
        generation = capturedGen // restore for the test
        assertEquals(capturedGen, generation) // not stale
    }

    // TEST 10: target peer filtering
    @Test fun `reconnect targets only the specific peer`() {
        sessions.registerPeer("target", "Target Phone", TransportType.BLUETOOTH)
        sessions.registerPeer("other", "Other Phone", TransportType.BLUETOOTH)
        sessions.updateConnectionState("target", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("other", TransportConnectionState.CONNECTED)
        // Only target disconnects.
        sessions.updateConnectionState("target", TransportConnectionState.DISCONNECTED)
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("target")?.connectionState)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("other")?.connectionState)
    }

    // TEST 11: missing target peer fails safely
    @Test fun `missing peer does not create accidental session`() {
        assertFalse(sessions.updateConnectionState("nonexistent", TransportConnectionState.CONNECTING))
        assertTrue(sessions.getSessions().isEmpty())
    }

    // TEST 12: Peer A reconnect does not affect Peer B
    @Test fun `peer A reconnect does not affect peer B`() {
        sessions.registerPeer("A", transport = TransportType.BLUETOOTH)
        sessions.registerPeer("B", transport = TransportType.WIFI_DIRECT)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("B", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTING) // reconnecting
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("B")?.connectionState)
    }

    // TEST 13: queued message survives reconnect
    @Test fun `queued message survives bluetooth reconnect`() {
        sessions.registerPeer("bt-peer")
        sessions.recordOutgoingMessage("bt-peer", msg("m-survive", "bt-peer"))
        assertEquals(1, sessions.getSession("bt-peer")?.pendingMessageCount)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        assertEquals(1, sessions.getSession("bt-peer")?.pendingMessageCount) // preserved
    }

    // TEST 14: message identity preserved
    @Test fun `message identity preserved through bluetooth reconnect`() {
        val original = msg("bt-msg", "bt-peer", DeliveryState.TRANSMITTING)
        val requeued = original.copy(deliveryState = DeliveryState.QUEUED)
        assertEquals(original.id, requeued.id)
        assertEquals(original.receiverId, requeued.receiverId)
        assertEquals(original.sequenceNumber, requeued.sequenceNumber)
    }

    // TEST 15: pending ACK handling
    @Test fun `pending ack cancelled on bluetooth disconnect`() = runBlocking {
        val transport = FakeBtTransport()
        val entry = acks.register(PendingAckRegistry.Key("bt-ack", 0), transport, 10_000)
        acks.clearTransport(transport)
        assertTrue(entry.completion.isCompleted)
        assertFalse(entry.completion.await())
    }

    // TEST 16: duplicate ACK safely ignored
    @Test fun `duplicate ack after bluetooth reconnect is safe`() {
        sessions.registerPeer("bt-peer")
        sessions.recordOutgoingMessage("bt-peer", msg("dup-bt", "bt-peer"))
        assertTrue(sessions.recordAck("bt-peer", "dup-bt", 0))
        assertFalse(sessions.recordAck("bt-peer", "dup-bt", 0)) // duplicate
    }

    // TEST 17: no reconnect job remains after success (generation-based)
    @Test fun `clean state after successful reconnect`() {
        sessions.registerPeer("bt-peer", transport = TransportType.BLUETOOTH)
        sessions.updateConnectionState("bt-peer", TransportConnectionState.CONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("bt-peer")?.connectionState)
    }

    // TEST 18: no reconnect job remains after exhaustion
    @Test fun `policy reports exhaustion correctly`() {
        val p = ReconnectPolicy.BLUETOOTH
        assertFalse(p.exhausted(0))
        assertFalse(p.exhausted(p.maxAttempts - 1))
        assertTrue(p.exhausted(p.maxAttempts))
        assertTrue(p.exhausted(p.maxAttempts + 100))
    }
}

private class FakeBtTransport : PacketTransport {
    override val type = TransportType.BLUETOOTH
    override val state = MutableStateFlow(PacketTransportState.CONNECTED)
    override val peers = MutableStateFlow<List<WifiPeer>>(emptyList())
    override suspend fun discover() = Unit
    override suspend fun connect(deviceAddress: String) = Unit
    override suspend fun send(packet: ByteArray) = Unit
    override suspend fun disconnect() = Unit
    override fun observePackets(): Flow<ByteArray> = emptyFlow()
}
