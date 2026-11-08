# VAANI Android Cloud Sync Data Model

## Overview

This document specifies the local database schema extensions required for the Android client to support cloud synchronization while preserving existing nearby/offline functionality.

**Status**: Schema implemented through Room version 10. Migration 7→8 adds cloud sync tables, migration 8→9 moves the active token into a temporary legacy field for Keystore transfer, and migration 9→10 adds local conversation/delivery/pairing tables plus the local message conversation reference. Android compile and JVM tests pass; device migration execution requires a connected device.

## Existing Android Schema (Room v7)

### Current Entities

| Entity | Purpose | ID Type |
|---|---|---|
| `MessageEntity` | Chat messages | UUID string |
| `PeerEntity` | Nearby peers | String (device address) |
| `TransportEventEntity` | Delivery logs | Auto-increment long |
| `EmergencyAlertEntity` | Emergency broadcasts | UUID string |
| `ReceivedPacketEntity` | Deduplication | Composite (device, message, sequence) |
| `AppSettingsEntity` | User preferences | Singleton (id=1) |

### Current Message Schema

```kotlin
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,          // Local UUID
    val senderId: String,                 // Peer ID (device address)
    val receiverId: String?,              // Peer ID or null (broadcast)
    val timestamp: Long,                  // Local timestamp
    val text: String,
    val language: String,
    val messageType: String,              // TEXT, VOICE, EMERGENCY, etc.
    val deliveryState: String,            // QUEUED, TRANSMITTING, etc.
    val transport: String?,               // BLUETOOTH, WIFI_DIRECT, etc.
    val hopCount: Int,
    val retryCount: Int,
    val requiresAck: Boolean,
    val lastError: String?,
    val sequenceNumber: Long,
    val ttlMs: Long,
    val priority: Int,
    val checksum: Long,
    val nextRetryAt: Long?,
    val semanticMetadata: String?,
    val translatedText: String?,
    val translatedLanguage: String?,
)
```

---

## Cloud Sync Schema Extensions

### CloudUserEntity

Maps authenticated cloud user to local device.

```kotlin
@Entity(tableName = "cloud_user")
data class CloudUserEntity(
    @PrimaryKey val id: Int = 1,         // Singleton
    val cloudUserId: Long?,               // Server user ID
    val email: String?,
    val displayName: String?,
    val preferredLanguage: String?,
    val avatarHue: Int?,
    val jwtToken: String?,                // Encrypted in production
    val tokenExpiresAt: Long?,
    val lastSyncedAt: Long?,
    val syncCursor: String?,              // Opaque cursor from server
)
```

### CloudIdMappingEntity

Maps local IDs to cloud IDs for synchronized entities.

```kotlin
@Entity(
    tableName = "cloud_id_mapping",
    primaryKeys = ["accountCloudUserId", "localType", "localId"],
    indices = [Index(value = ["cloudId"])]
)
data class CloudIdMappingEntity(
    val accountCloudUserId: Long,         // Canonical authenticated user ID
    val localType: String,                 // "message" | "conversation"
    val localId: String,                  // Local UUID
    val cloudId: Long?,                   // Server ID (null = pending sync)
    val syncedAt: Long?,                  // Successful sync timestamp
    val syncStatus: String,               // "pending" | "synced" | "error"
    val syncError: String?,               // Error message if failed
    val retryCount: Int = 0,
)
```

### CloudConversationEntity

Tracks cloud conversation state.

```kotlin
@Entity(
    tableName = "cloud_conversations",
    indices = [Index(value = ["localConversationId"])]
)
data class CloudConversationEntity(
    @PrimaryKey val cloudId: Long,       // Server conversation ID
    val localConversationId: String?,     // Local conversation UUID (if exists)
    val kind: String,                     // "direct" | "group"
    val title: String?,
    val myRole: String,                   // "owner" | "member"
    val lastMessageCloudId: Long?,
    val updatedAt: Long,
    val syncedAt: Long,
)
```

### CloudMemberEntity

Tracks cloud conversation membership.

```kotlin
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
```

### CloudMessageEntity

Stores messages received from cloud (not originated locally).

```kotlin
@Entity(
    tableName = "cloud_messages",
    indices = [
        Index(value = ["conversationCloudId", "cloudId"]),
        Index(value = ["localMessageId"])
    ]
)
data class CloudMessageEntity(
    @PrimaryKey val cloudId: Long,       // Server message ID
    val conversationCloudId: Long,
    val senderCloudId: Long,
    val senderName: String,
    val localMessageId: String?,          // Local UUID if we sent it
    val kind: String,                     // "text" | "voice" | "system"
    val body: String?,
    val replyToCloudId: Long?,
    val sourceLanguage: String?,
    val createdAt: Long,
    val editedAt: Long?,
    val deletedAt: Long?,
    val syncedAt: Long,
)
```

### SyncQueueEntity

Queue of pending sync operations.

