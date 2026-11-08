# iTANTRA LAP1 Corpus-First Pipeline Validation

## Executive Summary

The canonical v2 APK SHA was independently verified and installed on both connected devices. The existing synthetic corpus validated successfully: 500 WAV files, 50 per language, 16 kHz mono PCM16.

The repository's existing corpus runner is an evaluator for an **Android-produced results CSV**; it is not an audio injector or STT inference runner. No existing supported mechanism feeds all corpus WAVs into the installed production APK. Therefore no fabricated 500-sample STT, CT2, TTS, or full-pipeline results are reported.

## Canonical APK

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `7a947cdb39974f8148cb9d32ef9431d367af926ffbc9693acc2532b781bb7c06`
- Size: 1,187,389,113 bytes
- Package/version: `com.vokie` / 1.0.0 / versionCode 1
- ABI: arm64-v8a
- Canonical-establishing commit: `faf94fd`
- Recorded artifact build source commit: `7589e0faaa360cd52add664af9468e15b4398e1a`

## Corpus Integrity

| Property | Result |
|---|---|
| Total WAV files | 500 |
| Samples per language | 50 × 10 |
| Metadata records | 500 |
| Audio format | 16 kHz, mono, PCM S16LE |
| Validator | PASS (`python3 corpus/validate_corpus.py corpus`) |
| Dataset label | SYNTHETIC SPEECH CORPUS |

Language counts: EN 50, HI 50, TA 50, GU 50, MR 50, KN 50, ML 50, TE 50, OR 50, BN 50.

## Tooling Assessment

`corpus/run_stt_corpus.py` explicitly requires `--android-results`, described as a CSV produced by an actual Android offline STT harness. It calculates metrics from supplied predictions; it does not invoke Whisper, SenseVoice, Android audio capture, CT2, or TTS. The run was attempted with no results CSV and correctly failed with `FileNotFoundError`. No substitute engine was used.

`ANDROID_CORPUS_EXECUTION=BLOCKED` because the existing APK has no supported automated corpus-WAV injection path. `CORPUS_ENGINE_TEST=NOT AVAILABLE`; no non-Android production-equivalent batch harness exists in the repository.

## STT Results — 10 Languages

No corpus sample reached production STT through an automated supported path. Odia remains unavailable and its corpus audio is Hindi TTS fallback.

| Language | Samples | STT Success | STT Failure | WER | CT2 | TTS | Full Pipeline |
|---|---:|---:|---:|---:|---|---|---|
| English | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Hindi | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Tamil | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Gujarati | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Marathi | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Kannada | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Malayalam | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Telugu | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |
| Odia | 50 | 0 | 0 | NOT_VALIDATED | BLOCKED | BLOCKED | BLOCKED |
| Bengali | 50 | 0 | 0 | NOT_MEASURED | BLOCKED | BLOCKED | BLOCKED |

Zeros mean no corpus execution was performed, not failed transcriptions.

## Odia Limitation

`ODIA_CORPUS_AUDIO=HINDI_TTS_FALLBACK`.

`ODIA_STT=RUNTIME_UNAVAILABLE` and `ODIA_STT_ACCURACY=NOT_VALIDATED`. The 50 Odia files must not be treated as genuine Odia speech evidence.

## Translation Results

No successfully transcribed corpus outputs existed to pass into CT2. Device B also lacked the runtime-staged CT2 model during the preceding runtime gate; device A's staged model did not produce a completed translation result in that gate. No corpus translation output was fabricated.

`CT2_CORPUS=BLOCKED`.

## TTS Results — 10 Languages

No corpus-derived translated text was available and no corpus TTS batch harness exists in the current APK/tooling. Prior isolated TTS smoke results are not corpus-pipeline results.

`TTS_CORPUS_10_LANGUAGE=BLOCKED`.

## Full Pipeline Results

No WAV → STT → transcript → CT2 → target text → TTS → output audio flow was completed for any corpus sample.

`FULL_CORPUS_PIPELINE=BLOCKED`.

## Latency

| Language | Mean STT | P50 STT | P95 STT | Mean CT2 | Mean TTS | RTF |
|---|---:|---:|---:|---:|---:|---:|
| All ten | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED |

`LATENCY_VALIDATION=NOT TESTED` for the corpus. No timing was invented.

## Memory / CPU / Thermal

No corpus resource measurements were possible. Existing device observations are not corpus measurements. `RESOURCE_VALIDATION=NOT TESTED`.

## Android vs Offline Harness Coverage

| Layer | Evidence | Result |
|---|---|---|
| Canonical APK | SHA, package, version, ABI verified; installed on both phones | PASS |
| Corpus files/metadata | Local validator | PASS |
| Android corpus injection | No existing supported path | BLOCKED |
| Android batch STT | No results CSV/harness | BLOCKED |
| CT2 corpus stage | No STT outputs; no batch route | BLOCKED |
| TTS corpus stage | No translated corpus outputs; no batch route | BLOCKED |
| Full corpus pipeline | No complete executable route | BLOCKED |

## Accuracy Interpretation

`ACCURACY_VALIDATION=NOT TESTED`. Synthetic corpus WER/CER would be an engineering/regression metric only, not real-world human accuracy. No WER/CER values were calculated. No real microphone or human voice testing was performed.

## Failures

- Existing STT evaluator failed because required Android results CSV was absent (`FileNotFoundError`); this is an expected tooling dependency failure, not a fabricated zero-result evaluation.
- The application does not expose an automated corpus WAV injection interface.
- Existing debug activities are isolated smoke/profiling surfaces, not a 500-sample corpus runner.
- CT2 and TTS could not be chained from corpus STT output.

## Blockers

1. A validation-only Android corpus injection/runner is required to feed WAV files into the same production STT path without changing production behavior.
2. CT2 must be staged and successfully exercised on a device.
3. A controlled corpus result export is required before running `run_stt_corpus.py`.
4. TTS and full-pipeline corpus orchestration are not present in existing tooling.
5. Odia STT remains runtime unavailable.

## Next Phase

Do not begin real-human voice testing or claim accuracy. The next technical step is to provide an approved validation-only corpus injection harness that uses the production STT engine and exports per-sample results; then stage CT2, execute representative translation/TTS chaining, and rerun the corpus metrics with synthetic-data labeling preserved.

## Final Status

```text
CORPUS_VALIDATION=PASS
CORPUS_SAMPLE_COUNT=500
STT_CORPUS_10_LANGUAGE=BLOCKED
STT_VALID_LANGUAGE_COUNT=0
ODIA_STT=RUNTIME_UNAVAILABLE
CT2_CORPUS=BLOCKED
TTS_CORPUS_10_LANGUAGE=BLOCKED
FULL_CORPUS_PIPELINE=BLOCKED
ANDROID_CORPUS_EXECUTION=BLOCKED
ACCURACY_VALIDATION=NOT TESTED
LATENCY_VALIDATION=NOT TESTED
RESOURCE_VALIDATION=NOT TESTED

EN=BLOCKED
HI=BLOCKED
TA=BLOCKED
GU=BLOCKED
MR=BLOCKED
KN=BLOCKED
ML=BLOCKED
TE=BLOCKED
OR=RUNTIME_UNAVAILABLE
BN=BLOCKED
```
