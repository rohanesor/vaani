# Phase 2B production TTS decision

## Decision: no model artifact is selected for production

**Status: BLOCKED.** Phase 2B establishes the required architecture boundary but does not approve an artifact backend. Every researched candidate fails at least one mandatory gate. Replacing the existing MMS implementation or shipping an unverified substitute would violate offline, redistribution, or low-end Android requirements.

## Recommended architecture boundary

```text
Incoming persisted text + declared language
        ↓
UnifiedTtsEngine.synthesize(text, languageCode, optionalVoice)
        ↓
LanguageRouter (canonical language → registry entry; no UI selection)
        ↓
TtsModelRegistry (pinned, bundled, legally approved artifacts only)
        ↓
TtsBackend session (one native session at a time)
        ↓
PCM → AudioTrack
```

`app/src/main/java/com/vokie/tts/UnifiedTtsEngine.kt` adds this candidate-neutral contract without wiring it into current playback. A future implementation must return `TtsErrorCode.UNSUPPORTED_LANGUAGE` for an absent route; it must never silently synthesize in a different language.

The eventual registry must map every required canonical code (`hi`, `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`, `en`) to a verified bundled backend. It must lazy-load only the requested backend, serialize synthesis, release the prior native session before a language switch, and use a bounded LRU/eviction policy validated by Android PSS/RSS measurements. It must not download, import, query, or select voices at runtime.

## Why no current candidate is recommended

| Candidate | Why it is not selected |
|---|---|
| AI4Bharat `vits_rasa_13` | Only 6/10 required languages, gated artifacts, and no Android-native package. |
| AI4Bharat Indic Parler | 10/10 language tags and Apache card, but gated, 3.76 GB payload, and no proven Android CPU path. |
| AI4Bharat Indic-TTS | Covers required language releases, but uses 1.5 GB-class FastPitch/HiFi-GAN checkpoint packs, has no Android package, and lacks artifact-specific redistribution/digest evidence. |
| Current MMS ONNX | Completes the matrix and has a sherpa-onnx path, but official upstream MMS cards are CC-BY-NC-4.0, conversion provenance/redistribution approval is incomplete, and 2 GB-device benchmark evidence is absent. |

## Required evidence before production migration

1. A candidate (or hybrid) must provide pinned official artifacts for all ten languages with weight, frontend, code, and vocoder redistribution approval for APK/S3/CloudFront use.
2. The complete installed payload must be measured from the signed candidate APK, including extraction duplication.
3. Each language must pass native Android CPU synthesis in airplane mode on a representative 2 GB RAM device.
4. Per-language initialization, first/warm latency, RTF, PSS/RSS, CPU, and thermal behavior must be captured.
5. An approved five-sentence-per-language corpus and blinded native-listener evaluation must establish intelligibility, pronunciation, naturalness, prosody, artifacts, and failures.
6. Any FP16/INT8 artifact must reproduce valid audio and pass the same quality/device gates.
7. A concrete `TtsModelRegistry` mapping must be reviewed; only then may `TextToSpeechUseCase` be migrated to `UnifiedTtsEngine`.

## Current operational posture

MMS remains an unselected baseline, not the architecture decision. The existing app is intentionally untouched: no model packaging, downloader, AWS/CloudFront configuration, release process, or playback wiring changed in this phase.
