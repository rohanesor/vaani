# SIH-L10-C1 — ContinuousTurnManager

**Status: CODE-IMPLEMENTED, UNIT-VALIDATED.** Not yet wired into `CommunicationViewModel`; production STT behaviour is unchanged.

## What it is

A new layer above the existing `SttEngine`, added without modifying `WhisperSttEngine`, `MicrophoneAudioRecorder`, `EnergyVadEngine`, `SpeechToTextUseCase`, `PacketV2`, transport, translation, TTS, or UI. Introduces:

- `TurnMode` (PUSH_TO_TALK / CONTINUOUS)
- `TurnState` (IDLE / LISTENING / PROCESSING / SENTENCE_READY / STOPPED / ERROR)
- `TurnEvent` sealed hierarchy: `Started`, `Sentence`, `TurnCompleted` (carries STT processing time, audio duration, injected-clock start/end ms), `Error`, `Stopped`
- `TranscriptEvent` sealed hierarchy: `Partial` (reserved for a future streaming STT — never emitted today), `Final`
- `SentenceSegmenter`: deterministic script-agnostic splitter using `. ! ? ।(U+0964) ॥(U+0965)` — no NLP model
- `ContinuousTurnManager`: subscribes to `SttEngine.status`, splits each RESULT into sentence events, and in CONTINUOUS mode auto-restarts capture with `finalizeOnVad = true`; in PTT mode transitions to STOPPED after one turn

## What it does not do

- Does not modify recorder, VAD, JNI, Whisper, message repository, PacketV2, transport, translation, TTS, or UI.
- Does not fake partial ASR. `TranscriptEvent.Partial` is architected only.
- Does not perform NLP sentence disambiguation; heuristic terminator split only.
- Not wired into `CommunicationViewModel` in this phase — that is a separate integration step so both owners never drive the STT engine concurrently.

## Files added

- `app/src/main/java/com/vokie/stt/ContinuousTurnManager.kt`
- `app/src/test/java/com/vokie/stt/SentenceSegmenterTest.kt`
- `app/src/test/java/com/vokie/stt/ContinuousTurnManagerTest.kt`

## Files modified

None.

## Tests

- `SentenceSegmenterTest`: 13 tests / 0 failures — empty/whitespace, missing terminator, English periods and mixed terminators, Devanagari purna viraam and double danda, Tamil periods, trailing fragment, whitespace collapse, mixed-script code-switched input, terminator-only input, long-run one-sentence.
- `ContinuousTurnManagerTest`: 10 tests / 0 failures — PTT single turn, single-sentence PTT, Hindi danda split, continuous auto-resume, continuous stop halts restart, duplicate-timestamp deduplication, STT error propagation, monotonic timing from injected clock, distinct turn IDs across continuous turns, Started event language/mode.
- Full suite: **317 tests, 0 failures, 0 errors** (was 294 → +23 added).
- `assembleDebug`: PASS.

## Physical validation

None. This is a JVM state-machine + splitter component. Physical validation belongs with the two-phone voice-loop phase (C4).

## C2 hook

`TurnEvent.Started.startedAtMs` and `TurnEvent.TurnCompleted.completedAtMs` use an injected monotonic clock (`() -> Long`, default `System.nanoTime() / 1_000_000L`). This is the T0/T1/T2 anchor for C2 without further API change: production wiring will pass `SystemClock.elapsedRealtime()` and propagate `turnId` as the correlation ID through message creation → PacketV2 → receiver → TTS.

## Next phase

**SIH-L10-C2 — End-to-end T0–T8 timing instrumentation.** Add a lightweight local timing header keyed by `turnId` / `messageId` at each pipeline boundary; no PacketV2 wire change. Do not wire ContinuousTurnManager into the ViewModel until C2's timing anchors are agreed, so both integrations can land in one controlled step.
