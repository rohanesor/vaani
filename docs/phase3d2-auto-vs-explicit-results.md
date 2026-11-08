# Phase 3D.2 — Device-A same-PCM AUTO versus explicit Whisper results

## Baseline preservation

- Git commit at investigation start: `8bed392392a3e120c67cdac8466daaaab687bace`.
- Production configuration is unchanged: portable arm64-v8a NEON, no forced dotprod, 4 threads, Whisper `tiny` multilingual Q5_1, `AUTO`, timestamps enabled, and fixed `audio_ctx=1500`.
- Device A is the realme RMX3782 / narzo 60x 5G, Android 15/API 35, arm64-v8a, MT6835.

## Evidence scope

Only one retained replay PCM exists: the English phrase **“How are you?”**, 24,320 samples / 1,520 ms. Phase instructions prohibit recording new speech for this experiment. There is no retained same-PCM capture for HI, TA, TE, BN, GU, MR, KN, ML, or OR. Those rows are deliberately **NOT MEASURED**, not failures or inferred results.

| Language | Audio ms | AUTO runs / median ms | AUTO RTF | Explicit runs / median ms | Explicit RTF | Accuracy | Speed gain |
|---|---:|---:|---:|---:|---:|---|---:|
| EN | 1520 | 6634, 6462, 6074 / **6462** | 4.25 | 3079, 3102, 3119 / **3102** | 2.04 | AUTO detected EN; both modes transcribed “How are you?” correctly | **52.0%** |
| HI | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| TA | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| TE | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| BN | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| GU | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| MR | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| KN | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| ML | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |
| OR | — | NOT MEASURED | — | NOT MEASURED | — | No retained same PCM | — |

The aggregate across measured languages is necessarily EN-only: AUTO median 6462 ms, explicit median 3102 ms, and speed gain 52.0%. This is not a ten-language aggregate and must not be generalized.

## Auto-LID profiling limits

Current JNI timing exposes `nativeFullMs`, aggregate encoder average, decode average, sampling average, batch-decode average, and encoder callback count. It does **not** expose a separately reliable duration for mel creation, Auto-LID, first encoder, language-token decode, or second transcription encoder. Those values are **NOT EXPOSED BY CURRENT VENDORED API**.

Source inspection establishes the topology: `whisper_full_with_state()` invokes `whisper_lang_auto_detect_with_state()` before normal transcription; Auto-LID calls `whisper_encode_with_state()`, then normal transcription calls `whisper_encode_internal()`. The exact same EN PCM showed 6462 ms AUTO versus 3102 ms explicit, a 3360 ms difference. That is measured end-to-end Auto-path overhead for this utterance, not a precise isolated Auto-LID timer.

No whisper.cpp internal changes were made.

## Preferred-language recognition strategy (design only)

`UserLanguageProfile` already stores a local preferred language independently from `SttLanguagePreferences`, whose fresh default is `AUTO`.

```text
Preferred language (local profile)
        |
Recognition mode selected by user
        |-- AUTO: Whisper AUTO; valid detected language wins; unsupported LID uses profile fallback
        `-- FAST (future): Whisper explicit preferred language
```

FAST must be opt-in and visibly labelled with its explicit language. It must never silently replace AUTO, and users retain the Auto Detect selector. Sender packet language continues to derive from the resolved STT result; no PacketV2 behavior changes are required.

## Decision

No production configuration changes are justified. Complete ten-language comparison remains blocked until one retained, labelled PCM capture per target language is available for replay.
