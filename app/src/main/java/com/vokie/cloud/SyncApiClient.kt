package com.vokie.cloud

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Sync API client implementing the M7 sync contract.
 * All requests use HTTPS and require authentication.
 */
private fun utcNow(): String = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}.format(Date())

class SyncApiClient(
    private val baseUrl: String,
    private val tokenProvider: suspend () -> String?,
) {
    companion object {
        const val SYNC_VERSION = 1
        private const val CONNECT_TIMEOUT_MS = 10_000
        private const val READ_TIMEOUT_MS = 60_000
    }

    /**
     * Performs a sync request.
     * @throws SyncException on protocol or network errors
     */
    suspend fun sync(request: SyncRequest): SyncResponse = withContext(Dispatchers.IO) {
        require(baseUrl.startsWith("https://") || baseUrl.startsWith("http://10.0.2.2") || baseUrl.startsWith("http://localhost")) {
            "Cloud sync requires HTTPS except local development endpoints"
        }
        val token = tokenProvider() ?: throw SyncException.AuthRequired("Authentication required")

        val url = URL("${baseUrl.trimEnd('/')}/api/sync")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-Vaani-Sync-Version", SYNC_VERSION.toString())
            setRequestProperty("X-Request-Id", java.util.UUID.randomUUID().toString())
            doOutput = true
        }

        try {
            // Write request body
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(request.toJson().toString())
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            when (responseCode) {
                200 -> parseResponse(responseBody)
                400 -> {
                    val error = runCatching { JSONObject(responseBody) }.getOrNull()
                    val code = error?.optString("code").orEmpty()
                    when (code) {
                        "sync_version_required" -> throw SyncException.ProtocolError("Sync version header required")
                        "unsupported_sync_version" -> throw SyncException.ProtocolError("Sync version not supported")
                        else -> throw SyncException.BadRequest(error?.optString("message") ?: "Invalid request")
                    }
                }
                401 -> throw SyncException.AuthRequired("Session expired")
                429 -> {
                    val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                    throw SyncException.RateLimited(retryAfter ?: 60)
                }
                in 500..599 -> throw SyncException.ServerError("Server error: $responseCode", retryable = true)
                else -> throw SyncException.ServerError("Unexpected response: $responseCode", retryable = false)
            }
        } catch (e: SyncException) {
            throw e
        } catch (e: java.net.SocketTimeoutException) {
            throw SyncException.NetworkError("Connection timeout", retryable = true)
        } catch (e: java.io.IOException) {
            throw SyncException.NetworkError("Network error: ${e.message}", retryable = true)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseResponse(json: String): SyncResponse {
        val root = JSONObject(json)

        val uploadResults = mutableListOf<UploadResult>()
        root.optJSONArray("uploadResults")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                uploadResults.add(UploadResult(
                    clientId = item.getString("clientId"),
                    status = item.getString("status"),
                    cloudId = item.optLong("cloudId").takeIf { it > 0 },
                    error = item.optJSONObject("error")?.let { err ->
                        UploadError(
                            code = err.getString("code"),
                            message = err.getString("message"),
                            retryable = err.getBoolean("retryable")
                        )
                    }
                ))
            }
        }

        val messages = mutableListOf<SyncMessage>()
        root.optJSONArray("messages")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                messages.add(SyncMessage(
                    id = item.getLong("id"),
                    conversationId = item.getLong("conversationId"),
                    senderId = item.optLong("senderId").takeIf { !item.isNull("senderId") },
                    senderName = item.optString("senderName", "Unknown"),
                    kind = item.getString("kind"),
                    body = item.optString("body").takeIf { !item.isNull("body") },
                    replyTo = item.optLong("replyTo").takeIf { !item.isNull("replyTo") && it > 0 },
                    clientId = item.optString("clientId").takeIf { !item.isNull("clientId") },
                    sourceLanguage = item.optString("sourceLanguage").takeIf { !item.isNull("sourceLanguage") },
                    createdAt = item.getString("createdAt"),
                    editedAt = item.optString("editedAt").takeIf { !item.isNull("editedAt") },
                    deletedAt = item.optString("deletedAt").takeIf { !item.isNull("deletedAt") },
                ))
            }
        }

        val conversations = mutableListOf<SyncConversation>()
        root.optJSONArray("conversations")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val members = mutableListOf<SyncMember>()
                item.optJSONArray("members")?.let { membersArray ->
                    for (j in 0 until membersArray.length()) {
                        val member = membersArray.getJSONObject(j)
                        members.add(SyncMember(
                            id = member.getLong("id"),
                            displayName = member.getString("displayName"),
                            avatarHue = member.getInt("avatarHue")
                        ))
                    }
                }
                conversations.add(SyncConversation(
                    id = item.getLong("id"),
                    kind = item.getString("kind"),
                    title = item.optString("title").takeIf { !item.isNull("title") },
                    members = members,
                    lastMessageId = item.optLong("lastMessageId").takeIf { !item.isNull("lastMessageId") && it > 0 },
                    updatedAt = item.getString("updatedAt"),
                    myRole = item.getString("myRole")
                ))
            }
        }

        return SyncResponse(
            cursor = root.getString("cursor"),
            uploadResults = uploadResults,
            messages = messages,
            conversations = conversations,
            hasMore = root.optBoolean("hasMore", false),
            serverTime = root.getString("serverTime")
        )
    }
}

