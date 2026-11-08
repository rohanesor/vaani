# iTantra SIH Final Validation Report

**Scope:** LAP1 only. No LAP2 code was used or merged.

## 1. Build Identity

- Source commit: `49dfe705f222430e36e59160d130b3043e00fa66`
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Size: 502,851,112 bytes
- SHA-256: `2521042d37a2662b4b4e05b7cb860e03c6c69c557ad17849a32d6cf4b9647bf4`
- Package: `com.vokie`
- Version: `1.0.0`, versionCode `1`
- Variant: debug
- ABI: `arm64-v8a`
- Build: **PASS**

Native libraries and bundled STT/TTS assets were verified directly in the APK. The CT2 translation model is runtime-staged and was not claimed as bundled.

## 2. Device Matrix

| Device | Android | ABI | Installation | Launch |
|---|---|---|---|---|
| RMX3782 (`MJPVXCSG9HYL65YL`) | 15 | arm64-v8a | PASS | PASS via monkey |
| SM-M356B (`RZCX41PAVLB`) | 16 | arm64-v8a | PASS | PASS via monkey |

Both devices have the same package/version identity. No full runtime pipeline test was performed.

## 3. Corpus

- 500 WAV files, 50 per language
- 10 languages represented
- 16 kHz, mono, PCM S16LE
- Corpus validator: **PASS**
- Audio: **SYNTHETIC TEST INPUT**, not human speech
- Odia audio: Hindi TTS fallback; genuine Odia STT accuracy is **NOT VALIDATED**
- Corpus was not copied into APK assets

## 4. Ten-Language STT Matrix

| Language | Samples | Runtime | Success | WER | CER | Latency | Status |
|---|---:|---|---|---|---|---|---|
| English | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Hindi | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Tamil | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Gujarati | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Marathi | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Kannada | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Malayalam | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Telugu | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| Odia | 50 | RUNTIME_UNAVAILABLE/NOT VERIFIED | NOT VALIDATED | NOT VALIDATED | NOT VALIDATED | NOT TESTED | NOT PASS |
| Bengali | 50 | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |

No Android transcript export exists, so no WER/CER or latency values are reported.

## 5. Translation Results

| Scope | Result |
|---|---|
| CT2 library packaged | VERIFIED in APK |
| CT2 model staged on devices | NOT TESTED |
| Direct ten-language routing | NOT TESTED |
| Translation quality | NOT TESTED |

## 6. TTS Results

| Scope | Result |
|---|---|
| Native Sherpa/ONNX libraries packaged | VERIFIED in APK |
| All ten language synthesis | NOT TESTED |
| Model load/synthesis latency | NOT TESTED |
| Playback | NOT TESTED |
| Approval/licensing status | Unchanged; no approval claimed |

## 7. Single-Phone E2E

**NOT TESTED.** No corpus sample was run through Android STT → CT2 → TTS.

## 8. Two-Phone E2E / Communication

**NOT TESTED.** No PTT, PacketV2, Bluetooth/Wi-Fi Direct, ACK, ordering, persistence, or receiver playback validation was performed in this session.

## 9. Offline Verification

**NOT TESTED.** Devices were not placed into an offline functional test procedure.

## 10. Emergency Audio / N5

Emergency audio priority and N5 semantic metadata were **NOT TESTED** in this session.

## 11. Performance / Thermal / Resources

**NOT TESTED.** No latency, WER/CER, PSS, CPU, thermal, or E2E measurements were collected.

## 12. Failures and Blockers

1. Full Android corpus automation is not present in the repository; `corpus/run_stt_corpus.py` consumes an Android-result CSV but does not drive the app.
2. CT2 model staging on the installed devices was not performed.
3. Odia corpus audio is Hindi fallback audio and cannot support genuine Odia accuracy claims.
4. The APK is debug-signed; this is unsuitable as a production release identity.

## 13. Readiness Assessment

| Dimension | Status |
|---|---|
| Complete APK build | PASS |
| APK content audit | PASS |
| Corpus validation | PASS |
| Ten-language STT | NOT TESTED |
| Translation | NOT TESTED |
| TTS | NOT TESTED |
| Two-phone communication | NOT TESTED |
| Full E2E | NOT TESTED |
| Offline operation | NOT TESTED |
| SIH readiness | PARTIAL |

## Final Verdict

```text
OVERALL_SIH_VALIDATION = PARTIAL
LANGUAGE_COVERAGE = PARTIAL
```

The complete LAP1 APK was built and installed on both phones, but no fabricated pipeline results are reported. The actual 500-sample Android STT and multilingual E2E validation remain to be executed with a device-side harness and CT2 model staging.
