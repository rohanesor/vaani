package com.vokie.data

import com.vokie.data.local.ConversationDao
import com.vokie.data.local.ConversationEntity
import com.vokie.domain.model.Conversation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface ConversationRepository {
    fun observeConversations(): Flow<List<Conversation>>
    suspend fun markReadForPeer(peerId: String)
}

class RoomConversationRepository(private val dao: ConversationDao) : ConversationRepository {
    override fun observeConversations(): Flow<List<Conversation>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    override suspend fun markReadForPeer(peerId: String) = dao.markReadForPeer(peerId)
}

private fun ConversationEntity.toDomain() = Conversation(conversationId, peerId, createdAt, updatedAt, unreadCount)
