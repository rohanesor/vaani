package com.vokie.communication

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BluetoothPeerCandidatesTest {
    private val candidates = BluetoothPeerCandidates()

    @Test fun `found device is a candidate without SDP UUID`() {
        val peers = candidates.retain("AA:BB", "vivo V2205", false, -61, 1L)
        assertEquals(listOf("AA:BB"), peers!!.map { it.address })
    }

    @Test fun `duplicate discovery keeps one peer and latest signal`() {
        candidates.retain("AA:BB", "vivo V2205", false, -70, 1L)
        val peers = candidates.retain("AA:BB", "vivo V2205", false, -55, 2L)
        assertEquals(1, peers!!.size)
        assertEquals(-55, peers.single().rssi)
    }

    @Test fun `late name update replaces generic candidate label`() {
        candidates.retain("AA:BB", null, false, -70, 1L)
        val peers = candidates.retain("AA:BB", "vivo V2205", false, null, 2L)
        assertEquals("vivo V2205", peers!!.single().name)
        assertEquals(-70, peers.single().rssi)
    }

    @Test fun `null or blank device address is ignored`() {
        assertNull(candidates.retain(null, "Nearby device", false, null, 1L))
        assertNull(candidates.retain("", "Nearby device", false, null, 1L))
        assertEquals(emptyList<Any>(), candidates.snapshot())
    }
}
