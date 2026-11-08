# SIH-L10-P0 — Remaining Seven Languages + Core Preparation

## Scope
Read-only preparation. EN/HI/TA model-lab work and production paths were not revisited, replaced, or modified. No GU/MR/KN/ML/TE/OR/BN model was integrated.

## Seven-language result

| Language | Existing staged candidate | Provenance/license | Android evidence | Decision |
|---|---|---|---|---|
| Gujarati | manifest-only MMS-family ONNX | conversion unknown; candidate CC-BY-NC-4.0 | none | REJECTED |
| Marathi | manifest-only MMS-family ONNX | conversion unknown; candidate CC-BY-NC-4.0 | none | REJECTED |
| Kannada | manifest-only MMS-family ONNX | conversion unknown; candidate CC-BY-NC-4.0 | none | REJECTED |
| Malayalam | manifest-only MMS-family ONNX | conversion unknown; candidate CC-BY-NC-4.0 | none | REJECTED |
| Telugu | manifest-only MMS-family ONNX | conversion unknown; candidate CC-BY-NC-4.0 | none | REJECTED |
| Odia | manifest-only MMS-family ONNX | conversion unknown; candidate CC-BY-NC-4.0 | none | REJECTED |
| Bengali | manifest-only MMS-family ONNX | conversion unknown; candidate CC-BY-NC-4.0 | none | REJECTED |

No replacement candidate has enough immutable source/revision/license/checksum/frontend/ARM64 evidence to be recommended in this offline repository-only phase. “Supports a language” is insufficient for approval.

## Core parallel preparation conclusions

- **Turn manager:** current `CommunicationViewModel.startVoice` chooses VAD finalization when PTT is disabled; `MicrophoneAudioRecorder` finalizes a single bounded capture; `WhisperSttEngine` emits one final `SttResult`. A future turn manager must sit above recorder/STT state, emit final turns safely, reset after delivery/error, and not alter PacketV2.
- **Timing:** existing logs expose recorder, Whisper, CT2, and TTS portions. T0–T8 requires a monotonic correlation ID propagated locally through message/presentation/playback; design only, no instrumentation added.
- **Accuracy corpus:** use consented/offline scripted recordings per language with reference transcripts, speaker/noise labels, endpoint ground truth, rescue keyword labels, and separate text/translation/TTS human-review records. No WER/MOS fabricated.
- **Physical protocols:** EN/HI/TA two-phone loop must precede seven-language claims. Multi-peer needs at least three physical peers or an explicitly labelled hybrid simulation.

## Recommended next phase

**SIH-L10-P1 — targeted permissive-license artifact sourcing**. It must use authoritative upstream evidence outside this checkout and produce one candidate record at a time. No integration until the L9 approval gate passes.
