package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.DeliveryState
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests ensuring outbound messages are correctly routed to their intended peer.
 * 
 * Validates:
 * - receiverId is set correctly
 * - Messages are tracked per-peer
 * - ACKs only clear the correct peer's pending state
 */
class OutboundPeerRoutingTest {
    private lateinit var mgr: PeerSessionManager
    private var clock = 1_000L
    private val operator = "rescue-operator"
    
    private val civA = "CIV-001"
    private val civB = "CIV-002"
    private val civC = "CIV-003"

    @Before
    fun setUp() {
        mgr = PeerSessionManager { clock }
    }

    private fun outbound(id: String, receiver: String, seq: Long = 0, ack: Boolean = true) =
        Message(id, operator, clock, "reply-$id to $receiver", "EN", 
            receiverId = receiver, sequenceNumber = seq, requiresAck = ack)

    // ========== TEST GROUP 1: Message Routing ==========

    @Test
    fun `01 - outbound message to civA has receiverId = civA`() {
        mgr.registerPeer(civA, "Alpha")
        
        val msg = outbound("msg-1", civA)
        
        assertEquals(civA, msg.receiverId)
        assertTrue(mgr.recordOutgoingMessage(civA, msg))
    }

    @Test
    fun `02 - outbound message to wrong peer is rejected`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        val msg = outbound("msg-1", civA)
        
        // Try to record it for civB - should fail
        assertFalse(mgr.recordOutgoingMessage(civB, msg))
        
