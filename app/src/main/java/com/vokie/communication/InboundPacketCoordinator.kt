package com.vokie.communication

import com.vokie.data.MessageRepository
import com.vokie.data.local.ReceivedPacketDao
import com.vokie.data.local.ReceivedPacketEntity
import com.vokie.domain.model.Message
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** Single authoritative raw-packet inbound path for every transport. */
class InboundPacketCoordinator(
    private val repository: MessageRepository,
    private val replayDao: ReceivedPacketDao,
    private val reassembler: PacketReassembler = PacketReassembler(),
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private val _messages = MutableSharedFlow<Message>(extraBufferCapacity = 32)
    val messages: SharedFlow<Message> = _messages.asSharedFlow()

    suspend fun accept(bytes: ByteArray, transport: PacketTransport, acknowledge: suspend (PacketTransport, String, Long) -> Unit) {
        VokieLog.bt("PACKET_RX_BUFFER length=${bytes.size}")
        val decoded = runCatching { PacketV2.decode(bytes) }.getOrElse {
            VokieLog.bt("PACKET_DECODE_FAILURE reason=${it.message}")
            return
        }
        if (decoded is PacketV2.Decoded.Ack) return
        acceptDecoded(decoded as PacketV2.Decoded.MessagePacket, transport, acknowledge)
    }

    /** Called by the single transport dispatcher after PacketV2 has been decoded once. */
    suspend fun acceptDecoded(packet: PacketV2.Decoded.MessagePacket, transport: PacketTransport, acknowledge: suspend (PacketTransport, String, Long) -> Unit) {
        VokieLog.bt("FRAME_DISPATCH type=MESSAGE id=${packet.packet.messageId}")
        VokieLog.bt("FRAGMENT_RX messageId=${packet.packet.messageId} index=${packet.packet.index} count=${packet.packet.count}")
        val complete = reassembler.add(packet, now()) ?: return
        VokieLog.bt("MESSAGE_REASSEMBLED messageId=${complete.id}")
        val expiry = if (complete.ttlMs == 0L) Long.MAX_VALUE else complete.timestamp + complete.ttlMs
        if (now() >= expiry) return
        val replayInserted = replayDao.insert(ReceivedPacketEntity(complete.senderId, complete.id, complete.sequenceNumber, now(), expiry))
        if (replayInserted == -1L) {
            VokieLog.bt("MESSAGE_DUPLICATE messageId=${complete.id}")
            acknowledge(transport, complete.id, complete.sequenceNumber)
            return
        }
        val inserted = repository.persistIncoming(complete)
        VokieLog.bt(if (inserted) "MESSAGE_RECEIVED messageId=${complete.id}" else "MESSAGE_DUPLICATE messageId=${complete.id}")
        // ACK both first delivery and duplicates so a lost ACK can recover, but only
        // publish a newly persisted message to the presentation layer.
        acknowledge(transport, complete.id, complete.sequenceNumber)
        if (inserted) _messages.emit(complete)
    }
}