```kotlin
@Entity(
    tableName = "sync_queue",
    indices = [Index(value = ["operationType", "createdAt"])]
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationType: String,           // "upload" | "edit" | "delete" | "read_cursor"
    val localMessageId: String?,
    val conversationCloudId: Long?,
    val messageCloudId: Long?,
    val payload: String,                  // JSON-encoded operation data
    val createdAt: Long,
    val lastAttemptAt: Long?,
    val attemptCount: Int = 0,
    val lastError: String?,
    val status: String,                   // "pending" | "in_progress" | "failed" | "completed"
)
```

---

## Database Migration

### Migration 7 → 8

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Cloud user singleton
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_user (
                id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                cloudUserId INTEGER,
                email TEXT,
                displayName TEXT,
                preferredLanguage TEXT,
                avatarHue INTEGER,
                jwtToken TEXT,
                tokenExpiresAt INTEGER,
                lastSyncedAt INTEGER,
                syncCursor TEXT
            )
        """)
        
        // ID mapping table
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_id_mapping (
                accountCloudUserId INTEGER NOT NULL,
                localType TEXT NOT NULL,
                localId TEXT NOT NULL,
                cloudId INTEGER,
                syncedAt INTEGER,
                syncStatus TEXT NOT NULL DEFAULT 'pending',
                syncError TEXT,
                retryCount INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (accountCloudUserId, localType, localId)
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_cloud_id ON cloud_id_mapping(cloudId)")
        
        // Cloud conversations
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_conversations (
                cloudId INTEGER PRIMARY KEY NOT NULL,
                localConversationId TEXT,
                kind TEXT NOT NULL,
                title TEXT,
                myRole TEXT NOT NULL,
                lastMessageCloudId INTEGER,
                updatedAt INTEGER NOT NULL,
                syncedAt INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_local_conv ON cloud_conversations(localConversationId)")
        
        // Cloud members
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_members (
                conversationCloudId INTEGER NOT NULL,
                userCloudId INTEGER NOT NULL,
                displayName TEXT NOT NULL,
                avatarHue INTEGER NOT NULL,
                PRIMARY KEY (conversationCloudId, userCloudId)
            )
        """)
        
        // Cloud messages
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_messages (
                cloudId INTEGER PRIMARY KEY NOT NULL,
                conversationCloudId INTEGER NOT NULL,
                senderCloudId INTEGER NOT NULL,
                senderName TEXT NOT NULL,
                localMessageId TEXT,
                kind TEXT NOT NULL,
                body TEXT,
                replyToCloudId INTEGER,
                sourceLanguage TEXT,
                createdAt INTEGER NOT NULL,
                editedAt INTEGER,
                deletedAt INTEGER,
                syncedAt INTEGER NOT NULL
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_conv_msg ON cloud_messages(conversationCloudId, cloudId)")
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_local_msg ON cloud_messages(localMessageId)")
        
        // Sync queue
        database.execSQL("""
            CREATE TABLE IF NOT EXISTS sync_queue (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                operationType TEXT NOT NULL,
                localMessageId TEXT,
                conversationCloudId INTEGER,
                messageCloudId INTEGER,
                payload TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                lastAttemptAt INTEGER,
                attemptCount INTEGER NOT NULL DEFAULT 0,
                lastError TEXT,
                status TEXT NOT NULL DEFAULT 'pending'
            )
        """)
        database.execSQL("CREATE INDEX IF NOT EXISTS idx_sync_queue_type ON sync_queue(operationType, createdAt)")
    }
}
```

---

## DAO Interfaces

### CloudUserDao

```kotlin
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
    
    @Query("DELETE FROM cloud_user WHERE id = 1")
    suspend fun clear()
}
```

### CloudIdMappingDao

```kotlin
@Dao
interface CloudIdMappingDao {
    @Query("SELECT cloudId FROM cloud_id_mapping WHERE localType = :type AND localId = :localId")
    suspend fun getCloudId(type: String, localId: String): Long?
    
    @Query("SELECT localId FROM cloud_id_mapping WHERE localType = :type AND cloudId = :cloudId")
    suspend fun getLocalId(type: String, cloudId: Long): String?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: CloudIdMappingEntity)
    
    @Query("UPDATE cloud_id_mapping SET cloudId = :cloudId, syncedAt = :timestamp, syncStatus = 'synced', syncError = NULL WHERE localType = :type AND localId = :localId")
    suspend fun markSynced(type: String, localId: String, cloudId: Long, timestamp: Long)
    
    @Query("UPDATE cloud_id_mapping SET syncStatus = 'error', syncError = :error, retryCount = retryCount + 1 WHERE localType = :type AND localId = :localId")
    suspend fun markError(type: String, localId: String, error: String)
    
    @Query("SELECT * FROM cloud_id_mapping WHERE syncStatus = 'pending' AND localType = 'message'")
    suspend fun pendingMessages(): List<CloudIdMappingEntity>
}
```

### CloudConversationDao

```kotlin
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
}
```

### CloudMessageDao

```kotlin
@Dao
interface CloudMessageDao {
    @Query("SELECT * FROM cloud_messages WHERE conversationCloudId = :conversationId ORDER BY cloudId DESC LIMIT :limit")
    fun observeRecent(conversationId: Long, limit: Int = 100): Flow<List<CloudMessageEntity>>
    
