# Android TTS benchmark record

## Result: NOT RUN — blocked before installation

A real ARM Android device is mandatory for this benchmark. The current execution environment is `x86_64`, has no `adb` executable, and exposes no connected device. No emulator, desktop, CUDA, or x86 value is substituted for Android evidence.

More importantly, no complete artifact set has passed commercial redistribution and all-language gates. Installing current MMS or incomplete Piper artifacts would not validate a shippable iTantra architecture.

## Required isolated lab

The future lab must be separate from production playback and use `UnifiedTtsEngine` with a test-only immutable `TtsModelRegistry`. It must never use production AWS distribution or perform runtime downloads. Inputs must be APK-local artifacts with URL/revision/checksum/license records.

For every backend/language it must record:

- model and APK contribution bytes;
- initialization, first synthesis, warm synthesis, and time-to-first-audio;
- generated audio duration and RTF;
- Java heap, native heap, PSS/RSS, CPU utilization, and process crashes;
- backend release/switch behavior across Tamil → Hindi → Bengali → Telugu → English → Gujarati;
- airplane-mode launch/synthesis logs with no network request;
- five-language low-memory cycle with no simultaneous multi-model residency.

## Required device evidence

| Requirement | Result |
|---|---|
| Connected ARM Android device | Not available |
| 2 GB RAM device confirmed | Not available |
| Signed/candidate APK installed | Not run |
| All-ten language synthesis | Not run |
| Cold/warm/TTFA/RTF | Not measured |
| PSS/native heap/CPU | Not measured |
| Airplane-mode trace | Not run |
| Low-memory and language-switch test | Not run |

The sole existing host MMS smoke result remains explicitly non-Android: English RTF 0.559, 315 ms synthesis for 564 ms audio, 171,072 KiB process RSS. It is excluded from this Android benchmark record.
