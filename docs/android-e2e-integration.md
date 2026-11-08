# Android speech E2E integration status

## Phase 4B-1 wiring

This phase wires existing local components only; no microphone E2E or two-phone speech E2E was run.

Before wiring, the application graph used `EmergencyPhraseDemoTranslationEngine` and `UnavailableTtsEngine`, despite independent physical validation of CT2 and Tamil sherpa-onnx. It now uses:

```text
WhisperSttEngine result
  -> VokieApplication.enqueueWhisperTranscript(result)
  -> existing Room outbound queue / PacketV2 transport

received PacketV2 source text + source language
  -> ReceiverTranslationCoordinator
  -> Ctranslate2TranslationEngine
  -> SherpaOnnxTtsEngine
  -> VokieAudioPlayer
```

`Ctranslate2TranslationEngine` is a Kotlin adapter over existing `Ctranslate2Native`; it preserves direct EN/HI/TA calls. Its JNI implementation continues to build `[src_lang] + SentencePiece pieces + ["</s>"]`; HI↔TA remains one native call with NLLB prefixes, not an English pivot. It lazily loads and reuses only an approved filesystem model staged at `files/ct2/nllb600m`. It has no downloader or fallback. If that directory is absent/incomplete it returns `UNAVAILABLE`.

Target language remains intentionally **receiver-local**, as already designed: PacketV2 transmits immutable source text/language and the receiving device's local preferred-output profile selects EN/HI/TA. This is backward compatible and does not alter PacketV2 framing, CRC, fragmentation, ACKs, or TCP. The receiver coordinator bypasses CT2 where source and local target match.

`SherpaOnnxTtsEngine` now replaces `UnavailableTtsEngine` in application DI and retains the validated PCM_FLOAT, mono, 16 kHz `VokieAudioPlayer` path. Tamil (`tam`) is APK-staged and approved. English assets are also present. Hindi model metadata exists but no Hindi model asset is currently staged, so HI target speech explicitly reports model missing rather than using a substitute.

## Not physically validated in this phase

- controlled local CT2→TTS integration;
- two-phone controlled text E2E;
- microphone/Whisper E2E;
- combined memory, latency, or stability.

Those require staging the exact approved CT2 directory into the new durable `files/ct2/nllb600m` location on each participating device and selecting receiver-local output profiles. No production UI changes were made.

## Phase 4B-2R — Controlled two-phone E2E

The debug harness now uses an in-APK Kotlin constant, not an ADB Unicode extra:

```text
मुझे मदद चाहिए।
```

Both devices were explicitly deployed and CT2 `model.bin` was staged at `files/ct2/nllb600m`; both device-side hashes matched `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`.

### A → B HI → TA

Phone A (`10BCAC2HM5000CR`, V2205) sent the exact constant with source `HI`. Phone B (`MJPVXCSG9HYL65YL`, RMX3782) was configured with receiver-local target `TA`.

```text
A TX_SOURCE:       मुझे मदद चाहिए। language=HI
B RX:              मुझे मदद चाहिए।
B presentation:    source=मुझे मदद चाहिए। sourceLang=HI target=TA
B output:          எனக்கு உதவி தேவை.
Transport ACK:     correlated / acknowledged=true
B Tamil model:     loaded by application TTS
B TTS:             synthesis completed; 16 kHz mono PCM_FLOAT
B AudioTrack:      STATE_INITIALIZED; complete sample buffer written
```

The receiver presentation used the wired local CT2 engine. This is a direct `hin_Deva → tam_Taml` route; no English pivot is present. The harness did not separately timestamp CT2 and total presentation stages, so numeric CT2/transport/E2E timing is not claimed here. The previous ADB truncation was eliminated: all application and receiver logs contain the complete Unicode sentence.

### Same-language HI → HI

A second exact-constant message arrived at B with target preference `HI`:

```text
source=मुझे मदद चाहिए। sourceLang=HI target=HI
state=TRANSLATED output=मुझे मदद चाहिए।
```

This is a coordinator passthrough and does not invoke CT2/nativeTranslate. B lacks an approved Hindi TTS model asset, so same-language Hindi speaker playback was not claimed.

### B → A limitation

B → A TA→HI speaker E2E remains **BLOCKED**. Phone A has no approved Hindi `model.onnx`/`tokens.txt` asset, and no replacement model was used. Packet transport and CT2 direct TA→HI had been independently validated earlier, but this phase does not claim Hindi speaker playback.

This phase proves the controlled A→B Unicode transport → direct CT2 → Tamil TTS → AudioTrack path and the HI same-language bypass, but is not full Phase 4B and does not include microphone/Whisper E2E.

