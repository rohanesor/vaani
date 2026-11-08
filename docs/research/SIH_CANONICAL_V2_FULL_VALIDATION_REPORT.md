# iTANTRA LAP1 Canonical v2 Full Validation Report

## Executive Summary

The canonical v2 APK SHA matched exactly and was installed on both connected devices. APK content verification passed for all ten TTS model/token pairs and required native/STT components. The autonomous run continued through available tests, but the complete SIH runtime gate did not pass: the APK's existing debug harnesses cover only selected TTS/STT paths, CT2 was unavailable or did not complete, and no complete two-phone speech loop was established.

The corpus itself validated as 500 synthetic WAV files. Corpus STT execution was blocked because no Android-results CSV was produced. No real-world accuracy claim is made.

## Canonical APK Identity

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `7a947cdb39974f8148cb9d32ef9431d367af926ffbc9693acc2532b781bb7c06`
- Size: 1,187,389,113 bytes
- Package: `com.vokie`
- Version: 1.0.0 / versionCode 1
- ABI: arm64-v8a
- Source commit: `7589e0faaa360cd52add664af9468e15b4398e1a`
- Build flag recorded: `-PbundleAllTtsModels=true`

## Device Matrix

| Device | Serial | Manufacturer/model | Android | ABI | Storage observed | Thermal |
|---|---|---|---:|---|---|---|
| A | `MJPVXCSG9HYL65YL` | realme RMX3782 | 15 | arm64-v8a | 55 GB available | Status 1 |
| B | `RZCX41PAVLB` | Samsung SM-M356B | 16 | arm64-v8a | 31 GB available | Status 0 |

## Installation

| Device | Install | Package/version | Startup |
|---|---|---|---|
| A | PASS | Confirmed `com.vokie`, 1.0.0, code 1 | MainActivity launched; no immediate native crash observed |
| B | PASS | Confirmed `com.vokie`, 1.0.0, code 1 | MainActivity launched; no immediate native crash observed |

## 10-Language TTS Results

The exact v2 APK was used. Existing APK debug activities enabled direct smoke execution for Tamil on A and Gujarati on B. The other eight language synthesis paths were not directly executable through the existing APK harness and are therefore not inferred as passes. APK packaging for all ten was independently verified.

| Language | Model | Load | Synthesis | Playback | Latency | RTF | Result |
|---|---|---|---|---|---|---|---|
| English | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED |
| Hindi | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED |
| Tamil | packaged | PASS | PASS | PASS | 1,961 ms load; 2,810 ms first synthesis | 1.525 first | PASS on A |
| Gujarati | packaged | PASS | PASS | PASS | 4,693 ms load; 4,568 ms first synthesis | 3.525 first | PASS on B |
| Marathi | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED |
| Kannada | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED |
| Malayalam | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED |
| Telugu | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED |
| Odia | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED as exact-v2 runtime coverage |
| Bengali | packaged | NOT TESTED | NOT TESTED | NOT TESTED | NOT_MEASURED | NOT_MEASURED | BLOCKED |

`TEN_LANGUAGE_TTS_RUNTIME=PARTIAL`. TTS smoke output is functional evidence only, not linguistic-quality evidence.

## 9-Language STT Results

The existing APK harness explicitly exercises Tamil only. One Tamil capture/inference returned a transcript on device B; an earlier process also emitted a native fatal signal during the run, so the language result is recorded as PARTIAL rather than unconditional PASS. No other supported-language transcription was fabricated.

| Language | Runtime | Transcript | Latency | Result |
|---|---|---|---|---|
| English | not exercised | — | NOT_MEASURED | BLOCKED |
| Hindi | not exercised | — | NOT_MEASURED | BLOCKED |
| Tamil | Whisper executed; result returned | returned Tamil text | 11,504 ms | PARTIAL |
| Gujarati | not exercised | — | NOT_MEASURED | BLOCKED |
| Marathi | not exercised | — | NOT_MEASURED | BLOCKED |
| Kannada | not exercised | — | NOT_MEASURED | BLOCKED |
| Malayalam | not exercised | — | NOT_MEASURED | BLOCKED |
| Telugu | not exercised | — | NOT_MEASURED | BLOCKED |
| Bengali | not exercised | — | NOT_MEASURED | BLOCKED |

### Odia STT Limitation

`ODIA_STT=RUNTIME_UNAVAILABLE`. The Odia Hindi-fallback audio was not used as genuine Odia STT evidence.

## CT2 Translation Results

The CT2 smoke activity was launched on both devices. Device A contained the staged model but did not produce a completed translation result during the observation window. Device B failed immediately because `files/ct2/nllb600m/model.bin` was absent. No representative successful output was recorded.

| Source | Target | Execution | Output | Latency | Result |
|---|---|---|---|---|---|
| EN | HI | attempted on A; no completed result | — | NOT_MEASURED | NOT PASS |
| Representative ten-language routes | — | not completed | — | NOT_MEASURED | BLOCKED |

`OFFLINE_CT2_TRANSLATION=NOT PASS`.

## Single-Phone Full Pipeline

