package com.vokie.stt

import com.vokie.communication.VokieLog
import com.vokie.domain.model.VokieLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * SIH-L10-C1 — ContinuousTurnManager sits above [SttEngine] and exposes turn-scoped
 * events without modifying recorder, VAD, Whisper JNI, PacketV2, or transport. It
 * preserves the existing single-utterance capture semantics: PTT release finalises
 * one turn; continuous mode auto-resumes capture after each finalised result.
 *
 * Partial transcript emission is architected but not enabled today because the
 * vendored whisper.cpp integration is one-shot; [TranscriptEvent.Partial] is
 * reserved for a future streaming STT.
 */
enum class TurnMode { PUSH_TO_TALK, CONTINUOUS }
enum class TurnState { IDLE, LISTENING, PROCESSING, SENTENCE_READY, STOPPED, ERROR }

sealed interface TranscriptEvent {
    val text: String; val language: VokieLanguage
    data class Partial(override val text: String, override val language: VokieLanguage) : TranscriptEvent
    data class Final(override val text: String, override val language: VokieLanguage, val sentences: List<String>) : TranscriptEvent
}

sealed interface TurnEvent {
    val turnId: String
    data class Started(override val turnId: String, val mode: TurnMode, val language: VokieLanguage, val startedAtMs: Long) : TurnEvent
    data class Sentence(override val turnId: String, val index: Int, val text: String, val language: VokieLanguage) : TurnEvent
    data class TurnCompleted(
        override val turnId: String,
        val sentenceCount: Int,
        val sttProcessingMs: Long,
        val audioDurationMs: Long,
        val startedAtMs: Long,
        val completedAtMs: Long,
    ) : TurnEvent
    data class Error(override val turnId: String, val code: SttErrorCode, val message: String) : TurnEvent
    data class Stopped(override val turnId: String) : TurnEvent
}

/**
 * Deterministic, script-agnostic sentence splitter. Terminators are kept attached to
 * the preceding sentence; whitespace-only trailing text is discarded. This is a
 * bounded heuristic; it is not a general NLP sentence tokenizer.
 */
class SentenceSegmenter(private val terminators: Set<Char> = DEFAULT_TERMINATORS) {
    fun split(text: String, @Suppress("UNUSED_PARAMETER") language: VokieLanguage? = null): List<String> {
        if (text.isEmpty()) return emptyList()
        val sentences = mutableListOf<String>()
        val current = StringBuilder()
        text.forEach { ch ->
            current.append(ch)
            if (ch in terminators) {
                val chunk = current.toString().trim()
                if (chunk.isNotEmpty()) sentences += chunk
                current.clear()
            }
        }
        val tail = current.toString().trim()
        if (tail.isNotEmpty()) sentences += tail
        return sentences
    }
    companion object {
        // ASCII '.', '!', '?' plus Devanagari purna viraam (U+0964) and double danda (U+0965).
        val DEFAULT_TERMINATORS: Set<Char> = setOf('.', '!', '?', '\u0964', '\u0965')
    }
}

internal class TurnSubmissionGate(private val maxEntries: Int = 256) {
    private val claimed = LinkedHashSet<String>()

    @Synchronized
    fun claim(turnId: String, sentenceIndex: Int): Boolean {
        val key = "$turnId:$sentenceIndex"
        if (!claimed.add(key)) return false
        while (claimed.size > maxEntries) claimed.remove(claimed.first())
        return true
    }
}

