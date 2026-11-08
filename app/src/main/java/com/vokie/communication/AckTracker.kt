package com.vokie.communication

import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

class AckTracker {
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Boolean>>()
    fun register(messageId: String): CompletableDeferred<Boolean> = CompletableDeferred<Boolean>().also { check(pending.putIfAbsent(messageId, it) == null) { "ACK already pending" } }
    fun acknowledge(messageId: String): Boolean = pending.remove(messageId)?.let { it.complete(true); true } ?: false
    fun remove(messageId: String) { pending.remove(messageId)?.cancel() }
}
