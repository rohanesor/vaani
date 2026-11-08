package com.vokie.communication

import java.util.TreeMap

/** Small bounded, sequence-based PCM jitter buffer for 20ms call frames. */
internal class CallJitterBuffer(
    private val framesForInitialPlayout: Int = 3,
    private val maxFrames: Int = 10,
    private val frameBytes: Int = 640,
) {
    private val frames = TreeMap<Long, ByteArray>()
    private var nextSequence: Long? = null
    private var started = false
    private var consecutiveUnderruns = 0

    @Synchronized
    fun offer(sequence: Long, pcm: ByteArray) {
        if (pcm.size != frameBytes) return
        val next = nextSequence
        // A remote capture restart or a prolonged local underrun needs a clean resync;
        // never let an old expected sequence permanently reject fresh speech.
        if (started && next != null && (sequence - next > 20 || next - sequence > 50)) {
            frames.clear(); nextSequence = null; started = false; consecutiveUnderruns = 0
        }
        if (started && nextSequence != null && sequence < nextSequence!!) return
        if (frames.containsKey(sequence)) return
        frames[sequence] = pcm.copyOf()
        while (frames.size > maxFrames) frames.pollFirstEntry()
    }

    /** Null means waiting/rebuffering. At most three silence frames bridge a short gap. */
    @Synchronized
    fun poll(): ByteArray? {
        if (!started) {
            if (frames.size < framesForInitialPlayout) return null
            nextSequence = frames.firstKey()
            started = true
            consecutiveUnderruns = 0
        }
        val expected = nextSequence ?: return null
        val frame = frames.remove(expected)
        if (frame != null) {
            nextSequence = expected + 1
            consecutiveUnderruns = 0
            return frame
        }
        if (frames.isNotEmpty()) {
            // Loss inside an otherwise live stream: conceal one 20ms frame.
            nextSequence = expected + 1
            consecutiveUnderruns = 0
            return ByteArray(frameBytes)
        }
        // Do not increment forever during an empty Bluetooth/Wi-Fi interval.
        consecutiveUnderruns++
        if (consecutiveUnderruns <= 3) return ByteArray(frameBytes)
        started = false
        nextSequence = null
        consecutiveUnderruns = 0
        return null
    }

    @Synchronized fun clear() {
        frames.clear()
        nextSequence = null
        started = false
        consecutiveUnderruns = 0
    }
}
