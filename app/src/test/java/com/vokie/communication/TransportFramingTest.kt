package com.vokie.communication

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.ByteArrayOutputStream

class TransportFramingTest {
    @Test fun lengthPrefixHandlesExactPacket() = runBlocking {
        val packet = byteArrayOf(1, 2, 3)
        val out = ByteArrayOutputStream(); LengthPrefixedFrames.write(DataOutputStream(out), packet)
        assertArrayEquals(packet, LengthPrefixedFrames.read(DataInputStream(ByteArrayInputStream(out.toByteArray()))))
    }

    @Test fun oversizedFrameIsRejected() = runBlocking {
        val out = ByteArrayOutputStream(); DataOutputStream(out).writeInt(PacketV2.MAX_FRAME_BYTES + 1)
        try { LengthPrefixedFrames.read(DataInputStream(ByteArrayInputStream(out.toByteArray()))); throw AssertionError("expected rejection") } catch (_: IllegalArgumentException) { }
    }
}
