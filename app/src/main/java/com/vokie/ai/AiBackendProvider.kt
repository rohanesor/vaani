package com.vokie.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Auth is supplied by a future signed-in session; this class never owns a provider secret. */
fun interface AiSessionTokenProvider { suspend fun token(): String? }
class UnconfiguredAIProvider : AIProvider {
    override suspend fun assist(request: AiRequest): AiResponse = error("AI assistance is not configured")
}

class VaaniBackendProvider(
    private val baseUrl: String,
    private val tokenProvider: AiSessionTokenProvider,
) : AIProvider {
    override suspend fun assist(request: AiRequest): AiResponse = withContext(Dispatchers.IO) {
        request.validate()
        require(baseUrl.startsWith("https://")) { "AI backend must use HTTPS" }
        val token = tokenProvider.token() ?: error("Sign in is required for AI assistance")
        val connection = (URL(baseUrl.trimEnd('/') + "/v1/ai/assist").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10_000; readTimeout = 45_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            doOutput = true
        }
        try {
            connection.outputStream.bufferedWriter().use { it.write(request.toJson().toString()) }
            val stream = if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (connection.responseCode !in 200..299) error("AI request failed (${connection.responseCode})")
            response.toAiResponse()
        } finally { connection.disconnect() }
    }
}

private fun AiRequest.toJson() = JSONObject().apply {
    put("operation", operation.name); put("conversationId", conversationId); put("requesterId", requesterId)
    put("prompt", prompt); put("targetLanguage", targetLanguage)
    put("messages", JSONArray().apply { messages.forEach { m -> put(JSONObject().apply {
        put("id", m.id); put("senderId", m.senderId); put("text", m.text); put("timestamp", m.timestamp)
    }) } })
}
private fun String.toAiResponse(): AiResponse {
    val root = JSONObject(this)
    val citations = root.optJSONArray("citations")?.let { array -> List(array.length()) { i -> array.getJSONObject(i).let { AiCitation(it.getString("messageId"), it.getString("excerpt")) } } }.orEmpty()
    val actions = root.optJSONArray("actions")?.let { array -> List(array.length()) { i -> array.getJSONObject(i).let { SuggestedAction(it.getString("type"), it.getString("title"), it.optString("date").ifBlank { null }, it.optString("time").ifBlank { null }, it.optBoolean("requiresConfirmation", true)) } } }.orEmpty()
    return AiResponse(root.optString("text"), citations, actions)
}