        // civB should have no pending messages
        assertEquals(0, mgr.getSession(civB)?.pendingMessageCount)
    }

    @Test
    fun `03 - pending count isolated per peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        mgr.recordOutgoingMessage(civA, outbound("a1", civA, seq = 1))
        mgr.recordOutgoingMessage(civA, outbound("a2", civA, seq = 2))
        mgr.recordOutgoingMessage(civB, outbound("b1", civB, seq = 1))
        
        assertEquals(2, mgr.getSession(civA)?.pendingMessageCount)
        assertEquals(1, mgr.getSession(civB)?.pendingMessageCount)
    }

    @Test
    fun `04 - unacknowledged count isolated per peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        mgr.recordOutgoingMessage(civA, outbound("a1", civA, seq = 1))
        mgr.recordOutgoingMessage(civA, outbound("a2", civA, seq = 2))
        mgr.recordOutgoingMessage(civB, outbound("b1", civB, seq = 1))
        
        assertEquals(2, mgr.getSession(civA)?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession(civB)?.unacknowledgedMessageCount)
    }

    // ========== TEST GROUP 2: ACK Correlation ==========

    @Test
    fun `05 - ACK for civA only clears civA pending`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        mgr.recordOutgoingMessage(civA, outbound("msg-x", civA, seq = 1))
        mgr.recordOutgoingMessage(civB, outbound("msg-x", civB, seq = 1))
        
        // ACK from civA
        assertTrue(mgr.recordAck(civA, "msg-x", 1))
        
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession(civB)?.unacknowledgedMessageCount)
    }

    @Test
    fun `06 - ACK with wrong sequence number is rejected`() {
        mgr.registerPeer(civA)
        
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, seq = 5))
        
        // Wrong sequence
        assertFalse(mgr.recordAck(civA, "msg-1", 3))
        assertEquals(1, mgr.getSession(civA)?.unacknowledgedMessageCount)
        
        // Correct sequence
        assertTrue(mgr.recordAck(civA, "msg-1", 5))
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }

    @Test
    fun `07 - ACK for unknown peer is rejected`() {
        mgr.registerPeer(civA)
        
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, seq = 1))
        
        assertFalse(mgr.recordAck("unknown-peer", "msg-1", 1))
        assertEquals(1, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }

    @Test
    fun `08 - duplicate ACK is rejected`() {
        mgr.registerPeer(civA)
        
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, seq = 1))
        
        assertTrue(mgr.recordAck(civA, "msg-1", 1))
        assertFalse(mgr.recordAck(civA, "msg-1", 1)) // duplicate
        
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }

    // ========== TEST GROUP 3: Non-ACK Messages ==========

    @Test
    fun `09 - message without requiresAck does not increment pending`() {
        mgr.registerPeer(civA)
        
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, ack = false))
        
        assertEquals(0, mgr.getSession(civA)?.pendingMessageCount)
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }

    @Test
    fun `10 - mix of ACK and non-ACK messages`() {
        mgr.registerPeer(civA)
        
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, seq = 1, ack = true))
        mgr.recordOutgoingMessage(civA, outbound("msg-2", civA, seq = 2, ack = false))
        mgr.recordOutgoingMessage(civA, outbound("msg-3", civA, seq = 3, ack = true))
        
        assertEquals(2, mgr.getSession(civA)?.pendingMessageCount) // only ACK messages
        assertEquals(2, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }

    // ========== TEST GROUP 4: Multi-Peer Broadcast Scenario ==========

    @Test
    fun `11 - broadcast to all peers tracks independently`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        
        // Operator sends same content to all peers
        mgr.recordOutgoingMessage(civA, outbound("broadcast-a", civA, seq = 1))
        mgr.recordOutgoingMessage(civB, outbound("broadcast-b", civB, seq = 1))
        mgr.recordOutgoingMessage(civC, outbound("broadcast-c", civC, seq = 1))
        
        assertEquals(1, mgr.getSession(civA)?.pendingMessageCount)
        assertEquals(1, mgr.getSession(civB)?.pendingMessageCount)
        assertEquals(1, mgr.getSession(civC)?.pendingMessageCount)
        
        // ACKs arrive in different order
        mgr.recordAck(civC, "broadcast-c", 1)
        assertEquals(1, mgr.getSession(civA)?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession(civB)?.unacknowledgedMessageCount)
        assertEquals(0, mgr.getSession(civC)?.unacknowledgedMessageCount)
        
        mgr.recordAck(civA, "broadcast-a", 1)
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession(civB)?.unacknowledgedMessageCount)
        assertEquals(0, mgr.getSession(civC)?.unacknowledgedMessageCount)
    }

    @Test
    fun `12 - outbound message updates lastMessageTimestamp`() {
        mgr.registerPeer(civA)
        
        clock = 5_000
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA))
        
        assertEquals(5_000L, mgr.getSession(civA)?.lastMessageTimestamp)
        
        clock = 10_000
        mgr.recordOutgoingMessage(civA, outbound("msg-2", civA))
        
        assertEquals(10_000L, mgr.getSession(civA)?.lastMessageTimestamp)
    }

    @Test
    fun `13 - outbound message does not change sourceLanguage`() {
        mgr.registerPeer(civA)
        
        // Incoming message sets source language
        mgr.recordIncomingMessage(civA, Message("in-1", civA, clock, "Tamil text", "TA"))
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        
        // Outgoing message should not change it
        mgr.recordOutgoingMessage(civA, outbound("out-1", civA))
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
    }

    @Test
    fun `14 - outbound message does not change priority`() {
        mgr.registerPeer(civA)
        
        // Incoming message sets priority
        mgr.recordIncomingMessage(civA, Message("in-1", civA, clock, "SOS", "TA", priority = 200))
        assertEquals(200, mgr.getSession(civA)?.priority)
        
        // Outgoing message should not change it
        val outMsg = Message("out-1", operator, clock, "Help coming", "EN", receiverId = civA, priority = 0)
        mgr.recordOutgoingMessage(civA, outMsg)
        assertEquals(200, mgr.getSession(civA)?.priority)
    }

    // ========== TEST GROUP 5: Session Lifecycle ==========

    @Test
    fun `15 - pending state survives disconnect`() {
        mgr.registerPeer(civA)
        mgr.updateConnectionState(civA, TransportConnectionState.CONNECTED)
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, seq = 1))
        
        mgr.updateConnectionState(civA, TransportConnectionState.DISCONNECTED)
        
        assertEquals(1, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }

    @Test
    fun `16 - pending state survives re-registration`() {
        mgr.registerPeer(civA)
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, seq = 1))
        
        // Re-register (simulating reconnect)
        mgr.registerPeer(civA, "Alpha Updated", TransportType.WIFI_DIRECT)
        
        assertEquals(1, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }

    @Test
    fun `17 - removal clears pending state`() {
        mgr.registerPeer(civA)
        mgr.recordOutgoingMessage(civA, outbound("msg-1", civA, seq = 1))
        
        mgr.removePeer(civA)
        
        // Re-register
        mgr.registerPeer(civA)
        
        assertEquals(0, mgr.getSession(civA)?.pendingMessageCount)
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
    }
}
