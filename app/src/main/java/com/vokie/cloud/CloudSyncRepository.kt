package com.vokie.cloud

import com.vokie.data.local.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Repository for cloud sync operations.
 * Coordinates between the sync API client and local Room database.
 */
class CloudSyncRepository(
    private val cloudUserDao: CloudUserDao,
    private val cloudIdMappingDao: CloudIdMappingDao,
    private val cloudConversationDao: CloudConversationDao,
    private val cloudMemberDao: CloudMemberDao,
    private val cloudMessageDao: CloudMessageDao,
    private val syncQueueDao: SyncQueueDao,
    private val localMessageDao: MessageDao,
    private val apiClient: SyncApiClient,
    private val credentials: SecureCredentialStore,
    private val syncPreferences: CloudSyncPreferences,
) {
    // ============ Authentication ============

    fun observeUser(): Flow<CloudUserEntity?> = cloudUserDao.observe()

    suspend fun getUser(): CloudUserEntity? = cloudUserDao.get()

    suspend fun isAuthenticated(): Boolean {
        val user = cloudUserDao.get() ?: return false
        val token = credentials.token() ?: return false
        val expiresAt = user.tokenExpiresAt ?: return false
        return token.isNotBlank() && expiresAt > System.currentTimeMillis()
    }

    /** Moves the one-time legacy Room token into Keystore, then clears the Room field. */
    suspend fun migrateLegacyCredential(): Boolean {
        val user = cloudUserDao.get() ?: return true
        val legacy = user.legacyJwtToken ?: return true
        return runCatching {
            credentials.saveToken(legacy)
            cloudUserDao.clearLegacyToken()
            true
        }.getOrElse {
            credentials.clear()
            false
        }
    }

    suspend fun saveAuthState(
        cloudUserId: Long,
        email: String,
        displayName: String,
        preferredLanguage: String,
        avatarHue: Int,
        token: String,
        expiresAt: Long,
    ) {
        credentials.saveToken(token)
        syncPreferences.rotateSession()
        cloudUserDao.upsert(CloudUserEntity(
            id = 1,
            cloudUserId = cloudUserId,
            email = email,
            displayName = displayName,
            preferredLanguage = preferredLanguage,
            avatarHue = avatarHue,
            legacyJwtToken = null,
            tokenExpiresAt = expiresAt,
            lastSyncedAt = null,
            syncCursor = null,
        ))
    }

    suspend fun logout() {
        // Clear all cloud sync data and account-scoped mappings before another account can use this DB.
        val accountId = cloudUserDao.get()?.cloudUserId
        if (accountId != null) cloudIdMappingDao.clearAccount(accountId)
        credentials.clear()
        syncPreferences.rotateSession()
        syncPreferences.setEnabled(false)
        cloudUserDao.clear()
        cloudConversationDao.clear()
        cloudMessageDao.clear()
        syncQueueDao.clear()
    }

    // ============ Sync Operations ============

    fun sessionGeneration(): String = syncPreferences.sessionGeneration

    /**
     * Performs a full sync cycle: upload pending messages, download changes.
     * @return SyncResult with statistics
     * @throws SyncException on errors
     */
    suspend fun sync(): SyncResult {
        val user = cloudUserDao.get() ?: throw SyncException.AuthRequired("Not authenticated")
        if (!syncPreferences.enabled.value) throw SyncException.SyncDisabled("Cloud sync is disabled")
        val accountId = user.cloudUserId ?: throw SyncException.AuthRequired("Cloud account identity is missing")
        val cursor = user.syncCursor

        // Collect pending uploads for the active account only.
        val pendingMappings = cloudIdMappingDao.pendingMessages(accountId)
        val uploads = mutableListOf<UploadMessage>()

        for (mapping in pendingMappings.take(50)) {
            val localMsg = localMessageDao.find(mapping.localId) ?: continue
            // Need cloud conversation ID - skip if we don't have it
            val convMapping = cloudIdMappingDao.get(accountId, "conversation", localMsg.conversationId ?: continue)
            val convCloudId = convMapping?.cloudId ?: continue

            uploads.add(UploadMessage(
                clientId = mapping.localId,
                conversationCloudId = convCloudId,
                body = localMsg.text,
                kind = if (localMsg.messageType == "VOICE") "voice" else "text",
                sourceLanguage = localMsg.language,
                clientTimestamp = formatTimestamp(localMsg.timestamp),
            ))
        }

        // Build sync request
        val request = SyncRequest(
            cursor = cursor,
            upload = uploads,
            readCursors = emptyList(), // TODO: implement read cursor sync
            edits = emptyList(), // TODO: implement edit sync
            deletions = emptyList(), // TODO: implement deletion sync
        )

        // Execute sync
        val response = apiClient.sync(request)

        // Process upload results
        var uploadedCount = 0
        var uploadErrorCount = 0
        for (result in response.uploadResults) {
            when (result.status) {
                "created", "exists" -> {
                    result.cloudId?.let { cloudId ->
                        cloudIdMappingDao.markSynced(
                            accountId = accountId,
                            type = "message",
                            localId = result.clientId,
                            cloudId = cloudId,
                            timestamp = System.currentTimeMillis()
                        )
                        uploadedCount++
                    }
                }
                "error" -> {
                    val error = result.error
                    if (error != null && !error.retryable) {
                        // Permanent error - mark as failed
                        cloudIdMappingDao.markError(accountId, "message", result.clientId, error.message)
                    }
                    uploadErrorCount++
                }
            }
        }

        // Process downloaded conversations
        val now = System.currentTimeMillis()
        for (conv in response.conversations) {
            cloudConversationDao.upsert(CloudConversationEntity(
                cloudId = conv.id,
                localConversationId = null, // Link later if needed
                kind = conv.kind,
                title = conv.title,
                myRole = conv.myRole,
                lastMessageCloudId = conv.lastMessageId,
                updatedAt = parseTimestamp(conv.updatedAt),
                syncedAt = now,
            ))

            // Update members
            cloudMemberDao.clearForConversation(conv.id)
            cloudMemberDao.upsertAll(conv.members.map { m ->
                CloudMemberEntity(
                    conversationCloudId = conv.id,
                    userCloudId = m.id,
                    displayName = m.displayName,
                    avatarHue = m.avatarHue,
                )
            })
        }

        // Process downloaded messages
        val downloadedMessages = response.messages.map { msg ->
            CloudMessageEntity(
                cloudId = msg.id,
                conversationCloudId = msg.conversationId,
                senderCloudId = msg.senderId,
                senderName = msg.senderName,
                localMessageId = msg.clientId,
                kind = msg.kind,
                body = msg.body,
                replyToCloudId = msg.replyTo,
                sourceLanguage = msg.sourceLanguage,
                createdAt = parseTimestamp(msg.createdAt),
                editedAt = msg.editedAt?.let { parseTimestamp(it) },
                deletedAt = msg.deletedAt?.let { parseTimestamp(it) },
                syncedAt = now,
            )
        }
        cloudMessageDao.upsertAll(downloadedMessages)

        // Update ID mappings for received messages
        for (msg in response.messages) {
            msg.clientId?.let { clientId ->
                cloudIdMappingDao.markSynced(accountId, "message", clientId, msg.id, now)
            }
        }

        // Update sync cursor
        cloudUserDao.updateSyncState(response.cursor, now)

        val pageResult = SyncResult(
            messagesUploaded = uploadedCount,
            uploadErrors = uploadErrorCount,
            messagesDownloaded = downloadedMessages.size,
            conversationsUpdated = response.conversations.size,
        )
        // Continue with the returned cursor only after this page was persisted.
        return if (response.hasMore) {
            val next = sync()
            SyncResult(
                messagesUploaded = pageResult.messagesUploaded + next.messagesUploaded,
                uploadErrors = pageResult.uploadErrors + next.uploadErrors,
                messagesDownloaded = pageResult.messagesDownloaded + next.messagesDownloaded,
                conversationsUpdated = pageResult.conversationsUpdated + next.conversationsUpdated,
            )
        } else pageResult
    }

    // ============ Message Queue ============

    /**
     * Queues a local message for cloud sync.
     * Call this after creating a local message that should be synced.
     */
    suspend fun queueForSync(localMessageId: String, conversationLocalId: String?) {
        if (!syncPreferences.enabled.value || conversationLocalId.isNullOrBlank()) return
        val accountId = cloudUserDao.get()?.cloudUserId
            ?: return
        // Do not queue until the local conversation is linked to this account.
        if (cloudIdMappingDao.get(accountId, "conversation", conversationLocalId)?.cloudId == null) return
        // Create pending mapping for the active account.
        cloudIdMappingDao.upsert(CloudIdMappingEntity(
            accountCloudUserId = accountId,
            localType = "message",
            localId = localMessageId,
            cloudId = null,
            syncedAt = null,
            syncStatus = "pending",
            syncError = null,
            retryCount = 0,
        ))
    }

    /**
     * Gets the cloud ID for a local message ID.
     */
    suspend fun getCloudMessageId(localId: String): Long? {
        val accountId = cloudUserDao.get()?.cloudUserId ?: return null
        return cloudIdMappingDao.getCloudId(accountId, "message", localId)
    }

    /**
     * Gets the local ID for a cloud message ID.
     */
    suspend fun getLocalMessageId(cloudId: Long): String? {
        val accountId = cloudUserDao.get()?.cloudUserId ?: return null
        return cloudIdMappingDao.getLocalId(accountId, "message", cloudId)
    }

    // ============ Conversations ============

    fun observeCloudConversations(): Flow<List<CloudConversationEntity>> =
        cloudConversationDao.observeAll()

    suspend fun getCloudConversation(cloudId: Long): CloudConversationEntity? =
        cloudConversationDao.get(cloudId)

    suspend fun linkLocalConversation(cloudId: Long, localId: String): Boolean {
        val cloudConversation = cloudConversationDao.get(cloudId) ?: return false
        cloudConversationDao.linkLocal(cloudId, localId)
        val accountId = cloudUserDao.get()?.cloudUserId
            ?: throw SyncException.AuthRequired("Not authenticated")
        cloudIdMappingDao.upsert(CloudIdMappingEntity(
            accountCloudUserId = accountId,
            localType = "conversation",
            localId = localId,
            cloudId = cloudConversation.cloudId,
            syncedAt = System.currentTimeMillis(),
            syncStatus = "synced",
            syncError = null,
            retryCount = 0,
        ))
        return true
    }

    // ============ Messages ============

    fun observeCloudMessages(conversationCloudId: Long, limit: Int = 100): Flow<List<CloudMessageEntity>> =
        cloudMessageDao.observeRecent(conversationCloudId, limit)

    // ============ Helpers ============

    private fun formatTimestamp(timestamp: Long): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }.format(Date(timestamp))

    private fun parseTimestamp(iso8601: String): Long {
        val formats = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
        )
        for (pattern in formats) {
            runCatching {
                return SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.parse(iso8601)?.time ?: 0L
            }
        }
        return System.currentTimeMillis()
    }
}

data class SyncResult(
    val messagesUploaded: Int,
    val uploadErrors: Int,
    val messagesDownloaded: Int,
    val conversationsUpdated: Int,
) {
    val isSuccess: Boolean get() = uploadErrors == 0
}
