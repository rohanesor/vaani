package com.vokie.communication

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallJitterBufferTest {
    @Test fun `waits for initial jitter delay then plays sequence order`() {
        val buffer = CallJitterBuffer(framesForInitialPlayout = 2, maxFrames = 4, frameBytes = 4)
        val first = byteArrayOf(1, 1, 1, 1)
        val second = byteArrayOf(2, 2, 2, 2)
        buffer.offer(8, first)
        assertNull(buffer.poll())
        buffer.offer(9, second)
        assertArrayEquals(first, buffer.poll())
        assertArrayEquals(second, buffer.poll())
    }

    @Test fun `returns silence for a lost frame after playout begins`() {
        val buffer = CallJitterBuffer(framesForInitialPlayout = 1, maxFrames = 4, frameBytes = 4)
        buffer.offer(3, byteArrayOf(3, 3, 3, 3))
        assertArrayEquals(byteArrayOf(3, 3, 3, 3), buffer.poll())
        assertArrayEquals(ByteArray(4), buffer.poll())
    }

    @Test fun `re-buffers after prolonged underrun and accepts a restarted sequence`() {
        val buffer = CallJitterBuffer(framesForInitialPlayout = 1, maxFrames = 4, frameBytes = 4)
        buffer.offer(1, byteArrayOf(1, 1, 1, 1))
        assertArrayEquals(byteArrayOf(1, 1, 1, 1), buffer.poll())
        repeat(3) { assertArrayEquals(ByteArray(4), buffer.poll()) }
        assertNull(buffer.poll())
        buffer.offer(0, byteArrayOf(9, 9, 9, 9))
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), buffer.poll())
    }
}
