# SIH-L10-P1.19 — Receiver Latency Optimization Continuation

## Final decision

# NO SAFE RECEIVER OPTIMIZATION FOUND

No production behavior was changed. This phase inspected supported CT2 configuration and receiver dispatch behavior after P1.18, and rejected speculative changes before physical execution.

## Checkpoint

- Starting SHA: `038e58b4902566c89d9563bc610f797bd4e8f830`
- Safety branch: `sih/laptop1-before-l10-p1-19`
- Validated rollback APK: `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9`
- Tracked working tree was clean at investigation start. Existing untracked artifacts were preserved.

## Baseline

| CT2 physical receiver metric | Value |
|---|---:|
| Short, beam=1 median / P95 | 1122 / 1423 ms |
| Medium, beam=1 median / P95 | 2014 / 2058 ms |
| Long, beam=4 first sample | 9600 ms |
| Long-series thermal result | stopped at thermal status 3 |

## A. Actual CT2 runtime configuration

The deployed ARM64 JNI constructs:

```cpp
ctranslate2::Translator(model_path, Device::CPU, ComputeType::DEFAULT, {0})
```

CTranslate2 4.8.2 exposes `ReplicaPoolConfig` to this constructor:

| API field | Current effective input | Meaning |
|---|---:|---|
| `num_threads_per_replica` | default `0` | CT2 resolves `0` to `OMP_NUM_THREADS`, if supplied, otherwise `min(4, hardware_concurrency)` |
| `max_queued_batches` | default `0` | default internal queue capacity (4 × replica count) |
| `cpu_core_offset` | default `-1` | no affinity offset |
| replicas | one model path / one device index | one model replica |

The Android CT2 build cache records `OPENMP_RUNTIME=NONE` and `WITH_RUY=ON`. Thus CT2 uses its CPU/Ruy path rather than an OpenMP runtime. The app does not set an explicit CT2 worker count. The default configuration already asks CT2 for up to four computation threads; it is not a hidden one-thread configuration.

A thread-count test is not a safe latency hypothesis here:

- Raising the count is explicitly disallowed without compatibility evidence and prior explicit-thread work was unsafe.
- Lowering the count has no plausible mechanism to reduce single-request latency on this model; it may reduce heat but is likely slower.
- More replicas would duplicate approximately 620 MB model weights and violate the one-session memory gate.
- CPU affinity can constrain rather than add compute and has no device-specific core-performance evidence.

No thread, affinity, queue, or replica experiment was promoted or run.

## B. Beam/search options

CT2 exposes beam, patience, length penalty, coverage penalty, repetition penalty, n-gram suppression, maximum input length, maximum decoding length, and greedy callback controls.

Current normal rescue inputs already use beam=1. Existing defaults are length penalty 1, patience 1, repetition penalty 1, no repeat n-gram disabled, and max decoding length 256. The latter is an upper safety bound; it does not cause a short translation to generate 256 tokens after EOS.

Changing search penalties or an early-stop callback changes output selection. Lowering the maximum decode length risks destructive truncation. Neither has a five-run quality-validation basis, so neither is an eligible latency experiment.

## C. Input length

The sender has a `SentenceSegmenter`; normal messages already enter the receiver as submitted sentence events. The receiver’s ordinary-message route intentionally passes the text unchanged to CT2. This preserves transcript content and PacketV2 semantics.

Code-switch handling can make serial additional translation calls only for detected language spans. It is not an optimization path: changing it would alter routing and can increase compute. No arbitrary truncation or new segmentation was introduced.

## D/E. Pipeline and warm behavior

Packet ingress persists/deduplicates and ACKs first, then emits one incoming message. The receiver collector translates once and invokes a quick queue enqueue; it does not wait for TTS playback. CT2 owns a single mutex-protected reused handle. TTS owns a separate single queue worker.

P1.16 receiver `T5→T6 start` was only 45.9–67.1 ms, whereas CT2 inference was 718–3303 ms. There is no measured Kotlin/transport dispatch delay comparable to the native bottleneck. Parallelizing translations would require multiple large model contexts or queue competition and is not justified.

P1.17 verified one CT2 model load and tokenizer/session reuse. The remaining dominant cost is the native combined inference call, not per-turn initialization.

## F. Native API exposure

CTranslate2’s public `Translator::translate_batch` returns completed translation results. Its deployed API does not expose separate encoder and decoder execution timing or independently schedulable encoder/decoder calls. P1.17 already measured SentencePiece encoding/decoding as negligible. CT2 has profiler infrastructure, but using it does not provide a safe reduction in inference work. CT2 was not forked or replaced.

## Physical benchmark decision

No physical experiment was run in this phase. No supported, isolated one-variable change offered a defensible chance of reducing latency while preserving output and stability. The existing long-input physical series had reached thermal status 3, and the receiver/sender were disconnected when this inspection completed. Inventing a five-sample before/after result would be invalid.

| Item | Result |
|---|---|
| Experiment performed | API/configuration and pipeline inspection only |
| Optimized result | none |
| Quality result | unchanged; no output-changing experiment |
| Thermal/memory | unchanged; no new session/replica or physical load |
| Promoted/reverted | none required |

## Recommendation

Preserve the exact validated production configuration. Do not apply speculative thread, affinity, decoding-limit, segmentation, or parallelization changes. CT2 NLLB native inference remains the receiver architectural constraint.

Cool and reconnect devices only when a future milestone requires a controlled physical run. Do not begin Telugu/Kannada work and do not perform the official multilingual benchmark in this phase.
