# IndicVoices-R and LibriTTS data audit

## Exact source state

| Dataset | Exact version/release evidence | Acquisition performed | Result |
|---|---|---|---|
| IndicVoices-R | GitHub revision `4d77d8a402b680fb739c1a6a3ec531de0cf17139`, dated 2024-12-11; official HF dataset `ai4bharat/indicvoices_r` revision `5f4495c91d500742a58d1be2ab07d77f73c0acf8`; Zenodo concept `11636050` | official HF card and file index only; no parquet/audio download | GitHub revision is not a valid HF revision, so current official HF revision is pinned separately. Card/file index exposes schema, split counts, shard paths, bytes, and per-shard LFS SHA-256. Viewer/API needs authentication for per-row metadata. |
| LibriTTS | OpenSLR SLR60 | official landing page/archive index only; no corpus archive downloaded | Source index acquired; detailed manifests/checksums unavailable without archive acquisition |

## Required-language statistics

| Language | ISO | Dataset | Hours | Utterances | Speakers | Gender | Sample rate | Domains | License | Status |
|---|---|---|---|---|---|---|---|---|---|---|
| Hindi | hi | IndicVoices-R | UNKNOWN | 26,694 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Gujarati | gu | IndicVoices-R | UNKNOWN | 1,791 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Marathi | mr | IndicVoices-R | UNKNOWN | 19,141 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Kannada | kn | IndicVoices-R | UNKNOWN | 17,328 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Malayalam | ml | IndicVoices-R | UNKNOWN | 31,503 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Tamil | ta | IndicVoices-R | UNKNOWN | 39,585 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Telugu | te | IndicVoices-R | UNKNOWN | 47,547 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Odia | or | IndicVoices-R | UNKNOWN | 25,565 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| Bengali | bn | IndicVoices-R | UNKNOWN | 40,192 | UNKNOWN | schema field available; values require authenticated rows | 48,000 Hz | WAV, TTS/ASR-enhanced | CC-BY-4.0 declaration | Partial metadata |
| English | en | LibriTTS | approximately 585 total corpus hours | UNKNOWN | UNKNOWN | UNKNOWN | 24,000 Hz | read English | CC-BY-4.0 | Blocked detailed metadata |

IndicVoices-R public aggregate facts are 1,704 hours, 10,496 speakers, and 22 Indian languages. They must not be divided across languages without manifests.

## Audio and transcript audit result

No source audio or parquet shard was downloaded. The HF card establishes WAV/48-kHz schema and exposes text, verbatim, normalized, lang, speaker_id, gender, duration, and acoustic-quality fields, but the authenticated Dataset Viewer/API is required to enumerate their values. Bit depth, channels, clipping, silence, noise, volume, corruption, transcript/audio mismatch, Unicode normalization, punctuation, numeric forms, code-switching, transliteration, and spelling consistency remain **UNKNOWN**. The required automated audit tools are committed under `scripts/tts-data/`; they have not been run against data.

## Dataset training gate

**BLOCKED pending authenticated metadata query.** The official HF card/file index establishes 427,154,513,101 bytes of required-language parquet download and 9 language schemas without downloading audio, but total duration, unique speakers, gender distribution, and balance need authenticated per-row metadata. No Hugging Face token or CLI credential was available in this execution environment, so the Viewer/API returned HTTP 401. Training must not start.
