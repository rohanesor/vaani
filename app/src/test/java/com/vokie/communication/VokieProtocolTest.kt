package com.vokie.communication

import com.vokie.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class VokieProtocolTest {
    private fun message(text: String = "எனக்கு உதவி தேவை") = Message(
        id = VokieProtocol.newMessageId(), senderId = "sender-1", receiverId = "receiver-1",
        timestamp = 1_700_000_000_000, text = text, language = VokieLanguage.TA.code,
        messageType = MessageType.TEXT, hopCount = 1,
    )

    @Test fun messageRoundTripIsDeterministic() {
        val original = message()
        val first = VokieProtocol.encode(original)
        assertArrayEquals(first, VokieProtocol.encode(original))
        val decoded = (VokieProtocol.decode(first) as VokieProtocol.DecodedFrame.MessageFrame).message
        assertEquals(original.id, decoded.id); assertEquals(original.text, decoded.text)
        assertEquals(original.receiverId, decoded.receiverId); assertEquals("TA", decoded.language)
        assertTrue(decoded.requiresAck)
    }

    @Test fun acknowledgementReferencesOriginalMessage() {
        val bytes = VokieProtocol.encodeAck("message-1", "receiver-1", 1234)
        val ack = VokieProtocol.decode(bytes) as VokieProtocol.DecodedFrame.Ack
        assertEquals("message-1", ack.messageId); assertEquals("receiver-1", ack.receiverId); assertEquals(1234, ack.timestamp)
    }

    @Test fun generatedIdsAreUniqueUuidValues() {
        val ids = (1..1000).map { VokieProtocol.newMessageId() }
        assertEquals(ids.size, ids.toSet().size)
        ids.forEach(java.util.UUID::fromString)
    }

    @Test fun unknownProtocolVersionIsRejected() {
        val frame = VokieProtocol.encode(message()); frame[2] = 99
        assertFails { VokieProtocol.decode(frame) }
    }

    @Test fun malformedAndOversizedPayloadsAreRejected() {
        assertFails { VokieProtocol.decode(byteArrayOf(1, 2, 3)) }
        assertFails { VokieProtocol.encode(message("x".repeat(VokieProtocol.MAX_TEXT_CHARS + 1))) }
        assertFails { VokieProtocol.decode(ByteArray(VokieProtocol.MAX_FRAME_BYTES + 1)) }
    }

    @Test fun unsupportedLanguageAndInvalidIdAreRejected() {
        assertFails { VokieProtocol.encode(message().copy(language = "XX")) }
        assertFails { VokieProtocol.encode(message().copy(id = "")) }
    }

    private fun assertFails(block: () -> Unit) { try { block(); fail("Expected validation failure") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
}
