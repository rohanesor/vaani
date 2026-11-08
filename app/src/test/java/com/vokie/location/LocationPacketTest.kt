package com.vokie.location

import com.vokie.communication.PacketReassembler
import com.vokie.communication.PacketV2
import com.vokie.domain.model.MessageType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LocationPacketTest {
    @Test fun validFixRoundTripsThroughExistingPacketEnvelope() {
        val fix = locationMetadataFromRaw(12.9716, 77.5946, 12f, 1_700_000_000_000, 7)
        val transmission = LocationPacket.encode("sender", fix)
        val decoded = PacketV2.decode(transmission.frames.single()) as PacketV2.Decoded.MessagePacket
        assertEquals(MessageType.LOCATION, decoded.messageType)
        val message = requireNotNull(PacketReassembler().add(decoded, 1_700_000_000_001))
        val recovered = requireNotNull(LocationPacket.decode(message))
        assertEquals(fix.latitude, recovered.latitude)
        assertEquals(fix.longitude, recovered.longitude)
        assertEquals(fix.locationSequence, recovered.locationSequence)
    }
}
