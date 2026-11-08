package com.vokie.ai

import org.junit.Assert.assertEquals
import org.junit.Test

class AiContractsTest {
    @Test fun `retrieval ranks matching conversation text without returning all history`() {
        val messages = listOf(
            AiMessage("old", "a", "We considered SQLite.", 1),
            AiMessage("decision", "b", "The backend architecture uses Kotlin services.", 2),
            AiMessage("new", "a", "See you tomorrow.", 3),
        )
        val result = ConversationContextRetriever().relevant(messages, "What backend architecture decision?", 2)
        assertEquals(listOf("decision", "new"), result.map { it.id })
    }

    @Test(expected = IllegalArgumentException::class)
    fun `translation requires target language`() {
        AiRequest(AiOperation.TRANSLATE, "c", "u", messages = emptyList()).validate()
    }
}
