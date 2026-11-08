# Vokie Full Codebase + SIH Audit

**Audit outcome:** the audit is complete; the product is not SIH-complete. Claims below cite source classes and existing tests, and distinguish code from physical proof.

## Repository architecture

Single Android `app` module: Compose UI, Room, Kotlin/coroutines, JNI C++ (`vokie_whisper_jni.cpp`, `vokie_ct2_jni.cpp`), vendored whisper.cpp, ARM64 CTranslate2/SentencePiece linkage, app-private staged models, debug activities, JVM tests, model-lab research/scripts. `VokieApplication` is composition root; Room stores messages/peers/transport events; StateFlow is the primary UI/state mechanism.

Major flows: `CommunicationViewModel` → application/use cases; `RoomMessageRepository`; `TransportManager` selects Bluetooth/Wi-Fi Direct; `PeerSessionManager` provides in-memory peer state with Room restoration. Native inference is dispatched off main thread; CT2 uses a mutex; TTS queue serializes synthesis/playback.

## Actual production data flow

```text
ChatScreen / CommunicationViewModel.startVoice
 → SpeechToTextUseCase → WhisperSttEngine.start
 → MicrophoneAudioRecorder (16kHz mono; EnergyVadEngine)
 → WhisperSttEngine.transcribe → SttResult
 → VokieApplication.enqueueWhisperTranscript → RoomMessageRepository.createMessage
 → PacketV2.fromMessage → TransportManager → BluetoothTransport or WifiDirectTransport
 → inbound TransportManager.decodedFrames → InboundPacketCoordinator / Room message
 → VokieApplication receiver collector → ReceiverTranslationCoordinator
 → CodeSwitchTranslationCoordinator (EN/HI/TA local evidence) → CT2/NLLB where needed
 → ReceiverPresentation.displayText → ReceiverPresentation.ttsHandoff
 → TextToSpeechUseCase → TtsPlaybackQueue → SherpaOnnxTtsEngine → VokieAudioPlayer
```

Missing arrows: no streaming partial STT/text packets; no sentence splitter beyond VAD finalization; no confirmed continuous phone-turn loop; no synchronized T0–T8 telemetry.

## SIH matrix summary

See `VOKIE_SIH_REQUIREMENT_MATRIX.json`. Critical status: 10-language STT is partial; 10-language TTS is blocked; accuracy/RTF/full-stack efficiency/end-to-end latency are not measured. Offline text transport and PTT have implemented paths, but two-phone full-loop proof remains limited.

## Ten-language status

Canonical `VokieLanguage` lists ten languages, but actual `SttLanguage`, `TtsLanguage`, and CT2 JNI mapping expose EN/HI/TA only. NLLB tags exist for all ten, but model support does not equal app integration. See `../sih-10-language/sih_10_language_capability_matrix.json`.

| Group | Actual status |
|---|---|
| EN/HI/TA STT | explicit Whisper path in code; only limited physical/debug evidence |
| Other seven STT | upstream candidate availability, not integrated |
| EN/HI/TA TTS | engine/route code exists; approved artifact gate remains unresolved |
| Other seven TTS | not integrated, no approved artifact selection |
| Ten-language translation | NLLB family tags available; current JNI has only EN/HI/TA mapping |

## STT / VAD audit

`WhisperTinyMultilingualQ5_1` is 32 MiB Q5_1 GGML with code-declared ~273 MiB RAM. `WhisperSttEngine` uses one app-scoped context, 1–4 threads, a 60-second watchdog, explicit EN/HI/TA language selection, and a final `SttResult` without confidence/timestamps/segments. `MicrophoneAudioRecorder` captures max 30 seconds and uses `VOICE_RECOGNITION`; `EnergyVadEngine` uses RMS energy, 200 ms minimum speech and 750 ms silence. This meets a basic pause-finalize mechanism, **not** the SIH claim of efficient streamed sentence formation: there are no partial transcripts, sentence splitter, or per-sentence packets.

## TTS / alert audit

`SherpaOnnxTtsEngine` is a lazy single context; `TtsPlaybackQueue` is sequential and prioritizes SOS. `VokieAudioPlayer` uses `USAGE_ALARM` for emergency, repeats SOS, requests focus, and sets app gain 1.0. It cannot override user/device volume or guarantee non-interruption under Android policy. TTS success is artifact-gated; do not call ten-language intelligibility implemented.

## Communication / multi-peer / reconnection audit

`PacketV2` provides UTF-8 payload framing, CRC, fragmentation/reassembly, one language code, ACK framing, and bounded frame/payload sizes. `InboundPacketCoordinator`, `OutboundMessageProcessor`, ACK registry/tracker, retry policy, and Room inbox/outbox support reliable text semantics. `PeerSessionManager` scopes state/message routing; rescue UI and DEBUG simulation exist. `ReconnectPolicy`, transport callbacks, generation counters, and Room session restoration are unit-tested. Physical two-phone Wi-Fi/Bluetooth reconnect, full-loop TTS delivery, and true multi-peer validation remain unproven.

## Latency / efficiency / accuracy audit

