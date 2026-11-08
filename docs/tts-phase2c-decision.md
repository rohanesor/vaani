# Phase 2C decision

## Decision: BLOCKED — no production artifact architecture can be selected

The locked product abstraction remains:

```text
UnifiedTtsEngine → LanguageRouter → TtsModelRegistry → TtsBackend → PCM / AudioTrack
```

It is intentionally model-neutral. No current backend is wired to this contract, and no production MMS code or assets are changed by this phase.

## Recommended TTS architecture

**Recommended architecture class: a legally approved, compact native hybrid behind `UnifiedTtsEngine`.** This is an architectural recommendation, not a model selection. The future registry must contain explicit routes for `hi`, `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`, and `en`; it must bundle every route at install time, lazy-load one backend session, and return `TTS_UNSUPPORTED_LANGUAGE` rather than substitute a language.

A single physical model is not required. Present evidence does not identify a qualifying one-model candidate. Equally, shipping ten models is acceptable only if the aggregate artifacts, rights, install footprint, and 2 GB-device performance all pass.

## Why this is better than retaining MMS as the decision

The architecture decouples the app from MMS and removes the assumption that one VITS pack per language is permanent. It supports a smaller, mixed registry when valid artifacts appear, while preserving automatic language routing and one-active-session memory behavior. MMS remains a benchmark baseline only; its current 1.14 GB footprint, runtime-pack behavior, CC-BY-NC upstream licensing, and missing Android device evidence prevent production selection.

## Measured versus unmeasured evidence

- **Measured:** current ten MMS ONNX + token payload is 1,140,400,586 bytes; experimental individual INT8 files sum to 379,959,630 bytes but are rejected; Piper's selected five ONNX files sum to 342,400,826 bytes; Kokoro official weight is 327,212,226 bytes.
- **Host-only MMS evidence:** English `Help me` synthesis took 315 ms for 564 ms of audio (RTF 0.559); process max RSS was 171,072 KiB. This is not Android evidence.
- **Unmeasured:** all candidate Android PSS/RSS, CPU, cold/warm latency, time-to-first-audio, RTF, thermal behavior, and native-listener quality scores.

## Remaining blockers

1. No known official compact native artifact set covers all ten languages with artifact-specific commercial redistribution approval.
2. Piper covers only Bengali, Hindi, Malayalam, Marathi, and Telugu; its individual voice data licenses cannot be generalized from the repository MIT license.
3. Kokoro covers Hindi/English only and has no official native Android/ONNX package.
4. AI4Bharat one-model candidates are gated or exceed the low-end mobile envelope.
5. MMS is technically usable as a benchmark but fails legal, packaging, and Android-device gates.
6. No candidate has passed the required five-category, native-listener quality corpus or 2 GB Android CPU benchmark.

## Exact next implementation step

**Do not migrate production TTS.** Obtain an explicit written redistribution/commercial-use determination for an official compact Indic ONNX artifact set that can supply the five Piper-missing routes (`gu`, `kn`, `ta`, `or`, `en`) and the Piper routes if retained. In parallel, acquire a representative 2 GB ARM Android test device and run the locked benchmark protocol on only artifacts that pass that legal gate. The first prototype must be isolated from production playback and use `UnifiedTtsEngine` plus a test-only `TtsModelRegistry`.
