package com.vokie.stt

import org.junit.Assert.*
import org.junit.Test

class TurnTimingRecorderTest {
    private var now = 0L
    private fun recorder() = TurnTimingRecorder { now }
    private fun tick(ms: Long = 10) { now += ms * 1_000_000L }

    @Test fun completeTimelineCalculatesDerivedMetrics() {
        val r=recorder(); r.start("t"); tick(); r.endpoint("t"); tick(); r.sttComplete("t"); tick(); r.packetCreated("t","m"); tick(); r.packetReceived("m"); tick(); r.translationComplete("m"); tick(); r.ttsStart("m"); tick(); r.audioReady("m"); tick(); r.playbackStart("m")
        val s=r.snapshotForMessage("m")!!
        assertEquals(TurnTimingStatus.COMPLETE,s.status); assertEquals(10L,s.speechDurationMs); assertEquals(10L,s.sttLatencyMs); assertEquals(80L,s.endToEndLatencyMs); assertEquals(60L,s.postSttLatencyMs)
    }
    @Test fun incompleteDoesNotInventZeroLatency() { val r=recorder(); r.start("t"); tick(); r.endpoint("t"); val s=r.snapshotForTurn("t")!!; assertEquals(TurnTimingStatus.INCOMPLETE,s.status); assertNull(s.sttLatencyMs); assertNull(s.endToEndLatencyMs) }
    @Test fun duplicateEventsKeepFirstTimestamp() { val r=recorder(); r.start("t"); tick(7); r.start("t"); assertEquals(0L,r.snapshotForTurn("t")!!.t0SpeechStartNs) }
    @Test fun receiverOnlyTimelineCorrelatesByMessage() { val r=recorder(); r.packetReceived("m"); tick(); r.translationComplete("m"); assertEquals(10L,r.snapshotForMessage("m")!!.translationLatencyMs); assertEquals(null,r.snapshotForMessage("m")!!.turnId) }
    @Test fun ttsFailureIsExplicit() { val r=recorder(); r.ttsStart("m"); r.fail(null,"m",TurnTimingFailure.TTS); assertEquals(TurnTimingStatus.FAILED,r.snapshotForMessage("m")!!.status); assertEquals(TurnTimingFailure.TTS,r.snapshotForMessage("m")!!.failure) }
    @Test fun nonMonotonicDeltaIsAbsent() { val r=recorder(); now=10; r.start("t"); now=1; r.endpoint("t"); assertNull(r.snapshotForTurn("t")!!.speechDurationMs) }
    @Test fun p19BoundariesPreserveRequiredLocalOrder() {
        val r = recorder(); r.start("t"); tick(); r.sttStart("t"); tick(); r.sttComplete("t", "Help", 500L); tick()
        r.associateMessage("t", "m"); r.packetCreated("m", 1L)
        val s = r.snapshotForMessage("m")!!
        assertTrue(s.t0SpeechStartNs!! < s.sttStartNs!!)
        assertTrue(s.sttStartNs!! < s.t2SttCompleteNs!!)
        assertTrue(s.t2SttCompleteNs!! < s.t3PacketCreatedNs!!)
    }
}
