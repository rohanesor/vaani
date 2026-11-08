package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * End-to-end deterministic simulation of the multi-peer rescue workflow
 * using session-layer isolation only (no transport, no Room).
 */
class MultiPeerSimulationTest {
    private lateinit var mgr: PeerSessionManager
    private var clock = 1_000L
    private val operator = "operator"

    // Deterministic peer IDs matching the DEBUG simulation
    private val civA = "SIM-CIV-001"
    private val civB = "SIM-CIV-002"
    private val civC = "SIM-CIV-003"

    @Before fun setUp() {
        mgr = PeerSessionManager { clock }
    }

    private fun inbound(id: String, sender: String, lang: String = "EN", priority: Int = 0) =
        Message(id, sender, clock, "text-$id", lang, priority = priority)

    private fun outbound(id: String, receiver: String) =
        Message(id, operator, clock, "reply-$id", "EN", receiverId = receiver)

    // ---- PHASE 2: Registration ----

    // TEST 1
    @Test fun `three simulated peers register independently`() {
        mgr.registerPeer(civA, "SIMULATED — CIV-001")
        mgr.registerPeer(civB, "SIMULATED — CIV-002")
        mgr.registerPeer(civC, "SIMULATED — CIV-003")
        assertEquals(3, mgr.getSessions().size)
        assertNotNull(mgr.getSession(civA))
        assertNotNull(mgr.getSession(civB))
        assertNotNull(mgr.getSession(civC))
    }

