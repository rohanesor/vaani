# Phase 3 TTS integration

## Status

The runtime boundary is implemented without selecting an unapproved model:

```text
UnifiedTtsEngine -> LanguageRouter -> TtsModelRegistry -> TtsBackend
  -> PCM -> AudioTrack
```

`UnavailableTtsEngine` is the production-safe current route. It reports an explicit unavailable/unsupported-language failure. It is not a mock voice and does not silently fall back to another language. MMS remains isolated as **BENCHMARK_ONLY** and is not wired as the approved production TTS engine.

## Language behavior

Incoming packet `languageCode` is resolved internally by `LanguageRouter`. The receiver does not manually select or download a language package. Required routes are `hi`, `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`, and `en`; each remains blocked until a legally approved, bundled artifact is available.

## Audio output gate

The eventual backend must stream PCM through `AudioTrack`, request the strongest supported emergency audio-focus mode, use large-volume alert handling, and document Android interruption limitations. Startup latency, CPU, RAM, RTF, and quality must be measured on two physical devices. No such measurements exist yet.
