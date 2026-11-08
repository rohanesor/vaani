package com.vokie.communication

import com.vokie.data.VokieMessageId
import com.vokie.domain.model.Message
import com.vokie.semantic.*
import org.junit.Assert.*
import org.junit.Test

class SemanticPacketV2Test {
    private val metadata = SemanticMetadata(
        eventCategory = SemanticEventCategory.MEDICAL,
        urgency = SemanticUrgency.URGENT,
        severity = SemanticSeverity.HIGH,
        hazardFlags = setOf(SemanticHazard.GAS),
        victimCount = 1,
        certainty = SemanticCertainty.EXPLICIT,
    )

    @Test fun `semantic packet preserves full transcript and metadata`() {
        val message = Message(VokieMessageId.generate(), "peer-A", 1_000, "I need a doctor", "EN", receiverId = "peer-B", semanticMetadata = metadata)
        val decoded = PacketV2.decode(PacketV2.fromMessage(message).single()) as PacketV2.Decoded.MessagePacket
        assertEquals("I need a doctor", decoded.packet.payload.toString(Charsets.UTF_8))
        assertEquals(metadata, decoded.packet.semanticMetadata)
    }

    @Test fun `legacy text only packet byte layout has no semantic tail`() {
        val message = Message(VokieMessageId.generate(), "peer-A", 1_000, "Help me", "EN", receiverId = "peer-B")
        val decoded = PacketV2.decode(PacketV2.fromMessage(message).single()) as PacketV2.Decoded.MessagePacket
        assertNull(decoded.packet.semanticMetadata)
        assertEquals("Help me", decoded.packet.payload.toString(Charsets.UTF_8))
    }

    @Test fun `semantic reassembly keeps text authoritative across fragments`() {
        val text = "help ".repeat(3_000)
        val message = Message(VokieMessageId.generate(), "peer-A", System.currentTimeMillis(), text, "EN", receiverId = "peer-B", semanticMetadata = metadata)
        val reassembler = PacketReassembler()
        val result = PacketV2.fromMessage(message).map { PacketV2.decode(it) as PacketV2.Decoded.MessagePacket }
            .mapNotNull { reassembler.add(it) }.single()
        assertEquals(text, result.text)
        assertEquals(metadata, result.semanticMetadata)
    }

    @Test fun `inconsistent semantic hints across fragments are rejected`() {
        val now = System.currentTimeMillis()
        val id = VokieMessageId.generate()
        val first = PacketV2.Decoded.MessagePacket(PacketV2.Fragment(id, 0, "peer-A", now, 30_000, 0, "EN", 0, 2, "one ".toByteArray(), metadata), com.vokie.domain.model.MessageType.TEXT, null, true)
        val second = PacketV2.Decoded.MessagePacket(PacketV2.Fragment(id, 0, "peer-A", now, 30_000, 0, "EN", 1, 2, "two".toByteArray(), null), com.vokie.domain.model.MessageType.TEXT, null, true)
        val reassembler = PacketReassembler()
        assertNull(reassembler.add(first))
        assertNull(reassembler.add(second))
    }

    @Test fun `metadata overhead is fixed eight bytes per fragment`() {
        val plain = Message(VokieMessageId.generate(), "peer-A", 1_000, "Help me", "EN")
        val enriched = plain.copy(semanticMetadata = metadata)
        assertEquals(SemanticMetadata.WIRE_BYTES + 1, PacketV2.fromMessage(enriched).single().size - PacketV2.fromMessage(plain).single().size)
    }
}
