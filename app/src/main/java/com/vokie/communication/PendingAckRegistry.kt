package com.vokie.communication

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull

/** Single owner for transport ACK waiters. It is intentionally not a Room/application queue. */
class PendingAckRegistry {
    /**
     * ACKs are scoped to the peer that sent them. `peerId` defaults to empty only
     * for legacy/unit-test callers; production registration always supplies it.
     */
    data class Key(val messageId: String, val sequenceNumber: Long, val peerId: String = "")
    data class Entry(val key: Key, val transport: PacketTransport, val createdAt: Long, val deadline: Long, val completion: CompletableDeferred<Boolean>)
    private val entries = mutableMapOf<Key, Entry>()
    @Synchronized fun register(key: Key, transport: PacketTransport, timeoutMs: Long, now: Long = System.currentTimeMillis()): Entry {
        require(timeoutMs > 0); check(key !in entries) { "ACK already pending" }
        return Entry(key, transport, now, now + timeoutMs, CompletableDeferred()).also { entries[key] = it }
    }
    suspend fun await(entry: Entry): Boolean = try { withTimeoutOrNull((entry.deadline - System.currentTimeMillis()).coerceAtLeast(1)) { entry.completion.await() } ?: false } finally { remove(entry.key) }
    @Synchronized fun resolve(messageId: String, sequenceNumber: Long, peerId: String = "", transport: PacketTransport? = null): Boolean {
        // Prefer the PacketV2 ACK sender identity. Some legacy Bluetooth connections
        // address peers by MAC while PacketV2 identifies a device by its app ID; in
        // that case the single active transport link is the safe correlation boundary.
        val exact = entries[Key(messageId, sequenceNumber, peerId)]
        val legacy = entries[Key(messageId, sequenceNumber)]
        val transportScoped = transport?.let { candidate ->
            entries.values.singleOrNull { it.transport === candidate && it.key.messageId == messageId && it.key.sequenceNumber == sequenceNumber }
        }
        return (exact ?: legacy ?: transportScoped)?.completion?.complete(true) == true
    }
    @Synchronized fun fail(key: Key) { entries.remove(key)?.completion?.complete(false) }
    @Synchronized fun clearTransport(transport: PacketTransport) { entries.values.filter { it.transport === transport }.forEach { fail(it.key) } }
    @Synchronized fun remove(key: Key) { entries.remove(key) }
    @Synchronized fun size() = entries.size
}