// ============ Request/Response Models ============

data class SyncRequest(
    val cursor: String?,
    val upload: List<UploadMessage>,
    val readCursors: List<ReadCursorUpdate>,
    val edits: List<MessageEdit>,
    val deletions: List<MessageDeletion>,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("cursor", cursor)
        put("upload", JSONArray().apply {
            upload.forEach { msg ->
                put(JSONObject().apply {
                    put("clientId", msg.clientId)
                    put("conversationCloudId", msg.conversationCloudId)
                    put("body", msg.body)
                    put("kind", msg.kind)
                    msg.replyTo?.let { put("replyTo", it) }
                    msg.sourceLanguage?.let { put("sourceLanguage", it) }
                    put("clientTimestamp", msg.clientTimestamp)
                })
            }
        })
        put("readCursors", JSONArray().apply {
            readCursors.forEach { rc ->
                put(JSONObject().apply {
                    put("conversationCloudId", rc.conversationCloudId)
                    put("lastReadMessageId", rc.lastReadMessageId)
                })
            }
        })
        put("edits", JSONArray().apply {
            edits.forEach { edit ->
                put(JSONObject().apply {
                    put("messageCloudId", edit.messageCloudId)
                    put("newBody", edit.newBody)
                    put("clientTimestamp", edit.clientTimestamp)
                })
            }
        })
        put("deletions", JSONArray().apply {
            deletions.forEach { del ->
                put(JSONObject().apply {
                    put("messageCloudId", del.messageCloudId)
                    put("clientTimestamp", del.clientTimestamp)
                })
            }
        })
    }
}

data class UploadMessage(
    val clientId: String,
    val conversationCloudId: Long,
    val body: String,
    val kind: String = "text",
    val replyTo: Long? = null,
    val sourceLanguage: String? = null,
    val clientTimestamp: String = utcNow(),
)

data class ReadCursorUpdate(
    val conversationCloudId: Long,
    val lastReadMessageId: Long,
)

data class MessageEdit(
    val messageCloudId: Long,
    val newBody: String,
    val clientTimestamp: String = utcNow(),
)

data class MessageDeletion(
    val messageCloudId: Long,
    val clientTimestamp: String = utcNow(),
)

data class SyncResponse(
    val cursor: String,
    val uploadResults: List<UploadResult>,
    val messages: List<SyncMessage>,
    val conversations: List<SyncConversation>,
    val hasMore: Boolean,
    val serverTime: String,
)

data class UploadResult(
    val clientId: String,
    val status: String, // "created" | "exists" | "error"
    val cloudId: Long?,
    val error: UploadError?,
)

data class UploadError(
    val code: String,
    val message: String,
    val retryable: Boolean,
)

data class SyncMessage(
    val id: Long,
    val conversationId: Long,
    val senderId: Long?,
    val senderName: String,
    val kind: String,
    val body: String?,
    val replyTo: Long?,
    val clientId: String?,
    val sourceLanguage: String?,
    val createdAt: String,
    val editedAt: String?,
    val deletedAt: String?,
)

data class SyncConversation(
    val id: Long,
    val kind: String,
    val title: String?,
    val members: List<SyncMember>,
    val lastMessageId: Long?,
    val updatedAt: String,
    val myRole: String,
)

data class SyncMember(
    val id: Long,
    val displayName: String,
    val avatarHue: Int,
)

// ============ Exceptions ============

sealed class SyncException(message: String, val retryable: Boolean) : Exception(message) {
    class SyncDisabled(message: String) : SyncException(message, false)
    class AuthRequired(message: String) : SyncException(message, false)
    class ProtocolError(message: String) : SyncException(message, false)
    class BadRequest(message: String) : SyncException(message, false)
    class RateLimited(val retryAfterSeconds: Long) : SyncException("Rate limited", true)
    class NetworkError(message: String, retryable: Boolean) : SyncException(message, retryable)
    class ServerError(message: String, retryable: Boolean) : SyncException(message, retryable)
}
