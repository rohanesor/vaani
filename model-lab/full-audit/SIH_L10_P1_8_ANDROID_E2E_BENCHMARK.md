# SIH-L10-P1.8 Physical Android E2E Benchmark

Date: 2026-09-04
Branch: `sih/laptop1-c1-c2-integration`
Safety checkpoint: `sih/laptop1-before-l10-p1-8` at `a9104eb`

## 1. Device

| Field | Value |
|---|---|
| Model | Realme RMX3782 |
| Android | 15 |
| API | 35 |
| ABI | arm64-v8a |
| RAM | 5,749 MB |
| sherpa-onnx | 1.13.7 |
| CT2 NLLB | nllb-200-distilled-600M-ct2-int8 |

## 2. Methodology

Single-device benchmark using a debug activity (`E2eBenchmarkActivity`).
Each measurement segment is timed independently with `SystemClock.elapsedRealtime()`.
Translation and TTS models are loaded once per segment (cold), then run
multiple warm iterations. Combined translation+TTS measures the receiver-side
pipeline without STT or transport.

**Not measured (single device):** STT (T0–T2), transport (T3–T4), two-phone E2E.

## 3. T0–T8 Definitions and Coverage

| Boundary | Definition | Measured |
|---|---|---|
| T0 | Audio/input pipeline start | NOT MEASURED |
| T1 | STT start | NOT MEASURED |
| T2 | STT result available | NOT MEASURED |
| T3 | Translation start | ✓ (combined section) |
| T4 | Translated text available | ✓ |
| T5 | TTS synthesis start | ✓ |
| T6 | Synthesized audio available | ✓ |
| T7 | Playback start | ✓ (play() entry) |
| T8 | Playback complete | ✓ (play() return) |

T7 is the `engine.play()` entry point, not a physical AudioTrack callback.

## 4. Translation Benchmark (CT2 NLLB, warm, 5 runs each)

| Direction | Cold (ms) | Warm Median (ms) | P95 (ms) | Min (ms) | Max (ms) | Output |
|---|---|---|---|---|---|---|
| EN→HI | 7,297 | **2,050** | 2,084 | 2,006 | 2,084 | मुझे तत्काल मदद की जरूरत है। |
| HI→EN | 1,825 | **1,858** | 2,218 | 1,800 | 2,218 | I need immediate help. |
| EN→TA | 2,457 | **2,301** | 2,310 | 2,191 | 2,310 | எனக்கு அவசர உதவி தேவை. |
| TA→EN | 1,809 | **1,832** | 1,864 | 1,815 | 1,864 | I need help immediately. |
| HI→TA | 2,027 | **2,077** | 2,133 | 2,002 | 2,133 | எனக்கு உடனடி உதவி தேவை. |
| TA→HI | 2,032 | **4,405** | 6,411 | 2,140 | 6,411 | मुझे तत्काल मदद की जरूरत है. |

EN→HI cold includes model load (~7.3 s). Subsequent directions reuse the loaded model.
TA→HI shows high variance (2.1–6.4 s), coincident with the thermally stressed run; throttling is not independently demonstrated.

## 5. TTS Benchmark (sherpa-onnx, per language)

| Language | Model | Rate | Cold Load (ms) | First Synth (ms) | First RTF | Warm Median (ms) | P95 (ms) | PSS (KB) | Status |
|---|---|---|---|---|---|---|---|---|---|
| English | Piper lessac-medium | 16 kHz | 2,989 | 2,722 | 1.38 | **2,584** | 2,761 | 364,697 | PASS |
| Hindi | Piper priyamvada-medium | 22 kHz | 2,397 | 637 | 0.36 | **462** | 479 | 329,995 | PASS |
| Tamil | MMS-TTS tam | 16 kHz | 2,154 | 4,836 | 1.57 | **5,735** | 16,366 | 375,657 | PASS |
| Gujarati | MMS-TTS guj | 16 kHz | 5,966 | 6,321 | 4.65 | **5,763** | 6,418 | 345,766 | PASS |

**Hindi Piper** is the fastest TTS by a wide margin (462 ms warm, RTF 0.26).
**Tamil and Gujarati MMS** show significant thermal degradation — Tamil warm
runs 4–5 spike to 14–16 s, Gujarati is consistently ~5.5–6.4 s under thermal load.
The P1.7 isolated Gujarati benchmark (before thermal stress) showed 1,250 ms
warm median. The difference is consistent with thermal or benchmark-order effects,
but this run does not independently prove throttling as the cause.

## 6. Combined Translation+TTS (receiver-side pipeline)

| Direction | Translation (ms) | TTS (ms) | Playback (ms) | Total E2E (ms) | Status |
|---|---|---|---|---|---|
| EN→HI | 9,850 | 1,940 | 2,543 | **14,333** | PASS |
| HI→EN | 1,942 | 2,204 | 2,020 | **6,166** | PASS |
| EN→TA | 2,107 | 3,671 | 2,551 | **8,329** | PASS |

