package com.vokie.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * Cloud sync entities and DAOs for Android cloud sync support.
 * These map local IDs to canonical server IDs per the M7 contract.
 */

// ============ Entities ============

/**
 * Authenticated cloud user state (singleton).
 */
@Entity(tableName = "cloud_user")
data class CloudUserEntity(
    @PrimaryKey val id: Int = 1, // Singleton
    val cloudUserId: Long?,
    val email: String?,
    val displayName: String?,
    val preferredLanguage: String?,
    val avatarHue: Int?,
    /** Temporary migration-only field; active credentials live in Android Keystore. */
    val legacyJwtToken: String?,
    val tokenExpiresAt: Long?,
    val lastSyncedAt: Long?,
    val syncCursor: String?, // Opaque cursor from server
)

/**
 * Maps local IDs (UUID strings) to cloud IDs (Long integers).
 */
@Entity(
    tableName = "cloud_id_mapping",
    primaryKeys = ["accountCloudUserId", "localType", "localId"],
    indices = [Index(value = ["cloudId"])]
)
data class CloudIdMappingEntity(
    val accountCloudUserId: Long,
    val localType: String, // "message" | "conversation"
    val localId: String,   // Local UUID
    val cloudId: Long?,    // Server ID (null = pending sync)
    val syncedAt: Long?,   // Successful sync timestamp
    val syncStatus: String, // "pending" | "synced" | "error"
    val syncError: String?, // Error message if failed
    val retryCount: Int = 0,
)

/**
 * Cloud conversation cache.
 */
@Entity(
    tableName = "cloud_conversations",
    indices = [Index(value = ["localConversationId"])]
)
data class CloudConversationEntity(
    @PrimaryKey val cloudId: Long,
    val localConversationId: String?,
    val kind: String, // "direct" | "group"
    val title: String?,
    val myRole: String, // "owner" | "member"
    val lastMessageCloudId: Long?,
    val updatedAt: Long,
    val syncedAt: Long,
)

/**
 * Cloud conversation members.
 */
@Entity(
    tableName = "cloud_members",
    primaryKeys = ["conversationCloudId", "userCloudId"]
)
data class CloudMemberEntity(
    val conversationCloudId: Long,
    val userCloudId: Long,
    val displayName: String,
    val avatarHue: Int,
)

/**
 * Messages received from cloud.
 */
@Entity(
    tableName = "cloud_messages",
    indices = [
        Index(value = ["conversationCloudId", "cloudId"]),
        Index(value = ["localMessageId"])
    ]
)
data class CloudMessageEntity(
    @PrimaryKey val cloudId: Long,
    val conversationCloudId: Long,
    val senderCloudId: Long?,
    val senderName: String,
    val localMessageId: String?, // Local UUID if we sent it
    val kind: String, // "text" | "voice" | "system"
    val body: String?,
    val replyToCloudId: Long?,
    val sourceLanguage: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val deletedAt: Long?,
    val syncedAt: Long,
)

/**
 * Queue of pending sync operations.
 */
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["operationType", "createdAt"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String, // "upload" | "edit" | "delete" | "read_cursor"
    val localMessageId: String?,
    val conversationCloudId: Long?,
    val messageCloudId: Long?,
    val payload: String, // JSON-encoded operation data
    val createdAt: Long,
    val lastAttemptAt: Long?,
    val attemptCount: Int = 0,
    val lastError: String?,
    val status: String, // "pending" | "in_progress" | "failed" | "completed"
)

// ============ DAOs ============

@Dao
interface CloudUserDao {
    @Query("SELECT * FROM cloud_user WHERE id = 1")
    suspend fun get(): CloudUserEntity?

    @Query("SELECT * FROM cloud_user WHERE id = 1")
    fun observe(): Flow<CloudUserEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: CloudUserEntity)

    @Query("UPDATE cloud_user SET syncCursor = :cursor, lastSyncedAt = :timestamp WHERE id = 1")
    suspend fun updateSyncState(cursor: String?, timestamp: Long)

    @Query("UPDATE cloud_user SET legacyJwtToken = NULL WHERE id = 1")
    suspend fun clearLegacyToken()

    @Query("DELETE FROM cloud_user WHERE id = 1")
    suspend fun clear()
}

@Dao
interface CloudIdMappingDao {
    @Query("SELECT cloudId FROM cloud_id_mapping WHERE accountCloudUserId = :accountId AND localType = :type AND localId = :localId")
    suspend fun getCloudId(accountId: Long, type: String, localId: String): Long?

    @Query("SELECT localId FROM cloud_id_mapping WHERE accountCloudUserId = :accountId AND localType = :type AND cloudId = :cloudId")
    suspend fun getLocalId(accountId: Long, type: String, cloudId: Long): String?

