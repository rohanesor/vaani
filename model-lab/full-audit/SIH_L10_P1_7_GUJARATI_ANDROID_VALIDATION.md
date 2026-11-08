# SIH-L10-P1.7 Gujarati Android TTS — Physical ARM64 Validation

Date: 2026-09-04
Branch: `sih/laptop1-c1-c2-integration`
Safety checkpoint: `sih/laptop1-before-l10-p1-7` at `fe45fa8`

## 1. Device

| Field | Value |
|---|---|
| Model | Realme RMX3782 |
| Android | 15 |
| API level | 35 |
| ABI | arm64-v8a |
| RAM | 5,749 MB total |
| sherpa-onnx AAR | 1.13.7 |

## 2. Gujarati Model Artifact (on-device)

| Artifact | SHA-256 | Bytes |
|---|---|---|
| `model.onnx` | `59f073b2e63771dd7d7972c17577e05a903ae6f4aa8c65a6dc1d9eb1a9812ed2` | 114,033,848 |
| `tokens.txt` | `2d855f2affb7586cc6be095a4382eb0bee2a22242deda32c86aab6b1a810d8c4` | 402 |

On-device path: `/data/user/0/com.vokie/files/models/tts/guj/`
Model integrity verified by smoke activity hash check: **PASS**

## 3. Cold Model Load

| Metric | Value |
|---|---|
| Cold load time | **1,921 ms** |
| PSS before load | 124,606 KB |
| PSS after load | 350,444 KB |
| Native heap before | 18,684 KB |
| Native heap after | 628,725 KB |
| PSS delta (model) | **~226 MB** |

## 4. First Synthesis

| Metric | Value |
|---|---|
| Text | `મને મદદની જરૂર છે` |
| Synthesis time | **1,262 ms** |
| Audio duration | 1,328 ms |
| RTF | **0.950** |
| Samples | 21,248 |
| Sample rate | 16,000 Hz |
| Peak | 0.198 |
| PSS after first synth | 354,774 KB |

## 5. Warm Synthesis (10 cycles)

| Metric | Value |
|---|---|
| Median | **1,250 ms** |
| Min | 1,144 ms |
| Max | 1,733 ms |
| P95 | 1,733 ms |
| Typical audio duration | ~1,280–1,472 ms |
| Typical RTF | ~0.90–0.95 |
| Peak amplitude range | 0.185–0.215 |
| All finite | ✓ |
| All in [-1, 1] | ✓ |
| All playback | **PASS** (audio heard from speaker) |

## 6. Memory (post-benchmark)

| Metric | Value |
|---|---|
| Total PSS | **362,087 KB (~354 MB)** |
| Native Heap allocated | 486,292 KB |
| Native Heap size | 561,152 KB |
| Dalvik Heap | 3,603 KB |
| GL mtrack | 5,236 KB |

## 7. Thermal

| Sensor | Temperature | Status |
|---|---|---|
| CPU | 61.3 °C | 0 (normal) |
| GPU | 61.3 °C | 0 |
| NPU | 61.0 °C | 0 |
| SKIN | 44.9 °C | 3 (severe) |
| shell_skin | 41.0 °C | 2 (critical) |
| BATTERY | 40.9 °C | 0 |

The device skin sensor reports status 3 (THERMAL_STATUS_SEVERE) after the
benchmark. This is a concern for sustained operation on this device. The
benchmark ran 10 warm cycles plus first synthesis plus 10 playbacks in under
3 minutes.

## 8. T0–T8 Timing

The smoke activity does not pass through the production `CommunicationViewModel`
→ `ContinuousTurnManager` → message → transport → receiver pipeline. Therefore
T0–T8 timing from `TurnTimingRecorder` is **not applicable** to this isolated
TTS benchmark.

What was measured is the TTS-only segment:

| Boundary | Equivalent | Value |
|---|---|---|
| T6 (TTS start) | `synthesize()` entry | captured by smoke log |
| T7 (audio ready) | `synthesize()` return | 1,262 ms first / 1,250 ms warm median |
| T8 (playback start) | `play()` entry | immediately after synth |

T0–T5 are sender/transport/translation boundaries and require a full two-phone
voice loop test.

## 9. Desktop vs Android Comparison

| Metric | P1.6 Desktop (sherpa 1.13.6) | P1.7 Android ARM64 (sherpa 1.13.7) |
|---|---|---|
| Model | `5042d4f1...` (P1.6 export) | `59f073b2...` (manifest artifact) |
| Sample rate | 16,000 Hz | 16,000 Hz |
| Warm synthesis | 430.1 ms median | 1,250 ms median |
| RTF | 0.364 | ~0.92 |
| Peak RSS/PSS | 560.0 MB | ~354 MB PSS |
| Audio duration | ~1.55 s | ~1.28–1.47 s |

Android ARM64 synthesis is ~2.9× slower than desktop x86-64 Python, which is
expected for CPU-only inference on a mobile SoC. RTF < 1.0 for most cycles
means synthesis completes faster than real-time playback, which is the
practical requirement.

## 10. Build Result

| Gate | Result |
|---|---|
| `testDebugUnitTest` | **323 tests, 0 failures** |
| `assembleDebug` | **PASS** |
| APK install | **PASS** |

## 11. EN/HI/TA Regression

No changes to EN/HI/TA TTS paths. Piper languages continue using espeak-ng
`dataDir`. Tamil continues using `dataDir=""`. All 323 unit tests pass.

## 12. Source Changes

| File | Change |
|---|---|
| `TtsModels.kt` | Added `GUJARATI("guj", VokieLanguage.GU, "ગુજરાતી")` |
| `SherpaOnnxTtsEngine.kt` | Added `GUJARATI` to `MMS_CHARACTER_LANGUAGES`; `dataDir=""` |
| `BundledModelStore.kt` | Added `"tts/guj/model.onnx"`, `"tts/guj/tokens.txt"` to `BUNDLED_FILES` |
| `TtsCoreTest.kt` | Updated language count 3→4; added GU→guj assertion |
| `GujaratiTtsSmokeActivity.kt` | New debug-only physical validation activity |
| `debug/AndroidManifest.xml` | Registered smoke activity |

## 13. Known Limitations

- The staged model (`59f073b2`) is not the P1.6 re-exported model (`5042d4f1`).
- RTF occasionally exceeds 1.0 (max 1.22) under thermal pressure.
- Skin thermal status reached SEVERE during sustained benchmark.
- CC-BY-NC-4.0 licensing; non-commercial prototype only.
- Punctuation handling by MMS character frontend unverified for correctness.
- No two-phone voice loop test; T0–T5 timing unavailable.
- VITS model has stochastic inference; audio varies across runs.

## 14. Verdict

**PHYSICAL VALIDATION: PASS**

Gujarati MMS-TTS loads, synthesizes, and plays finite 16 kHz audio on a
physical ARM64 Android device through the existing sherpa-onnx runtime. Warm
median RTF is ~0.92, which is real-time viable for single-utterance rescue
communication. Thermal behavior requires monitoring for sustained use.

## 15. Next Steps

1. Telugu (`tel`) or Kannada (`kan`) — same MMS character frontend pattern.
2. Full two-phone voice loop for T0–T8 end-to-end timing.
3. Monitor thermal throttling impact on sustained multi-language use.
