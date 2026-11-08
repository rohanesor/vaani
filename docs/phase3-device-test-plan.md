# Phase 3 physical-device test plan

## Evidence policy

Use two physical Android devices with airplane mode enabled and local Bluetooth/Wi-Fi Direct enabled. Record raw timestamped measurements; never claim a result without a device log. No physical devices or `adb` evidence are available in the current environment.

## Scenarios

1. text Phone A -> Phone B
2. microphone -> VAD -> Whisper -> language -> packet -> receiver TTS boundary
3. Bluetooth-only
4. Wi-Fi Direct-only
5. Wi-Fi unavailable then Bluetooth fallback
6. queued message across process death/restart
7. duplicate packet
8. fragmented message
9. Tamil, Hindi, Gujarati, and English language routing

## Record per run

Device model/API/RAM, battery/thermal state, source and receiver timestamps, STT duration/RTF, transport connect/send/ACK/retry durations, TTS initialization/startup/RTF, CPU, PSS/RSS, battery delta, packet IDs, and failure state. Preserve only redacted logs outside Git.

## Gates

- airplane-mode success;
- zero packet-language/manual-language mismatch;
- duplicate/replay rejection;
- speaker-disjoint data is not relevant to runtime testing;
- no `Delivered` state without an actual ACK;
- unsupported TTS route is visible and actionable;
- no runtime model download.

Results: **NOT RUN — no physical device/adb evidence available.**
