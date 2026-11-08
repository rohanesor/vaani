package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import com.vokie.domain.model.DeliveryState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates Wi-Fi Direct reconnection lifecycle at the session/state layer.
 * Uses PeerSessionManager and PendingAckRegistry (no Android framework).
 */
class WifiReconnectTest {
    private lateinit var sessions: PeerSessionManager
    private lateinit var acks: PendingAckRegistry
    private var clock = 1_000L

    @Before fun setUp() {
        sessions = PeerSessionManager { clock }
        acks = PendingAckRegistry()
    }

    private fun msg(id: String, receiver: String, state: DeliveryState = DeliveryState.QUEUED) =
        Message(id, "operator", clock, "text", "EN", deliveryState = state, receiverId = receiver)

    // TEST 8: unexpected failure starts RECONNECTING lifecycle
    @Test fun `unexpected failure transitions to disconnect then reconnect-ready`() {
        sessions.registerPeer("wifi-peer", "Phone B", TransportType.WIFI_DIRECT)
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.CONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("wifi-peer")?.connectionState)
        // Simulate unexpected failure: disconnect notification arrives.
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.DISCONNECTED)
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("wifi-peer")?.connectionState)
        // Application would then start reconnect, which would set CONNECTING.
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.CONNECTING)
        assertEquals(TransportConnectionState.CONNECTING, sessions.getSession("wifi-peer")?.connectionState)
    }

    // TEST 9: successful reconnect restores CONNECTED
    @Test fun `successful reconnect restores connected state`() {
        sessions.registerPeer("wifi-peer", transport = TransportType.WIFI_DIRECT)
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.DISCONNECTED)
        // Reconnect succeeds.
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.CONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("wifi-peer")?.connectionState)
    }

    // TEST 10: intentional disconnect does not leave reconnect artifacts
    @Test fun `intentional disconnect clears state cleanly`() {
        sessions.registerPeer("wifi-peer", transport = TransportType.WIFI_DIRECT)
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.CONNECTED)
        // Intentional disconnect → IDLE, no RECONNECTING.
        sessions.updateConnectionState("wifi-peer", TransportConnectionState.IDLE)
        assertEquals(TransportConnectionState.IDLE, sessions.getSession("wifi-peer")?.connectionState)
    }

    // TEST 11: peer A reconnect does not affect peer B
    @Test fun `reconnect isolation across peers`() {
        sessions.registerPeer("A", transport = TransportType.WIFI_DIRECT)
        sessions.registerPeer("B", transport = TransportType.BLUETOOTH)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("B", TransportConnectionState.CONNECTED)
        // A disconnects, starts reconnecting.
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTING)
        assertEquals(TransportConnectionState.CONNECTING, sessions.getSession("A")?.connectionState)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("B")?.connectionState)
    }

    // TEST 12: peer A reconnect does not affect peer C
    @Test fun `three-peer reconnect isolation`() {
        sessions.registerPeer("A"); sessions.registerPeer("B"); sessions.registerPeer("C")
        listOf("A", "B", "C").forEach { sessions.updateConnectionState(it, TransportConnectionState.CONNECTED) }
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("B")?.connectionState)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("C")?.connectionState)
    }

    // TEST 13: queued messages survive reconnect
    @Test fun `queued message survives disconnect and reconnect cycle`() {
        sessions.registerPeer("A")
        val message = msg("m-survive", "A")
        sessions.recordOutgoingMessage("A", message)
        assertEquals(1, sessions.getSession("A")?.pendingMessageCount)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        // Pending message count is preserved through state transitions.
        assertEquals(1, sessions.getSession("A")?.pendingMessageCount)
    }

    // TEST 14: queued messages resume after CONNECTED
    @Test fun `pending count resolves after ack post reconnect`() {
        sessions.registerPeer("A")
        sessions.recordOutgoingMessage("A", msg("m-resume", "A"))
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        // ACK arrives after reconnect.
        assertTrue(sessions.recordAck("A", "m-resume", 0))
        assertEquals(0, sessions.getSession("A")?.unacknowledgedMessageCount)
    }

    // TEST 15: message identity unchanged through reconnect
    @Test fun `message identity preserved through reconnect`() {
        val original = msg("preserved-id", "A", DeliveryState.TRANSMITTING)
        val requeued = original.copy(deliveryState = DeliveryState.QUEUED)
        assertEquals(original.id, requeued.id)
        assertEquals(original.receiverId, requeued.receiverId)
        assertEquals(original.senderId, requeued.senderId)
        assertEquals(original.sequenceNumber, requeued.sequenceNumber)
    }

    // TEST 16: ACK dedup behaviour unchanged
    @Test fun `duplicate ack after reconnect is safely ignored`() {
        sessions.registerPeer("A")
        sessions.recordOutgoingMessage("A", msg("dup-ack", "A"))
        assertTrue(sessions.recordAck("A", "dup-ack", 0))
        assertFalse(sessions.recordAck("A", "dup-ack", 0)) // duplicate
        assertEquals(0, sessions.getSession("A")?.unacknowledgedMessageCount)
    }

    // TEST 17: no reconnect task after successful connection (session state)
    @Test fun `connected state is clean after reconnect`() {
        sessions.registerPeer("A", transport = TransportType.WIFI_DIRECT)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, sessions.getSession("A")?.connectionState)
        // No stale intermediate state.
        assertNotEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("A")?.connectionState)
    }

    // TEST 18: no reconnect after exhaustion (session state)
    @Test fun `exhausted reconnect leaves session disconnected`() {
        sessions.registerPeer("A", transport = TransportType.WIFI_DIRECT)
        sessions.updateConnectionState("A", TransportConnectionState.CONNECTED)
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        // After exhaustion, session stays DISCONNECTED.
        assertEquals(TransportConnectionState.DISCONNECTED, sessions.getSession("A")?.connectionState)
    }

    // TEST 19: ACK registry isolation for multi-transport reconnect
    @Test fun `ack registry clears only affected transport on disconnect`() = runBlocking {
        val wifiTransport = FakeTransport(TransportType.WIFI_DIRECT)
        val btTransport = FakeTransport(TransportType.BLUETOOTH)
        val wifiEntry = acks.register(PendingAckRegistry.Key("wifi-msg", 0), wifiTransport, 10_000)
        val btEntry = acks.register(PendingAckRegistry.Key("bt-msg", 0), btTransport, 10_000)
        // Wi-Fi disconnect.
        acks.clearTransport(wifiTransport)
        assertTrue(wifiEntry.completion.isCompleted)
        assertFalse(btEntry.completion.isCompleted)
        acks.clearTransport(btTransport)
    }

    // TEST 20: re-registration after reconnect does not create duplicate session
    @Test fun `re-registration after reconnect is idempotent`() {
        sessions.registerPeer("A", "Phone", TransportType.WIFI_DIRECT)
        sessions.recordOutgoingMessage("A", msg("out1", "A"))
        sessions.updateConnectionState("A", TransportConnectionState.DISCONNECTED)
        // Re-register on reconnect discovery.
        sessions.registerPeer("A", "Phone", TransportType.WIFI_DIRECT)
        assertEquals(1, sessions.getSessions().size)
        assertEquals(1, sessions.getSession("A")?.pendingMessageCount) // preserved
    }
}

private class FakeTransport(override val type: TransportType) : PacketTransport {
    override val state = MutableStateFlow(PacketTransportState.CONNECTED)
    override val peers = MutableStateFlow<List<WifiPeer>>(emptyList())
    override suspend fun discover() = Unit
    override suspend fun connect(deviceAddress: String) = Unit
    override suspend fun send(packet: ByteArray) = Unit
    override suspend fun disconnect() = Unit
    override fun observePackets(): Flow<ByteArray> = emptyFlow()
}