## Phase 4D-1 — Physical Whisper STT smoke result

A debug-only `WhisperTamilSmokeActivity` was added and run on Phone A (`10BCAC2HM5000CR`, V2205, Android 15, arm64-v8a) with explicit `SttLanguage.TAMIL` (`ta`). It did not send a message or invoke CT2/TTS/transport. The existing APK-bundled Whisper tiny multilingual Q5_1 model was loaded locally and reused by the existing `WhisperSttEngine`.

Observed sequence:

```text
Model load:       PASS, 204 ms
Microphone:       AudioRecord initialized and RECORDSTATE_RECORDING
Capture:          190,720 samples, 11,920 ms, mono PCM16 at 16 kHz
Whisper request:  language=ta, threads=4
Native inference: 3656 ms
Result state:     RESULT
Result language:  TAMIL
```

The run returned a non-empty UTF-8 string:

```text
எனக்காப் பாட்டுங்கள்... நன்று காப்பாட்டுங்கள்... நன்று காப்பாட்டுங்கள்... நன்று காப்பாட்டுங்கள்... நன்று காப்பாட்டுங்கள்...
```

However, `vadHasSpeech=false`, and no controlled human Tamil phrase was supplied during the capture window. Therefore this output may be ambient/no-speech Whisper behavior and is not claimed as an accuracy result. Repeated controlled speech runs and intelligibility/word-accuracy review remain pending. No network path was used (`mobile_data=0`), and no crash, ANR, permission failure, microphone initialization failure, or native crash occurred.

**Phase 4D-1 status: NOT PASS — physical controlled Tamil speech and repeat stability evidence are still required.**

### Phase 4D-1R controlled retest

The tester completed five sequential physical Tamil capture windows on the same Phone A. The harness used explicit `ta`, existing `MicrophoneAudioRecorder`, `EnergyVadEngine`, and `WhisperSttEngine`; CT2, PacketV2, Wi-Fi Direct, and TTS were unused. The Whisper model was loaded once (`293 ms`) and reused across all five runs.

| Run | Expected phrase | VAD speech | Capture | Inference | Observed transcript | Key-word result |
|---:|---|---|---:|---:|---|---|
| 1 | `எனக்கு உதவி தேவை.` | false | 9940 ms | 10312 ms | `ஏனாகக் கூட வித்தேன் வைத்துக்கொள்ள வித்துவிட்டும் விட்டும் ஏனாக இருக்கிறார்கள்.` | FAIL |
| 2 | `தயவுசெய்து எனக்கு உதவுங்கள்.` | false | 9940 ms | 10075 ms | `காணமாக இருக்கிறாயா?` | FAIL |
| 3 | `நீங்கள் எங்கே இருக்கிறீர்கள்?` | true | 9940 ms | 23310 ms | `அனுமாக விருவிடும்? எங்களில் என்று எங்களில் இருக்கிறீர்கள்? எங்களில் என்களில் இருக்கிறீர்கள்?` | Partial only; not accepted |
| 4 | `எனக்கு தண்ணீர் வேண்டும்.` | true | 9940 ms | 8977 ms | `எனக்கு தண்ணி இரவேண்டும். அட்டா, எனக்கு தண்ணி இரவேண்டும். எனக்கு தண்ணி இரவேண்டும்.` | Partial approximation; not accepted |
| 5 | `நான் பாதுகாப்பாக இருக்கிறேன்.` | true | 9940 ms | 3049 ms | `நான் பாதிக்கொண்டார்கள் நீ இருக்கிறேன்.` | Partial only; not accepted |

All five returned `SttResult.language=TAMIL` and reached `RESULT`; no permission failure, microphone initialization failure, crash, ANR, or native error occurred. The objective runtime path is therefore operational. However, runs 1–2 had `vadHasSpeech=false`, and none of the five transcripts is sufficiently faithful to claim controlled phrase recognition. This retest does not establish Tamil STT accuracy or punctuation accuracy.

**Phase 4D-1R: NOT PASS.** A further controlled retest is required after ensuring the speaker is within the microphone window and the expected phrase is spoken clearly; no Whisper implementation change was made.

## Phase 4B-3 — Hindi TTS artifact audit

An approved-for-prototype Hindi artifact does exist in the recovered Model Lab artifacts, but it has not been staged into either Android device in this audit. The exact artifact is:

