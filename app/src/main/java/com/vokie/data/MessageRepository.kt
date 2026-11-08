package com.vokie.data

import com.vokie.data.local.MessageDao
import com.vokie.data.local.MessageEntity
import com.vokie.domain.model.*
import com.vokie.semantic.SemanticExtractor
import com.vokie.semantic.SemanticMetadata
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

interface MessageRepository {
    fun observeMessages(): Flow<List<Message>>
    fun observeOutboundQueue(): Flow<List<Message>>
    fun observeMessagesForPeer(peerId: String): Flow<List<Message>>
    fun observeMessagesForConversation(conversationId: String): Flow<List<Message>>
    suspend fun getMessage(id: String): Message?
    suspend fun createMessage(text: String, senderId: String, receiverId: String?, language: VokieLanguage, type: MessageType = MessageType.TEXT, priority: MessagePriority = MessagePriority.NORMAL): Message
    suspend fun persistIncoming(message: Message): Boolean
    suspend fun markTransmitting(id: String, transport: TransportType)
    suspend fun markReceived(id: String)
    /** Save a receiver-local translation without changing authoritative source text. */
    suspend fun saveReceiverTranslation(id: String, text: String, language: VokieLanguage)
    suspend fun markQueued(id: String, error: String? = null)
    suspend fun incrementRetry(id: String, error: String): Int
    suspend fun markFailed(id: String, error: String)
    /** Requeue only a specific peer's interrupted in-flight message(s). */
    suspend fun recoverInterruptedForPeer(peerId: String): Int
    suspend fun recordDelivery(delivery: MessageDelivery)
    suspend fun acknowledgeMessage(messageId: String, peerId: String)
    suspend fun retry(id: String)
    suspend fun deleteMessage(id: String)
}

