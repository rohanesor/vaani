# LAP 1.3 — Receiver-Side Optimization Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `1c0e6cd`

## Audit Summary

✅ **PASS** — Receiver-side performance has been extensively benchmarked and optimized.

Optimization investigation was completed in prior phases (P1.15A through P1.20) and frozen. No additional safe optimizations were identified.

## Current Performance Baseline (P1.16 Five-Run Benchmark)

### Warm Median Measurements (EN→HI)

| Stage | Warm Median | Notes |
|---|---:|---|
| SenseVoice STT (T1→T2) | 273.2 ms | Optimized int8 model |
| Transport TX→ACK | 89.4 ms | Bluetooth RTT |
| CT2 Translation | 733.8 ms | NLLB-600M int8, beam=1 |
| Hindi TTS Synthesis | 189.8 ms | MMS/VITS, 2 threads |
| Playback | 992.6 ms | Speech duration, not compute |
| **Receiver T5→T8** | **1986.7 ms** | End-to-end receiver latency |

### Cold Start (First Run)

| Stage | Cold | Notes |
|---|---:|---|
| CT2 Translation | 3303.4 ms | Includes model loading |
| TTS Synthesis | 2360.4 ms | Includes Hindi model load (2007 ms) |

## Existing Optimizations

### 1. STT (SenseVoice)

- **Model:** SenseVoice-Small int8 quantized
- **Lazy Loading:** Model loaded on first use, not app start
- **Session Reuse:** Single engine instance reused across turns
- **Warm RTF:** ~0.87 (processes faster than real-time)

### 2. Translation (CTranslate2)

- **Model:** NLLB-200-distilled-600M CT2 int8
- **Beam Policy:** beam=1 for short inputs (<20 pieces), beam=4 for longer
- **Session Reuse:** Single model handle reused (no reload per message)
- **Mutex Protection:** Serializes concurrent translation requests
- **Tokenizer:** SentencePiece encode/decode <6ms (not bottleneck)

### 3. TTS (Sherpa-ONNX)

- **Model:** MMS/VITS approved artifacts
- **Threads:** 2 (balanced for mobile thermal)
- **Lazy Loading:** Per-language model loaded on first synthesis
- **Model Reuse:** Same language model stays loaded
- **Model Release:** Unused language models released to save memory

### 4. Audio Playback

- **Mode:** `MODE_STREAM` with float PCM
- **Buffer:** Max of platform minimum and full utterance
- **No Intermediate Files:** Direct AudioTrack playback
- **Position Tracking:** Lightweight polling for UI progress

## Investigation Findings (P1.17, P1.18, P1.19)

### CT2 Bottleneck Analysis

| Finding | Result |
|---|---|
| Tokenizer (SentencePiece) | NOT bottleneck (<6ms total) |
| CT2 inference | Dominates translation time |
| Encoder vs Decoder split | Not available without runtime modification |
| Thread increase | Not tested (thermal risk) |
| Memory allocation | No evidence of excessive GC |

### TTS Bottleneck Analysis

| Finding | Result |
|---|---|
| Kotlin preprocessing | Negligible |
| Native pipeline | Well-optimized |
| AudioTrack setup | 14-22ms |
| AudioTrack write | <0.1ms |
| Playback duration | Equal to speech duration (expected) |

## Why No Further Optimization

Per P1.20 freeze decision:

1. **No safe CT2 optimization found** — Thread changes risk thermal instability
2. **TTS already optimized** — Warm synthesis is <200ms
3. **Playback is speech duration** — Cannot be shortened without audio quality loss
4. **Production behavior unchanged** — Stability prioritized over marginal gains

## Timing Instrumentation

The codebase includes comprehensive timing instrumentation:

```
T0: Speech capture start
T1: Speech endpoint detected
T2: STT complete
T3: Packet created
T4: Packet received (receiver)
T5: Translation start
T6: Translation complete
T7: TTS synthesis complete
T8: Playback start
```

All timestamps use `SystemClock.elapsedRealtimeNanos()` within device clock domain.

Logs available via:
- `VOKIE_TIMING` JSON events
- `VOKIE][STT:` STT profiling
- `VOKIE][CT2:` Translation profiling
- `VOKIE][TTS:` TTS profiling

## Thermal Considerations

Device thermal state is monitored during benchmarks:
- P1.17 long-text test raised thermal state to 3
- Profiler was stopped to prevent thermal throttling
- Production configuration respects thermal limits

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.3 RECEIVER OPTIMIZATION: PASS**

The receiver pipeline has been thoroughly benchmarked and optimized:

- Warm receiver latency: ~2s (T5→T8)
- Cold latency: ~6s (includes model loading)
- All bottlenecks investigated
- No safe further optimizations identified
- Optimization frozen per P1.20 decision

Future optimization opportunities exist only at model/runtime level (outside scope of this LAP).