```text
Model:       hi_IN-priyamvada-medium (Piper VITS)
Runtime:     sherpa-onnx 1.13.7
Bundle:      vits-piper-hi_IN-priyamvada-medium.tar.bz2
Source:      rhasspy/piper-voices via the official sherpa-onnx tts-models release
Bundle SHA:  399d91cc97eb288725633261f26b715f9a971e3bf7ec4fa1d7910cd0080d37eb
ONNX SHA:    8871f3e07adb6ca490f8dbcd3956a8647c53c35b5d0a1c2a8d097b3bf721a31b
Tokens SHA:  ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d
License:     Piper hub MIT; voice dataset CC-BY-NC-SA-4.0
Status:      APPROVED FOR NON-COMMERCIAL PROTOTYPE
```

The recovered Model Lab evidence includes host CPU synthesis benchmarks (for example, `मुझे मदद चाहिए।`: approximately 0.38 s median, 289 MB peak RSS) and marks Hindi TTS as approved for the non-commercial prototype. These are not Android measurements. The archive is present locally under `model-lab/models/`, but no files were extracted, copied into APK assets, or staged on devices during this audit.

A separate legacy MMS Hindi file also exists at `models/tts/vits-mms-hin/model.onnx` with SHA-256 `42c69b3611dc016ff337e994c78a76b5131156718c5a69e9cfa8912cfd850c5e` and tokens SHA-256 `aa9abf8320da4ca80b153c51e2d3b6b52cb41e930ccc16dec570e70726ab3dd6`. It is not treated as approved: the project audit records unknown conversion/token provenance and rejects it for distribution. No MMS Hindi substitute was used.

Current Android status remains unchanged: the production asset extraction includes English and Tamil only; Hindi is not staged. B→A TA→HI speaker E2E therefore remains blocked until the approved Piper bundle is explicitly reviewed, staged, and Android-validated.

## Phase 4B-3 — Reverse two-phone controlled E2E

The debug harness uses an in-APK Unicode-safe constant for the Tamil source, avoiding ADB Unicode extras:

```text
Source:  எனக்கு உதவி தேவை.
Target:  HI
Expected: मुझे मदद की जरूरत है.
```

Phone A (`10BCAC2HM5000CR`, V2205) was receiver with local target `HI`; Phone B (`MJPVXCSG9HYL65YL`, RMX3782) was sender. Both devices had the approved CT2 model staged at `files/ct2/nllb600m`; device-side `model.bin` SHA-256 matched `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` on both. Hindi model and tokens on both devices matched `8871f3e07adb6ca490f8dbcd3956a8647c53c35b5d0a1c2a8d097b3bf721a31b` and `ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d`.

### Result

```text
Wi-Fi Direct connect: 1505 ms
B TX runs:            3
A RX runs:            3/3 exact Tamil payloads
A presentation:       sourceLang=TA target=HI
A output:              मुझे मदद की जरूरत है.
Direct route:         tam_Taml → hin_Deva; no English pivot
Hindi TTS model:      loaded (4369 ms on first run)
Synthesis:            922 ms, then 694 ms and 700 ms
Audio durations:      1869 ms, 1904 ms, 2020 ms
AudioTrack:           22,050 Hz mono PCM_FLOAT; complete writes observed
Playback:             all three AudioTrack writes completed; no TTS/audio failure
ACKs:                 correlated for all three messages
```

The three-message repeat verified model reuse: the receiver did not reload the Hindi model between messages. No crash, ANR, `TtsException`, AudioTrack initialization/write failure, or transport corruption was observed. Mobile data was disabled while Wi-Fi Direct remained enabled; translation, Hindi TTS, and playback used local resources only.

This is a controlled reverse-path **PASS**. It does not include microphone/Whisper E2E or a human listening-quality claim. The debug harness was corrected to select the known Phone A P2P address after a stale desktop peer caused one preliminary connection attempt to fail; no production transport code was changed.

## Phase 4D-2 — Whisper → PacketV2 outbound integration

A debug-only `WhisperPacketSmokeActivity` connected Phone A (`10BCAC2HM5000CR`, V2205) to Phone B (`MJPVXCSG9HYL65YL`, RMX3782) over existing Wi-Fi Direct TCP. The receiver used temporary debug transport-only mode, so it did not invoke CT2 or TTS. No PacketV2 framing, CRC, fragmentation, ACK, or transport implementation was changed.

Observed path:

```text
Phone A microphone
→ WhisperSttEngine, explicit ta
→ SttResult
→ enqueueWhisperTranscript()
→ Room outbound queue
→ PacketV2 / Wi-Fi Direct TCP
→ Phone B receiver
```

Physical run:

