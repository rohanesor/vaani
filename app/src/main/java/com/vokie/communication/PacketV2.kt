package com.vokie.communication

import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.semantic.SemanticMetadata
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.zip.CRC32

/** Transport-independent, big-endian Packet v2. Integrity is not encryption or authentication. */
object PacketV2 {
    const val VERSION = 2
    const val MAX_FRAME_BYTES = 16 * 1024
    const val MAX_PAYLOAD_BYTES = 8 * 1024
    private const val MAGIC = 0x5449 // iT
    private const val FLAG_ACK = 1
    /** Optional N5 tail; text-only packets retain their original v2 byte layout. */
    private const val FLAG_SEMANTIC_METADATA = 1 shl 1
    private const val MAX_STRING_BYTES = 512

    data class Fragment(val messageId: String, val sequenceNumber: Long, val sourceDeviceId: String, val timestamp: Long, val ttlMs: Long, val priority: Int, val languageCode: String, val index: Int, val count: Int, val payload: ByteArray, val semanticMetadata: SemanticMetadata? = null)
    sealed interface Decoded { data class MessagePacket(val packet: Fragment, val messageType: MessageType, val receiverId: String?, val requiresAck: Boolean) : Decoded; data class Ack(val messageId: String, val receiverId: String, val timestamp: Long, val sequenceNumber: Long = 0) : Decoded }

    /**
     * Encodes a single, non-retriable 20ms PCM call frame. Media uses the established
     * PacketV2 framing/socket boundary but deliberately has no ACK or persistence path:
     * late audio is less useful than fresh audio.
     */
    fun fromCallAudio(callId: String, sequenceNumber: Long, sourceDeviceId: String, timestamp: Long, pcm: ByteArray): ByteArray {
        require(pcm.isNotEmpty() && pcm.size <= MAX_PAYLOAD_BYTES)
        return encode(
            Fragment(callId, sequenceNumber, sourceDeviceId, timestamp, 0, 0, "EN", 0, 1, pcm),
            MessageType.CALL_AUDIO,
            receiverId = null,
            requiresAck = false,
        )
    }

    fun fromMessage(message: Message, maxPayload: Int = MAX_PAYLOAD_BYTES): List<ByteArray> {
        require(maxPayload in 1..MAX_PAYLOAD_BYTES)
        val bytes = message.text.toByteArray(StandardCharsets.UTF_8)
        val count = ((bytes.size + maxPayload - 1) / maxPayload).coerceAtLeast(1)
        return (0 until count).map { index ->
            val start = index * maxPayload
            val end = minOf(bytes.size, start + maxPayload)
            encode(Fragment(message.id, message.sequenceNumber, message.senderId, message.timestamp, message.ttlMs, message.priority, message.language, index, count, bytes.copyOfRange(start, end), message.semanticMetadata), message.messageType, message.receiverId, message.requiresAck)
        }
    }

    fun encode(fragment: Fragment, type: MessageType = MessageType.TEXT, receiverId: String? = null, requiresAck: Boolean = true): ByteArray {
        validate(fragment)
        val body = ByteArrayOutputStream(); DataOutputStream(body).use { out ->
            out.writeShort(MAGIC); out.writeByte(VERSION); out.writeByte(if (fragment.semanticMetadata == null) 0 else FLAG_SEMANTIC_METADATA)
            string(out, fragment.messageId); out.writeLong(fragment.sequenceNumber); string(out, fragment.sourceDeviceId)
            out.writeLong(fragment.timestamp); out.writeLong(fragment.ttlMs); out.writeByte(fragment.priority)
            string(out, fragment.languageCode); out.writeShort(fragment.index); out.writeShort(fragment.count); out.writeInt(fragment.payload.size); out.write(fragment.payload)
            out.writeByte(type.ordinal); string(out, receiverId.orEmpty()); out.writeBoolean(requiresAck)
            fragment.semanticMetadata?.let { metadata ->
                val bytes = metadata.toWireBytes()
                out.writeByte(bytes.size)
                out.write(bytes)
            }
        }
        val withoutCrc = body.toByteArray(); val crc = CRC32().apply { update(withoutCrc) }.value.toInt()
        return ByteArrayOutputStream().also { stream -> DataOutputStream(stream).use { out -> out.write(withoutCrc); out.writeInt(crc) } }.toByteArray().also { require(it.size <= MAX_FRAME_BYTES) }
    }