class ContinuousTurnManager(
    private val stt: SttEngine,
    private val scope: CoroutineScope,
    private val segmenter: SentenceSegmenter = SentenceSegmenter(),
    private val clockMs: () -> Long = { System.nanoTime() / 1_000_000L },
    private val timing: TurnTimingRecorder? = null,
) {
    private val _events = MutableSharedFlow<TurnEvent>(replay = 16, extraBufferCapacity = 32)
    val events: SharedFlow<TurnEvent> = _events.asSharedFlow()
    private val _state = MutableStateFlow(TurnState.IDLE)
    val state: StateFlow<TurnState> = _state.asStateFlow()
    private val mutex = Mutex()
    private val stopRequested = AtomicBoolean(false)
    @Volatile private var mode: TurnMode = TurnMode.PUSH_TO_TALK
    @Volatile private var language: SttLanguage = SttLanguage.ENGLISH
    @Volatile private var profile: UserLanguageProfile = UserLanguageProfile.same(VokieLanguage.EN)
    @Volatile private var currentTurnId: String? = null
    @Volatile private var currentTurnStartedAtMs: Long = 0L
    /**
     * Monotonically increasing generation counter. Each call to [start] increments this.
     * The collector and result handler check their captured generation against the live
     * value; if they differ the callback is stale and must be discarded.
     */
    private val generation = AtomicLong(0)
    private var collectorJob: Job? = null
    // Application-scoped defensive gate. Multiple UI owners must never submit the same
    // turn sentence twice; the key is stable turn identity, never transcript text.
    private val submissionGate = TurnSubmissionGate()

    init {
        // This callback is invoked by Whisper immediately before native inference,
        // not when microphone capture merely begins.
        stt.setInferenceStartListener { turnId -> turnId?.let { timing?.sttStart(it) } }
    }

    suspend fun start(mode: TurnMode, language: SttLanguage, profile: UserLanguageProfile) = mutex.withLock {
        if (_state.value !in setOf(TurnState.IDLE, TurnState.STOPPED, TurnState.ERROR)) return@withLock
        this.mode = mode
        this.language = language
        this.profile = profile
        stopRequested.set(false)
        val gen = generation.incrementAndGet()
        collectorJob?.cancel()
        // Drop(1) skips the StateFlow replay of whatever the previous turn left behind.
        collectorJob = scope.launch { collectStt(gen) }
        beginTurn()
        stt.setInferenceTurnId(currentTurnId)
        VokieLog.stt("VOICE_STATE: IDLE -> CAPTURING turn=${currentTurnId} gen=$gen")
        stt.start(language, profile, finalizeOnVad = (mode == TurnMode.CONTINUOUS))
    }

    /**
     * Atomically claims the message-submission boundary for one turn sentence.
     * This is defensive: normal operation has one UI owner, while duplicate/lifecycle
     * collectors receive false and cannot create a second message ID.
     */
    fun claimSubmission(turnId: String, sentenceIndex: Int): Boolean =
        submissionGate.claim(turnId, sentenceIndex)

    /** PTT release or user cancel. Signals no further auto-restart, then finalises STT. */
    suspend fun stop() = mutex.withLock {
        if (_state.value in setOf(TurnState.IDLE, TurnState.STOPPED)) return@withLock
        stopRequested.set(true)
        // PTT release must leave LISTENING immediately even before recorder finalization.
        _state.value = if (_state.value == TurnState.LISTENING) TurnState.PROCESSING else TurnState.IDLE
        VokieLog.stt("VOICE_STATE: stop requested turn=${currentTurnId} state=${_state.value}")
        stt.stop()
    }

    private suspend fun beginTurn() {
        val id = UUID.randomUUID().toString()
        currentTurnId = id
        currentTurnStartedAtMs = clockMs()
        _state.value = TurnState.LISTENING
        timing?.start(id)
        _events.emit(TurnEvent.Started(id, mode, language.messageLanguage, currentTurnStartedAtMs))
    }

    private suspend fun collectStt(gen: Long) {
        // Drop the first emission which is the stale StateFlow replay from the previous turn.
        stt.status.drop(1).collect { status ->
            // If generation has advanced, this collector belongs to an old turn — stop it.
            if (generation.get() != gen) {
                VokieLog.stt("VOICE_STATE: stale collector discarded gen=$gen current=${generation.get()}")
                return@collect
            }
            when (status.state) {
                SttState.READY -> {
                    // Short/cancelled PTT can return READY without RESULT. Close the
                    // turn so PROCESSING never rejects all subsequent button presses.
                    if (_state.value in setOf(TurnState.LISTENING, TurnState.PROCESSING)) {
                        val turnId = currentTurnId
                        _state.value = TurnState.STOPPED
                        turnId?.let { _events.emit(TurnEvent.Stopped(it)) }
                        collectorJob?.cancel(); collectorJob = null
                    }
                }
                SttState.LISTENING -> if (_state.value != TurnState.LISTENING) _state.value = TurnState.LISTENING
                SttState.PROCESSING -> {
                    _state.value = TurnState.PROCESSING
                    VokieLog.stt("VOICE_STATE: CAPTURING -> PROCESSING turn=${currentTurnId}")
                    currentTurnId?.let { timing?.endpoint(it) }
                }
                SttState.RESULT -> status.result?.let { processResult(it, gen) }
                SttState.ERROR -> {
                    val turnId = currentTurnId ?: return@collect
                    val failure = status.failure
                    if (failure != null) _events.emit(TurnEvent.Error(turnId, failure.code, failure.userMessage))
                    _state.value = TurnState.ERROR
                    stopRequested.set(true)
                    VokieLog.stt("VOICE_STATE: -> ERROR turn=$turnId")
                    _events.emit(TurnEvent.Stopped(turnId))
                    collectorJob?.cancel(); collectorJob = null
                }
                else -> Unit
            }
        }
    }

    private suspend fun processResult(result: SttResult, gen: Long) {
        // Stale generation — discard silently.
        if (generation.get() != gen) return
        val turnId = currentTurnId ?: return
        timing?.sttComplete(turnId, result.text, result.audioDurationMs)
        VokieLog.stt("VOICE_STATE: PROCESSING -> COMPLETED turn=$turnId transcript=${result.text.take(40)}")
        val sentences = segmenter.split(result.text, result.language.messageLanguage)
        sentences.forEachIndexed { index, sentence ->
            _events.emit(TurnEvent.Sentence(turnId, index, sentence, result.language.messageLanguage))
        }
        _events.emit(TurnEvent.TurnCompleted(
            turnId = turnId,
            sentenceCount = sentences.size,
            sttProcessingMs = result.processingTimeMs,
            audioDurationMs = result.audioDurationMs,
            startedAtMs = currentTurnStartedAtMs,
            completedAtMs = clockMs(),
        ))
        _state.value = TurnState.SENTENCE_READY
        if (mode == TurnMode.CONTINUOUS && !stopRequested.get()) {
            val nextGen = generation.incrementAndGet()
            collectorJob?.cancel()
            collectorJob = scope.launch { collectStt(nextGen) }
            beginTurn()
            stt.setInferenceTurnId(currentTurnId)
            stt.start(language, profile, finalizeOnVad = true)
        } else {
            // STOPPED is terminal for this turn; the UI maps it to idle rather than
            // interpreting stale engine LISTENING/RESULT as an active press.
            _state.value = TurnState.STOPPED
            _events.emit(TurnEvent.Stopped(turnId))
            collectorJob?.cancel(); collectorJob = null
        }
    }
}
