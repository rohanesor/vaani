package com.vokie.location

import com.vokie.communication.PacketV2
import com.vokie.domain.model.Message
import com.vokie.domain.model.MessageType
import com.vokie.domain.model.VokieLanguage
import java.util.Locale
import java.util.UUID

/** Explicit LOCATION payload carried by the existing CRC/fragment/ACK PacketV2 message envelope. */
object LocationPacket {
    data class Transmission(val messageId: String, val sequenceNumber: Long, val frames: List<ByteArray>)
    private const val DEFAULT_TTL_MS = 2 * 60_000L

    fun encode(senderId: String, fix: LocationMetadata, ttlMs: Long = DEFAULT_TTL_MS, priority: Int = 255): Transmission {
        require(fix.availability == LocationAvailability.AVAILABLE) { "Only valid location fixes may be transmitted." }
        val payload = listOf(
            requireNotNull(fix.latitude), requireNotNull(fix.longitude), requireNotNull(fix.accuracyMeters),
            requireNotNull(fix.timestamp), fix.locationSequence,
        ).joinToString(",") { value -> if (value is Number) String.format(Locale.US, "%s", value) else value.toString() }
        val message = Message(UUID.randomUUID().toString(), senderId, requireNotNull(fix.timestamp), payload, VokieLanguage.EN.code,
            MessageType.LOCATION, sequenceNumber = fix.locationSequence, ttlMs = ttlMs, priority = priority)
        return Transmission(message.id, message.sequenceNumber, PacketV2.fromMessage(message))
    }

    fun decode(message: Message): LocationMetadata? {
        if (message.messageType != MessageType.LOCATION) return null
        val values = message.text.split(',')
        if (values.size != 5) return null
        val latitude = values[0].toDoubleOrNull()
        val longitude = values[1].toDoubleOrNull()
        val accuracy = values[2].toFloatOrNull()
        val timestamp = values[3].toLongOrNull()
        val sequence = values[4].toLongOrNull()
        return runCatching { locationMetadataFromRaw(latitude ?: Double.NaN, longitude ?: Double.NaN, accuracy, timestamp ?: 0, sequence ?: -1) }.getOrNull()
    }
}
