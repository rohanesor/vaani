package com.vokie.communication

import com.vokie.domain.model.Peer

/**
 * Keeps Bluetooth inquiry results separate from optional SDP UUID resolution.
 * A visible device is only a connection candidate; the RFCOMM handshake using
 * VokieProtocol.SERVICE_UUID remains the authority for a real iTantra peer.
 */
internal class BluetoothPeerCandidates {
    private data class Candidate(val peer: Peer, val discoveredAtMs: Long)
    private val candidates = linkedMapOf<String, Candidate>()

    fun retain(
        address: String?,
        name: String?,
        bonded: Boolean,
        rssi: Int?,
        discoveredAtMs: Long,
    ): List<Peer>? {
        val id = address?.takeIf { it.isNotBlank() } ?: return null
        val prior = candidates[id]?.peer
        val resolvedName = name?.takeIf { it.isNotBlank() }
            ?: prior?.name?.takeIf { it != "Nearby device" }
            ?: "Nearby device"
        val peer = Peer(
            id = id,
            name = resolvedName,
            address = id,
            bonded = bonded || (prior?.bonded == true),
            rssi = rssi ?: prior?.rssi,
        )
        candidates[id] = Candidate(peer, discoveredAtMs)
        return snapshot()
    }

    fun clear() = candidates.clear()
    fun contains(address: String?): Boolean = address != null && candidates.containsKey(address)
    fun snapshot(): List<Peer> = candidates.values.map { it.peer }.sortedBy { it.name }
}
