# Phase 3D.4B — controlled offline translation benchmark

## Final status

**CANDIDATE = NOT APPROVED — stopped at the provenance gate.**

No model, tokenizer, runtime dependency, or dataset was downloaded. No benchmark was run. No production Android/iTantra code or configuration was modified.

The required controlled benchmark cannot begin until the candidate's redistribution and export provenance are sufficient. This is an intentional stop, not an inference failure.

## 1. Candidate identity

| Field | Observed value |
|---|---|
| Candidate repository | `hari31416/indictrans2-indic-indic-dist-320M-ONNX-int8` (community Hugging Face repository) |
| Candidate revision | `b04956dee2f2a3e06e44bf09f8d654a6b81af99a` |
| Claimed base model | `ai4bharat/indictrans2-indic-indic-dist-320M` |
| Official base revision previously recorded | `ffb7582b6d43791f1fb26b2153fc065f2e9ea575` |
| Intended direction family | Indic → Indic; HI ↔ TA candidate |
| Quantization claimed by publisher | dynamic INT8 |
| Model files | `encoder_model.onnx`, `decoder_model.onnx`, `decoder_with_past_model.onnx`, external encoder/decoder weight sidecars |

## 2. Provenance gate

The official AI4Bharat IndicTrans2 project identifies the official base checkpoint as MIT licensed. The community HF model card declares both `base_model: ai4bharat/indictrans2-indic-indic-dist-320M` and `license: mit`.

The publisher links an export-code repository: `Hari31416/indictrans2-onnx-export`, revision observed: `c488d66bd1a59bcd419d246e6c2b85c17384d9bc`. Its README describes manual `torch.onnx.export`, externalized encoder/decoder weights, graph optimization, and dynamic INT8 quantization.

However, this is not sufficient provenance for acquisition/redistribution:

- The exporter repository declares no GitHub license.
- No `LICENSE` file exists at the repository root or in its observed recursive tree.
- No reproducible, pinned export environment or artifact SHA-256 for the published ONNX files was verified.
- The community card’s own title incorrectly calls this Indic-Indic **320M** bundle “200M”, which is a material metadata inconsistency.
- The relationship between the exact official base revision and the exact published ONNX weights has not been independently reproduced or cryptographically established.

**Gate result: fail.** The named community bundle must not be downloaded, benchmarked, bundled, or integrated under the current provenance rule.

## 3. License

| Artifact | Evidence | Status |
|---|---|---|
| Official AI4Bharat base checkpoint | Official project and HF metadata report MIT | acceptable for investigation, subject to artifact-record verification |
| Community ONNX export code | No repository license found | unacceptable / incomplete |
| Community ONNX bundle | HF card declares MIT, but exporter provenance is incomplete | not approved |

## 4. Artifact sizes

Observed HF metadata for the community INT8 candidate lists:

| Component | Bytes |
|---|---:|
| encoder external weights | 120,068,096 |
| shared decoder external weights | 203,048,944 |
| ONNX external weight subtotal | 323,117,040 |
| source tokenizer JSON | 23,876,630 |
| target tokenizer JSON | 23,869,629 |
| SentencePiece source + target models | 6,513,806 |
| source + target dictionaries | 6,781,648 |

Graph files, Python helper files, configuration, and runtime are additional. These metadata sizes are not locally verified artifact sizes and are not an Android footprint measurement.

## 5–7. Runtime, tokenizer, offline verification

The community card describes Python `onnxruntime`, `tokenizers`, and `huggingface-hub`; its export material also mentions browser `onnxruntime-web` and SentencePiece WASM. It is not evidence of an Android runtime.

Tokenizer inputs appear to include two SentencePiece model files, dictionaries, and two tokenizer JSON files. Their integrity and behavior have not been tested.

**Offline runtime verification: not performed.** No candidate passed provenance, so no files/dependencies were acquired and no network-disconnected run occurred.

## 8–15. Benchmark and quality results

| Required result | Status |
|---|---|
| Cold initialization | not measured |
| First translation | not measured |
| Five warm translations / min / median / max | not measured |
| Peak / idle / post-translation RAM | not measured |
| HI → TA fixed emergency outputs | not generated |
| TA → HI fixed emergency outputs | not generated |
| EN → HI fixed emergency outputs | not generated |
| EN → TA fixed emergency outputs | not generated |
| Semantic/negation/target-language review | not performed |
| Repeated-run stability | not tested |
| Q4F16 comparison | not attempted; INT8 did not pass provenance |

No BLEU, COMET, quality, latency, RAM, Android, or stability values are claimed.

## Direction coverage

The Indic→Indic candidate is for HI↔TA. It is not the correct direction-family artifact for EN→HI or EN→TA; those require separate EN→Indic artifacts. It must not be assumed to translate English merely because it is multilingual over Indic languages.

## 16. Android feasibility

**ANDROID STATUS = UNVERIFIED.** ONNX format alone does not establish ONNX Runtime Mobile operator support, ARM64 execution, greedy encoder-decoder generation, local tokenizer compatibility, runtime binary size, memory use, or RMX3782 latency.

## 17. Failure modes

1. Incomplete community-export license/provenance.
2. No verified published artifact checksum/reproducible export chain.
3. Wrong direction-family assumption would make EN→HI/TA invalid.
4. Unverified Android decoder/tokenizer path.
5. Unmeasured model coexistence with Whisper, TTS, UI, Room, transport, and location.

## 18. Recommendation

**Reject this exact community candidate for controlled acquisition today.**

A future benchmark may proceed only after one of these gates is satisfied:

1. AI4Bharat publishes an official ONNX/mobile artifact with explicit license, revisions, checksums, tokenizer bundle, and Android guidance; or
2. the community exporter supplies a repository license, reproducible pinned export procedure, base-artifact revision/checksum mapping, and checksums/licenses for the ONNX/tokenizer release, followed by a separate review approval.

Only then should the designated laptop download the single INT8 HI↔TA candidate, run the fixed offline benchmark, and decide whether Q4F16 is justified. No Android integration follows from this report.
