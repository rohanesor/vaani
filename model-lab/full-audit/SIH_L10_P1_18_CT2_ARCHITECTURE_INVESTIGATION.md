# SIH-L10-P1.18 — CT2 Translation Architecture Investigation

## Final decision

# B. NO SAFE OPTIMIZATION FOUND

Production translation behavior is unchanged. No alternative model was downloaded, staged, installed, or integrated. PacketV2, transport, SenseVoice, TTS, playback, language routing, beam policy, and timing semantics remain unchanged.

## Safety

- Starting commit: `b9785eaadf491b4dd780cadff333268f73ad1cb8`
- Safety branch: `sih/laptop1-before-l10-p1-18` at the same SHA
- Validated pre-research APK SHA-256: `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9`
- Existing untracked models, WAVs, logs, local runtime files, and environment files were neither deleted nor committed.

## 1. Deployed architecture inventory

| Field | Measured/recorded value |
|---|---|
| Base model | `facebook/nllb-200-distilled-600M` |
| Deployed CT2 artifact | `osa911/nllb-200-distilled-600M-ct2-int8` revision `46858753dbaf8eb5e21bb6f0037c3b90851e090a` |
| Model binary | 619,704,329 bytes; SHA-256 `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` |
| Quantization | CT2 `int8_float16` stored artifact; CPU runtime representation is backend-dependent |
| Runtime | CTranslate2 4.8.2, custom static Android ARM64 JNI, CPU `DEFAULT`, device index `{0}` |
| Tokenizer | NLLB SentencePiece; 4,852,054-byte model and 5,921,176-byte shared vocabulary |
| Language coverage | NLLB-200 supports broad multilingual tags; the existing JNI intentionally maps EN/HI/TA only |
| Session/lifecycle | One lazy Kotlin handle, mutex-serialized; one `Translator` and tokenizer per loaded handle |
| Input | explicit source tag + SentencePiece pieces + `</s>` |
| Output | explicit target language prefix |
| Beam policy | beam=1 when input has <=20 pieces; otherwise beam=4 |
| Max decoding length | 256 |
| License record | NLLB CC-BY-NC-4.0; CT2 MIT |

`Translator::translate_batch()` is opaque at the deployed public API boundary: encoder and decoder cannot be independently timed without changing CT2 internals. P1.17 correctly measured SentencePiece encode/decode independently and found them negligible; combined native inference is the bottleneck.

## 2. Physical baseline

P1.17 receiver-only physical results on Realme RMX3782:

| Metric | Current CT2 |
|---|---:|
| Short (8 chars, 5 pieces, beam=1) median / P95 | 1122 / 1423 ms |
| Medium (46 chars, 12 pieces, beam=1) median / P95 | 2014 / 2058 ms |
| Long (135 chars, 31 pieces, beam=4) | 9600 ms first sample |
| Long series | stopped at thermal status 3 |
| PSS/native heap while profile session resident | 1,357,161 / 837,636 KB |

The translation model was loaded once, and tokenizer/session reuse was verified. This is architectural native inference cost, not tokenization, per-message model reload, queue backlog, or TTS contention.

## 3. Candidate compatibility review

| Candidate | Full existing direct EN/HI/TA route | CT2/Android path | Size/quality evidence | Decision |
|---|---|---|---|---|
| Current NLLB-200 distilled 600M INT8 | Yes | deployed | 619.7 MB, validated | retain baseline |
| Larger NLLB variants | Yes in principle | conversion required | larger than current; no speed rationale | reject |
| M2M100 418M | possible | Android INT8/tokenizer path unverified | documented ~1.94 GB FP32; no approved local validation | reject |
| IndicTrans2 official set | Yes only with three direction artifacts | no verified Android CT2 artifact | ~3.30 GB weights before runtime/tokenizers | reject |
| Community IndicTrans2 ONNX | direction dependent | ONNX, not CT2 | existing evaluation found degenerate output/provenance gap | reject |
| Pair-specific OPUS/Marian EN→HI | **No**; EN→HI only | conversion would be required | no pinned local artifact/quality run | reject |

### Why no candidate entered desktop validation

The required desktop texts were reserved as:

1. `Help me.`
2. `I am stuck and need help.`
3. `I am really stuck right now and I need immediate help.`

No candidate passed all preconditions: compatible Android architecture, preservation of the existing direct multilingual routing, pinned artifact/provenance, practical footprint, and a reason to expect a material speed improvement. Downloading a large unqualified checkpoint merely to create a benchmark would violate the requested evidence-first approach. Therefore there is no honest desktop or Android candidate latency/quality table to report.

This is not a missing benchmark silently treated as success. It is a pre-integration rejection. A candidate cannot satisfy the >=25% warm-median acceptance target without first satisfying the safety and correctness gates.

## 4. Prior evidence considered

- Existing NLLB CT2 reproduction validated direct EN→HI output `मेरी मदद करो.` for `Help me.` and confirmed exact artifact identity.
- Existing ONNX NLLB evaluation is unusable: it produced degenerate/repetitive output in greedy and beam modes, despite both INT8 and FP16 tests.
- Existing IndicTrans2 review found no official CT2-compatible Android runtime and requires approximately 3.30 GB across the three direct direction-family artifacts needed for EN/HI/TA behavior.
- P1.17 stopped a long-input series at thermal status 3. This makes speculative thread/architecture runs on the hot receiver unsuitable.

## 5. Recommendation

Current NLLB CT2 inference is the receiver-side architectural bottleneck. There is no qualified smaller/faster architecture ready to replace it while preserving current behavior. Leave production unchanged and stop speculative CT2 tuning.

The next appropriate milestone is not Telugu/Kannada adaptation. If official performance evidence is again required, cool the devices and repeat the physical EN→HI loop until exactly five transcript-correct valid runs are collected. Do not characterize P1.16’s four valid runs as an official completed five-run benchmark.
