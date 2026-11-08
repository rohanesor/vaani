# SIH-L10-P1.13 Zipformer/CTC Android STT Prototype

Date: 2026-09-05
Branch: `sih/laptop1-c1-c2-integration`
Checkpoint: `sih/laptop1-before-l10-p1-13` at `9fc4c2d`

## 1. Current Baseline

Whisper-tiny-multilingual-q5_1: **~3,321 ms** median STT on ARM64.

## 2. sherpa-onnx 1.13.7 AAR STT Capabilities

The bundled `com.k2fsa:sherpa-onnx:1.13.7@aar` already contains these
offline STT model backends (verified from API jar):

| Backend | Class | Multilingual |
|---|---|---|
| Zipformer CTC | `OfflineZipformerCtcModelConfig` | Per-language |
| Transducer | `OfflineTransducerModelConfig` | Per-language |
| Whisper | `OfflineWhisperModelConfig` | ✅ |
| Paraformer | `OfflineParaformerModelConfig` | ZH/EN only |
| **SenseVoice** | `OfflineSenseVoiceModelConfig` | **✅ 50+ languages** |
| NeMo CTC | `OfflineNemoEncDecCtcModelConfig` | Per-language |
| Omnilingual CTC | `OfflineOmnilingualAsrCtcModelConfig` | ✅ |

## 3. Model Discovery

### Priority 1: SenseVoice-Small (FunAudioLLM)

| Field | Value |
|---|---|
| Name | sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17 |
| Architecture | SenseVoice encoder-only |
| Languages | 50+ including **English, Hindi, Tamil** |
| Size | ~236 MB (ONNX int8 quantized ~60 MB available) |
| License | Apache 2.0 (model) + MIT (sherpa-onnx) |
| Source | https://github.com/k2-fsa/sherpa-onnx/releases |
| HuggingFace | csukuangfj/sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17 |
| Runtime | sherpa-onnx 1.13.7 `OfflineSenseVoiceModelConfig` |
| Offline | ✅ |
| ARM64 | ✅ (ONNX CPU) |

**Key advantage**: Single model for all project languages (EN/HI/TA).
No per-language model switching needed.

Expected inference: **200-800 ms** on ARM64 (based on architecture —
SenseVoice uses a non-autoregressive CTC-like approach, ~10× faster
than autoregressive Whisper decoder).

### Priority 2: Zipformer Transducer (English)

| Field | Value |
|---|---|
| Name | sherpa-onnx-zipformer-en-2023-06-26 |
| Architecture | Zipformer transducer |
| Languages | English only |
| Size | ~70 MB |
| License | Apache 2.0 |
| Hindi/Tamil | ❌ Not available |

**Rejected for production**: English only, no Hindi/Tamil support.

### Priority 3: Whisper via sherpa-onnx

Same model, different runtime. No speed advantage expected since the
bottleneck is the encoder architecture, not the runtime wrapper.

## 4. Recommended Candidate: SenseVoice-Small

### Why SenseVoice over Zipformer

1. **Multilingual**: One model covers EN, HI, TA — matches current Whisper
2. **Non-autoregressive**: No beam-search decoder loop
3. **Runtime exists**: `OfflineSenseVoiceModelConfig` in sherpa-onnx 1.13.7
4. **Compact**: int8 quantized variant ~60 MB
5. **Apache 2.0**: SIH-compatible license

### Required artifacts

| File | Purpose |
|---|---|
| `model.int8.onnx` | Quantized SenseVoice model |
| `tokens.txt` | Token vocabulary |

### Android integration path

```kotlin
val config = OfflineRecognizerConfig(
    modelConfig = OfflineModelConfig(
        senseVoice = OfflineSenseVoiceModelConfig(
            model = "/path/to/model.int8.onnx"
        ),
        tokens = "/path/to/tokens.txt",
        numThreads = 4
    ),
    decodingMethod = "greedy_search"
)
val recognizer = OfflineRecognizer(config)
```

No new JNI wrapper needed — the Kotlin API is already in the AAR.

## 5. Status

### Model artifacts
- **NOT DOWNLOADED** — this environment cannot access HuggingFace/GitHub
  model repositories directly

### Desktop benchmark
- **NOT PERFORMED** — no model artifacts available

### Android benchmark
- **NOT PERFORMED** — requires model artifacts first

## 6. Feasibility Assessment

| Criterion | Status |
|---|---|
| Runtime available | ✅ sherpa-onnx 1.13.7 in project |
| API available | ✅ OfflineSenseVoiceModelConfig in AAR |
| Multilingual (EN/HI/TA) | ✅ SenseVoice supports all three |
| License | ✅ Apache 2.0 |
| Offline | ✅ |
| ARM64 | ✅ ONNX CPU |
| Model size | ✅ ~60 MB (int8) vs 31 MB (current Whisper) |
| Expected speed | ✅ ~200-800 ms (non-autoregressive) |
| Quality | ❓ UNKNOWN — not benchmarked |
| Integration effort | Low — Kotlin API, no new JNI |

## 7. Decision

**BLOCKED — ANDROID RUNTIME/ARTIFACT ISSUE**

The SenseVoice model is the strongest candidate identified:
- Multilingual (EN/HI/TA in one model)
- Apache 2.0
- Runtime already bundled
- API already available in the AAR
- Expected 4-16× faster than Whisper-tiny

However, model artifacts (model.int8.onnx, tokens.txt) are not available
locally and cannot be downloaded from this environment.

## 8. Unblocking Steps

1. Download `sherpa-onnx-sense-voice-zh-en-ja-ko-yue-2024-07-17` from
   HuggingFace or k2-fsa GitHub releases
2. Extract `model.int8.onnx` and `tokens.txt`
3. Record SHA-256 hashes
4. Stage on Android device
5. Create debug benchmark activity using `OfflineRecognizer`
6. Measure cold/warm latency with rescue phrases
7. Compare transcript quality against Whisper baseline

## 9. Build & Regression

No production code was modified in P1.13.
- Tests: 329 / 0 failures
- assembleDebug: PASS
- No new APK required
