# SIH-L10-P0 Core Parallel Preparation

## Turn-manager implementation audit

Current states: `EnergyVadEngine` decides endpoint; `MicrophoneAudioRecorder` owns one 30-second capture; `WhisperSttEngine` emits one final result; PTT release is authoritative; disabled PTT uses VAD finalization. Missing: a long-lived conversation/turn state machine, partial text, sentence splitting, post-delivery reset policy, interruption policy, and endpoint tuning evidence.

Future isolated design boundary:

```text
interaction mode (PTT / hands-free)
 → capture turn state
 → VAD endpoint or release
 → final STT result
 → sentence policy (one or bounded final messages)
 → existing MessageRepository / PacketV2
```

## T0–T8 timing design

Use monotonic elapsed-realtime timestamps and a local correlation/message ID. Record T0 capture start, T1 endpoint, T2 STT result, T3 message queued, T4 receiver decode, T5 receiver presentation/translation result, T6 TTS queue synthesis start, T7 generated audio, T8 AudioTrack playback start. Do not use wall-clock comparison across phones; correlate sender/receiver with local stage metrics plus transport/ACK measurement.

## Accuracy corpus design

Per language: consented speaker ID pseudonym, reference transcript, audio duration, quiet/noise condition, phrase category, rescue keyword annotations, endpoint label. Measure WER/CER/keyword recall only against reviewed reference text. TTS needs separate listener protocol for intelligibility/pronunciation/naturalness. Code-switch corpus remains separate.

## Two-phone protocol

Offline/airplane mode where transport permits; test EN/HI/TA both directions, PTT and VAD endpoint, Wi-Fi Direct and Bluetooth separately. Record model readiness, text, packet ID, ACK, target voice, playback start, and failures. A fake transcript test is not voice-loop proof.

## Multi-peer protocol

Use three actual peers where possible. Verify selecting a peer isolates conversation, language target, queue, ACK, reconnect state, and priority. If fewer devices exist, record simulation separately and do not call it physical multi-peer validation.
