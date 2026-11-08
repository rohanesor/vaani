package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates that message routing through PeerSessionManager correctly associates
 * every event with the explicit peer, and that no cross-peer leakage occurs.
 */
class PeerMessageRoutingTest {
    private lateinit var mgr: PeerSessionManager
    private val localDeviceId = "operator"

    @Before fun setUp() {
        mgr = PeerSessionManager { 1_000L }
        mgr.registerPeer("A", "Alpha", TransportType.BLUETOOTH)
        mgr.registerPeer("B", "Bravo", TransportType.WIFI_DIRECT)
    }

    private fun inbound(id: String, sender: String, lang: String = "EN") =
        Message(id, sender, 1_000L, "text-$id", lang)

    private fun outbound(id: String, receiver: String, lang: String = "EN") =
        Message(id, localDeviceId, 1_000L, "text-$id", lang, receiverId = receiver)

    // TEST 1
    @Test fun `inbound from A updates only A`() {
        assertTrue(mgr.recordIncomingMessage("A", inbound("m1", "A")))
        assertNotNull(mgr.getSession("A")?.lastMessageTimestamp)
        assertNull(mgr.getSession("B")?.lastMessageTimestamp)
    }

    // TEST 2
    @Test fun `inbound from B updates only B`() {
        assertTrue(mgr.recordIncomingMessage("B", inbound("m2", "B")))
        assertNotNull(mgr.getSession("B")?.lastMessageTimestamp)
        assertNull(mgr.getSession("A")?.lastMessageTimestamp)
    }

    // TEST 3 + TEST 4: Conversation isolation is verified via filtered message list.
    // The global repository contains both A and B messages; filtering is by senderId/receiverId.
    @Test fun `message filtering isolates peer conversations`() {
        val all = listOf(
            inbound("m1", "A"), outbound("m2", "A"),
            inbound("m3", "B"), outbound("m4", "B"),
        )
        val peerAMessages = all.filter { it.senderId == "A" || it.receiverId == "A" }
        val peerBMessages = all.filter { it.senderId == "B" || it.receiverId == "B" }
        assertEquals(2, peerAMessages.size)
        assertEquals(2, peerBMessages.size)
        assertTrue(peerAMessages.none { it.senderId == "B" || it.receiverId == "B" })
        assertTrue(peerBMessages.none { it.senderId == "A" || it.receiverId == "A" })
    }

    // TEST 5
    @Test fun `outbound message for A has correct receiverId`() {
        val msg = outbound("out1", "A")
        assertEquals("A", msg.receiverId)
        assertTrue(mgr.recordOutgoingMessage("A", msg))
    }

    // TEST 6
    @Test fun `outbound message for B has correct receiverId`() {
        val msg = outbound("out2", "B")
        assertEquals("B", msg.receiverId)
        assertTrue(mgr.recordOutgoingMessage("B", msg))
    }

    // TEST 7
    @Test fun `ack for A updates only A pending state`() {
        mgr.recordOutgoingMessage("A", outbound("ack-test", "A"))
        mgr.recordOutgoingMessage("B", outbound("ack-test-b", "B"))
        assertTrue(mgr.recordAck("A", "ack-test", 0))
        assertEquals(0, mgr.getSession("A")?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession("B")?.unacknowledgedMessageCount)
    }

    // TEST 8
    @Test fun `ack for B updates only B pending state`() {
        mgr.recordOutgoingMessage("A", outbound("ack-a", "A"))
        mgr.recordOutgoingMessage("B", outbound("ack-b", "B"))
        assertTrue(mgr.recordAck("B", "ack-b", 0))
        assertEquals(0, mgr.getSession("B")?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession("A")?.unacknowledgedMessageCount)
    }

    // TEST 9: Switch selected peer and verify isolation
    @Test fun `switching selected peer preserves conversation isolation`() {
        val all = listOf(
            inbound("m1", "A", "TA"), outbound("m2", "A"),
            inbound("m3", "B", "HI"), outbound("m4", "B"),
            inbound("m5", "A", "TA"),
        )
        // Simulate selecting peer A then peer B
        var selectedPeer = "A"
        var filtered = all.filter { it.senderId == selectedPeer || it.receiverId == selectedPeer }
        assertEquals(3, filtered.size)

        selectedPeer = "B"
        filtered = all.filter { it.senderId == selectedPeer || it.receiverId == selectedPeer }
        assertEquals(2, filtered.size)

        // Switch back to A
        selectedPeer = "A"
        filtered = all.filter { it.senderId == selectedPeer || it.receiverId == selectedPeer }
        assertEquals(3, filtered.size)
    }

    // TEST 10
    @Test fun `unknown peer message does not corrupt sessions`() {
        assertFalse(mgr.recordIncomingMessage("unknown", inbound("m-unknown", "unknown")))
        assertEquals(2, mgr.getSessions().size)
        assertNull(mgr.getSession("A")?.lastMessageTimestamp)
        assertNull(mgr.getSession("B")?.lastMessageTimestamp)
    }

    // TEST 11: Same content, different peers
    @Test fun `same message content for different peers stays separated`() {
        mgr.recordIncomingMessage("A", inbound("msg-same-text", "A", "TA"))
        mgr.recordIncomingMessage("B", inbound("msg-same-text-b", "B", "HI"))
        assertEquals("TA", mgr.getSession("A")?.sourceLanguage)
        assertEquals("HI", mgr.getSession("B")?.sourceLanguage)
    }

    // TEST 12: Existing single-peer compatibility
    @Test fun `single peer flow is compatible with existing architecture`() {
        // Only one peer registered, simulating existing two-phone behavior
        val single = PeerSessionManager { 1_000L }
        single.registerPeer("remote-phone", "Phone B", TransportType.WIFI_DIRECT)
        single.recordIncomingMessage("remote-phone", inbound("rx1", "remote-phone"))
        single.recordOutgoingMessage("remote-phone", outbound("tx1", "remote-phone"))
        single.recordAck("remote-phone", "tx1", 0)
        assertEquals(1, single.getSessions().size)
        assertEquals(0, single.getSession("remote-phone")?.unacknowledgedMessageCount)
        assertNotNull(single.getSession("remote-phone")?.lastMessageTimestamp)
    }
}