    @Query("SELECT * FROM cloud_id_mapping WHERE accountCloudUserId = :accountId AND localType = :type AND localId = :localId")
    suspend fun get(accountId: Long, type: String, localId: String): CloudIdMappingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: CloudIdMappingEntity)

    @Query("UPDATE cloud_id_mapping SET cloudId = :cloudId, syncedAt = :timestamp, syncStatus = 'synced', syncError = NULL WHERE accountCloudUserId = :accountId AND localType = :type AND localId = :localId")
    suspend fun markSynced(accountId: Long, type: String, localId: String, cloudId: Long, timestamp: Long)

    @Query("UPDATE cloud_id_mapping SET syncStatus = 'error', syncError = :error, retryCount = retryCount + 1 WHERE accountCloudUserId = :accountId AND localType = :type AND localId = :localId")
    suspend fun markError(accountId: Long, type: String, localId: String, error: String)

    @Query("SELECT * FROM cloud_id_mapping WHERE accountCloudUserId = :accountId AND syncStatus = 'pending' AND localType = 'message'")
    suspend fun pendingMessages(accountId: Long): List<CloudIdMappingEntity>

    @Query("DELETE FROM cloud_id_mapping WHERE accountCloudUserId = :accountId AND localType = :type AND localId = :localId")
    suspend fun delete(accountId: Long, type: String, localId: String)

    @Query("DELETE FROM cloud_id_mapping WHERE accountCloudUserId = :accountId")
    suspend fun clearAccount(accountId: Long)

    // Read-only observers for the WebView UI (docs/ANDROID_BRIDGE_STATE_CONTRACT.md). No schema change.
    @Query("SELECT * FROM cloud_id_mapping WHERE accountCloudUserId = :accountId AND localType = 'conversation' AND cloudId IS NOT NULL")
    fun observeConversationLinks(accountId: Long): Flow<List<CloudIdMappingEntity>>

    @Query("SELECT messages.conversationId AS conversationId, cloud_id_mapping.syncStatus AS syncStatus, COUNT(*) AS count FROM cloud_id_mapping INNER JOIN messages ON messages.id = cloud_id_mapping.localId WHERE cloud_id_mapping.accountCloudUserId = :accountId AND cloud_id_mapping.localType = 'message' GROUP BY messages.conversationId, cloud_id_mapping.syncStatus")
    fun observeMessageSyncCounts(accountId: Long): Flow<List<ConversationSyncCount>>
}

/** Query result: number of account-scoped message mappings per local conversation and sync status. */
data class ConversationSyncCount(
    val conversationId: String?,
    val syncStatus: String,
    val count: Int,
)

@Dao
interface CloudConversationDao {
    @Query("SELECT * FROM cloud_conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<CloudConversationEntity>>

    @Query("SELECT * FROM cloud_conversations WHERE cloudId = :cloudId")
    suspend fun get(cloudId: Long): CloudConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: CloudConversationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(conversations: List<CloudConversationEntity>)

    @Query("UPDATE cloud_conversations SET localConversationId = :localId WHERE cloudId = :cloudId")
    suspend fun linkLocal(cloudId: Long, localId: String)

    @Query("DELETE FROM cloud_conversations")
    suspend fun clear()
}

@Dao
interface CloudMemberDao {
    @Query("SELECT * FROM cloud_members WHERE conversationCloudId = :conversationId")
    suspend fun getMembers(conversationId: Long): List<CloudMemberEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(members: List<CloudMemberEntity>)

    @Query("DELETE FROM cloud_members WHERE conversationCloudId = :conversationId")
    suspend fun clearForConversation(conversationId: Long)
}

@Dao
interface CloudMessageDao {
    @Query("SELECT * FROM cloud_messages WHERE conversationCloudId = :conversationId ORDER BY cloudId DESC LIMIT :limit")
    fun observeRecent(conversationId: Long, limit: Int = 100): Flow<List<CloudMessageEntity>>

    @Query("SELECT * FROM cloud_messages WHERE conversationCloudId = :conversationId AND cloudId > :afterId ORDER BY cloudId ASC")
    suspend fun getAfter(conversationId: Long, afterId: Long): List<CloudMessageEntity>

    @Query("SELECT * FROM cloud_messages WHERE cloudId = :cloudId")
    suspend fun get(cloudId: Long): CloudMessageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<CloudMessageEntity>)

    @Query("UPDATE cloud_messages SET body = :body, editedAt = :editedAt WHERE cloudId = :cloudId")
    suspend fun updateBody(cloudId: Long, body: String, editedAt: Long)

    @Query("UPDATE cloud_messages SET deletedAt = :deletedAt WHERE cloudId = :cloudId")
    suspend fun markDeleted(cloudId: Long, deletedAt: Long)

    @Query("DELETE FROM cloud_messages")
    suspend fun clear()
}

@Dao
interface SyncQueueDao {
    @Query("SELECT * FROM sync_queue WHERE status = 'pending' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun pending(limit: Int = 50): List<SyncQueueEntity>

    @Insert
    suspend fun enqueue(item: SyncQueueEntity): Long

    @Query("UPDATE sync_queue SET status = 'in_progress', lastAttemptAt = :timestamp WHERE id = :id")
    suspend fun markInProgress(id: Long, timestamp: Long)

    @Query("UPDATE sync_queue SET status = 'completed' WHERE id = :id")
    suspend fun markCompleted(id: Long)

    @Query("UPDATE sync_queue SET status = 'pending', lastError = :error, attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun markRetry(id: Long, error: String)

    @Query("UPDATE sync_queue SET status = 'failed', lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("DELETE FROM sync_queue WHERE status = 'completed' AND createdAt < :before")
    suspend fun cleanupOld(before: Long): Int

    @Query("DELETE FROM sync_queue")
    suspend fun clear()
}
