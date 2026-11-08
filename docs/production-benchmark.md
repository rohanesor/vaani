# Production model benchmark

## Test classification

| Check | Status |
|---|---|
| Archive SHA-256 | **Automated pass** — `c35a238ca735f2db0197dee48e4ea4c9c9db74d8c41205e811703f9163572814` |
| Model inventory / ONNX graph audit | **Automated pass** on the supplied archive |
| Candidate ONNX Runtime validation | **Automated host-only**; not an Android or sherpa-onnx approval |
| Android release APK / install | Not run locally (JDK and release signing material unavailable) |
| Airplane-mode test | Not run |
| Physical-device test | Not performed |

## Inventory

The verified archive contains exactly 21 required model files: one 32,152,673-byte Whisper `ggml-tiny-q5_1.bin`, ten ONNX files, and ten token files. TTS FP32 payload is **1,140,396,076 bytes**; the complete model payload is **1,172,553,259 bytes**.

`models-audit.json` is the machine-readable audit. All ten TTS graphs use ONNX opset 13, have 460 `FLOAT` initializers, and have no FP16, INT8, duplicate initializer payload, duplicate full-language model, or training artifact in the supplied archive. Every model has inputs `x`, `x_length`, `noise_scale`, `length_scale`, and `noise_scale_w`, and emits `y`.

## INT8 candidate investigation

ONNX Runtime dynamic QInt8 candidates were generated outside production assets. They were structurally valid, but are **not approved for iTantra production**: deterministic host inference was substantially slower than FP32 and six languages changed generated waveform length. No candidate is packaged in the release path.

| Language | FP32 bytes | INT8 candidate bytes | Ratio | FP32 init ms | INT8 init ms | FP32 inference ms | INT8 inference ms | Result |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| ben | 114,044,600 | 37,997,212 | 3.00x | 2968.94 | 1892.88 | 162.14 | 1099.19 | waveform length changed |
| eng | 114,016,948 | 37,990,295 | 3.00x | 1996.63 | 1255.10 | 36.79 | 201.80 | output valid; slower |
| guj | 114,033,848 | 37,994,523 | 3.00x | 1845.95 | 1252.95 | 102.87 | 938.99 | output valid; slower |
| hin | 114,043,064 | 37,996,827 | 3.00x | 1802.34 | 1304.38 | 126.02 | 1114.05 | waveform length changed |
| kan | 114,045,368 | 37,997,404 | 3.00x | 2147.99 | 1957.31 | 275.60 | 2341.62 | waveform length changed |
| mal | 114,052,280 | 37,999,131 | 3.00x | 2064.83 | 1943.19 | 149.37 | 861.61 | output valid; slower |
| mar | 114,043,832 | 37,997,019 | 3.00x | 1880.04 | 1578.82 | 132.07 | 1033.90 | output valid; slower |
| ory | 114,046,136 | 37,997,595 | 3.00x | 4701.48 | 1576.92 | 232.73 | 1727.64 | waveform length changed |
| tam | 114,032,312 | 37,994,140 | 3.00x | 2003.73 | 1318.84 | 158.69 | 1161.10 | waveform length changed |
| tel | 114,037,688 | 37,995,484 | 3.00x | 2045.67 | 1396.75 | 104.24 | 663.59 | waveform length changed |

These timings are a single x86_64 host ONNX Runtime run using synthetic token IDs, not RTF, not audio-quality validation, and not Android measurements. The model’s stochastic-duration path means numeric waveform comparison is insufficient for quality approval. Sherpa-onnx Android inference and intelligibility regression tests for all ten language sentences remain required before any alternative precision is approved.

## Runtime design and pending device measurements

The application extracts APK assets atomically to private storage, verifies each SHA-256, and opens filesystem paths. It initializes Whisper and one active TTS language only; changing language releases the prior native TTS session. This avoids ten simultaneous ONNX sessions but does not eliminate the unavoidable extracted storage copy required by the native runtimes.

Final APK size, installed storage, Android heap/native heap/RSS/PSS, TTS RTF, Bluetooth latency, restart/interrupted-extraction reliability, and airplane-mode acceptance are **not measured**. They must be collected on intended ARM devices from a signed release APK; no physical-device claim is made.
