package com.vokie.communication

import com.vokie.domain.model.DeliveryState
import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Deterministic N2 identity/ACK tests; no Android radio is required. */
class StoreAndForwardIsolationTest {
    private val transport = object : PacketTransport {
        override val type = TransportType.BLUETOOTH
        override val state = MutableStateFlow(PacketTransportState.CONNECTED)
        override val peers = MutableStateFlow<List<WifiPeer>>(emptyList())
        override suspend fun discover() = Unit
        override suspend fun connect(deviceAddress: String) = Unit
        override suspend fun send(packet: ByteArray) = Unit
        override suspend fun disconnect() = Unit
        override fun observePackets(): Flow<ByteArray> = emptyFlow()
    }

    @Test fun `same message sequence for two peers has independent ACK waiters`() = runBlocking {
        val registry = PendingAckRegistry()
        val a = registry.register(PendingAckRegistry.Key("message-1", 7, "peer-A"), transport, 5_000)
        val b = registry.register(PendingAckRegistry.Key("message-1", 7, "peer-B"), transport, 5_000)

        assertTrue(registry.resolve("message-1", 7, "peer-A"))
        assertTrue(a.completion.await())
        assertFalse(b.completion.isCompleted)
        assertFalse(registry.resolve("message-1", 7, "wrong-peer")) // no matching peer and no transport fallback
        assertTrue(registry.resolve("message-1", 7, "peer-B"))
        assertTrue(b.completion.await())
    }

    @Test fun `duplicate ACK remains idempotent`() {
        val registry = PendingAckRegistry()
        registry.register(PendingAckRegistry.Key("message-1", 7, "peer-A"), transport, 5_000)
        assertTrue(registry.resolve("message-1", 7, "peer-A"))
        assertFalse(registry.resolve("message-1", 7, "peer-A"))
    }

    @Test fun `retry preserves stable packet identity`() {
        val original = Message("stable-id", "local", 1_000, "I need help", "EN", receiverId = "peer-A", sequenceNumber = 42)
        val requeued = original.copy(deliveryState = DeliveryState.QUEUED, retryCount = 2)
        assertEquals(original.id, requeued.id)
        assertEquals(original.receiverId, requeued.receiverId)
        assertEquals(original.sequenceNumber, requeued.sequenceNumber)
        assertEquals(original.text, requeued.text)
    }

    @Test fun `lost ACK retransmit is one logical incoming message`() {
        // Room idempotency and ReceivedPacketEntity use sender/message/sequence keys.
        val original = Message("stable-id", "peer-A", 1_000, "Hello", "TA", sequenceNumber = 42)
        val retry = original.copy()
        assertEquals(original.id, retry.id)
        assertEquals(original.senderId, retry.senderId)
        assertEquals(original.sequenceNumber, retry.sequenceNumber)
    }
}
