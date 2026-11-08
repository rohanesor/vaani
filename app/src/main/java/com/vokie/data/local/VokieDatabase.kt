package com.vokie.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages ORDER BY timestamp DESC") fun observeAll(): Flow<List<MessageEntity>>
    /** FIFO recovery order: timestamp, then SQLite insertion order for equal timestamps. */
    @Query("SELECT * FROM messages WHERE deliveryState IN ('QUEUED','RETRYING') ORDER BY timestamp ASC, rowid ASC") fun observeOutboundQueue(): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE id = :id") suspend fun find(id: String): MessageEntity?
    @Query("SELECT * FROM messages WHERE senderId = :peerId OR receiverId = :peerId ORDER BY timestamp ASC")
    fun observeByPeer(peerId: String): Flow<List<MessageEntity>>
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun observeByConversation(conversationId: String): Flow<List<MessageEntity>>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(entity: MessageEntity): Long
    @Update suspend fun update(entity: MessageEntity)
    @Query("UPDATE messages SET deliveryState = :state, lastError = :error WHERE id = :id") suspend fun setState(id: String, state: String, error: String? = null)
    @Query("UPDATE messages SET translatedText = :text, translatedLanguage = :language WHERE id = :id") suspend fun setTranslation(id: String, text: String, language: String)
    @Query("UPDATE messages SET deliveryState = 'TRANSMITTING', transport = :transport, lastError = NULL WHERE id = :id") suspend fun markTransmitting(id: String, transport: String)
    @Query("UPDATE messages SET deliveryState = :state, retryCount = retryCount + 1, lastError = :error WHERE id = :id") suspend fun incrementRetry(id: String, state: String, error: String)
    @Query("UPDATE messages SET deliveryState = 'QUEUED', retryCount = 0, lastError = NULL, nextRetryAt = NULL WHERE id = :id") suspend fun resetForManualRetry(id: String)
    @Query("SELECT * FROM messages WHERE deliveryState = 'RETRYING' AND nextRetryAt IS NOT NULL AND nextRetryAt <= :now AND timestamp + ttlMs > :now ORDER BY timestamp ASC") suspend fun retryable(now: Long): List<MessageEntity>
    @Query("UPDATE messages SET deliveryState = 'EXPIRED', lastError = :reason WHERE deliveryState IN ('QUEUED','RETRYING','TRANSMITTING') AND timestamp + ttlMs <= :now") suspend fun expire(now: Long, reason: String = "TTL expired"): Int
    @Query("UPDATE messages SET deliveryState = 'QUEUED', lastError = 'Transmission interrupted; queued after restart' WHERE deliveryState = 'TRANSMITTING'") suspend fun recoverInterrupted()
    @Query("UPDATE messages SET deliveryState = 'QUEUED', lastError = 'Connection interrupted; queued for automatic resend' WHERE deliveryState = 'TRANSMITTING' AND receiverId = :peerId") suspend fun recoverInterruptedForPeer(peerId: String): Int
    @Query("DELETE FROM messages WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface ReceivedPacketDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.IGNORE) suspend fun insert(packet: ReceivedPacketEntity): Long
    @Query("DELETE FROM received_packets WHERE expiresAt <= :now") suspend fun deleteExpired(now: Long): Int
    @Query("SELECT COUNT(*) FROM received_packets WHERE sourceDeviceId = :source AND messageId = :message AND sequenceNumber = :sequence") suspend fun count(source: String, message: String, sequence: Long): Int
}

@Dao interface PeerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(peer: PeerEntity)
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC") fun observeAll(): Flow<List<PeerEntity>>
    @Query("SELECT * FROM peers ORDER BY lastSeen DESC") suspend fun getAll(): List<PeerEntity>
    @Query("UPDATE peers SET sourceLanguage = :lang WHERE id = :id") suspend fun updateSourceLanguage(id: String, lang: String?)
    @Query("UPDATE peers SET targetLanguage = :lang WHERE id = :id") suspend fun updateTargetLanguage(id: String, lang: String?)
    @Query("UPDATE peers SET priority = :priority WHERE id = :id") suspend fun updatePriority(id: String, priority: Int)
    @Query("UPDATE peers SET connectionState = :state WHERE id = :id") suspend fun updateConnectionState(id: String, state: String)
}
@Dao interface TransportEventDao { @Insert suspend fun insert(event: TransportEventEntity); @Query("SELECT * FROM transport_events ORDER BY timestamp DESC LIMIT :limit") fun observeRecent(limit: Int = 100): Flow<List<TransportEventEntity>> }

