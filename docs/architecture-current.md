# Current architecture audit

This document records the repository state as audited on the `feat/branding-itancore` branch. It is descriptive, not a claim that the current implementation satisfies the target iTantra production architecture.

## Product and build identity

- Android package namespace and application ID: `com.vokie`.
- User-facing application label: `iTantra`.
- Minimum SDK: 24; target/compile SDK: 34; ARM64 is the only release ABI.
- UI: Kotlin, Jetpack Compose, Material 3, one activity, application-scoped manual dependency wiring in `VokieApplication`.
- Persistence: Room database `vokie.db`, schema version 1.
- Build: Gradle 8.9, Kotlin/JVM 17, KSP Room compiler, CMake/native whisper.cpp build.

The remaining `Vokie*`, `vokie_*`, and `com.vokie` identifiers are stability-sensitive implementation identifiers retained during the branding migration.

## Application layers

```text
Compose UI
  -> CommunicationViewModel / MapViewModel
  -> use cases and Room repository
  -> Bluetooth transport, STT, TTS, map implementations
  -> Android platform / native whisper.cpp / sherpa-onnx
```

`VokieApplication` constructs the Room repository, classic Bluetooth transport, transport manager, outbound queue processor, Whisper STT engine, MMS-TTS engine, model store, and offline map use case. There is no dependency-injection framework.

## Message persistence and lifecycle

`MessageEntity` persists id, sender/receiver IDs, timestamp, UTF-8 text, language, message type, delivery state, selected transport, hop count, retry count, ACK requirement, and error text. `PeerEntity` and `TransportEventEntity` retain peer and transport history.

The active outbound lifecycle is:

```text
QUEUED -> TRANSMITTING -> RECEIVED_BY_PEER
                     -> RETRYING -> QUEUED | FAILED
```

Room is authoritative. On process restart, `TRANSMITTING` rows are returned to `QUEUED`. Incoming persistence uses `INSERT IGNORE` by message ID, providing duplicate suppression before the receiver sends an ACK.

Current limitations:

- `DeliveryState` has no `ACKNOWLEDGED` or `EXPIRED` state.
- State updates are individually issued DAO queries rather than a transactionally enforced state machine.
- There is no packet sequence/order persistence, expiration policy, or replay window.

## Current packet and transport

`VokieProtocol` version 1 is a deterministic, length-framed binary protocol transported over Bluetooth Classic RFCOMM. It contains a magic, protocol version, frame kind, UUID message ID, sender/receiver IDs, timestamp, language string, message type, UTF text, hop count, and ACK requirement. A separate ACK includes message ID, receiver ID, and timestamp.

Bluetooth Classic is the only real transport. It provides discovery, RFCOMM listening/connect roles, service-UUID filtering, socket framing, ACK waiting, reconnect attempts, and queued-message integration. `TransportManager` currently wraps only `BluetoothTransport` despite `WIFI_DIRECT` and `ULTRASONIC` enum values.

Current limitations:

- Wi-Fi Direct has manifest permissions/features only; no peer, group, socket, or packet implementation exists.
- Packet v1 has no CRC, packet sequence number, fragmentation/reassembly, compression, replay protection, transport authentication, or message ordering semantics.
- Decoded messages are marked as Bluetooth regardless of an eventual alternate transport.

## Speech-to-text baseline

The production STT implementation is `WhisperSttEngine` backed by whisper.cpp JNI (`vokie_whisper`).

- Model: OpenAI Whisper tiny multilingual, Q5_1 GGML.
- Asset: `stt/ggml-tiny-q5_1.bin`.
- Exact manifest size: 32,152,673 bytes (30.66 MiB).
- Runtime model metadata estimates 273 MiB, but this has not been validated on target Android devices.
- Audio: bounded 16 kHz mono PCM, maximum 30 seconds.
- VAD: replaceable RMS-energy endpoint detector (`EnergyVadEngine`), not Silero VAD.
- Inference: one native context, up to four threads, 60-second watchdog.
- Result: text, caller-selected `SttLanguage`, nullable confidence, processing time, audio duration, and timestamp.

The current UI persists a selected STT language and passes it to whisper.cpp. It does **not** perform validated automatic language detection, does not expose start/end timestamps, and has no calibrated confidence. The target product requirement that language is derived from the message is therefore not yet met.

## Text-to-speech baseline

The production TTS implementation is `SherpaOnnxTtsEngine` using the official sherpa-onnx 1.13.6 Android AAR and MMS/VITS ONNX models. It maps the persisted message language to `TtsLanguage`, selects one active model at a time, synthesizes locally, and writes float PCM through `AudioTrack`. Incoming persisted messages are automatically enqueued for speech when their model is available.

The verified manifest inventory is:

| Asset set | Exact bytes | MiB |
|---|---:|---:|
| Whisper STT | 32,152,673 | 30.66 |
| English MMS-TTS model + tokens | 114,017,251 | 108.73 |
| Current base STT + English TTS | 146,169,924 | 139.40 |
| All ten MMS-TTS model + token packs | 1,140,400,586 | 1,087.57 |
| All listed STT + TTS assets | 1,172,553,259 | 1,118.23 |

Supported message languages are English, Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, and Bengali.

Current limitation: only Whisper and English TTS are atomically extracted by `BundledModelStore`. The other nine MMS packs are expected from a CDN through `ModelDownloadManager`; the manifest declares `INTERNET` and network-state permissions for that path. This conflicts with the required single-APK/no-runtime-model-download target.

## Model supply chain

A release workflow retrieves a private, checksummed model archive from S3, validates it, stages selected assets, audits ONNX models, builds and signs the APK, verifies APK contents, and publishes through S3/CloudFront. Model files are intentionally ignored by Git. The working tree may contain local model artifacts, but they are not tracked.

The installed app currently contains a model CDN configuration and a one-time TTS download implementation. AWS is otherwise distribution/build infrastructure, not part of Bluetooth/STT/TTS processing.

## Location, SOS, maps, diagnostics

- SOS is a `MessageType` with priority TTS playback, but it does not carry an explicit location payload, priority field, sequence, or SOS-specific protocol schema.
- The offline map implementation reads local region data. There is no GNSS location acquisition, no offline location message, and no GPS claim.
- There is no dedicated development-only diagnostics screen. STT/TTS expose some timing/RTF state in the communication UI and logcat.

## Existing validation

JVM tests cover protocol encoding/ACK/retry, Room repository behavior, core STT/VAD states, TTS mappings/state logic, and offline-map parsing. CI runs JVM tests, Android lint, and debug assembly. The signed release workflow additionally validates the private archive and APK model inventory.

There are no checked-in reproducible WER/CER/language-identification, TTS quality, Android memory/CPU, Wi-Fi Direct, GNSS, packet-loss, or end-to-end physical-device benchmark suites.