class RoomMessageRepository(
    private val dao: MessageDao,
    private val deliveryDao: com.vokie.data.local.MessageDeliveryDao? = null,
    private val conversationDao: com.vokie.data.local.ConversationDao? = null,
    private val semanticExtractor: SemanticExtractor = SemanticExtractor(),
    private val onLocalMessageCreated: suspend (Message) -> Unit = {},
) : MessageRepository {
    override fun observeMessages() = dao.observeAll().map { list -> list.map(MessageEntity::toDomain) }
    override fun observeOutboundQueue() = dao.observeOutboundQueue().map { list -> list.map(MessageEntity::toDomain) }
    override fun observeMessagesForPeer(peerId: String) = dao.observeByPeer(peerId).map { list -> list.map(MessageEntity::toDomain) }
    override fun observeMessagesForConversation(conversationId: String) = dao.observeByConversation(conversationId).map { list -> list.map(MessageEntity::toDomain) }
    override suspend fun getMessage(id: String) = dao.find(id)?.toDomain()

    override suspend fun createMessage(text: String, senderId: String, receiverId: String?, language: VokieLanguage, type: MessageType, priority: MessagePriority): Message {
        val normalizedText = text.trim()
        val convo = receiverId?.let { ensureConversation(it) }
        val convoId = convo?.conversationId
        val message = Message(
            VokieMessageId.generate(),
            senderId,
            System.currentTimeMillis(),
            normalizedText,
            language.code,
            type,
            receiverId = receiverId,
            priority = priority.level,
            semanticMetadata = semanticExtractor.extract(normalizedText),
            conversationId = convoId,
        )
        com.vokie.communication.VokieProtocol.validate(message)
        check(dao.insert(message.toEntity()) != -1L) { "Unable to persist message" }
        convo?.let { conversationDao?.updateActivity(it.conversationId, System.currentTimeMillis(), 0) }
        com.vokie.communication.VokieLog.msg("Message queued: ${message.id}")
        // Cloud sync is additive and opt-in; the callback is a no-op when disabled/unlinked.
        onLocalMessageCreated(message)
        return message
    }

    override suspend fun persistIncoming(message: Message): Boolean {
        com.vokie.communication.VokieProtocol.validate(message)
        val convo = ensureConversation(message.senderId)
        val normalized = message.copy(
            deliveryState = DeliveryState.RECEIVED_BY_PEER,
            conversationId = message.conversationId ?: convo?.conversationId,
        )
        val inserted = dao.insert(normalized.toEntity()) != -1L
        if (inserted) convo?.let { conversationDao?.updateActivity(it.conversationId, System.currentTimeMillis(), 1) }
        com.vokie.communication.VokieLog.msg(if (inserted) "Message persisted: ${message.id}" else "Duplicate ignored: ${message.id}")
        return inserted
    }

    private suspend fun ensureConversation(peerId: String): com.vokie.data.local.ConversationEntity? {
        val dao = conversationDao ?: return null
        dao.findByPeer(peerId)?.let { return it }
        val now = System.currentTimeMillis()
        val created = com.vokie.data.local.ConversationEntity(UUID.randomUUID().toString(), peerId, now, now, 0)
        dao.upsert(created)
        return dao.findByPeer(peerId) ?: created
    }

    override suspend fun markTransmitting(id: String, transport: TransportType) = dao.markTransmitting(id, transport.name)
    override suspend fun markReceived(id: String) = dao.setState(id, DeliveryState.RECEIVED_BY_PEER.name)
    override suspend fun saveReceiverTranslation(id: String, text: String, language: VokieLanguage) =
        dao.setTranslation(id, text, language.code)
    override suspend fun markQueued(id: String, error: String?) = dao.setState(id, DeliveryState.QUEUED.name, error)
    override suspend fun incrementRetry(id: String, error: String): Int { dao.incrementRetry(id, DeliveryState.RETRYING.name, error); return dao.find(id)?.retryCount ?: 0 }
    override suspend fun markFailed(id: String, error: String) = dao.setState(id, DeliveryState.FAILED.name, error)
    override suspend fun recoverInterruptedForPeer(peerId: String): Int = dao.recoverInterruptedForPeer(peerId)

    override suspend fun recordDelivery(delivery: MessageDelivery) {
        deliveryDao?.upsert(com.vokie.data.local.MessageDeliveryEntity(
            messageId = delivery.messageId,
            peerId = delivery.peerId,
            transport = delivery.transport.name,
            status = delivery.status.name,
            sentAt = delivery.sentAt,
            deliveredAt = delivery.deliveredAt,
            acknowledgedAt = delivery.acknowledgedAt,
            failureReason = delivery.failureReason,
        ))
    }

    override suspend fun acknowledgeMessage(messageId: String, peerId: String) {
        val now = System.currentTimeMillis()
        deliveryDao?.markAcknowledged(messageId, peerId, MessageDeliveryStatus.DELIVERED.name, now)
        dao.setState(messageId, DeliveryState.DELIVERED.name)
    }

    override suspend fun retry(id: String) = dao.resetForManualRetry(id)
    override suspend fun deleteMessage(id: String) = dao.delete(id)
}

object VokieMessageId { fun generate(): String = java.util.UUID.randomUUID().toString() }

private fun SemanticMetadata?.toStorage(): String? = this?.toWireBytes()?.let { java.util.Base64.getEncoder().encodeToString(it) }
private fun String?.toSemanticMetadata(): SemanticMetadata? = this?.let { encoded ->
    runCatching { SemanticMetadata.fromWireBytes(java.util.Base64.getDecoder().decode(encoded)) }.getOrNull()
}
fun Message.toEntity() = MessageEntity(id, senderId, receiverId, timestamp, text, language, messageType.name, deliveryState.name, transport?.name, hopCount, retryCount, requiresAck, lastError, sequenceNumber, ttlMs, priority, checksum, nextRetryAt, semanticMetadata.toStorage(), translatedText, translatedLanguage, conversationId)
fun MessageEntity.toDomain() = Message(id, senderId, timestamp, text, language, runCatching { MessageType.valueOf(messageType) }.getOrDefault(MessageType.TEXT), runCatching { DeliveryState.valueOf(deliveryState) }.getOrDefault(DeliveryState.QUEUED), transport?.let { runCatching { TransportType.valueOf(it) }.getOrNull() }, hopCount, receiverId, retryCount, requiresAck, lastError, sequenceNumber, ttlMs, priority, checksum, nextRetryAt, semanticMetadata.toSemanticMetadata(), translatedText, translatedLanguage, conversationId)
