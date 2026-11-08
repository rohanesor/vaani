package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Comprehensive multi-peer rescue communication tests validating:
 * - Message isolation per peer
 * - ACK correlation per peer
 * - Unread count tracking
 * - Priority isolation
 * - Connection state independence
 * - Session lifecycle during disconnects/reconnects
 */
class MultiPeerRescueTest {
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

    private fun inbound(id: String, sender: String, lang: String = "EN", priority: Int = 0, type: MessageType = MessageType.TEXT) =
        Message(id, sender, clock, "message-$id from $sender", lang, priority = priority, messageType = type)

    private fun outbound(id: String, receiver: String, seq: Long = 0) =
        Message(id, operator, clock, "reply-$id to $receiver", "EN", receiverId = receiver, sequenceNumber = seq)

    // ========== TEST GROUP 1: Registration ==========

    @Test
    fun `01 - multiple peers register independently`() {
        mgr.registerPeer(civA, "Civilian Alpha", TransportType.BLUETOOTH)
        mgr.registerPeer(civB, "Civilian Bravo", TransportType.WIFI_DIRECT)
        mgr.registerPeer(civC, "Civilian Charlie", TransportType.BLUETOOTH)
        
        assertEquals(3, mgr.getSessions().size)
        assertEquals("Civilian Alpha", mgr.getSession(civA)?.displayName)
    }

    @Test
    fun `02 - each peer starts with zero unread count`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        
        assertEquals(0, mgr.getSession(civA)?.unreadCount)
        assertEquals(0, mgr.getSession(civB)?.unreadCount)
    }

    // ========== TEST GROUP 2: Unread Counts ==========

    @Test
    fun `03 - incoming message increments unread`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.recordIncomingMessage(civA, inbound("m1", civA))
        
        assertEquals(1, mgr.getSession(civA)?.unreadCount)
        assertEquals(0, mgr.getSession(civB)?.unreadCount)
    }

    @Test
    fun `04 - markAsRead clears only that peer`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.recordIncomingMessage(civA, inbound("m1", civA))
        mgr.recordIncomingMessage(civB, inbound("m2", civB))
        
        mgr.markAsRead(civA)
        
        assertEquals(0, mgr.getSession(civA)?.unreadCount)
        assertEquals(1, mgr.getSession(civB)?.unreadCount)
    }

    @Test
    fun `05 - totalUnreadCount sums all peers`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.recordIncomingMessage(civA, inbound("m1", civA))
        mgr.recordIncomingMessage(civA, inbound("m2", civA))
        mgr.recordIncomingMessage(civB, inbound("m3", civB))
        
        assertEquals(3, mgr.totalUnreadCount())
    }

    // ========== TEST GROUP 3: ACK Isolation ==========

    @Test
    fun `06 - ACK for civA does not affect civB`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.recordOutgoingMessage(civA, outbound("out-a", civA, seq = 1))
        mgr.recordOutgoingMessage(civB, outbound("out-b", civB, seq = 1))
        
        assertTrue(mgr.recordAck(civA, "out-a", 1))
        
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession(civB)?.unacknowledgedMessageCount)
    }

    // ========== TEST GROUP 4: Priority Isolation ==========

    @Test
    fun `07 - priority is peer-isolated`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.recordIncomingMessage(civA, inbound("m1", civA, priority = 200))
        mgr.recordIncomingMessage(civB, inbound("m2", civB, priority = 50))
        
        assertEquals(200, mgr.getSession(civA)?.priority)
        assertEquals(50, mgr.getSession(civB)?.priority)
    }

    // ========== TEST GROUP 5: Language Isolation ==========

    @Test
    fun `08 - source language is peer-isolated`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.recordIncomingMessage(civA, inbound("m1", civA, lang = "TA"))
        mgr.recordIncomingMessage(civB, inbound("m2", civB, lang = "HI"))
        
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        assertEquals("HI", mgr.getSession(civB)?.sourceLanguage)
    }

    // ========== TEST GROUP 6: Connection Isolation ==========

    @Test
    fun `09 - connection state is peer-isolated`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.updateConnectionState(civA, TransportConnectionState.CONNECTED)
        mgr.updateConnectionState(civB, TransportConnectionState.DISCONNECTED)
        
        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession(civA)?.connectionState)
        assertEquals(TransportConnectionState.DISCONNECTED, mgr.getSession(civB)?.connectionState)
    }

    // ========== TEST GROUP 7: Complex Scenario ==========

    @Test
    fun `10 - interleaved events across peers`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        
        clock = 1_001
        mgr.recordIncomingMessage(civA, inbound("ia1", civA, "TA", 200))
        clock = 1_002
        mgr.recordIncomingMessage(civB, inbound("ib1", civB, "HI"))
        clock = 1_003
        mgr.updateConnectionState(civA, TransportConnectionState.CONNECTED)
        clock = 1_004
        mgr.recordOutgoingMessage(civA, outbound("oa1", civA, seq = 1))
        clock = 1_005
        mgr.recordAck(civA, "oa1", 1)
        clock = 1_006
        mgr.recordIncomingMessage(civC, inbound("ic1", civC, "EN", 50))
        
        assertEquals(1, mgr.getSession(civA)?.unreadCount)
        assertEquals(1, mgr.getSession(civB)?.unreadCount)
        assertEquals(1, mgr.getSession(civC)?.unreadCount)
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        assertEquals(200, mgr.getSession(civA)?.priority)
        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession(civA)?.connectionState)
    }
}