No complete speech → STT → CT2 → TTS → playback flow was completed. Existing debug transport harnesses are transport-focused and suppress receiver translation/TTS. `SINGLE_PHONE_PIPELINE=BLOCKED`.

## Two-Phone E2E

Wi-Fi Direct discovery was started on both devices. Logs showed discovery started, but no peer connection, PacketV2 message exchange, ACK, receiver routing, or reverse-direction flow completed.

| Direction | Transport | STT | TX | RX | ACK | TTS | Playback | Result |
|---|---|---|---|---|---|---|---|---|
| A → B | Wi-Fi Direct discovery only | not completed | not completed | not completed | not completed | not completed | not completed | BLOCKED |
| B → A | Wi-Fi Direct discovery only | not completed | not completed | not completed | not completed | not completed | not completed | BLOCKED |

`TWO_PHONE_E2E=BLOCKED`.

## PTT Validation

No controlled PTT ON/OFF communication flow was completed. `PTT_FLOW=BLOCKED`.

## 500-Sample Corpus

Corpus validation was executed without regeneration:

- 500 WAV files
- 50 per language across ten languages
- 16 kHz, mono, PCM16
- Validation result: PASS
- Duration: minimum 0.72 s, mean 2.77 s, maximum 9.53 s
- Classification: **SYNTHETIC CORPUS**

The STT runner was invoked, but no Android results CSV existed. It failed with `FileNotFoundError` for the required Android-results input. No transcripts, WER, or CER values were generated.

`CORPUS_VALIDATION=PARTIAL` and `ACCURACY_VALIDATION=NOT TESTED`.

## Accuracy Results

No valid real-speech accuracy evaluation was performed. Synthetic corpus outputs were not available and would not constitute real-world accuracy evidence. `ODIA_ACCURACY=NOT_VALIDATED`.

## Latency Results

Observed functional instrumentation:

- Tamil TTS on A: load 1,961 ms; first synthesis 2,810 ms; audio 1,843 ms; first RTF 1.525.
- Gujarati TTS on B: load 4,693 ms; first synthesis 4,568 ms; audio 1,296 ms; first RTF 3.525.
- Tamil STT on B: 11,504 ms inference for approximately 9,880 ms captured audio.

Coverage is incomplete; `LATENCY_VALIDATION=PARTIAL`. These are not cross-device benchmarks.

## Memory / CPU / Thermal Results

TTS debug logs exposed PSS, but no complete comparable resource suite was completed. Device A reported thermal status 1 and device B status 0 during this run. No thermal state was classified as a functional failure.

`RESOURCE_VALIDATION=PARTIAL`; performance conclusions are not established.

## Licensing / Provenance

All ten TTS artifacts remain `RESEARCH / UNAPPROVED`. Provenance and redistribution authorization are incomplete. No approval metadata was changed.

## Failures / Blockers

1. Existing exact-v2 APK debug harness does not provide all-ten TTS or nine-language STT execution surfaces.
2. CT2 was absent on B and did not complete on A.
3. No complete single-phone pipeline completed.
4. No two-phone connection/message/ACK flow completed.
5. Corpus STT requires an Android-results CSV that was not produced.
6. Odia STT remains runtime unavailable.
7. A native fatal signal was observed during one STT process; a later process returned a transcript.

## SIH Readiness Assessment

The artifact and corpus are present, but the runtime gate is incomplete. The project is not ready for accuracy or full SIH runtime claims. No application logic or model artifacts were modified.

## Final Status

```text
CANONICAL_APK_SHA=PASS
DEVICE_A_INSTALL=PASS
DEVICE_B_INSTALL=PASS

TTS_EN=BLOCKED
TTS_HI=BLOCKED
TTS_TA=PASS
TTS_GU=PASS
TTS_MR=BLOCKED
TTS_KN=BLOCKED
TTS_ML=BLOCKED
TTS_TE=BLOCKED
TTS_OR=BLOCKED
TTS_BN=BLOCKED

TEN_LANGUAGE_TTS_RUNTIME=PARTIAL

STT_EN=BLOCKED
STT_HI=BLOCKED
STT_TA=PARTIAL
STT_GU=BLOCKED
STT_MR=BLOCKED
STT_KN=BLOCKED
STT_ML=BLOCKED
STT_TE=BLOCKED
STT_OR=RUNTIME_UNAVAILABLE
STT_BN=BLOCKED

TEN_LANGUAGE_STT_RUNTIME=PARTIAL

OFFLINE_CT2_TRANSLATION=NOT PASS
SINGLE_PHONE_PIPELINE=BLOCKED
TWO_PHONE_E2E=BLOCKED
PTT_FLOW=BLOCKED
CORPUS_VALIDATION=PARTIAL
ACCURACY_VALIDATION=NOT TESTED
LATENCY_VALIDATION=PARTIAL
RESOURCE_VALIDATION=PARTIAL
ODIA_STT=RUNTIME_UNAVAILABLE
LICENSING_APPROVAL=RESEARCH / UNAPPROVED
OVERALL_SIH_RUNTIME_STATUS=PARTIAL
```
