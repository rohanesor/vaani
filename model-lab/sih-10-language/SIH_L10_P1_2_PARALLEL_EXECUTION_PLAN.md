# SIH Core + Novelty Parallel Execution Plan (Part F)

The seven-language TTS artifact search must not block Vokie. EN/HI/TA are already integrated; the following work can advance in parallel without changing PacketV2, transport, existing STT/TTS/translation artifacts, or `TtsLanguage`.

## SIH Core track

**C1. Continuous turn manager (implementation phase).** Add a coroutine turn coordinator above `WhisperSttEngine` / `MicrophoneAudioRecorder` handling PTT and hands-free modes: emit a bounded sequence of final sentences per capture, safe post-delivery reset, interruption policy. Do not modify recorder/STT internals.

**C2. End-to-end T0–T8 instrumentation.** Add a monotonic timing header carried locally between STT result, message repository, transport ACK, presentation, TTS synthesis start, audio buffer ready, and playback start. Locally correlated per message ID; no PacketV2 change.

**C3. WER/CER + TTS listening corpus.** Consented scripted per-language recordings with reference transcripts, endpoint labels, noise labels, rescue keyword labels. Store outside APK. Human review protocol for TTS.

**C4. Physical two-phone voice loop.** Offline/airplane where possible. EN/HI/TA both directions, Wi‑Fi Direct and Bluetooth separately, PTT and VAD endpoint. Record correlation ID, target voice, playback confirmation. Do not label success from unit tests.

**C5. PSS/CPU/thermal measurement pass.** Cold/warm STT, translation, TTS on one low-range and one mid-range device. Model coexistence stress.

## Novelty track

**N1. Physical multi-peer isolation.** Real hardware where possible: three peers, per-peer session/language/priority/queue/reconnect. Simulation stays labeled simulation.

**N2. Physical reconnection recovery.** Wi‑Fi Direct and Bluetooth: send, disconnect, recover, retry, ACK, deduplicate. Process death where feasible.

**N3. GPS-free ranging validation.** Pair calibration at 0.5, 1, 2, 3, 5 m; multiple orientations; report mean/median/std/error/confidence/repeatability; qualitative UI when insufficient.

**N4. Direction validation (only after N3).** Bearings 0/45/…/315 and rotation; require confidence to display bearing, else UNKNOWN.

**N5. Noise robustness evaluation.** Quiet/traffic/crowd/wind/background speech/playback interference: WER/CER/endpoint metrics.

**N6. Code-switch expansion (only after new language STT/TTS exists).** Deterministic script rules cannot naively extend to ten languages; HI/MR Devanagari must be handled via configured-language prior plus acoustic/translation evidence in a later phase.

## Serialized dependency

Semantic tokens and JSCC remain research-only and must not become critical-path work until SIH C1–C5 and novelty N1–N3 have physical evidence.

## Governance

Multiple agents may prepare research and design in parallel, but every production edit must go through one integrator and preserve the current 294-test baseline.
