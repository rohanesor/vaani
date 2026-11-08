package com.vokie.data

import com.vokie.data.local.MessageDao
import com.vokie.data.local.MessageEntity
import com.vokie.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RoomMessageRepositoryTest {
    @Test fun queueTransitionsAndManualRetryArePersisted() = runBlocking {
        val dao = MemoryMessageDao(); val repository = RoomMessageRepository(dao)
        val message = repository.createMessage("Hello from iTantra", "sender", "peer", VokieLanguage.EN)
        assertEquals(DeliveryState.QUEUED, repository.getMessage(message.id)?.deliveryState)
        repository.markTransmitting(message.id, TransportType.BLUETOOTH)
        assertEquals(DeliveryState.TRANSMITTING, repository.getMessage(message.id)?.deliveryState)
        assertEquals(TransportType.BLUETOOTH, repository.getMessage(message.id)?.transport)
        assertEquals(1, repository.incrementRetry(message.id, "ACK timeout"))
        assertEquals(DeliveryState.RETRYING, repository.getMessage(message.id)?.deliveryState)
        repository.markFailed(message.id, "Retry limit reached")
        assertEquals(DeliveryState.FAILED, repository.getMessage(message.id)?.deliveryState)
        repository.retry(message.id)
        assertEquals(DeliveryState.QUEUED, repository.getMessage(message.id)?.deliveryState)
        assertEquals(0, repository.getMessage(message.id)?.retryCount)
        repository.markReceived(message.id)
        assertEquals(DeliveryState.RECEIVED_BY_PEER, repository.getMessage(message.id)?.deliveryState)
    }

    @Test fun `created message persists supplementary semantic metadata`() = runBlocking {
        val repository = RoomMessageRepository(MemoryMessageDao())
        val message = repository.createMessage("There is a fire. Send help.", "sender", "peer", VokieLanguage.EN)
        assertEquals("There is a fire. Send help.", repository.getMessage(message.id)?.text)
        assertNotNull(repository.getMessage(message.id)?.semanticMetadata)
        assertEquals(com.vokie.semantic.SemanticEventCategory.FIRE, repository.getMessage(message.id)?.semanticMetadata?.eventCategory)
    }

    @Test fun `receiver translation is persisted without replacing authoritative source text`() = runBlocking {
        val repository = RoomMessageRepository(MemoryMessageDao())
        val incoming = Message("incoming-translation", "sender", 1234, "मुझे मदद चाहिए", "HI", receiverId = "receiver")
        assertTrue(repository.persistIncoming(incoming))

        repository.saveReceiverTranslation(incoming.id, "I need help", VokieLanguage.EN)

        val stored = repository.getMessage(incoming.id)
        assertEquals("मुझे मदद चाहिए", stored?.text)
        assertEquals("I need help", stored?.translatedText)
        assertEquals("EN", stored?.translatedLanguage)
    }

    @Test fun duplicateIncomingMessageUsesIdempotencyKey() = runBlocking {
        val repository = RoomMessageRepository(MemoryMessageDao())
        val incoming = Message("same-id", "sender", 1234, "Help", receiverId = "receiver")
        assertTrue(repository.persistIncoming(incoming))
        assertFalse(repository.persistIncoming(incoming))
        assertEquals(1, repository.observeMessages().first().size)
    }

    @Test fun `peer disconnect requeues only that peer in-flight message`() = runBlocking {
        val repository = RoomMessageRepository(MemoryMessageDao())
        val a = repository.createMessage("A", "local", "peer-A", VokieLanguage.EN)
        val b = repository.createMessage("B", "local", "peer-B", VokieLanguage.EN)
        repository.markTransmitting(a.id, TransportType.BLUETOOTH)
        repository.markTransmitting(b.id, TransportType.WIFI_DIRECT)

        assertEquals(1, repository.recoverInterruptedForPeer("peer-A"))
        assertEquals(DeliveryState.QUEUED, repository.getMessage(a.id)?.deliveryState)
        assertEquals(DeliveryState.TRANSMITTING, repository.getMessage(b.id)?.deliveryState)
        assertEquals(a.id, repository.getMessage(a.id)?.id)
    }
}

private class MemoryMessageDao : MessageDao {
    private val rows = MutableStateFlow<List<MessageEntity>>(emptyList())
    override fun observeAll() = rows
    override fun observeOutboundQueue() = MutableStateFlow(rows.value.filter { it.deliveryState in setOf("QUEUED", "RETRYING") })
    override fun observeByPeer(peerId: String) = MutableStateFlow(rows.value.filter { it.senderId == peerId || it.receiverId == peerId })
    override fun observeByConversation(conversationId: String) = MutableStateFlow(rows.value.filter { it.conversationId == conversationId })
    override suspend fun find(id: String) = rows.value.firstOrNull { it.id == id }
    override suspend fun insert(entity: MessageEntity): Long { if (find(entity.id) != null) return -1; rows.value = rows.value + entity; return rows.value.size.toLong() }
    override suspend fun update(entity: MessageEntity) { rows.value = rows.value.map { if (it.id == entity.id) entity else it } }
    override suspend fun setState(id: String, state: String, error: String?) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = state, lastError = error) else it } }
    override suspend fun setTranslation(id: String, text: String, language: String) { rows.value = rows.value.map { if (it.id == id) it.copy(translatedText = text, translatedLanguage = language) else it } }
    override suspend fun markTransmitting(id: String, transport: String) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = "TRANSMITTING", transport = transport, lastError = null) else it } }
    override suspend fun incrementRetry(id: String, state: String, error: String) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = state, retryCount = it.retryCount + 1, lastError = error) else it } }
    override suspend fun resetForManualRetry(id: String) { rows.value = rows.value.map { if (it.id == id) it.copy(deliveryState = "QUEUED", retryCount = 0, lastError = null) else it } }
    override suspend fun retryable(now: Long) = rows.value.filter { it.deliveryState == "RETRYING" && (it.nextRetryAt ?: Long.MAX_VALUE) <= now && it.timestamp + it.ttlMs > now }
    override suspend fun expire(now: Long, reason: String): Int { val before = rows.value.size; rows.value = rows.value.map { if (it.deliveryState in setOf("QUEUED", "RETRYING", "TRANSMITTING") && it.timestamp + it.ttlMs <= now) it.copy(deliveryState = "EXPIRED", lastError = reason) else it }; return before - rows.value.size }
    override suspend fun recoverInterrupted() { rows.value = rows.value.map { if (it.deliveryState == "TRANSMITTING") it.copy(deliveryState = "QUEUED") else it } }
    override suspend fun recoverInterruptedForPeer(peerId: String): Int {
        var recovered = 0
        rows.value = rows.value.map {
            if (it.deliveryState == "TRANSMITTING" && it.receiverId == peerId) { recovered++; it.copy(deliveryState = "QUEUED") } else it
        }
        return recovered
    }
    override suspend fun delete(id: String) { rows.value = rows.value.filterNot { it.id == id } }
}