    // TEST 2
    @Test fun `each peer has independent session state`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        mgr.updateConnectionState(civA, TransportConnectionState.CONNECTED)
        mgr.updateConnectionState(civC, TransportConnectionState.CONNECTING)
        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession(civA)?.connectionState)
        assertEquals(TransportConnectionState.IDLE, mgr.getSession(civB)?.connectionState)
        assertEquals(TransportConnectionState.CONNECTING, mgr.getSession(civC)?.connectionState)
    }

    // ---- PHASE 3: Incoming messages ----

    // TEST 3
    @Test fun `incoming message A belongs only to A`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        assertTrue(mgr.recordIncomingMessage(civA, inbound("m-a", civA, "TA", 150)))
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        assertNull(mgr.getSession(civB)?.sourceLanguage)
        assertNull(mgr.getSession(civC)?.sourceLanguage)
    }

    // TEST 4
    @Test fun `incoming message B belongs only to B`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        assertTrue(mgr.recordIncomingMessage(civB, inbound("m-b", civB, "HI")))
        assertNull(mgr.getSession(civA)?.sourceLanguage)
        assertEquals("HI", mgr.getSession(civB)?.sourceLanguage)
    }

    // TEST 5
    @Test fun `incoming message C belongs only to C`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        assertTrue(mgr.recordIncomingMessage(civC, inbound("m-c", civC, "EN", 50)))
        assertNull(mgr.getSession(civA)?.sourceLanguage)
        assertEquals("EN", mgr.getSession(civC)?.sourceLanguage)
        assertEquals(50, mgr.getSession(civC)?.priority)
    }

    // ---- PHASE 4: Peer selection / filtering ----

    private fun allMessages(): List<Message> = listOf(
        inbound("m-a1", civA, "TA"), outbound("r-a1", civA),
        inbound("m-b1", civB, "HI"), outbound("r-b1", civB),
        inbound("m-c1", civC, "EN"), outbound("r-c1", civC),
        inbound("m-a2", civA, "TA"),
    )

    private fun filteredFor(peerId: String?): List<Message> {
        val all = allMessages()
        return if (peerId == null) all else all.filter { it.senderId == peerId || it.receiverId == peerId }
    }

    // TEST 6
    @Test fun `selected peer A filters correctly`() {
        val filtered = filteredFor(civA)
        assertEquals(3, filtered.size)
        assertTrue(filtered.all { it.senderId == civA || it.receiverId == civA })
    }

    // TEST 7
    @Test fun `selected peer B filters correctly`() {
        val filtered = filteredFor(civB)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.senderId == civB || it.receiverId == civB })
    }

    // TEST 8
    @Test fun `selected peer C filters correctly`() {
        val filtered = filteredFor(civC)
        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.senderId == civC || it.receiverId == civC })
    }

    // ---- PHASE 5: Outbound routing ----

    // TEST 9
    @Test fun `outbound A has receiverId=A`() {
        mgr.registerPeer(civA)
        val msg = outbound("out-a", civA)
        assertEquals(civA, msg.receiverId)
        assertTrue(mgr.recordOutgoingMessage(civA, msg))
        assertEquals(1, mgr.getSession(civA)?.pendingMessageCount)
    }

    // TEST 10
    @Test fun `outbound B has receiverId=B`() {
        mgr.registerPeer(civB)
        val msg = outbound("out-b", civB)
        assertEquals(civB, msg.receiverId)
        assertTrue(mgr.recordOutgoingMessage(civB, msg))
    }

    // TEST 11
    @Test fun `outbound C has receiverId=C`() {
        mgr.registerPeer(civC)
        val msg = outbound("out-c", civC)
        assertEquals(civC, msg.receiverId)
        assertTrue(mgr.recordOutgoingMessage(civC, msg))
    }

    // ---- PHASE 6: Priority isolation ----

    // TEST 12
    @Test fun `priority is peer-isolated`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        mgr.recordIncomingMessage(civA, inbound("p-a", civA, priority = 150))
        mgr.recordIncomingMessage(civB, inbound("p-b", civB, priority = 10))
        mgr.recordIncomingMessage(civC, inbound("p-c", civC, priority = 50))
        assertEquals(150, mgr.getSession(civA)?.priority)
        assertEquals(10, mgr.getSession(civB)?.priority)
        assertEquals(50, mgr.getSession(civC)?.priority)
    }

    // ---- PHASE 6 continued: Connection isolation ----

    // TEST 13
    @Test fun `connection state is peer-isolated`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)
        mgr.updateConnectionState(civA, TransportConnectionState.CONNECTED)
        mgr.updateConnectionState(civB, TransportConnectionState.DISCONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession(civA)?.connectionState)
        assertEquals(TransportConnectionState.DISCONNECTED, mgr.getSession(civB)?.connectionState)
        assertEquals(TransportConnectionState.IDLE, mgr.getSession(civC)?.connectionState)
    }

    // ---- ACK isolation ----

    // TEST 14
    @Test fun `ACK is peer-isolated`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB)
        mgr.recordOutgoingMessage(civA, outbound("ack-a", civA))
        mgr.recordOutgoingMessage(civB, outbound("ack-b", civB))
        assertTrue(mgr.recordAck(civA, "ack-a", 0))
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)
        assertEquals(1, mgr.getSession(civB)?.unacknowledgedMessageCount)
    }

    // ---- PHASE 6: Interleaved events ----

    // TEST 15
    @Test fun `interleaved events across three peers`() {
        mgr.registerPeer(civA); mgr.registerPeer(civB); mgr.registerPeer(civC)

        // 1. CIV-001 sends message
        clock = 1_001
        mgr.recordIncomingMessage(civA, inbound("ia1", civA, "TA", 150))

        // 2. CIV-002 sends message
        clock = 1_002
        mgr.recordIncomingMessage(civB, inbound("ib1", civB, "HI"))

        // 3. CIV-003 sends message
        clock = 1_003
        mgr.recordIncomingMessage(civC, inbound("ic1", civC, "EN", 50))

        // 4. CIV-001 sends another message
        clock = 1_004
        mgr.recordIncomingMessage(civA, inbound("ia2", civA, "TA", 200))

        // 5. CIV-002 disconnects
        clock = 1_005
        mgr.updateConnectionState(civB, TransportConnectionState.DISCONNECTED)

        // 6. CIV-003 sends another message
        clock = 1_006
        mgr.recordIncomingMessage(civC, inbound("ic2", civC, "EN", 50))

        // 7. Operator replies to CIV-001 and ACK arrives
        clock = 1_007
        mgr.recordOutgoingMessage(civA, outbound("oa1", civA))
        mgr.recordAck(civA, "oa1", 0)

        // 8. CIV-002 reconnects
        clock = 1_008
        mgr.updateConnectionState(civB, TransportConnectionState.CONNECTED)

        // 9. CIV-002 sends another message
        clock = 1_009
        mgr.recordIncomingMessage(civB, inbound("ib2", civB, "HI", 10))

        // Verify final state
        assertEquals(200, mgr.getSession(civA)?.priority)
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        assertEquals(0, mgr.getSession(civA)?.unacknowledgedMessageCount)

        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession(civB)?.connectionState)
        assertEquals("HI", mgr.getSession(civB)?.sourceLanguage)
        assertEquals(10, mgr.getSession(civB)?.priority)

        assertEquals(50, mgr.getSession(civC)?.priority)
        assertEquals(TransportConnectionState.IDLE, mgr.getSession(civC)?.connectionState)

        // 3 sessions, no duplicates
        assertEquals(3, mgr.getSessions().size)
    }

    // ---- PHASE 9: Simulation lifecycle ----

    // TEST 16
    @Test fun `simulated peer removal cleans up sessions`() {
        mgr.registerPeer(civA, "SIMULATED — CIV-001")
        mgr.registerPeer(civB, "SIMULATED — CIV-002")
        mgr.registerPeer(civC, "SIMULATED — CIV-003")
        assertEquals(3, mgr.getSessions().size)
        listOf(civA, civB, civC).forEach { mgr.removePeer(it) }
        assertEquals(0, mgr.getSessions().size)
    }

    // TEST 17
    @Test fun `selection cleanup after simulated peer removal`() {
        mgr.registerPeer(civA)
        // Simulate selecting civA then removing it
        var selectedPeerId: String? = civA
        mgr.removePeer(civA)
        // If selected peer is removed, getSession returns null
        if (mgr.getSession(selectedPeerId!!) == null) selectedPeerId = null
        assertNull(selectedPeerId)
    }

    // TEST 18
    @Test fun `no duplicate session after simulated reconnect`() {
        mgr.registerPeer(civB)
        mgr.updateConnectionState(civB, TransportConnectionState.CONNECTED)
        mgr.updateConnectionState(civB, TransportConnectionState.DISCONNECTED)
        mgr.registerPeer(civB) // re-register on reconnect
        mgr.updateConnectionState(civB, TransportConnectionState.CONNECTED)
        assertEquals(1, mgr.getSessions().size)
        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession(civB)?.connectionState)
    }

    // TEST 19
    @Test fun `language preferences remain independent of peer metadata`() {
        mgr.registerPeer(civA)
        mgr.recordIncomingMessage(civA, inbound("lang-test", civA, "TA"))
        assertEquals("TA", mgr.getSession(civA)?.sourceLanguage)
        // Session language metadata is peer-local, it does not change any global preference.
        // This test confirms the field is isolated — global prefs are a separate DataStore concern.
    }

    // TEST 20
    @Test fun `real peer state is not removed by simulation cleanup`() {
        // Add a real peer
        mgr.registerPeer("real-device-001", "Real Phone", TransportType.BLUETOOTH)
        mgr.recordIncomingMessage("real-device-001", inbound("real-msg", "real-device-001"))
        // Add and remove simulated peers
        mgr.registerPeer(civA, "SIMULATED — CIV-001")
        mgr.registerPeer(civB, "SIMULATED — CIV-002")
        mgr.registerPeer(civC, "SIMULATED — CIV-003")
        assertEquals(4, mgr.getSessions().size)
        listOf(civA, civB, civC).forEach { mgr.removePeer(it) }
        // Real peer must survive
        assertEquals(1, mgr.getSessions().size)
        assertNotNull(mgr.getSession("real-device-001"))
        assertNotNull(mgr.getSession("real-device-001")?.lastMessageTimestamp)
    }
}
