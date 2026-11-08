# SIH-L10-P1.12 Fast STT Model Investigation

Date: 2026-09-05
Branch: `sih/laptop1-c1-c2-integration`
Checkpoint: `sih/laptop1-before-l10-p1-12` at `26eeb0a`

## 1. Current Baseline

| Metric | Value |
|---|---|
| Model | whisper-tiny-multilingual-q5_1 |
| File | ggml-tiny-q5_1.bin |
| Size | 31 MB |
| Format | GGML Q5_1 |
| RAM | ~273 MB |
| Encoder latency (ARM64) | ~3,200 ms |
| Total STT latency | ~3,321 ms |
| Accuracy | "Help me." ✅ correct |
| Languages | EN, HI, TA (multilingual) |
| Audio context | 1500 frames (30 s) |
| Threads | 4 (optimal on big.LITTLE) |

## 2. Whisper.cpp Model Family

whisper.cpp supports these GGML model variants:

| Model | Parameters | FP16 Size | Q5_1 Size | Multilingual |
|---|---:|---:|---:|---|
| tiny | 39M | 75 MB | 31 MB | ✅ |
| tiny.en | 39M | 75 MB | 31 MB | ❌ English only |
| base | 74M | 142 MB | 57 MB | ✅ |
| base.en | 74M | 142 MB | 57 MB | ❌ English only |
| small | 244M | 466 MB | 181 MB | ✅ |
| small.en | 244M | 466 MB | 181 MB | ❌ English only |

The `.en` variants cannot support Hindi/Tamil — eliminated.

### Quantization options for multilingual models

| Variant | Format | Approx Size | Expected Speed vs tiny-Q5_1 |
|---|---|---:|---|
| tiny-q8_0 | Q8_0 | 42 MB | Similar (higher precision, same arch) |
| tiny-q4_0 | Q4_0 | 22 MB | Slightly faster (less memory bandwidth) |
| tiny-q4_1 | Q4_1 | 24 MB | Slightly faster |
| base-q5_1 | Q5_1 | 57 MB | SLOWER (2× parameters) |
| base-q4_0 | Q4_0 | 40 MB | May be similar to tiny-FP16 |
| small-q5_1 | Q5_1 | 181 MB | Much SLOWER (6× parameters) |
| small-q4_0 | Q4_0 | 126 MB | SLOWER |

## 3. Analysis

### Why tiny is already the fastest viable option

The Whisper architecture processes audio through a fixed encoder that
scales with model parameters, not audio length. The encoder dominates
at 97.6% of inference time.

- **tiny** (39M params): 3,200 ms encoder
- **base** (74M params): expected ~6,000-7,000 ms encoder
- **small** (244M params): expected ~20,000+ ms encoder

Going LARGER is counterproductive. Going to a SMALLER quantization
(Q4_0) of tiny might save 5-15% memory bandwidth but the encoder
compute is dominated by matrix multiplications that don't scale
significantly with quantization level.

### Distilled Whisper (distil-whisper)

| Model | Parameters | Size | Multilingual | whisper.cpp GGML |
|---|---:|---:|---|---|
| distil-whisper-large-v3 | 756M | ~1.5 GB | ✅ | ✅ (GGML available) |
| distil-whisper-medium.en | 394M | ~750 MB | ❌ | ✅ |
| distil-whisper-small.en | 166M | ~330 MB | ❌ | ✅ |

The distilled variants are designed for FASTER inference at the SAME
quality as their parent model, but they are LARGER than tiny. On ARM64
they would be SLOWER, not faster.

distil-whisper-large-v3 at 1.5 GB is impractical for a rescue device.

### Alternative offline STT engines

| Engine | Format | Size | ARM64 | Multilingual | License |
|---|---|---:|---|---|---|
| Vosk (Kaldi-based) | model pack | 50-200 MB | ✅ | Per-language | Apache 2.0 |
| sherpa-onnx STT | ONNX | varies | ✅ | Per-language | Apache 2.0 |
| Silero VAD + STT | PyTorch/ONNX | ~100 MB | ✅ | Limited | MIT |