    fun encodeAck(messageId: String, receiverId: String, timestamp: Long, sequenceNumber: Long = 0): ByteArray {
        val body = ByteArrayOutputStream(); DataOutputStream(body).use { out -> out.writeShort(MAGIC); out.writeByte(VERSION); out.writeByte(FLAG_ACK); string(out,messageId); out.writeLong(sequenceNumber); string(out,receiverId); out.writeLong(timestamp); out.writeLong(0); out.writeByte(0); string(out,"EN"); out.writeShort(0); out.writeShort(1); out.writeInt(0); out.writeByte(0); string(out,""); out.writeBoolean(false) }
        val raw=body.toByteArray(); val crc=CRC32().apply{update(raw)}.value.toInt(); return ByteArrayOutputStream().also{DataOutputStream(it).use{out->out.write(raw);out.writeInt(crc)}}.toByteArray()
    }

    fun decode(bytes: ByteArray): Decoded {
        require(bytes.size >= 8 && bytes.size <= MAX_FRAME_BYTES)
        val expected = DataInputStream(ByteArrayInputStream(bytes, bytes.size - 4, 4)).readInt()
        val actual = CRC32().apply { update(bytes, 0, bytes.size - 4) }.value.toInt(); require(expected == actual) { "CRC mismatch" }
        return DataInputStream(ByteArrayInputStream(bytes, 0, bytes.size - 4)).use { input ->
            require(input.readUnsignedShort() == MAGIC) { "Invalid packet magic" }; require(input.readUnsignedByte() == VERSION) { "Unsupported packet version" }
            val flags=input.readUnsignedByte(); require(flags and (FLAG_ACK or FLAG_SEMANTIC_METADATA).inv() == 0) { "Invalid flags" }; val id=readString(input); val sequence=input.readLong(); val source=readString(input); val timestamp=input.readLong(); val ttl=input.readLong(); val priority=input.readUnsignedByte(); val language=readString(input); val index=input.readUnsignedShort(); val count=input.readUnsignedShort(); val length=input.readInt(); require(length in 0..MAX_PAYLOAD_BYTES && length <= input.available()) { "Invalid payload length" }; val payload=ByteArray(length); input.readFully(payload); val type=MessageType.entries.getOrNull(input.readUnsignedByte()) ?: error("Invalid message type"); val receiver=readString(input).ifEmpty { null }; val ack=input.readBoolean()
            val metadata = if (flags and FLAG_SEMANTIC_METADATA != 0 && flags and FLAG_ACK == 0) {
                val metadataLength = input.readUnsignedByte()
                require(metadataLength == SemanticMetadata.WIRE_BYTES && metadataLength <= input.available()) { "Invalid semantic metadata" }
                SemanticMetadata.fromWireBytes(ByteArray(metadataLength).also(input::readFully)) ?: error("Invalid semantic metadata")
            } else null
            require(input.available()==0)
            if (flags and FLAG_ACK != 0) {
                require(flags == FLAG_ACK && payload.isEmpty() && index == 0 && count == 1 && !ack && receiver == null) { "Invalid ACK layout" }
                require(id.isNotBlank() && source.isNotBlank() && sequence >= 0 && timestamp > 0) { "Invalid ACK identity" }
                Decoded.Ack(id, source, timestamp, sequence)
            } else {
                val fragment = Fragment(id, sequence, source, timestamp, ttl, priority, language, index, count, payload, metadata)
                // CRC verifies bytes, not their semantics. Apply the same constraints
                // to untrusted inbound fragments as locally encoded fragments.
                validate(fragment)
                Decoded.MessagePacket(fragment, type, receiver, ack)
            }
        }
    }

