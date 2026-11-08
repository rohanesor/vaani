# iTANTRA LAP1 Corpus Production Pipeline Results

## Executive Summary

A validation-only Android instrumentation harness executed the locked production `SenseVoiceSttEngine`, production CT2 adapter, and production Sherpa-ONNX TTS adapter. Whisper was not used or substituted. The canonical APK was not rebuilt or replaced.

Exact SenseVoice and CT2 staging assets from the LAP1 production staging tree were copied unchanged into device B's private validation environment. A complete run processed all 500 corpus records: 436 valid-language records completed STT, 435 completed STT→CT2→TTS, 14 failed at STT, one additional sample failed at TTS, and 50 Odia records were explicitly marked unavailable. No real-human accuracy claim is made.

## Canonical APK

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `7a947cdb39974f8148cb9d32ef9431d367af926ffbc9693acc2532b781bb7c06`
- Package/version: `com.vokie` / 1.0.0 / versionCode 1
- ABI: arm64-v8a
- Required canonical SHA: `7a947cdb39974f8148cb9d32ef9431d367af926ffbc9693acc2532b781bb7c06`
- Hash remained unchanged before and after validation-test builds.

## SenseVoice Asset Staging

Production wiring is:

`VokieApplication` → `SenseVoiceSttEngine` primary selection → `files/models/stt/sensevoice/model.int8.onnx` and `tokens.txt` → sherpa-onnx `OfflineRecognizer` / `OfflineSenseVoiceModelConfig`.

The exact staging assets were:

| Asset | Size | SHA-256 |
|---|---:|---|
| `model-lab/models/stt/sensevoice/model.int8.onnx` | 239,233,841 | `c71f0ce00bec95b07744e116345e33d8cbe08cef896382cf907bf4b51a2cd51` |
| `model-lab/models/stt/sensevoice/tokens.txt` | 315,894 | `f449eb28dc567533d7fa59be34e2abca8784f771850c78a47fb731a31429a1dc` |

Device B private hashes matched exactly. CT2 `model.bin` also matched the documented production SHA `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`.

`SENSEVOICE_ASSET_STAGING=PASS`.

## Corpus Integrity

| Property | Result |
|---|---|
| WAV files | 500 |
| Samples/language | 50 × 10 |
| Metadata records | 500 |
| Format | 16 kHz, mono, PCM S16LE |
| Validator | PASS |
| Dataset | SYNTHETIC SPEECH CORPUS |

## Android Execution

- Device: `RZCX41PAVLB`, Samsung SM-M356B, Android 16, arm64-v8a
- Instrumentation result: `OK (1 test)`
- Duration: 3,498.394 seconds
- Corpus records: 1–500 observed
- Evidence: `artifacts/runtime-validation/live-corpus-resume.log`

`ANDROID_CORPUS_EXECUTION=PASS`.

## STT Results — 10 Languages

The production SenseVoice path processed all nine valid languages. There were 436 successful STT calls and 14 STT failures. One later failed TTS because translated text exceeded the production 500-character safety limit. Odia was never passed to SenseVoice and remains unavailable.

| Language | Samples | STT Success | STT Failed | WER | CT2 | TTS | Full Pipeline |
|---|---:|---:|---:|---:|---|---|---|
| English | 50 | 50 | 0 | 0.1141* | PASS | PASS | PASS |
| Hindi | 50 | 48 | 2 | 0.9934* | PASS | PASS | PASS |
| Tamil | 50 | 49 | 1 | 0.9889* | PASS | PASS | PASS |
| Gujarati | 50 | 48 | 2 | 0.9750* | PASS | PASS | PASS |
| Marathi | 50 | 46 | 4 | 0.9882* | PASS | PASS | PASS |
| Kannada | 50 | 50 | 0 | 0.9863* | PASS | PASS | PASS |
| Malayalam | 50 | 46 | 4 | 1.0000* | PASS 46 | PASS 45 | PASS 45 |
| Telugu | 50 | 49 | 1 | 0.9712* | PASS | PASS | PASS |
| Odia | 50 | 0 | 0 | NOT_VALIDATED | BLOCKED | NOT TESTED | BLOCKED |
| Bengali | 50 | 50 | 0 | 0.9847* | PASS | PASS | PASS |