    @Query("SELECT * FROM cloud_messages WHERE conversationCloudId = :conversationId AND cloudId > :afterId ORDER BY cloudId ASC")
    suspend fun getAfter(conversationId: Long, afterId: Long): List<CloudMessageEntity>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(messages: List<CloudMessageEntity>)
    
    @Query("UPDATE cloud_messages SET body = :body, editedAt = :editedAt WHERE cloudId = :cloudId")
    suspend fun updateBody(cloudId: Long, body: String, editedAt: Long)
    
    @Query("UPDATE cloud_messages SET deletedAt = :deletedAt WHERE cloudId = :cloudId")
    suspend fun markDeleted(cloudId: Long, deletedAt: Long)
}
```

### SyncQueueDao

```kotlin
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
}
```

---

## Repository Pattern

### CloudSyncRepository

```kotlin
interface CloudSyncRepository {
    // Auth
    suspend fun login(email: String, password: String): Result<CloudUserEntity>
    suspend fun logout()
    fun observeUser(): Flow<CloudUserEntity?>
    
    // Sync
    suspend fun sync(): Result<SyncResult>
    suspend fun enqueueUpload(localMessageId: String)
    suspend fun enqueueEdit(cloudMessageId: Long, newBody: String)
    suspend fun enqueueDelete(cloudMessageId: Long)
    suspend fun updateReadCursor(conversationCloudId: Long, messageCloudId: Long)
    
    // Queries
    suspend fun getCloudId(localType: String, localId: String): Long?
    suspend fun getLocalId(localType: String, cloudId: Long): String?
}

data class SyncResult(
    val messagesReceived: Int,
    val messagesUploaded: Int,
    val errors: List<SyncError>,
)
```

---

## ID Resolution Flow

### Sending a Message (Local → Cloud)

```
1. User composes message
2. Create MessageEntity with local UUID
3. Insert CloudIdMappingEntity(localId=UUID, cloudId=null, status="pending")
4. Add to SyncQueueEntity(operationType="upload", localMessageId=UUID)
5. Trigger sync worker
6. On success: update CloudIdMappingEntity(cloudId=serverAssigned, status="synced")
```

### Receiving a Message (Cloud → Local)

```
1. Sync returns new CloudMessageEntity
2. If cloudMessage.localMessageId != null:
   - It's our own message, update CloudIdMappingEntity
3. Else:
   - Insert CloudMessageEntity
   - Optionally create local MessageEntity for unified display
   - Insert CloudIdMappingEntity if creating local copy
```

### Displaying Messages

```kotlin
// Unified message view combining local and cloud messages
data class UnifiedMessage(
    val source: MessageSource,
    val cloudId: Long?,
    val localId: String?,
    val senderName: String,
    val body: String,
    val timestamp: Long,
    val syncStatus: SyncStatus,
)

enum class MessageSource { LOCAL_ONLY, CLOUD_ONLY, SYNCED }
enum class SyncStatus { PENDING, SYNCED, ERROR }
```

---

## Offline Behavior

### Without Cloud Account

- All existing nearby/Bluetooth functionality works unchanged
- No sync tables are populated
- `CloudUserEntity` remains null

### With Cloud Account, Offline

- Messages queue in `SyncQueueEntity`
- `WorkManager` retries when connectivity returns
- User sees pending sync indicator
- Local messaging continues to work

### Sync Recovery

```kotlin
// On app start
if (cloudUser != null) {
    workManager.enqueueUniqueWork(
        "cloud_sync",
        ExistingWorkPolicy.KEEP,
        syncWorkRequest()
    )
}

// Sync work constraints
Constraints.Builder()
    .setRequiredNetworkType(NetworkType.CONNECTED)
    .build()
```

---

## Security Considerations

### Token Storage

```kotlin
// Use EncryptedSharedPreferences or Android Keystore
val masterKey = MasterKey.Builder(context)
    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
    .build()

val encryptedPrefs = EncryptedSharedPreferences.create(
    context,
    "cloud_auth",
    masterKey,
    ...
)
```

### Database Encryption (Optional)

Consider SQLCipher for encrypting local Room database containing cloud messages.

---

## Appendix: Entity Relationships

```
CloudUserEntity (singleton)
    │
    └── owns ──► CloudConversationEntity (many)
                    │
                    ├── members ──► CloudMemberEntity (many)
                    │
                    └── contains ──► CloudMessageEntity (many)
                                        │
                                        └── maps ──► CloudIdMappingEntity
                                                        │
                                                        └── references ──► MessageEntity (local)
```

---

## Related Documents

- [ANDROID_CLOUD_SYNC_CONTRACT.md](./ANDROID_CLOUD_SYNC_CONTRACT.md) — Sync protocol specification
- [DATA_MODEL.md](./DATA_MODEL.md) — Server database schema
