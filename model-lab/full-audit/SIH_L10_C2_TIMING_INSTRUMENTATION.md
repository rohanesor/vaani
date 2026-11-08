# SIH-L10-C2 Timing Instrumentation

## Implementation
`TurnTiming` / `TurnTimingRecorder` is an in-memory, bounded (128 records), local-only monotonic recorder. It uses `SystemClock.elapsedRealtimeNanos()` in Android production and preserves raw nanoseconds. It neither persists timings nor transmits them.

## C1 production wiring
`CommunicationViewModel.startVoice()` / `stopVoice()` now exclusively delegate active capture to `ContinuousTurnManager`. The ViewModel creates outbound messages from `TurnEvent.Sentence`; ChatScreen's previous raw `SttStatus.RESULT` enqueue effect was removed. Thus the chat path has one active STT/capture owner. PTT uses `PUSH_TO_TALK`; hands-free uses `CONTINUOUS`; explicit stop prevents manager restart.

## T0–T8 boundaries
- T0: `ContinuousTurnManager.beginTurn` / `Started`
- T1: observed STT transition to `PROCESSING` (existing VAD/PTT finalization boundary)
- T2: final `RESULT` observed by manager
- T3: immediately after local message creation, before existing outbound queue/PacketV2 path
- T4: decoded message packet ingress, before inbound processing
- T5: `ReceiverTranslationCoordinator.presentOnce` returns
- T6: immediately before `TtsEngine.synthesize`
- T7: synthesis returns `AudioBuffer`
- T8: immediately before the existing `TtsEngine.play` handoff

T0–T7 are direct pipeline boundaries. **T8 is currently the playback-engine handoff, not an instrumented `AudioTrack.play()` callback.** Therefore this phase does not claim a physical speaker-start measurement.

## Correlation
Sender records start with `turnId`, binds `messageId` at T3, and receiver records are keyed by `messageId`. Separate-phone records are correlated during debug-log export by message ID; timing timestamps across separate devices are not treated as a valid cross-device latency clock without physical protocol work.

## Metrics and failures
Derived values are nullable: speech duration, STT, packet preparation, transport, translation, TTS, playback queue, post-STT and end-to-end latency. Missing or reverse timestamps produce null, never fabricated `0 ms`. Records are `INCOMPLETE`, `COMPLETE`, or `FAILED`; translation/TTS failures are explicit.

## Regression boundary
No PacketV2, CRC, framing, ACK/retry, Bluetooth/Wi-Fi Direct semantics, Room schema, Whisper/JNI/model, CT2/NLLB, sherpa model, language routing, ranging, or direction implementation changed.

## Tests/build
- `TurnTimingRecorderTest`: 6 tests, including complete derived values, incomplete handling, duplicate events, message correlation, failed TTS, and non-monotonic timestamps.
- Full `testDebugUnitTest`: PASS.
- `assembleDebug`: PASS.

## Physical validation
**NOT PERFORMED.** No end-to-end latency is claimed.

## Status and next phase
C2 instrumentation is code-integrated, but hardware-level T8 and cross-device clock validation remain for C4. Next planned work is C3 accuracy evidence, then C4 real two-phone offline loop.
