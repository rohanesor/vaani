package com.vokie.communication

import com.vokie.domain.model.Message
import java.util.UUID

/** Compatibility facade retained for callers while wire framing is Packet v2. */
object VokieProtocol {
    const val VERSION = PacketV2.VERSION
    const val SERVICE_NAME = "iTantra Emergency Communication"
    val SERVICE_UUID: UUID = UUID.fromString("8f6f3a10-4c5b-4d22-9f1c-2c7d9f4e1a01")
    const val MAX_FRAME_BYTES = PacketV2.MAX_FRAME_BYTES
    const val MAX_TEXT_CHARS = 4096

    fun newMessageId(): String = UUID.randomUUID().toString()
    fun encode(message: Message): ByteArray { validate(message); return PacketV2.fromMessage(message).singleOrNull() ?: error("Message requires fragmentation; use PacketV2.fromMessage") }
    fun encodeAck(messageId: String, receiverId: String, timestamp: Long): ByteArray = PacketV2.encodeAck(messageId, receiverId, timestamp)
    fun decode(bytes: ByteArray): DecodedFrame = when (val decoded=PacketV2.decode(bytes)) {
        is PacketV2.Decoded.Ack -> DecodedFrame.Ack(decoded.messageId, decoded.receiverId, decoded.timestamp)
        is PacketV2.Decoded.MessagePacket -> {
            val p=decoded.packet
            require(p.index==0 && p.count==1) { "Fragmented packet requires PacketReassembler" }
            DecodedFrame.MessageFrame(Message(p.messageId,p.sourceDeviceId,p.timestamp,p.payload.toString(Charsets.UTF_8),p.languageCode,decoded.messageType,receiverId=decoded.receiverId,requiresAck=decoded.requiresAck,sequenceNumber=p.sequenceNumber,ttlMs=p.ttlMs,priority=p.priority,semanticMetadata=p.semanticMetadata))
        }
    }
    fun validate(message: Message) { require(message.id.isNotBlank() && message.senderId.isNotBlank()); require(message.timestamp>0); require(com.vokie.domain.model.VokieLanguage.isSupported(message.language)); require(message.text.isNotBlank() && message.text.length<=MAX_TEXT_CHARS); require(message.hopCount in 0..32); require(message.ttlMs>=0); require(message.priority in 0..255) }
    sealed interface DecodedFrame { data class Ack(val messageId:String,val receiverId:String,val timestamp:Long):DecodedFrame; data class MessageFrame(val message:Message):DecodedFrame }
}
