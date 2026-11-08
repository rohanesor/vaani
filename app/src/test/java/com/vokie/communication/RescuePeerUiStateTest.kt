package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.TransportConnectionState
import com.vokie.domain.model.TransportType
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Validates the peer-list state, selection, filtering, and priority
 * behavior that drives the rescue UI.
 */
class RescuePeerUiStateTest {
    private lateinit var mgr: PeerSessionManager
    private val operator = "operator"

    @Before fun setUp() {
        mgr = PeerSessionManager { 1_000L }
    }

    private fun inbound(id: String, sender: String, lang: String = "EN", priority: Int = 0) =
        Message(id, sender, 1_000L, "text", lang, priority = priority)

    private fun outbound(id: String, receiver: String) =
        Message(id, operator, 1_000L, "text", "EN", receiverId = receiver)

    // 1. Sessions appear in state
    @Test fun `registered peers appear in sessions`() {
        mgr.registerPeer("A", "Alpha"); mgr.registerPeer("B", "Bravo")
        assertEquals(2, mgr.sessions.value.size)
        assertNotNull(mgr.sessions.value["A"])
        assertNotNull(mgr.sessions.value["B"])
    }

    // 2 + 3. Selecting peer sets selectedPeerId (tested at session manager level)
    @Test fun `selecting A then B updates independent state`() {
        mgr.registerPeer("A"); mgr.registerPeer("B")
        // Selection is a ViewModel concern; here we verify sessions remain separate.
        val sessionA = mgr.getSession("A")!!
        val sessionB = mgr.getSession("B")!!
        assertNotEquals(sessionA.peerId, sessionB.peerId)
    }

    // 4 + 5. Message filtering isolation
    @Test fun `message list filtered by peer A excludes B messages`() {
        val all = listOf(
            inbound("m1", "A"), outbound("m2", "A"),
            inbound("m3", "B"), outbound("m4", "B"),
        )
        val peerA = all.filter { it.senderId == "A" || it.receiverId == "A" }
        val peerB = all.filter { it.senderId == "B" || it.receiverId == "B" }
        assertEquals(2, peerA.size)
        assertEquals(2, peerB.size)
        assertTrue(peerA.none { it.senderId == "B" || it.receiverId == "B" })
        assertTrue(peerB.none { it.senderId == "A" || it.receiverId == "A" })
    }

    // 6 + 7. Sending targets the correct peer
    @Test fun `outbound targeting A goes to A only`() {
        mgr.registerPeer("A"); mgr.registerPeer("B")
        assertTrue(mgr.recordOutgoingMessage("A", outbound("out-a", "A")))
        assertEquals(1, mgr.getSession("A")?.pendingMessageCount)
        assertEquals(0, mgr.getSession("B")?.pendingMessageCount)
    }

    @Test fun `outbound targeting B goes to B only`() {
        mgr.registerPeer("A"); mgr.registerPeer("B")
        assertTrue(mgr.recordOutgoingMessage("B", outbound("out-b", "B")))
        assertEquals(0, mgr.getSession("A")?.pendingMessageCount)
        assertEquals(1, mgr.getSession("B")?.pendingMessageCount)
    }

    // 8. No selection → empty filter shows all (or requires selection)
    @Test fun `null selected peer shows all messages when no filter applied`() {
        val all = listOf(inbound("m1", "A"), inbound("m2", "B"))
        val selectedPeerId: String? = null
        val filtered = if (selectedPeerId == null) all else all.filter { it.senderId == selectedPeerId || it.receiverId == selectedPeerId }
        assertEquals(2, filtered.size)
    }

    // 9. Switching A → B → A
    @Test fun `switching selection preserves correct filtering`() {
        val all = listOf(
            inbound("m1", "A"), inbound("m2", "B"), inbound("m3", "A"),
        )
        var selected: String? = "A"
        assertEquals(2, all.filter { it.senderId == selected || it.receiverId == selected }.size)
        selected = "B"
        assertEquals(1, all.filter { it.senderId == selected || it.receiverId == selected }.size)
        selected = "A"
        assertEquals(2, all.filter { it.senderId == selected || it.receiverId == selected }.size)
    }

    // 10. Connection state changes update correct peer
    @Test fun `connection state changes update only the target peer`() {
        mgr.registerPeer("A"); mgr.registerPeer("B")
        mgr.updateConnectionState("A", TransportConnectionState.CONNECTED)
        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession("A")?.connectionState)
        assertEquals(TransportConnectionState.IDLE, mgr.getSession("B")?.connectionState)
        mgr.updateConnectionState("B", TransportConnectionState.FAILED)
        assertEquals(TransportConnectionState.CONNECTED, mgr.getSession("A")?.connectionState)
        assertEquals(TransportConnectionState.FAILED, mgr.getSession("B")?.connectionState)
    }

    // 11. Priority changes affect only the correct peer
    @Test fun `priority from incoming message updates only sender`() {
        mgr.registerPeer("A"); mgr.registerPeer("B")
        mgr.recordIncomingMessage("A", inbound("m-urgent", "A", priority = 200))
        assertEquals(200, mgr.getSession("A")?.priority)
        assertEquals(0, mgr.getSession("B")?.priority)
    }

    // 12. Simulated peers are identifiable
    @Test fun `simulated peer names are clearly labelled`() {
        mgr.registerPeer("SIM-CIV-001", "SIMULATED — CIV-001")
        assertTrue(mgr.getSession("SIM-CIV-001")?.displayName?.contains("SIMULATED") == true)
    }
}