**Vosk** provides per-language small models (~50 MB each) that run
significantly faster than Whisper because they use a simpler CTC/TDNN
architecture. However:
- Separate model per language required
- Lower accuracy than Whisper on non-English
- Different integration (not whisper.cpp compatible)
- Requires new JNI wrapper

**sherpa-onnx STT** (same runtime as existing TTS) supports:
- Zipformer-based models
- CTC/transducer models
- Models as small as 6-30 MB per language
- Already has Android ARM64 runtime in the project (1.13.7)
- Could share the sherpa-onnx AAR already bundled for TTS

This is the most promising path because the runtime is already present.

## 4. Candidate Decision Matrix

| Candidate | Size | Expected Warm | Android ARM64 | Multilingual | License | Decision |
|---|---:|---:|---|---|---|---|
| whisper-tiny-q4_0 | 22 MB | ~2,800 ms* | ✅ whisper.cpp | ✅ | MIT | RESEARCH |
| whisper-base-q5_1 | 57 MB | ~6,500 ms* | ✅ whisper.cpp | ✅ | MIT | REJECT (slower) |
| distil-whisper-large-v3 | 1.5 GB | ~15,000 ms* | ✅ but too large | ✅ | MIT | REJECT (too large) |
| Vosk EN/HI/TA | ~150 MB | ~200-500 ms* | ✅ | Per-language | Apache 2.0 | RESEARCH |
| sherpa-onnx Zipformer | 30-80 MB | ~300-800 ms* | ✅ (runtime exists) | Per-language | Apache 2.0 | **PROMOTE** |

*Estimated, not measured on target device.

## 5. Recommended Candidate: sherpa-onnx Zipformer STT

### Rationale

1. **Runtime already exists**: sherpa-onnx 1.13.7 AAR is bundled for TTS
2. **Fast inference**: Zipformer/CTC models are 5-10× faster than Whisper
3. **Small models**: 6-80 MB per language
4. **Android ARM64**: proven on the existing devices
5. **Apache 2.0**: compatible license
6. **Offline**: fully offline operation
7. **Per-language**: EN, HI, TA models available separately

### Limitations

1. Per-language models (not one multilingual model)
2. Different integration than whisper.cpp
3. Quality may differ from Whisper for some utterances
4. Requires new STT JNI/wrapper code
5. Not yet benchmarked on the target devices

### Available sherpa-onnx STT models (from k2-fsa/sherpa-onnx)

| Language | Model | Type | Size |
|---|---|---|---:|
| English | sherpa-onnx-streaming-zipformer-en | Transducer | ~80 MB |
| English | sherpa-onnx-zipformer-ctc-en | CTC | ~30 MB |
| Hindi | sherpa-onnx-whisper-tiny (fallback) | Whisper | ~31 MB |
| Tamil | sherpa-onnx-whisper-tiny (fallback) | Whisper | ~31 MB |

Note: dedicated Hindi/Tamil sherpa-onnx CTC models may not exist.
The investigation must verify availability before promoting.

## 6. Desktop Benchmark Status

**NOT PERFORMED** — no candidate models are locally available for
desktop benchmarking. The investigation is architecture/feasibility
research only.

## 7. Conclusion

**BLOCKED — ANDROID RUNTIME REQUIRED**

A promising candidate (sherpa-onnx Zipformer STT) has been identified.
The runtime (sherpa-onnx 1.13.7) is already present in the project.
However:

1. No model artifacts are locally available for benchmarking
2. Per-language model availability for HI/TA needs verification
3. Quality comparison against Whisper needs measurement
4. A new STT wrapper would be required (different API than whisper.cpp)
5. Desktop benchmarking cannot proceed without downloading model artifacts

The existing Whisper-tiny at ~3.2 s remains the production baseline.
The ~32% translation improvement from beam=1 (P1.10) is the only
validated latency improvement.

## 8. Next Steps

1. Download sherpa-onnx Zipformer English CTC model for desktop benchmark
2. Verify HI/TA model availability in the sherpa-onnx ecosystem
3. If quality passes desktop gate, prototype Android integration
4. The existing Whisper path must remain as fallback

## 9. Build & Regression

No production code was modified in P1.12.
- Tests: 329 / 0 failures (from P1.11)
- assembleDebug: PASS (from P1.11)
- No new APK required
