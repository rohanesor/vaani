package com.vokie.communication

import com.vokie.domain.model.Message
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.util.zip.CRC32

/** Step 2 untrusted packet boundary; no radio or physical-test claims. */
class PeerPacketBoundaryTest {
    private val id = "11111111-1111-1111-1111-111111111111"
    private fun message(source: String = "A", sequence: Long = 1, text: String = "abcd") =
        Message(id, source, 1000, text, "EN", receiverId = "local", sequenceNumber = sequence, ttlMs = 60000)
    private fun fragments(message: Message) = PacketV2.fromMessage(message, 2).map {
        PacketV2.decode(it) as PacketV2.Decoded.MessagePacket
    }

    @Test fun callAudioFramePreservesPcmWithoutAck() {
        val pcm = ByteArray(640) { (it % 127).toByte() }
        val decoded = PacketV2.decode(PacketV2.fromCallAudio(id, 42, "peer", 1000, pcm)) as PacketV2.Decoded.MessagePacket
        assertEquals(com.vokie.domain.model.MessageType.CALL_AUDIO, decoded.messageType)
        assertFalse(decoded.requiresAck)
        assertEquals(42, decoded.packet.sequenceNumber)
        assertArrayEquals(pcm, decoded.packet.payload)
    }

    @Test fun sameMessageIdFromDifferentPeersDoesNotMix() {
        val a = fragments(message())
        val b = fragments(message("B", text = "wxyz"))
        val r = PacketReassembler()
        assertNull(r.add(a[0], 1001))
        assertNull(r.add(b[1], 1001))
        assertEquals("abcd", r.add(a[1], 1001)?.text)
        assertEquals("wxyz", r.add(b[0], 1001)?.text)
    }

    @Test fun separateSequencesDoNotMix() {
        val a = fragments(message())
        val b = fragments(message(sequence = 2, text = "wxyz"))
        val r = PacketReassembler()
        assertNull(r.add(a[0], 1001))
        assertNull(r.add(b[1], 1001))
        assertEquals(1L, r.add(a[1], 1001)?.sequenceNumber)
        assertEquals(2L, r.add(b[0], 1001)?.sequenceNumber)
    }

    @Test fun conflictingReceiverIsRejectedWithoutDestroyingOtherFragments() {
        val f = fragments(message())
        val r = PacketReassembler()
        assertNull(r.add(f[0], 1001))
        assertNull(r.add(f[1].copy(receiverId = "somebody-else"), 1001))
        assertEquals("local", r.add(f[1], 1001)?.receiverId)
    }

    @Test fun conflictingFragmentCountIsRejected() {
        val f = fragments(message())
        val r = PacketReassembler()
        assertNull(r.add(f[0], 1001))
        assertNull(r.add(f[1].copy(packet = f[1].packet.copy(count = 3)), 1001))
        assertEquals("abcd", r.add(f[1], 1001)?.text)
    }

    @Test fun noAckFlagSurvivesReassembly() {
        val r = PacketReassembler()
        val f = fragments(message().copy(requiresAck = false))
        assertNull(r.add(f[0], 1001))
        assertEquals(false, r.add(f[1], 1001)?.requiresAck)
    }

    @Test fun validCrcDoesNotPermitNegativeSequence() {
        val bytes = PacketV2.fromMessage(message()).single()
        // magic/version/flags, length-prefixed ID, then sequence.
        val sequenceOffset = 4 + 2 + id.toByteArray().size
        ByteBuffer.wrap(bytes).putLong(sequenceOffset, -1)
        repairCrc(bytes)
        assertThrows(IllegalArgumentException::class.java) { PacketV2.decode(bytes) }
    }

    @Test fun validCrcDoesNotPermitUnsupportedLanguage() {
        val bytes = PacketV2.fromMessage(message()).single()
        val languageOffset = 4 + 2 + id.length + 8 + 2 + 1 + 8 + 8 + 1 + 2
        bytes[languageOffset] = 'X'.code.toByte()
        bytes[languageOffset + 1] = 'X'.code.toByte()
        repairCrc(bytes)
        assertThrows(IllegalArgumentException::class.java) { PacketV2.decode(bytes) }
    }

    @Test fun invalidAckSenderIsRejected() {
        val bytes = PacketV2.encodeAck(id, "", 1000, 1)
        assertThrows(IllegalArgumentException::class.java) { PacketV2.decode(bytes) }
    }

    @Test fun validAckRemainsCompatible() {
        assertEquals(PacketV2.Decoded.Ack(id, "B", 1000, 1),
            PacketV2.decode(PacketV2.encodeAck(id, "B", 1000, 1)))
    }

    private fun repairCrc(bytes: ByteArray) {
        val crc = CRC32().apply { update(bytes, 0, bytes.size - 4) }.value.toInt()
        ByteBuffer.wrap(bytes).putInt(bytes.size - 4, crc)
    }
}