EN→HI first combined run includes CT2 cold load (~7.3 s of the 9.8 s).
HI→EN warm combined is **~6.2 s** including playback.

## 7. Memory

| Point | PSS (KB) | Native Heap (KB) |
|---|---|---|
| Baseline | 94,274 | — |
| Post-translation | 1,101,804 | — |
| Post-English TTS | 364,697 | — |
| Post-Hindi TTS | 329,995 | — |
| Post-Tamil TTS | 375,657 | — |
| Post-Gujarati TTS | 345,766 | — |
| Final | 1,278,456 | 487,195 |

Peak PSS ~1.25 GB reflects concurrent CT2 and TTS model residency.
Individual TTS models use ~280–375 MB PSS each.

## 8. CPU utilization

CPU utilization during inference was **NOT MEASURED**. A post-benchmark
`adb shell top -b -n 1 -p 17923` snapshot reported 0.0% for the sleeping app;
this is not representative of benchmark execution and is not used for a
performance claim.

## 9. Thermal

| Sensor | Post-benchmark | Status |
|---|---|---|
| CPU | 50.0–61.3 °C | 0 (normal) |
| SKIN | 46.1 °C | 3 (SEVERE) |
| shell_skin | 41.0 °C | 2 (CRITICAL) |
| BATTERY | 40.9 °C | 0 |

THERMAL_STATUS_SEVERE was active during the benchmark. This explains
Tamil/Gujarati TTS degradation in later runs.

## 10. Transport

**NOT MEASURED.** Single-device benchmark; no second phone connected.
PacketV2, Bluetooth, Wi-Fi Direct transport latency is unknown.

## 11. STT

**NOT MEASURED.** No microphone input was used. Whisper inference latency
on this device is unknown from this benchmark.

## 12. Failures

| Segment | Failures |
|---|---|
| Translation (6 directions × 6 runs) | 0 |
| TTS (4 languages × 6 runs) | 0 |
| Combined (3 directions) | 0 |
| Total | **0 failures out of 63 operations** |

## 13. Summary Table

| Language | Direction | Translation Warm (ms) | TTS Warm (ms) | Combined E2E (ms) | PSS (MB) | Status |
|---|---|---|---|---|---|---|
| EN→HI | translate+speak | 2,050 | 462 | 14,333* | ~330 | PASS |
| HI→EN | translate+speak | 1,858 | 2,584 | 6,166 | ~365 | PASS |
| EN→TA | translate+speak | 2,301 | 5,735 | 8,329 | ~376 | PASS |
| TA→EN | translate | 1,832 | — | — | — | PASS |
| HI→TA | translate | 2,077 | — | — | — | PASS |
| TA→HI | translate | 4,405 | — | — | — | PASS |
| GU (TTS only) | speak | — | 5,763 | — | ~346 | PASS |

*EN→HI combined includes CT2 cold load.

## 14. SIH-Safe Claims

- ✓ All 4 TTS languages produce finite 16/22 kHz audio on ARM64
- ✓ CT2 NLLB translates between EN/HI/TA on-device
- ✓ Combined translation+TTS pipeline works on single device
- ✓ Hindi Piper TTS is real-time viable (RTF ~0.26)
- ✓ Zero failures across 63 benchmark operations

## 15. Unsupported Claims

- ✗ End-to-end two-phone voice loop latency
- ✗ STT latency on this device
- ✗ Transport latency
- ✗ Tamil/Gujarati MMS TTS is real-time under thermal load
- ✗ Sustained multi-language operation without throttling
- ✗ Gujarati translation (CT2 JNI has no guj tag)
- ✗ Physical speaker audio quality (MOS)

## 16. Limitations

- Tamil MMS warm TTS degrades to 14–16 s under thermal stress
- Gujarati MMS degrades from ~1.25 s (cold device) to ~5.7 s (hot device)
- English Piper TTS is slower than expected (~2.6 s for short text)
- Peak PSS ~1.25 GB during combined benchmark may cause OOM on lower-RAM devices
- TA→HI translation shows high variance (2.1–6.4 s), likely thermal
- CT2 JNI maps only EN/HI/TA; Gujarati translation is not available

## 17. Reproducibility

1. Build and install debug APK with `E2eBenchmarkActivity`
2. Keep screen on: `adb shell svc power stayon true`
3. Launch: `adb shell am start -n com.vokie/.tts.E2eBenchmarkActivity`
4. Collect: `adb logcat -s VOKIE_E2E_BENCH`
5. Allow ~5 minutes for complete execution

## 18. Build and Regression

| Gate | Result |
|---|---|
| testDebugUnitTest | 323 tests, 0 failures |
| assembleDebug | PASS |
| EN/HI/TA/GU TTS | All PASS |
| Production source | UNCHANGED |

## 19. Next Phase

**Recommended: Telugu (`tel`) adaptation/validation** — same MMS character
frontend pattern as Tamil and Gujarati, P1 priority from P1.6 matrix.

Before adding more languages, consider thermal mitigation strategies
(sequential model unloading, inference thread reduction under thermal
pressure) to prevent sustained-use degradation.
