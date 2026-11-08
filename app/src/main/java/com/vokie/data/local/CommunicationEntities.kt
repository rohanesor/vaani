package com.vokie.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "trusted_peers")
data class TrustedPeerEntity(
    @PrimaryKey val peerId: String,
    val displayName: String,
    val createdAt: Long,
    val acceptedAt: Long?,
    val lastConnectedAt: Long?,
    val status: String,
)

@Dao
interface TrustedPeerDao {
    @Query("SELECT * FROM trusted_peers WHERE status = 'ACTIVE' ORDER BY displayName")
    fun observeAll(): Flow<List<TrustedPeerEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrustedPeerEntity)
    @Query("UPDATE trusted_peers SET status = :status WHERE peerId = :peerId")
    suspend fun updateStatus(peerId: String, status: String)
    @Query("DELETE FROM trusted_peers WHERE peerId = :peerId")
    suspend fun delete(peerId: String)
}

@Entity(tableName = "pair_requests")
data class PairRequestEntity(
    @PrimaryKey val requestId: String,
    val senderPeerId: String,
    val receiverPeerId: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String,
)

@Dao
interface PairRequestDao {
    @Query("SELECT * FROM pair_requests WHERE status = 'PENDING' AND expiresAt > :now ORDER BY createdAt DESC")
    fun observePending(now: Long = System.currentTimeMillis()): Flow<List<PairRequestEntity>>
    @Query("SELECT * FROM pair_requests WHERE requestId = :requestId")
    suspend fun find(requestId: String): PairRequestEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PairRequestEntity)
    @Query("UPDATE pair_requests SET status = :status WHERE requestId = :requestId")
    suspend fun updateStatus(requestId: String, status: String)
}

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val conversationId: String,
    val peerId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val unreadCount: Int,
)

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>
    @Query("SELECT * FROM conversations WHERE peerId = :peerId LIMIT 1")
    suspend fun findByPeer(peerId: String): ConversationEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ConversationEntity)
    @Query("UPDATE conversations SET updatedAt = :updatedAt, unreadCount = MAX(0, unreadCount + :unreadDelta) WHERE conversationId = :conversationId")
    suspend fun updateActivity(conversationId: String, updatedAt: Long, unreadDelta: Int)
    @Query("UPDATE conversations SET unreadCount = 0 WHERE peerId = :peerId")
    suspend fun markReadForPeer(peerId: String)
}

@Entity(tableName = "message_deliveries", primaryKeys = ["messageId", "peerId"])
data class MessageDeliveryEntity(
    val messageId: String,
    val peerId: String,
    val transport: String,
    val status: String,
    val sentAt: Long,
    val deliveredAt: Long?,
    val acknowledgedAt: Long?,
    val failureReason: String?,
)

@Dao
interface MessageDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: MessageDeliveryEntity)
    @Query("UPDATE message_deliveries SET status = :status, acknowledgedAt = :timestamp WHERE messageId = :messageId AND peerId = :peerId")
    suspend fun markAcknowledged(messageId: String, peerId: String, status: String, timestamp: Long)
}