Existing logs include recorder start/stop, Whisper inference times, CT2 inference time, and TTS synthesis/audio duration. Missing: synchronized T0 speech-start, T1 endpoint, T3 packet creation, T4 receipt, T6/7/8 playback timeline across phones. Current known footprint is not full-app evidence: Whisper 32 MiB, CT2 model 619,704,329 bytes. Risks: CT2 model PSS is high from prior debug observations; ranging measurement history is unbounded; validation recorder rewrites JSON; no idle CPU/thermal/full-stack PSS suite. No Vokie ten-language WER, noise WER, TTS intelligibility, or rescue-domain accuracy measurement exists.

## Localization / ranging audit

`RangingCapabilityManager`, `RelativePeerLocalizationEngine`, RSSI median/EMA, pair calibration, distance/direction estimators, motion classifier, debug recorder/lab, and replay tests exist. Distance returns metres only after pair calibration; direction intentionally returns UNKNOWN absent validated policy; no GPS coordinates are emitted and ACK RTT is qualitative only. Physical distance/bearing/two-phone validation is not complete. Universal adaptive ranging is research only.

## Code-switch / noise / semantic tokens / JSCC

EN/HI/TA `CodeSwitchAnalyzer`, segmented receiver-local translation, truthful presentation, and fake-engine corpus replay are implemented. It is deterministic script/span evidence, not general LID and not ten-language ready; HI/MR Devanagari overlap prevents naive expansion. Noise robustness is limited to microphone source choice and energy VAD: no denoiser, AGC, echo cancellation policy, noise benchmark, or acoustic LID. Semantic/acoustic tokens, neural codecs, and JSCC have no implementation; they are future research. Text transmission already addresses low-link bandwidth safely; any future learned representation must remain above stable PacketV2 framing rather than replacing transport prematurely.

## Architectural integrity findings

1. **Hard language boundary:** canonical enum has ten, but STT/TTS/CT2 JNI hard-code three. This is intentional prototype scope, not a hidden capability.
2. **TTS artifact gate:** `UnavailableTtsEngine` and model-gated comments correctly avoid fabricated playback but block SIH delivery.
3. **Global disconnect recovery:** current `recoverInterrupted()` requeues all transmitting messages; acceptable only for current single-active-connection assumptions and must be revisited for true concurrent transport.
4. **Ranging growth:** measurement history/recorder persistence needs bounding before long-running use.
5. **Debug tooling:** debug activities and PCM replay are guarded by debug source set/BuildConfig; no cloud path found in STT/TTS/translation execution.
6. **No severe build-breaking defect found.** Upstream whisper.cpp TODOs are vendor code, not Vokie feature claims.

## Test coverage

| Subsystem | Unit/replay | device/two-phone |
|---|---|---|
| Packet/ACK/retry/framing | yes | limited transport smoke only |
| peer sessions/multi-peer/reconnect | yes, simulation/fakes | multi-peer/reconnect not physically proven |
| STT/VAD | core/VAD tests | limited debug/device evidence, no 10-language corpus |
| CT2 translation | coordinator tests; prior debug smoke | limited EN/HI/TA debug evidence |
| TTS | core/queue/handoff tests | assets/artifact dependent; no ten-language proof |
| code-switch | analyzer, fake routing, corpus replay | no real ASR accuracy proof |
| ranging | synthetic/replay/recorder tests | no two-phone accuracy proof |

## Demo readiness

| Demo | Status |
|---|---|
| A speaks → B translated speech | PARTIAL: requires approved TTS assets and two phones |
| different EN/HI/TA direction | PARTIAL |
| ten-language selection | NOT READY |
| PTT | IMPLEMENTED / physical loop not fully proven |
| phone-like mode | PARTIAL |
| emergency alert | PARTIAL, Android policy limited |
| Wi-Fi/Bluetooth | implemented architecture; two-device proof required |
| multi-peer/reconnect | simulation/unit ready; physical proof required |
| code-switch | deterministic transcript/fake-engine demo ready, not ASR-quality proof |
| GPS-free localization | qualitative/evidence demo only; distance/direction not physically ready |
| offline operation | architecture supports it; complete artifact availability still gates |

## Dependency graph

```text
10-language STT + artifact licenses → VAD endpoint evaluation → final text message
 → PacketV2/peer routing → receiver target/translation → 10-language approved TTS → playback

multi-peer sessions → message isolation/priority → reconnect recovery → physical multi-peer demo

BLE RSSI → filtering → pair calibration → qualitative distance; orientation/motion + validation → direction
```

## Critical blockers and roadmap

**P0 blocking SIH:** ten-language approved TTS artifacts; seven-language STT integration/validation; WER/intelligibility evidence; full offline two-phone loop.

**P1 score impact:** T0–T8 timing, RTF/PSS/CPU/thermal tests on low/mid devices; noisy VAD/STT corpus; two-phone transport tests.

**P2 complete demo:** continuous turn loop, embedded-device protocol decision, Android-safe alert UX/policy.

**P3 differentiators:** physical multi-peer/reconnect demonstration; calibrated qualitative ranging.

**P4 research:** direction policy, adaptive ranging, noise models, semantic/acoustic tokens, JSCC.

## What actually works today

See `VOKIE_DEMO_READINESS.md`; build/test success is software evidence, not field proof.