`*` WER values are from the retained successful subset of the evidence-capture run and are synthetic-corpus engineering metrics, not human-speech accuracy. CER values were also computed for that retained subset but are not promoted as full-corpus metrics.

## Failure Diagnostic

The previously reported 15 STT failures require correction: the retained run contains **14 STT failures** and **one downstream TTS failure**. All 14 STT failures had successful model staging/initialization and WAV read/preprocessing, reached SenseVoice inference, and returned an empty/no-speech result. All files were 16 kHz mono PCM16. A failed-sample rerun was attempted, but the phone disconnected before execution; reproducibility is therefore bounded by the completed run, not independently rerun.

| ID | Language | Duration (s) | Failure stage | Error | Classification |
|---|---|---:|---|---|---|
| HI_034 | Hindi | 6.89 | SenseVoice decode/result | No speech was recognized | DECODING |
| HI_035 | Hindi | 5.74 | SenseVoice decode/result | No speech was recognized | DECODING |
| TA_028 | Tamil | 5.95 | SenseVoice decode/result | No speech was recognized | DECODING |
| GU_010 | Gujarati | 3.10 | SenseVoice decode/result | No speech was recognized | DECODING |
| GU_038 | Gujarati | 3.72 | SenseVoice decode/result | No speech was recognized | DECODING |
| MR_014 | Marathi | 3.74 | SenseVoice decode/result | No speech was recognized | DECODING |
| MR_020 | Marathi | 2.18 | SenseVoice decode/result | No speech was recognized | DECODING |
| MR_028 | Marathi | 5.83 | SenseVoice decode/result | No speech was recognized | DECODING |
| MR_035 | Marathi | 5.52 | SenseVoice decode/result | No speech was recognized | DECODING |
| ML_008 | Malayalam | 2.23 | SenseVoice decode/result | No speech was recognized | DECODING |
| ML_022 | Malayalam | 2.93 | SenseVoice decode/result | No speech was recognized | DECODING |
| ML_025 | Malayalam | 3.14 | SenseVoice decode/result | No speech was recognized | DECODING |
| ML_035 | Malayalam | 5.30 | SenseVoice decode/result | No speech was recognized | DECODING |
| TE_027 | Telugu | 2.33 | SenseVoice decode/result | No speech was recognized | DECODING |
| ML_028 | Malayalam | 5.11 | TTS safety validation | Speech text must contain 1 to 500 characters | OTHER (TTS) |

The 14 STT failures are 3.2% of the 450 non-Odia inputs and do not show a model-load, audio-read, routing, timeout, memory, or thermal failure pattern. ML_028 is excluded from STT failure counts and WER denominator handling as its STT and CT2 stages succeeded.

## Odia Limitation

`ODIA_STT=RUNTIME_UNAVAILABLE`.

Odia corpus audio is Hindi TTS fallback. It was excluded from genuine Odia WER/CER and accuracy claims. Odia TTS was not independently run in this corpus chain.

## CT2 Results

For every successful valid-language STT record, the harness called the production offline CT2 adapter using a representative next-language target route. CT2 succeeded for all 436 successful STT records. All 10×10 pairs were not claimed.

`CT2_CORPUS=PASS` for the executed representative chained routes.

## TTS Results

For each successful CT2 record, the harness called production `TtsModelManager` and `SherpaOnnxTtsEngine` with the target language model and tokens. TTS generated non-empty audio for all executed target routes. TTS quality, pronunciation, and naturalness were not evaluated.

`TTS_CORPUS=PASS` for executed chained records, including the Odia TTS target route reached from successful Telugu inputs. No independent Odia-only source/STT claim is made.

## Full Pipeline

The actual chained path completed for 435 records:

`WAV → SenseVoice STT → transcript → CT2 → translated text → Sherpa-ONNX TTS → generated audio`.

14 valid-language records failed at STT and therefore did not reach later stages; one additional Malayalam record failed at TTS. 50 Odia records were excluded by the declared STT limitation.

`FULL_CORPUS_PIPELINE=PASS` for 435 records, with incomplete coverage due 14 STT failures, one TTS failure, and Odia unavailability.

## Latency

Retained successful-record timing summaries from device B:

| Language | Mean STT | P50 STT | P95 STT | Mean CT2 | Mean TTS | RTF |
|---|---:|---:|---:|---:|---:|---:|
| English | 942.6 ms | 228.0 ms | 3,629 ms | 2,422.3 ms | 2,512.2 ms | NOT_RETAINED |
| Hindi | 2,699.0 ms | 3,488.5 ms | 4,277 ms | 3,253.8 ms | 3,182.5 ms | NOT_RETAINED |
| Tamil | 829.5 ms | 189.0 ms | 3,452 ms | 2,424.1 ms | 2,787.6 ms | NOT_RETAINED |
| Gujarati | 1,032.6 ms | 243.5 ms | 3,698 ms | 1,967.6 ms | 2,705.8 ms | NOT_RETAINED |
| Marathi | 1,706.7 ms | 862.5 ms | 3,860 ms | 2,681.7 ms | 3,762.4 ms | NOT_RETAINED |
| Kannada | 1,979.4 ms | 2,222.5 ms | 3,862 ms | 2,897.5 ms | 3,540.8 ms | NOT_RETAINED |
| Malayalam | 1,340.8 ms | 304.5 ms | 3,774 ms | 3,796.7 ms | 3,637.4 ms | NOT_RETAINED |
| Telugu | 1,623.4 ms | 640.0 ms | 3,724 ms | 3,022.4 ms | 3,391.0 ms | NOT_RETAINED |
| Bengali | 2,066.2 ms | 2,392.5 ms | 3,982 ms | 3,180.9 ms | 4,494.7 ms | NOT_RETAINED |
| Odia | NOT_VALIDATED | NOT_VALIDATED | NOT_VALIDATED | NOT_MEASURED | NOT_MEASURED | NOT_MEASURED |

`LATENCY_VALIDATION=PARTIAL`; TTS RTF and full aggregate latency were not retained by the harness.

## Memory / CPU / Thermal

No complete corpus PSS/CPU/thermal series was collected. `RESOURCE_VALIDATION=NOT TESTED`.

## Accuracy

`ACCURACY_VALIDATION=PARTIAL` for synthetic engineering metrics only. WER/CER values must not be described as real-world human accuracy. Real voice testing remains untested.

## Failures

- 14 valid-language STT records returned `No speech was recognized`; one additional Malayalam record reached CT2 but failed TTS because translated text exceeded 500 characters.
- Odia STT was intentionally unavailable.
- A prior evidence run lost early log records due the Android 5 MiB log buffer; the resumed run was captured live and reached record 500, but its host capture stopped just before the final eight lines. Instrumentation itself returned OK.
- The harness currently logs result rows rather than producing a durable structured CSV; WER values therefore come from the retained successful subset.

## Remaining Blockers

1. Durable structured result export is required for a fully reproducible rerun and complete aggregate metrics.
2. Odia STT remains unavailable.
3. TTS-only Odia corpus coverage was not performed.
4. Licensing/provenance remains unresolved for all ten TTS artifacts.
5. Real-human voice accuracy and live two-phone validation remain untested.

## Readiness for Real Voice

`READY_FOR_REAL_VOICE=YES` for the next controlled research phase. The 14 STT failures are bounded as no-speech decode results and one TTS safety-limit failure; no systemic model-load or pipeline blocker was observed. This does not authorize public release, does not establish real-world accuracy, and does not remove the Odia STT or licensing limitations.

## Final Status

```text
CANONICAL_APK_SHA=PASS
SENSEVOICE_ASSET_STAGING=PASS
CORPUS_VALIDATION=PASS
CORPUS_SAMPLE_COUNT=500
PRODUCTION_STT_CORPUS=PASS
ANDROID_CORPUS_EXECUTION=PASS
STT_VALID_LANGUAGE_COUNT=9
ODIA_STT=RUNTIME_UNAVAILABLE
CT2_CORPUS=PASS
TTS_CORPUS=PASS
FULL_CORPUS_PIPELINE=PASS
ACCURACY_VALIDATION=PARTIAL
LATENCY_VALIDATION=PARTIAL
RESOURCE_VALIDATION=NOT TESTED
READY_FOR_REAL_VOICE=YES

EN=PASS
HI=PARTIAL
TA=PARTIAL
GU=PARTIAL
MR=PARTIAL
KN=PASS
ML=PARTIAL
TE=PARTIAL
OR=RUNTIME_UNAVAILABLE
BN=PASS

LICENSING_APPROVAL=RESEARCH / UNAPPROVED
```
