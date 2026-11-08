package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.VokieLanguage
import org.junit.Assert.*
import org.junit.Test

class PacketV2Test {
    private fun message(text: String = "தமிழ் உதவி") = Message("11111111-1111-1111-1111-111111111111", "phone-a", 1_700_000_000_000, text, VokieLanguage.TA.code, sequenceNumber = 9, ttlMs = 60_000, priority = 2)

    @Test fun deterministicRoundTripAndCrc() {
        val packet = PacketV2.fromMessage(message()).single()
        assertArrayEquals(packet, PacketV2.fromMessage(message()).single())
        assertEquals("TA", (PacketV2.decode(packet) as PacketV2.Decoded.MessagePacket).packet.languageCode)
    }

    @Test fun crcRejectsCorruptionAndTruncation() {
        val packet = PacketV2.fromMessage(message()).single(); packet[packet.lastIndex - 5] = (packet[packet.lastIndex - 5] + 1).toByte()
        assertFails { PacketV2.decode(packet) }; assertFails { PacketV2.decode(packet.copyOf(packet.size - 1)) }
    }

    @Test fun fragmentationReassemblesOutOfOrderAndIgnoresDuplicate() {
        val packets = PacketV2.fromMessage(message("x".repeat(20_000)), maxPayload = 1000).map { PacketV2.decode(it) as PacketV2.Decoded.MessagePacket }
        assertTrue(packets.size > 1)
        val reassembler = PacketReassembler(); val now = 1_700_000_001_000
        assertNull(reassembler.add(packets[1], now)); assertNull(reassembler.add(packets[1], now))
        var result: Message? = null
        (packets.filterIndexed { i, _ -> i != 1 } + packets[1]).forEach { result = reassembler.add(it, now) ?: result }
        assertEquals(20_000, result?.text?.length); assertEquals("TA", result?.language)
    }

    @Test fun replayGuardRejectsDuplicateAndExpires() {
        val guard = ReplayGuard(100); assertTrue(guard.accept("a", "m", 1, 10)); assertFalse(guard.accept("a", "m", 1, 20)); assertTrue(guard.accept("a", "m", 1, 111))
    }

    @Test fun invalidLanguageIsRejected() {
        val fragment = PacketV2.Fragment("11111111-1111-1111-1111-111111111111", 1, "a", 10, 100, 0, "XX", 0, 1, byteArrayOf())
        assertFails { PacketV2.encode(fragment) }
    }

    private fun assertFails(block: () -> Unit) { try { block(); fail("expected failure") } catch (_: IllegalArgumentException) {} catch (_: IllegalStateException) {} }
}