@Database(
    entities = [
        MessageEntity::class,
        PeerEntity::class,
        TrustedPeerEntity::class,
        PairRequestEntity::class,
        ConversationEntity::class,
        MessageDeliveryEntity::class,
        TransportEventEntity::class,
        EmergencyAlertEntity::class,
        ReceivedPacketEntity::class,
        AppSettingsEntity::class,
        // Cloud sync entities (v8)
        CloudUserEntity::class,
        CloudIdMappingEntity::class,
        CloudConversationEntity::class,
        CloudMemberEntity::class,
        CloudMessageEntity::class,
        SyncQueueEntity::class,
    ],
    version = 10,
    exportSchema = true
)
abstract class VokieDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun receivedPackets(): ReceivedPacketDao
    abstract fun peers(): PeerDao
    abstract fun trustedPeers(): TrustedPeerDao
    abstract fun pairRequests(): PairRequestDao
    abstract fun conversations(): ConversationDao
    abstract fun messageDeliveries(): MessageDeliveryDao
    abstract fun transportEvents(): TransportEventDao
    // Cloud sync DAOs
    abstract fun cloudUser(): CloudUserDao
    abstract fun cloudIdMapping(): CloudIdMappingDao
    abstract fun cloudConversations(): CloudConversationDao
    abstract fun cloudMembers(): CloudMemberDao
    abstract fun cloudMessages(): CloudMessageDao
    abstract fun syncQueue(): SyncQueueDao

    companion object {
        /** Add the local conversation reference already used by the message repository. */
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN conversationId TEXT")
                database.execSQL("""CREATE TABLE IF NOT EXISTS trusted_peers (peerId TEXT NOT NULL PRIMARY KEY, displayName TEXT NOT NULL, createdAt INTEGER NOT NULL, acceptedAt INTEGER, lastConnectedAt INTEGER, status TEXT NOT NULL)""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS pair_requests (requestId TEXT NOT NULL PRIMARY KEY, senderPeerId TEXT NOT NULL, receiverPeerId TEXT NOT NULL, createdAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL, status TEXT NOT NULL)""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS conversations (conversationId TEXT NOT NULL PRIMARY KEY, peerId TEXT NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, unreadCount INTEGER NOT NULL)""")
                database.execSQL("""CREATE TABLE IF NOT EXISTS message_deliveries (messageId TEXT NOT NULL, peerId TEXT NOT NULL, transport TEXT NOT NULL, status TEXT NOT NULL, sentAt INTEGER NOT NULL, deliveredAt INTEGER, acknowledgedAt INTEGER, failureReason TEXT, PRIMARY KEY(messageId, peerId))""")
            }
        }

        /** Remove the active token column from the Room-facing schema. */
        val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE cloud_user_new (
                        id INTEGER NOT NULL PRIMARY KEY DEFAULT 1,
                        cloudUserId INTEGER,
                        email TEXT,
                        displayName TEXT,
                        preferredLanguage TEXT,
                        avatarHue INTEGER,
                        legacyJwtToken TEXT,
                        tokenExpiresAt INTEGER,
                        lastSyncedAt INTEGER,
                        syncCursor TEXT
                    )
                """.trimIndent())
                database.execSQL("""
                    INSERT INTO cloud_user_new
                    SELECT id, cloudUserId, email, displayName, preferredLanguage, avatarHue,
                           jwtToken, tokenExpiresAt, lastSyncedAt, syncCursor
                    FROM cloud_user
                """.trimIndent())
                database.execSQL("DROP TABLE cloud_user")
                database.execSQL("ALTER TABLE cloud_user_new RENAME TO cloud_user")
            }
        }

        /** Cloud sync tables for Android-server synchronization. */
        val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
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
                """.trimIndent())

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
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cloud_id_mapping_cloudId ON cloud_id_mapping(cloudId)")

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
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cloud_conversations_localConversationId ON cloud_conversations(localConversationId)")

                // Cloud members
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS cloud_members (
                        conversationCloudId INTEGER NOT NULL,
                        userCloudId INTEGER NOT NULL,
                        displayName TEXT NOT NULL,
                        avatarHue INTEGER NOT NULL,
                        PRIMARY KEY (conversationCloudId, userCloudId)
                    )
                """.trimIndent())

                // Cloud messages
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS cloud_messages (
                        cloudId INTEGER PRIMARY KEY NOT NULL,
                        conversationCloudId INTEGER NOT NULL,
                        senderCloudId INTEGER,
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
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cloud_messages_conversationCloudId_cloudId ON cloud_messages(conversationCloudId, cloudId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_cloud_messages_localMessageId ON cloud_messages(localMessageId)")

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
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_sync_queue_operationType_createdAt ON sync_queue(operationType, createdAt)")
            }
        }

        /** Translation is receiver-local presentation data; source packet text is unchanged. */
        val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN translatedText TEXT")
                database.execSQL("ALTER TABLE messages ADD COLUMN translatedLanguage TEXT")
            }
        }
        val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN semanticMetadata TEXT")
            }
        }
        val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE peers ADD COLUMN sourceLanguage TEXT")
                database.execSQL("ALTER TABLE peers ADD COLUMN targetLanguage TEXT")
                database.execSQL("ALTER TABLE peers ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) { override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) { database.execSQL("ALTER TABLE messages ADD COLUMN nextRetryAt INTEGER") } }
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) { database.execSQL("CREATE TABLE IF NOT EXISTS received_packets (sourceDeviceId TEXT NOT NULL, messageId TEXT NOT NULL, sequenceNumber INTEGER NOT NULL, receivedAt INTEGER NOT NULL, expiresAt INTEGER NOT NULL, PRIMARY KEY(sourceDeviceId, messageId, sequenceNumber))") }
        }
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE messages ADD COLUMN sequenceNumber INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE messages ADD COLUMN ttlMs INTEGER NOT NULL DEFAULT 300000")
                database.execSQL("ALTER TABLE messages ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE messages ADD COLUMN checksum INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var instance: VokieDatabase? = null
        fun get(context: android.content.Context): VokieDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, VokieDatabase::class.java, "vokie.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                .build().also { instance = it }
        }
    }
}
