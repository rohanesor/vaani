package com.vokie.tts

import com.vokie.communication.VokieLog
import com.vokie.domain.model.MessageType
import com.vokie.stt.TurnTimingFailure
import com.vokie.stt.TurnTimingRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

internal class TtsPriorityQueue {
    private val items = ArrayDeque<TtsQueueItem>()
    fun add(item: TtsQueueItem) { if (item.messageType == MessageType.SOS) items.addFirst(item) else items.addLast(item) }
    fun removeFirstOrNull(): TtsQueueItem? = if (items.isEmpty()) null else items.removeFirst()
    fun remove(messageId: String) { items.removeAll { it.messageId == messageId } }
    fun ids(): List<String> = items.map { it.messageId }
}

/** Small application-scoped sequential queue. Only one model inference or AudioTrack is active. */
class TtsPlaybackQueue(
    private val engine: TtsEngine,
    private val speed: StateFlow<Float>,
    private val scope: CoroutineScope,
    private val timing: TurnTimingRecorder? = null,
) {
    private val lock = Mutex()
    private val queue = TtsPriorityQueue()
    private val signal = Channel<Unit>(Channel.CONFLATED)
    private val activeIds = mutableSetOf<String>()
    private val _messageStates = MutableStateFlow<Map<String, MessageTtsState>>(emptyMap())
    val messageStates: StateFlow<Map<String, MessageTtsState>> = _messageStates.asStateFlow()
    private var worker: Job? = null
    private var current: TtsQueueItem? = null
    private val workerId = nextWorkerId.incrementAndGet()

    fun start() {
        if (worker?.isActive == true) return
        VokieLog.tts("TTS_QUEUE_WORKER_STARTED worker_id=$workerId worker_count=1")
        worker = scope.launch {
            for (ignored in signal) {
                while (true) {
                    val item = lock.withLock { queue.removeFirstOrNull()?.also { current = it } } ?: break
                    process(item)
                    lock.withLock {
                        activeIds.remove(item.messageId); current = null
                        VokieLog.tts("TTS_QUEUE_DRAINED message_id=${item.messageId} queue_depth=${queue.ids().size} active_jobs=${activeIds.size}")
                    }
                }
            }
        }
    }

    suspend fun enqueue(item: TtsQueueItem) {
        var preemptNormalPlayback: Boolean
        lock.withLock {
            if (!activeIds.add(item.messageId)) {
                VokieLog.tts("TTS_ENQUEUE_REJECTED message_id=${item.messageId} job_id=${item.messageId} source=active_message_guard")
                return
            }
            preemptNormalPlayback = item.messageType == MessageType.SOS && current != null && current?.messageType != MessageType.SOS
            queue.add(item)
            setState(item.messageId, MessageTtsState.QUEUED)
            VokieLog.tts("TTS_ENQUEUE message_id=${item.messageId} job_id=${item.messageId} source=receiver_handoff queued=${queue.ids().size}")
        }
        if (preemptNormalPlayback) engine.stop()
        signal.trySend(Unit)
    }

    suspend fun stop(messageId: String? = null, acknowledgedSos: Boolean = false) {
        lock.withLock {
            if (messageId != null) {
                queue.remove(messageId); activeIds.remove(messageId)
                if (current?.messageId != messageId) setState(messageId, if (acknowledgedSos) MessageTtsState.COMPLETED else MessageTtsState.FAILED)
            } else {
                queue.ids().forEach { setState(it, MessageTtsState.FAILED); activeIds.remove(it) }
                queue.ids().forEach(queue::remove)
            }
        }
        if (messageId == null || current?.messageId == messageId) {
            engine.stop()
            current?.messageId?.let { setState(it, if (acknowledgedSos) MessageTtsState.COMPLETED else MessageTtsState.FAILED) }
        }
    }

    fun release() {
        worker?.cancel(); worker = null
        engine.release()
        signal.close()
    }

    private suspend fun process(item: TtsQueueItem) {
        try {
            setState(item.messageId, MessageTtsState.SYNTHESIZING)
            VokieLog.tts("TTS_START message_id=${item.messageId} job_id=${item.messageId} source=playback_queue")
            timing?.ttsStart(item.messageId)
            val synthesis = engine.synthesize(item.text, item.language, speed.value)
            val audio = synthesis.first
            val result = synthesis.second
            VokieLog.tts("TTS_REQUEST_PROFILE message_id=${item.messageId} run_id=${item.messageId} language=${item.language.iso6393} text_length=${result.textLength} token_count=${result.tokenCount ?: "unavailable"} model_load_ms=${result.modelLoadTimeMs} preprocessing_ms=${result.preprocessingTimeMs} native_pipeline_ms=${result.nativePipelineTimeMs} audio_buffer_ms=${result.audioBufferTimeMs} generated_audio_ms=${result.audioDurationMs} rtf=${result.realTimeFactor} model_instance_id=${result.modelInstanceId}")
            timing?.audioReady(item.messageId, audio.durationMs, result.realTimeFactor)
            setState(item.messageId, MessageTtsState.PLAYING)
            // Engine.play enters AudioTrack playback immediately after queue handoff.
            timing?.playbackStart(item.messageId)
            engine.play(audio, emergency = item.messageType == MessageType.SOS)
            timing?.playbackComplete(item.messageId)
            setState(item.messageId, MessageTtsState.COMPLETED)
            VokieLog.tts("TTS_COMPLETE message_id=${item.messageId} job_id=${item.messageId} source=playback_queue")
        } catch (error: Throwable) {
            VokieLog.tts("TTS_FAILED message_id=${item.messageId} job_id=${item.messageId} source=playback_queue error=${error.message}")
            timing?.fail(null, item.messageId, TurnTimingFailure.TTS)
            setState(item.messageId, MessageTtsState.FAILED)
        }
    }

    private fun setState(messageId: String, state: MessageTtsState) {
        _messageStates.value = _messageStates.value + (messageId to state)
    }

    private companion object { val nextWorkerId = AtomicLong(0) }
}
