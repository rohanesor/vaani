# Phase 3D.5 — offline EN/HI/TA TTS prototype benchmark

## Final status

**NOT RUN — blocked before model execution.** No TTS model is selected or approved for the iTantra prototype. No production Android, TTS, translation, transport, location, or release configuration was changed in this phase.

The requested Tamil→Hindi receiver demo cannot honestly include `[PLAY HINDI]`, TTS latency, audio duration, or TTS total latency until a Hindi artifact passes provenance/legal/device gates. The UI must continue to expose model unavailability rather than fabricate playback or latency.

## Artifact inventory

The repository contains locally staged MMS/VITS-style ONNX files, but their presence is not approval:

| Language | Local candidate path | Model bytes | Tokens bytes | SHA-256 from manifest |
|---|---|---:|---:|---|
| EN | `app/src/main/assets/models/tts/eng/model.onnx` | 114,016,948 | 303 | `e3a198f6a4473429bab138be040e7cd40d2cab7a31b6410ff0a94d5a7fbbc254` |
| HI | `models/tts/vits-mms-hin/model.onnx` | 114,043,064 | 472 | `42c69b3611dc016ff337e994c78a76b5131156718c5a69e9cfa8912cfd850c5e` |
| TA | `models/tts/vits-mms-tam/model.onnx` | 114,032,312 | 375 | `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88` |

Only EN is presently staged under Android production assets. HI and TA files are outside that asset path. None has an acquisition record in this repository establishing an official source revision, artifact-license copy, redistribution approval, or APK-bundling approval for EN/HI/TA.

## Provenance and license gate

The project’s existing TTS production decision records current MMS ONNX as an **unselected baseline**, not an approved artifact. It identifies the upstream MMS cards as CC-BY-NC-4.0 and records incomplete conversion provenance and redistribution approval. The artifact matrix likewise reports that no TTS row passes all official-artifact, commercial-redistribution, and Android-native CPU gates.

Result:

| Gate | Status |
|---|---|
| Exact official EN/HI/TA model provenance | incomplete |
| Artifact license / APK redistribution approval | blocked |
| sherpa-onnx runtime architecture | available as pinned Android AAR `1.13.6` |
| Approved model registry route | absent by design |
| Offline inference after approved local installation | untested |

No replacement artifact was acquired, as required.

## Benchmark results

| Required measurement | EN | HI | TA |
|---|---:|---:|---:|
| Initialization | not measured | not measured | not measured |
| Cold / first synthesis | not measured | not measured | not measured |
| Five warm runs / median | not measured | not measured | not measured |
| Audio duration / sample rate | not measured | not measured | not measured |
| RTF | not measured | not measured | not measured |
| PCM conversion latency | not measured | not measured | not measured |
| Peak RAM | not measured | not measured | not measured |
| Quality / intelligibility | not evaluated | not evaluated | not evaluated |
| Device A initialization / first / warm / playback start | not run | not run | not run |

No laptop sherpa-onnx runtime is installed in the available environment, and no Device A is connected. No desktop result is substituted for Android evidence.

## Translation demo posture

There is no verified offline MT model. A small, explicitly labelled **DEMO / EMERGENCY PHRASE TRANSLATION** dictionary is permissible only for the supplied phrases and must return `TRANSLATION_UNAVAILABLE` for unknown text. It is not an AI/ML translation claim. This phase did not wire such a dictionary or presentation UI because the requested audio demonstration would still have no approved TTS backend.

## Build validation

```text
./gradlew test --no-daemon: PASS
./gradlew assembleDebug --no-daemon: BLOCKED
```

`assembleDebug` reached the native CMake stage but this WSL host cannot find a compatible Ninja tool in its Android CMake environment. This is a host build-toolchain failure; it is not Android-device or TTS evidence.

## Recommendation

Do not integrate or benchmark the staged MMS candidates further under the current legal/provenance record. The next permitted step is a separate acquisition/provenance decision for exact EN, HI, and TA artifacts. It must preserve official source/revision, license and redistribution terms, artifact checksums, sherpa-onnx compatibility, and authorization for APK inclusion. Only then can an isolated laptop benchmark and Device-A offline benchmark be run.
