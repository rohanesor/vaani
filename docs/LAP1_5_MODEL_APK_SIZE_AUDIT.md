# LAP 1.5 — Model / APK Size Optimization Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `1c0e6cd`

## Audit Summary

✅ **PASS** — Current APK size is acceptable for the 10-language SIH target.

## Current APK Size

| Metric | Value |
|---|---:|
| Debug APK | 480 MB |
| Bundled languages | 4 (EN, HI, TA, GU) |
| Downloadable languages | 6 (TE, KN, ML, MR, OR, BN) |

## Model Inventory

### Bundled in APK (BUNDLED_FILES)

| Model | Size (MB) | Format | Precision |
|---|---:|---|---|
| stt/ggml-tiny-q5_1.bin | 30.7 | GGML | Q5_1 quantized |
| tts/eng/model.onnx | 108.7 | ONNX | FP32 |
| tts/hin/model.onnx | 60.2 | ONNX | FP32 |
| tts/tam/model.onnx | 108.7 | ONNX | FP32 |
| tts/guj/model.onnx | 108.8 | ONNX | FP32 |
| tts/espeak-ng-data | ~7 MB | Data | N/A |
| **Bundled Total** | **~425 MB** | | |

### Downloadable (User-initiated)

| Model | Size (MB) | Format |
|---|---:|---|
| tts/tel/model.onnx | 108.8 | ONNX |
| tts/kan/model.onnx | 108.8 | ONNX |
| tts/mal/model.onnx | 108.8 | ONNX |
| tts/mar/model.onnx | 108.8 | ONNX |
| tts/ory/model.onnx | 108.8 | ONNX |
| tts/ben/model.onnx | 108.8 | ONNX |

### Native Libraries (arm64-v8a)

| Library | Size (MB) | Purpose |
|---|---:|---|
| libonnxruntime.so | 20.7 | ONNX Runtime |
| libsherpa-onnx-jni.so | 4.5 | Sherpa-ONNX JNI |
| libsherpa-onnx-c-api.so | 4.3 | Sherpa-ONNX C API |
| libvokie_ct2.so | 3.7 | CTranslate2 JNI |
| libvokie_whisper.so | 1.3 | Whisper JNI |
| libc++_shared.so | 1.2 | C++ stdlib |

### Translation Model (Staged, not in APK)

| Model | Size (MB) | Format | Precision |
|---|---:|---|---|
| ct2/nllb600m/model.bin | 591 | CT2 | INT8 quantized |
| ct2/nllb600m/sentencepiece.bpe.model | ~2 | SPM | N/A |

## Current Optimizations Applied

### 1. STT Model

- **Model:** Whisper Tiny Q5_1 (quantized)
- **Original size:** ~75 MB (FP16)
- **Current size:** 30.7 MB (Q5_1)
- **Reduction:** ~59%
- **Quality:** Acceptable for emergency communication

### 2. Translation Model

- **Model:** NLLB-200-distilled-600M CT2 INT8
- **Original size:** ~1.2 GB (FP32)
- **Current size:** 619 MB (INT8)
- **Reduction:** ~48%
- **Quality:** Validated in P1.16 benchmark

### 3. Selective Bundling

Only 4 TTS languages bundled in APK:
- English (required baseline)
- Hindi (most common Indian language)
- Tamil (South India coverage)
- Gujarati (West India coverage)

Other languages downloaded on-demand.

### 4. ABI Filtering

```gradle
ndk {
    abiFilters 'arm64-v8a'
}
```

Only arm64 libraries included (modern Android devices).

## Potential Optimization Opportunities

### 1. espeak-ng-data Pruning

**Current:** ~7 MB (all languages)
**Opportunity:** Keep only SIH target languages
**Estimated savings:** ~4-5 MB
**Risk:** May break phonemizer for edge cases
**Recommendation:** Low priority; risk outweighs benefit

### 2. TTS Model Quantization

**Current:** MMS/VITS FP32 (~108 MB each)
**Opportunity:** INT8 quantization
**Estimated savings:** ~50% per model
**Risk:** Audio quality degradation
**Recommendation:** Requires quality validation before implementation

### 3. Further STT Quantization

**Current:** Whisper Tiny Q5_1 (30.7 MB)
**Opportunity:** Q4 or lower
**Risk:** WER increase in noisy conditions
**Recommendation:** Already optimized; further reduction not safe

### 4. R8 / ProGuard

**Status:** Not evaluated for release builds
**Recommendation:** Enable for release APK

## Why Not Further Optimized

1. **TTS Quality Gate:** MMS/VITS FP32 provides intelligible emergency speech. Quantization requires human evaluation.

2. **STT Quality Gate:** Q5_1 already balances size vs accuracy. Further quantization risks degraded transcription in challenging conditions.

3. **Translation Already INT8:** NLLB is already quantized; further reduction would require distillation.

4. **10-Language Target:** All SIH languages require model support. Cannot remove languages.

## Model Precision Summary

| Component | Format | Precision | Notes |
|---|---|---|---|
| STT (SenseVoice) | Bundled | INT8 | Primary engine |
| STT (Whisper) | GGML | Q5_1 | Fallback |
| Translation (CT2) | Staged | INT8 | NLLB-600M |
| TTS (Sherpa) | ONNX | FP32 | MMS/VITS |

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.5 MODEL/APK OPTIMIZATION: PASS**

Current APK size (480 MB debug) is acceptable for:
- 10-language SIH target
- Offline-first operation
- Emergency communication quality

Models are already quantized where safe:
- STT: Q5_1 quantized
- Translation: INT8 quantized
- TTS: FP32 (quality-critical)

Further optimization opportunities exist but require:
- Human quality evaluation (TTS quantization)
- Trade-off analysis (espeak pruning)
- Release build validation (R8)

These are deferred to release engineering phase.