    fun validate(fragment: Fragment) { require(runCatching{UUID.fromString(fragment.messageId)}.isSuccess); require(fragment.sourceDeviceId.isNotBlank()); require(fragment.sequenceNumber >= 0); require(fragment.timestamp > 0); require(fragment.ttlMs >= 0); require(fragment.priority in 0..255); require(com.vokie.domain.model.VokieLanguage.isSupported(fragment.languageCode)); require(fragment.index in 0 until fragment.count); require(fragment.count > 0); require(fragment.payload.size <= MAX_PAYLOAD_BYTES) }
    private fun string(out: DataOutputStream, value: String) { val bytes=value.toByteArray(StandardCharsets.UTF_8); require(bytes.size<=MAX_STRING_BYTES); out.writeShort(bytes.size);out.write(bytes) }
    private fun readString(input:DataInputStream):String { val n=input.readUnsignedShort();require(n<=MAX_STRING_BYTES&&n<=input.available());val b=ByteArray(n);input.readFully(b);return String(b,StandardCharsets.UTF_8) }
}

class PacketReassembler(private val timeoutMs: Long = 30_000) {
    private data class Pending(val created: Long, val fragments: MutableMap<Int, PacketV2.Decoded.MessagePacket>)
    private data class Key(val source: String, val messageId: String, val sequence: Long)
    private val pending = mutableMapOf<Key, Pending>()
    @Synchronized
    fun add(packet: PacketV2.Decoded.MessagePacket, now: Long = System.currentTimeMillis()): Message? {
        PacketV2.validate(packet.packet)
        cleanup(now); if (packet.packet.ttlMs > 0 && now > packet.packet.timestamp && now - packet.packet.timestamp > packet.packet.ttlMs) return null
        val key = Key(packet.packet.sourceDeviceId, packet.packet.messageId, packet.packet.sequenceNumber)
        val p = pending.getOrPut(key) { Pending(now, mutableMapOf()) }
        val first = p.fragments.values.firstOrNull()
        if (first != null && !sameEnvelope(first, packet)) return null
        if (packet.packet.index in p.fragments) return null
        p.fragments[packet.packet.index] = packet
        if (p.fragments.size != packet.packet.count) return null
        val ordered=(0 until packet.packet.count).map { p.fragments[it] ?: return null }
        // The hint is duplicated per fragment so it survives any arrival order; reject
        // inconsistent hints instead of attaching an arbitrary fragment's value.
        val firstPacket = ordered.first()
        if (ordered.any { it.packet.semanticMetadata != firstPacket.packet.semanticMetadata }) { pending.remove(key); return null }
        val text=ordered.flatMap { it.packet.payload.asIterable() }.toByteArray().toString(StandardCharsets.UTF_8); pending.remove(key)
        val header = firstPacket.packet
        return Message(header.messageId, header.sourceDeviceId, header.timestamp, text, header.languageCode, firstPacket.messageType, receiverId=firstPacket.receiverId, sequenceNumber=header.sequenceNumber, ttlMs=header.ttlMs, priority=header.priority, requiresAck=firstPacket.requiresAck, semanticMetadata=header.semanticMetadata)
    }
    private fun sameEnvelope(a: PacketV2.Decoded.MessagePacket, b: PacketV2.Decoded.MessagePacket): Boolean =
        a.messageType == b.messageType && a.receiverId == b.receiverId && a.requiresAck == b.requiresAck &&
            a.packet.count == b.packet.count && a.packet.timestamp == b.packet.timestamp &&
            a.packet.ttlMs == b.packet.ttlMs && a.packet.priority == b.packet.priority &&
            a.packet.languageCode == b.packet.languageCode && a.packet.semanticMetadata == b.packet.semanticMetadata

    private fun cleanup(now:Long){pending.entries.removeIf{now-it.value.created>timeoutMs}}
}

/** Lightweight replay guard. Persistence of accepted IDs belongs in the Room inbox layer. */
class ReplayGuard(private val retentionMs: Long = 10 * 60 * 1_000L) {
    private val seen = mutableMapOf<String, Long>()
    @Synchronized fun accept(source: String, messageId: String, sequence: Long, now: Long = System.currentTimeMillis()): Boolean {
        seen.entries.removeIf { now - it.value > retentionMs }
        val key = "$source|$messageId|$sequence"
        if (seen.containsKey(key)) return false
        seen[key] = now
        return true
    }
}
