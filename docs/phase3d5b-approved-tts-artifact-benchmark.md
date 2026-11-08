# Phase 3D.5B — approved EN/HI/TA TTS artifact resolution

## Final answer

```text
TTS_ARTIFACT = NOT_APPROVED
```

No exact EN/HI/TA offline TTS artifact set can currently be legally and technically shipped in the iTantra prototype. Consequently no controlled benchmark, Android integration, or latency claim is authorized.

This resolution did not download a model, install a laptop TTS runtime, alter Android production configuration, or modify TTS routing.

## 1. Candidate table

| Language(s) | Candidate | Exact source / revision observed | License evidence | Runtime / Android path | Result |
|---|---|---|---|---|---|
| EN | Meta MMS `facebook/mms-tts-eng` | HF `c71de0fe7204c83f1c10820a7d696d0b450048ba` | CC-BY-NC-4.0 | Transformers; not an approved sherpa-onnx APK artifact | reject rights/runtime |
| HI | Meta MMS `facebook/mms-tts-hin` | HF `1d83b223ec78e30b944f7d96bd117eb3d7023303` | CC-BY-NC-4.0 | Transformers; not an approved sherpa-onnx APK artifact | reject rights/runtime |
| TA | Meta MMS `facebook/mms-tts-tam` | HF `e9cf59dae34f0f51e3b1842876a658e4516f9fe4` | CC-BY-NC-4.0 | Transformers; not an approved sherpa-onnx APK artifact | reject rights/runtime |
| EN/HI/TA | Local MMS-style ONNX conversions | local staged files; no source revision/conversion manifest | incomplete; existing decision records MMS as non-commercial/unapproved | `SherpaOnnxTtsEngine` can technically load VITS ONNX, but compatibility is not legal approval | reject provenance/rights |
| EN | Kokoro `kokoro-v1_0.pth` | previously audited HF revision `f3ff3571791e39611d31c381e3a41a3af07b4987` | Apache-2.0 card/weights | official PyTorch, no verified Android/sherpa route | reject runtime/coverage |
| HI/TA | Piper candidates | see existing artifact matrix | rights not established; Tamil has no verified artifact | no selected Android backend | reject |

## 2–5. Provenance, license, size, runtime

The official MMS cards report `cc-by-nc-4.0`; that does not meet the project’s redistribution/APK-shipping requirement. They also ship Transformers artifacts rather than official sherpa-onnx bundles.

Observed official weight sizes, not installed-size or RAM measurements:

| Artifact | `model.safetensors` bytes |
|---|---:|
| `facebook/mms-tts-eng` | 145,227,512 |
| `facebook/mms-tts-hin` | 145,253,624 |
| `facebook/mms-tts-tam` | 145,242,872 |

The project pins the official `com.k2fsa:sherpa-onnx:1.13.6@aar`, and its existing VITS engine is CPU/ARM64-oriented. This establishes a **runtime architecture**, not approved EN/HI/TA model artifacts. No official/pre-converted sherpa-onnx EN/HI/TA artifact with complete provenance and APK redistribution approval was verified.

The local ONNX candidates are about 114 MB each plus tiny token files, but their source conversion, upstream revision mapping, conversion tool/version/options, license record, sample rate, and speaker terms are not established. File size is not approval or a RAM estimate.

## 6–12. EN/HI/TA benchmark, latency, RTF, and RAM

No candidate passed the provenance/legal gate. Therefore all requested values are intentionally absent:

| Measurement | EN | HI | TA |
|---|---:|---:|---:|
| Laptop cold initialization | not measured | not measured | not measured |
| Laptop first synthesis | not measured | not measured | not measured |
| Five warm syntheses / median / min / max | not measured | not measured | not measured |
| Audio duration / sample rate | not measured | not measured | not measured |
| RTF | not measured | not measured | not measured |
| Peak RAM | not measured | not measured | not measured |
| Offline execution | not verified | not verified | not verified |

No sample audio or quality assessment was generated. No existing desktop smoke result is reused as Device-A evidence.

## 13–15. Android feasibility, Device A, offline verification

```text
ANDROID STATUS = UNVERIFIED
DEVICE A RESULTS = NOT RUN
OFFLINE VERIFICATION = NOT RUN
```

The Device-A serial is not connected in this session. The available host has no installed `sherpa_onnx` runtime. Neither limitation is worked around with cloud TTS, an emulator, or unapproved local files.

A valid future test must use an approved immutable local artifact set, a native ARM64 Device-A build, and airplane-mode synthesis. It must measure one selected receiver language at a time; EN, HI, and TA must not be presumed safe to load concurrently.

## Translation demo boundary

Translation remains unavailable as a general model. A separate, explicitly labelled **OFFLINE EMERGENCY PHRASE DEMO** may map only approved fixed phrases and must return `TRANSLATION_UNAVAILABLE` for unknown text. It must never be represented as AI or universal translation. It does not solve the absent Hindi/Tamil TTS artifact gate.

## Final recommendation

Do not ship, benchmark, or route to the current MMS-style ONNX files. The next action requires a separate approval record for exact EN, HI, and TA artifacts containing: official source/revision, artifact checksums, license and commercial/APK redistribution terms, token/frontend/speaker files, sample rate, sherpa-onnx compatibility evidence, and an Android ARM64 test authorization. Only after that record passes can laptop and RMX3782 cold/warm/RAM/RTF measurements answer how fast the artifacts are.
