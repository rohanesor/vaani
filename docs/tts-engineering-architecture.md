# Engineered multilingual TTS architecture

## Recommendation

Build a **shared non-autoregressive multilingual student acoustic model with language-specific text frontends/adapters and one shared lightweight neural vocoder**, exported as static ONNX graphs and run through ONNX Runtime Mobile. This is Option D: shared acoustic model + language embeddings + small language-specific components.

It is a training/deployment strategy, not a claim that a trained artifact currently exists or is ready for production.

```text
packet language code (mandatory)
  → LanguageRouter (canonical code; no user preference inference)
  → TtsModelRegistry (local version/checksum/sample rate/speaker/backend)
  → language text normalizer + grapheme/phoneme frontend
  → shared student acoustic ONNX + language embedding / optional adapter
  → shared HiFi-GAN-class vocoder ONNX
  → PCM → AudioTrack
```

## Exact model strategy

1. **Frontend:** deterministic, local text normalization and grapheme/phoneme tokenization per language. No remote tokenizer. The frontend is versioned independently and must be trained/evaluated with the same text conventions as its corpus.
2. **Acoustic student:** a compact FastSpeech2/FastPitch-class non-autoregressive multilingual student with a language embedding. It emits mel features, not audio. Small per-language output/adaptation modules are allowed only when held-out pronunciation data proves they are needed.
3. **Vocoder:** a single shared HiFi-GAN-class vocoder trained on the approved corpus at one fixed sample rate. A separate vocoder is not assumed per language.
4. **English:** English is a route in the same student initially, trained from LibriTTS, not a runtime dependency on Kokoro/MMS/Piper. A separate student is permitted later only if benchmark evidence justifies it.
5. **Registry:** all ten routes point to the same pinned acoustic/vocoder model version initially plus their frontend and language/speaker IDs. This preserves `UnifiedTtsEngine` while avoiding ten full graphs in memory.

## Training and distillation strategy

Start with supervised training from approved ground-truth corpus data. Train a larger multilingual teacher only if the supervised compact student does not meet intelligibility; its purpose is offline duration/prosody/quality supervision. Then distill into the compact non-autoregressive student. The teacher never ships.

Training requires a Linux GPU environment, corpus preparation, deterministic split manifests, text normalization, audio resampling, quality filters, speaker/language balancing, checkpoint/revision logging, and native-language evaluation. GPU count, VRAM, training duration, teacher size, and student quality are **UNKNOWN / REQUIRE PROTOTYPE**. No expensive training is started in Phase 2E.

## Android inference strategy

- Export the acoustic model and vocoder separately to pinned ONNX opsets after graph/operator inspection.
- Run through ONNX Runtime Mobile (MIT); use Kotlin only as orchestration, not as the inference runtime.
- Implement a JNI/native or ORT-mobile backend only in an isolated lab first. Python, PyTorch, CUDA, HTTP, and model downloads are development-only and prohibited from the installed app.
- Keep one backend/session live. Load/checksum-verify the local assets before creating a session; release it on controlled eviction or language/backend switch.
- `TtsModelRegistry` must contain `languageCode`, backend ID, model version, SHA-256, sample rate, speaker ID, frontend version, and availability. A failed checksum maps to `TTS_MODEL_CORRUPT`; an absent route maps to `TTS_UNSUPPORTED_LANGUAGE`.

## Compression strategy

1. Establish FP32 Android synthesis and quality baseline.
2. Export and validate FP16 only where ARM/ORT support and audio quality permit it.
3. Evaluate static or weight-only INT8 separately for acoustic and vocoder graphs; do not quantize sensitive normalization/output layers blindly.
4. Retain a precision only after same-corpus native-listener intelligibility, waveform validity, latency, memory, and repeated language-switch tests pass.

No compressed model size, RAM value, or RTF is predicted as a measurement. Engineering budgets are: total models + frontend + vocoder below 250 MB if possible; incremental TTS memory below 150 MB if possible; short-message RTF below 0.5 preferred and below 1.0 required.

## Legal strategy

Use only data and code with recorded, artifact-specific terms. Proposed primary data is CC-BY-4.0 IndicVoices-R plus CC-BY-4.0 LibriTTS. Framework candidates are ESPnet (Apache-2.0), ONNX (Apache-2.0), ONNX Runtime (MIT), sherpa-onnx (Apache-2.0), Piper (MIT), and Coqui TTS (MPL-2.0). Framework licenses do not grant any model/data rights. Before training, preserve licenses/NOTICE/attribution, review dataset consent and attribution obligations, and determine legally appropriate license/notice terms for newly trained weights.
