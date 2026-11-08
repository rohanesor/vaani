package com.vokie.ai

/** Operations are explicit so generated content is never treated as a sent message. */
enum class AiOperation { REPLY, REWRITE, TRANSLATE, SUMMARIZE, SEARCH, EXTRACT_ACTIONS, VOICE_TO_ACTION }

data class AiMessage(val id: String, val senderId: String, val text: String, val timestamp: Long)
data class AiCitation(val messageId: String, val excerpt: String)
data class SuggestedAction(
    val type: String,
    val title: String,
    val date: String? = null,
    val time: String? = null,
    val requiresConfirmation: Boolean = true,
)
data class AiRequest(
    val operation: AiOperation,
    val conversationId: String,
    val requesterId: String,
    val prompt: String = "",
    val messages: List<AiMessage>,
    val targetLanguage: String? = null,
) {
    fun validate() {
        require(conversationId.isNotBlank()) { "conversationId is required" }
        require(requesterId.isNotBlank()) { "requesterId is required" }
        require(messages.size <= 24) { "At most 24 relevant messages may be sent" }
        require(messages.all { it.text.isNotBlank() && it.text.length <= 4_000 }) { "Invalid message context" }
        if (operation == AiOperation.TRANSLATE) require(!targetLanguage.isNullOrBlank()) { "targetLanguage is required" }
    }
}
data class AiResponse(val text: String, val citations: List<AiCitation> = emptyList(), val actions: List<SuggestedAction> = emptyList())

interface AIProvider { suspend fun assist(request: AiRequest): AiResponse }

/** Selects local, relevant records before any cloud provider is invoked. */
class ConversationContextRetriever {
    fun relevant(messages: List<AiMessage>, query: String, limit: Int = 12): List<AiMessage> {
        require(limit in 1..24)
        val terms = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.toSet()
        return messages.sortedWith(compareByDescending<AiMessage> { message ->
            if (terms.isEmpty()) 0 else message.text.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).count { it in terms }
        }.thenByDescending { it.timestamp }).take(limit)
    }
}