```text
Wi-Fi Direct connect: 2521 ms
Whisper model load:  133 ms
Capture:             9940 ms / 159040 samples
VAD:                 vadHasSpeech=false
Whisper result:      மிகவும் வேண்டும் செய்வில்லை...
Whisper latency:     9816 ms
SttResult language:  TAMIL
Message ID:          5d800457-d782-431e-a182-0c5cdc45b997
B received payload:  மிகவும் வேண்டும் செய்வில்லை...
B received language:TA
ACK:                 correlated / TX_COMPLETE
```

The outbound message text and receiver text matched the actual Whisper result exactly, including Unicode. No hardcoded transcript was used. Mobile data was disabled (`mobile_data=0`); CT2 and TTS were skipped. No crash, ANR, native error, or transport corruption was observed.

This proves one real microphone→Whisper→Room→PacketV2→Wi-Fi Direct→receiver path. It does not establish transcript accuracy, because the run's VAD flag was false and the result was not judged against a controlled spoken phrase. Only one run was completed; the requested three-run stability gate remains pending.

**Phase 4D-2 status: NOT PASS — one transport integration run passed, but three-run physical stability and controlled speech evidence are incomplete.**

## Phase 5 — Production frontend language configuration

The production frontend now treats the persisted `UserLanguageProfile` fields as independent:

```text
preferredInputLanguage  = You Speak       → explicit Whisper source language
preferredOutputLanguage = You Understand  → receiver-local translation/TTS target
```

The existing DataStore keys (`preferred_input_language` and `preferred_output_language`) remain the persistence mechanism. The language editor initializes from the persisted profile, allows independent EN/HI/TA selection including equal pairs, and provides a swap action. The chat microphone continues to derive its label and `SttLanguage` exclusively from You Speak; the direction summary and incoming presentation derive target text from You Understand. No target-language field was added to PacketV2.

Conversation labels now identify the actual source and target language (`ORIGINAL · <language>` and `TRANSLATED · <language>`), rather than using a generic demo label. Wi-Fi Direct is shown as an available/local peer transport with TCP/39721 terminology instead of `PLANNED`; no internet connection is implied. Locate continues to distinguish peer connectivity from separately implemented coordinate sharing and does not claim RSSI distance or GPS proof.

The wired backend remains unchanged: explicit input reaches Whisper, received source text reaches the receiver-local coordinator, same-language pairs bypass CT2, and different-language pairs use direct CT2 routes before the selected TTS engine. The debug transport-only preference is confined to the debug harness.

Validation completed: `:app:testDebugUnitTest` passed; `:app:assembleDebug` passed; the debug APK installed explicitly on both `10BCAC2HM5000CR` (V2205) and `MJPVXCSG9HYL65YL` (RMX3782), and `MainActivity` launched successfully on both. No microphone E2E or Phase 4D-3 test was started.

## Phase 5 — Production frontend readiness

The normal production Chat flow now sends a completed Whisper result through `CommunicationViewModel.enqueueWhisperResult()` to `VokieApplication.enqueueWhisperTranscript(result, receiverId)`, preserving the actual transcript, source language, message metadata, and existing Room/PacketV2 queue. The text-composer path remains separate and uses the existing message creation path.

The language editor uses the existing DataStore-backed `UserLanguageProfile` with independent `preferredInputLanguage` (You Speak/Whisper) and `preferredOutputLanguage` (You Understand/receiver-local translation and TTS). It initializes from the persisted profile, permits all EN/HI/TA combinations including same-language pairs, and provides swap. Chat labels the microphone from You Speak and shows source/translated language labels from message/presentation metadata.

Production dependency injection remains local and approved: WhisperSttEngine, Ctranslate2TranslationEngine, SherpaOnnxTtsEngine, and VokieAudioPlayer. The old debug `transport_only` receiver suppression was removed from the production receiver so a stale debug preference cannot hide translation/TTS. Wi-Fi Direct is represented as direct local peer transport when active; no internet/cloud state is claimed.

Validation: `:app:testDebugUnitTest` and `:app:assembleDebug` passed. The debug APK was explicitly installed and `MainActivity` launched successfully on both V2205 (`10BCAC2HM5000CR`) and RMX3782 (`MJPVXCSG9HYL65YL`). The packaged APK still contains `lib/arm64-v8a/libvokie_ct2.so`. No final microphone production E2E was run; CT2/TTS physical regressions were not rerun in this frontend-only phase.

Phase L1 adds a separate message-triggered, GPS-free qualitative proximity observer. It records direct-link state, message receive/send/ACK triggers, delivery, retry metadata, and locally measured ACK RTT without changing the speech pipeline or PacketV2. See `docs/location-integration.md`; no metres, coordinates, direction, or GPS claim is made.
