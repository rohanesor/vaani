package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests ensuring inbound messages are correctly associated with their sender peer.
 * 
 * Validates:
 * - senderId determines the owning peer
 * - Session metadata (language, priority) is updated from incoming messages
 * - Unread counts increment correctly
 * - No cross-peer message association
 */
class InboundPeerRoutingTest {
    private lateinit var mgr: PeerSessionManager
    private var clock = 1_000L
    
    private val civA = "CIV-001"
    private val civB = "CIV-002"
    private val civC = "CIV-003"

    @Before
    fun setUp() {
        mgr = PeerSessionManager { clock }
    }

    private fun inbound(
        id: String, 
        sender: String, 
        lang: String = "EN", 
        priority: Int = 0,
        type: MessageType = MessageType.TEXT,
        text: String = "Message $id from $sender"
    ) = Message(id, sender, clock, text, lang, messageType = type, priority = priority)

    // ========== TEST GROUP 1: Basic Routing ==========

    @Test
    fun `01 - inbound message from civA updates only civA`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        assertTrue(mgr.recordIncomingMessage(civA, inbound("m1", civA)))
        
        assertNotNull(mgr.getSession(civA)?.lastMessageTimestamp)
        assertNull(mgr.getSession(civB)?.lastMessageTimestamp)
    }

    @Test
    fun `02 - inbound message to wrong peer is rejected`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        val msg = inbound("m1", civA)
        
        // Try to record it for civB - should fail (senderId != peerId)
        assertFalse(mgr.recordIncomingMessage(civB, msg))
        
        assertNull(mgr.getSession(civB)?.lastMessageTimestamp)
        assertNull(mgr.getSession(civA)?.lastMessageTimestamp)
    }

    @Test
    fun `03 - inbound from unregistered peer is rejected`() {
        mgr.registerPeer(civA)
        
        assertFalse(mgr.recordIncomingMessage("unknown", inbound("m1", "unknown")))
        
        assertEquals(1, mgr.getSessions().size)
        assertNull(mgr.getSession("unknown"))
    }

    // ========== TEST GROUP 2: Unread Count ==========

    @Test
    fun `04 - inbound increments unread count`() {
        mgr.registerPeer(civA)
        assertEquals(0, mgr.getSession(civA)?.unreadCount)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA))
        assertEquals(1, mgr.getSession(civA)?.unreadCount)
        
        mgr.recordIncomingMessage(civA, inbound("m2", civA))
        assertEquals(2, mgr.getSession(civA)?.unreadCount)
    }

    @Test
    fun `05 - unread count isolated per peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        
        mgr.recordIncomingMessage(civA, inbound("a1", civA))
        mgr.recordIncomingMessage(civA, inbound("a2", civA))
        mgr.recordIncomingMessage(civA, inbound("a3", civA))
        mgr.recordIncomingMessage(civB, inbound("b1", civB))
        
        assertEquals(3, mgr.getSession(civA)?.unreadCount)
        assertEquals(1, mgr.getSession(civB)?.unreadCount)
        assertEquals(0, mgr.getSession(civC)?.unreadCount)
    }

    @Test
    fun `06 - total unread aggregates correctly`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        mgr.recordIncomingMessage(civA, inbound("a1", civA))
        mgr.recordIncomingMessage(civA, inbound("a2", civA))
        mgr.recordIncomingMessage(civB, inbound("b1", civB))
        
        assertEquals(3, mgr.totalUnreadCount())
    }

    @Test
    fun `07 - markAsRead clears only that peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        mgr.recordIncomingMessage(civA, inbound("a1", civA))
        mgr.recordIncomingMessage(civA, inbound("a2", civA))
        mgr.recordIncomingMessage(civB, inbound("b1", civB))
        
        mgr.markAsRead(civA)
        
        assertEquals(0, mgr.getSession(civA)?.unreadCount)
        assertEquals(1, mgr.getSession(civB)?.unreadCount)
        assertEquals(1, mgr.totalUnreadCount())
    }

    @Test
    fun `08 - incrementUnread=false does not increment`() {
        mgr.registerPeer(civA)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA), incrementUnread = false)
        
        assertEquals(0, mgr.getSession(civA)?.unreadCount)
    }

    // ========== TEST GROUP 3: Language Metadata ==========

    @Test
    fun `09 - inbound sets source language`() {
        mgr.registerPeer(civA)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA, lang = "TA"))
        
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
    }

    @Test
    fun `10 - source language isolated per peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        
        mgr.recordIncomingMessage(civA, inbound("a1", civA, lang = "TA"))
        mgr.recordIncomingMessage(civB, inbound("b1", civB, lang = "HI"))
        mgr.recordIncomingMessage(civC, inbound("c1", civC, lang = "EN"))
        
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        assertEquals("HI", mgr.getSession(civB)?.sourceLanguage)
        assertEquals("EN", mgr.getSession(civC)?.sourceLanguage)
    }

    @Test
    fun `11 - source language updates with newer message`() {
        mgr.registerPeer(civA)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA, lang = "TA"))
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        
        mgr.recordIncomingMessage(civA, inbound("m2", civA, lang = "HI"))
        assertEquals("HI", mgr.getSession(civA)?.sourceLanguage)
    }

    // ========== TEST GROUP 4: Priority ==========

    @Test
    fun `12 - inbound sets priority`() {
        mgr.registerPeer(civA)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA, priority = 150))
        
        assertEquals(150, mgr.getSession(civA)?.priority)
    }

    @Test
    fun `13 - priority isolated per peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        mgr.recordIncomingMessage(civA, inbound("a1", civA, priority = 200))
        mgr.recordIncomingMessage(civB, inbound("b1", civB, priority = 50))
        
        assertEquals(200, mgr.getSession(civA)?.priority)
        assertEquals(50, mgr.getSession(civB)?.priority)
    }

    @Test
    fun `14 - SOS message type with emergency priority`() {
        mgr.registerPeer(civA)
        
        mgr.recordIncomingMessage(civA, inbound("sos1", civA, priority = 200, type = MessageType.SOS))
        
        assertEquals(200, mgr.getSession(civA)?.priority)
    }

    // ========== TEST GROUP 5: Message Preview ==========

    @Test
    fun `15 - inbound sets lastMessagePreview`() {
        mgr.registerPeer(civA)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA, text = "Need water urgently"))
        
        assertEquals("Need water urgently", mgr.getSession(civA)?.lastMessagePreview)
    }

    @Test
    fun `16 - preview truncated to 100 characters`() {
        mgr.registerPeer(civA)
        
        val longText = "A".repeat(200)
        mgr.recordIncomingMessage(civA, inbound("m1", civA, text = longText))
        
        assertEquals(100, mgr.getSession(civA)?.lastMessagePreview?.length)
    }

    @Test
    fun `17 - preview isolated per peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        mgr.recordIncomingMessage(civA, inbound("a1", civA, text = "Alpha message"))
        mgr.recordIncomingMessage(civB, inbound("b1", civB, text = "Bravo message"))
        
        assertEquals("Alpha message", mgr.getSession(civA)?.lastMessagePreview)
        assertEquals("Bravo message", mgr.getSession(civB)?.lastMessagePreview)
    }

    // ========== TEST GROUP 6: Timestamp ==========

    @Test
    fun `18 - inbound updates lastMessageTimestamp`() {
        mgr.registerPeer(civA)
        
        clock = 5_000
        mgr.recordIncomingMessage(civA, inbound("m1", civA))
        
        assertEquals(5_000L, mgr.getSession(civA)?.lastMessageTimestamp)
    }

    @Test
    fun `19 - timestamp uses max of existing and new`() {
        mgr.registerPeer(civA)
        
        clock = 10_000
        mgr.recordIncomingMessage(civA, inbound("m1", civA))
        
        // Earlier message arrives late
        clock = 8_000
        val oldMsg = Message("m2", civA, 8_000, "Old message", "EN")
        mgr.recordIncomingMessage(civA, oldMsg)
        
        // Timestamp should remain 10_000 (the max)
        assertEquals(10_000L, mgr.getSession(civA)?.lastMessageTimestamp)
    }

    // ========== TEST GROUP 7: Interleaved Scenarios ==========

    @Test
    fun `20 - rapid interleaved messages from multiple peers`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        
        // Simulate rapid message arrival
        clock = 1_001; mgr.recordIncomingMessage(civA, inbound("a1", civA, "TA", 100))
        clock = 1_002; mgr.recordIncomingMessage(civB, inbound("b1", civB, "HI", 50))
        clock = 1_003; mgr.recordIncomingMessage(civA, inbound("a2", civA, "TA", 150))
        clock = 1_004; mgr.recordIncomingMessage(civC, inbound("c1", civC, "EN", 0))
        clock = 1_005; mgr.recordIncomingMessage(civB, inbound("b2", civB, "HI", 200))
        clock = 1_006; mgr.recordIncomingMessage(civA, inbound("a3", civA, "TA", 150))
        
        // Verify final state
        assertEquals(3, mgr.getSession(civA)?.unreadCount)
        assertEquals(2, mgr.getSession(civB)?.unreadCount)
        assertEquals(1, mgr.getSession(civC)?.unreadCount)
        
        assertEquals(150, mgr.getSession(civA)?.priority)
        assertEquals(200, mgr.getSession(civB)?.priority)
        assertEquals(0, mgr.getSession(civC)?.priority)
        
        assertEquals(6, mgr.totalUnreadCount())
    }

    @Test
    fun `21 - markAsRead during message arrival`() {
        mgr.registerPeer(civA)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA))
        mgr.recordIncomingMessage(civA, inbound("m2", civA))
        assertEquals(2, mgr.getSession(civA)?.unreadCount)
        
        mgr.markAsRead(civA)
        assertEquals(0, mgr.getSession(civA)?.unreadCount)
        
        mgr.recordIncomingMessage(civA, inbound("m3", civA))
        assertEquals(1, mgr.getSession(civA)?.unreadCount)
    }

    @Test
    fun `22 - session state preserved after disconnect`() {
        mgr.registerPeer(civA)
        mgr.updateConnectionState(civA, TransportConnectionState.CONNECTED)
        
        mgr.recordIncomingMessage(civA, inbound("m1", civA, "TA", 100, text = "Help needed"))
        mgr.recordIncomingMessage(civA, inbound("m2", civA, "TA", 100, text = "Second message"))
        
        mgr.updateConnectionState(civA, TransportConnectionState.DISCONNECTED)
        
        assertEquals(2, mgr.getSession(civA)?.unreadCount)
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        assertEquals(100, mgr.getSession(civA)?.priority)
        // lastMessagePreview is the most recent message
        assertEquals("Second message", mgr.getSession(civA)?.lastMessagePreview)
    }
}
