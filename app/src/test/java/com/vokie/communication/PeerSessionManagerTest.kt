package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Test

class PeerSessionManagerTest {
    private var clock = 1_000L
    private val manager = PeerSessionManager { clock }

    private fun message(id: String, sender: String, receiver: String? = null, ack: Boolean = true, time: Long = clock) =
        Message(id, sender, time, "message-$id", "EN", receiverId = receiver, requiresAck = ack)

    @Test fun `register creates a session`() {
        assertNotNull(manager.registerPeer("A"))
        assertEquals("A", manager.getSession("A")?.peerId)
    }

    @Test fun `two peers remain independent`() {
        manager.registerPeer("A", "Alpha", TransportType.WIFI_DIRECT)
        manager.registerPeer("B", "Bravo", TransportType.BLUETOOTH)
        assertEquals(setOf("A", "B"), manager.getSessions().map { it.peerId }.toSet())
        assertEquals(TransportType.WIFI_DIRECT, manager.getSession("A")?.transport)
        assertEquals(TransportType.BLUETOOTH, manager.getSession("B")?.transport)
    }

    @Test fun `incoming messages update only their sender`() {
        manager.registerPeer("A"); manager.registerPeer("B")
        assertTrue(manager.recordIncomingMessage("A", message("a", "A")))
        assertNotNull(manager.getSession("A")?.lastMessageTimestamp)
        assertNull(manager.getSession("B")?.lastMessageTimestamp)
        assertTrue(manager.recordIncomingMessage("B", message("b", "B")))
        assertEquals("EN", manager.getSession("B")?.sourceLanguage)
    }

    @Test fun `outgoing message updates only addressed peer`() {
        manager.registerPeer("A"); manager.registerPeer("B")
        assertTrue(manager.recordOutgoingMessage("A", message("a", "operator", "A")))
        assertEquals(1, manager.getSession("A")?.unacknowledgedMessageCount)
        assertEquals(0, manager.getSession("B")?.unacknowledgedMessageCount)
    }

    @Test fun `connection state is peer scoped`() {
        manager.registerPeer("A"); manager.registerPeer("B")
        assertTrue(manager.updateConnectionState("A", TransportConnectionState.CONNECTED))
        assertEquals(TransportConnectionState.CONNECTED, manager.getSession("A")?.connectionState)
        assertEquals(TransportConnectionState.IDLE, manager.getSession("B")?.connectionState)
    }

    @Test fun `ack changes only the owning peer`() {
        manager.registerPeer("A"); manager.registerPeer("B")
        manager.recordOutgoingMessage("A", message("same", "operator", "A"))
        manager.recordOutgoingMessage("B", message("same", "operator", "B"))
        assertTrue(manager.recordAck("A", "same", 0))
        assertEquals(0, manager.getSession("A")?.unacknowledgedMessageCount)
        assertEquals(1, manager.getSession("B")?.unacknowledgedMessageCount)
        assertFalse(manager.recordAck("A", "same", 0))
    }

    @Test fun `unknown peer events are ignored`() {
        val m = message("x", "unknown", "unknown")
        assertFalse(manager.updateConnectionState("unknown", TransportConnectionState.CONNECTED))
        assertFalse(manager.recordIncomingMessage("unknown", m))
        assertFalse(manager.recordOutgoingMessage("unknown", m))
        assertFalse(manager.recordAck("unknown", "x", 0))
        assertTrue(manager.getSessions().isEmpty())
    }

    @Test fun `removal preserves other peers and re registration is clean`() {
        manager.registerPeer("A"); manager.registerPeer("B")
        manager.recordOutgoingMessage("A", message("a", "operator", "A"))
        assertTrue(manager.removePeer("A"))
        assertNotNull(manager.getSession("B"))
        assertNull(manager.getSession("A"))
        manager.registerPeer("A")
        assertEquals(0, manager.getSession("A")?.unacknowledgedMessageCount)
    }

    @Test fun `interleaved events remain isolated`() {
        manager.registerPeer("A"); manager.registerPeer("B"); manager.registerPeer("C")
        manager.recordIncomingMessage("A", message("a", "A"))
        manager.recordIncomingMessage("B", message("b", "B"))
        manager.recordOutgoingMessage("A", message("out-a", "operator", "A"))
        manager.updateConnectionState("C", TransportConnectionState.CONNECTING)
        manager.updateConnectionState("B", TransportConnectionState.DISCONNECTED)
        manager.recordAck("A", "out-a", 0)
        manager.recordIncomingMessage("A", message("a2", "A", time = 2_000L))
        assertEquals(2_000L, manager.getSession("A")?.lastMessageTimestamp)
        assertEquals(TransportConnectionState.DISCONNECTED, manager.getSession("B")?.connectionState)
        assertEquals(TransportConnectionState.CONNECTING, manager.getSession("C")?.connectionState)
        assertEquals(0, manager.getSession("A")?.unacknowledgedMessageCount)
    }
}
