# Phase 3D.4 — offline translation edge benchmark investigation

**Status: STOP — desk research only; no candidate is approved or recommended.**

No production Android, STT, TTS, PacketV2, transport, location, or translation-engine code was modified. No checkpoint, tokenizer, ONNX bundle, or Python package was downloaded. The designated second laptop was not available from this session, so no local benchmark, output, RAM, or Android result exists.

## 1–7. Candidates, provenance, license, size, quantization, runtime, tokenizer

Official base model: [`ai4bharat/indictrans2-indic-indic-dist-320M`](https://huggingface.co/ai4bharat/indictrans2-indic-indic-dist-320M), official revision `ffb7582b6d43791f1fb26b2153fc065f2e9ea575`. The official IndicTrans2 repository revision examined was `4f08e39cc6bf13cd62e2445dc725f22bff1a9219`. Official repository and HF metadata report MIT. Hindi and Tamil are among the Indic-Indic artifact's listed languages.

The ONNX bundles below are **community artifacts**, published by `hari31416`, not AI4Bharat. Their model cards declare `base_model: ai4bharat/indictrans2-indic-indic-dist-320M` and MIT, but that relationship, artifact license, checksums, and reproducibility have not been independently verified. They are candidates only.

| Candidate | HF revision observed | Claimed quantization | ONNX encoder + decoder weights | Other required tokenizer files | Claimed runtime |
|---|---|---|---:|---|---|
| `hari31416/indictrans2-indic-indic-dist-320M-ONNX` | `87edc54b2b43e44ea2a4d7e4ab9e36c4f8efbfe4` | FP32 | 1,284,501,504 bytes: encoder data 478,810,112 + decoder sidecar 805,691,392 | `model.SRC`, `model.TGT`, source/target dictionaries, two tokenizer JSON files | ONNX Runtime / onnxruntime-web claimed |
| `...-ONNX-fp16` | `735cd5e23d610e9b8c8a7c7454e7f6de91914733` | FP16 | 641,970,176 bytes | same layout | ONNX Runtime claimed |
| `...-ONNX-int8` | `b04956dee2f2a3e06e44bf09f8d654a6b81af99a` | dynamic INT8 | 323,117,040 bytes | same layout | ONNX Runtime claimed |
| `...-ONNX-q4f16` | `ae8bb9ccf437462e3306a188aaf7439f640415fa` | Q4F16 | 454,111,232 bytes | same layout | ONNX Runtime claimed |

The table's ONNX byte totals are only the two external weight sidecars; small graph files are additional. The listed tokenizer JSON files alone are about 47.7 MB, and SentencePiece `model.SRC`/`model.TGT` are about 6.5 MB. Thus the complete installed footprint is materially larger than the sidecar total.

The community export repository is `Hari31416/indictrans2-onnx-export`, revision `c488d66bd1a59bcd419d246e6c2b85c17384d9bc` observed. Its README describes manual `torch.onnx.export`, three graphs (`encoder_model`, `decoder_model`, `decoder_with_past_model`), externalized weights, a shared decoder sidecar, and post-export graph optimization. It says Optimum does not support IndicTrans. Its repository LICENSE was not found at the expected root URL during this investigation; therefore the exporter code/artifact licensing record is incomplete despite the HF card's MIT declaration.

For EN↔HI/TA, separate official base direction families are necessary: `ai4bharat/indictrans2-en-indic-dist-200M` and `ai4bharat/indictrans2-indic-en-dist-200M`. Community ONNX variants exist in the same publisher's namespace, but they were not selected or downloaded because Hindi↔Tamil direct translation is the required first gate.

## 8–11. Latency and RAM

| Measure | FP32 | FP16 | INT8 | Q4F16 |
|---|---:|---:|---:|---:|
| Cold initialization | not measured | not measured | not measured | not measured |
| First translation | not measured | not measured | not measured | not measured |
| Five warm translations / median | not measured | not measured | not measured | not measured |
| Peak RAM | not measured | not measured | not measured | not measured |
| Crashes/errors | not tested | not tested | not tested | not tested |

The community INT8 card claims CPU suitability and reports its own FP32-parity figures. Those are publisher-provided export-validation results, not Device-A measurements and not an emergency-quality benchmark; they are not accepted as performance or quality evidence here.

## 12–15. Manual quality table

No output was generated locally. The fixed Hindi→Tamil, Tamil→Hindi, English→Hindi, and English→Tamil emergency sentences were **not** sent to any cloud/API or model. Semantic correctness, intent, person/place, negation, grammar, hallucinations, omissions, repetition, and output-language correctness are all **unmeasured**.

## 16. Android feasibility

ONNX Runtime Mobile has Android/ARM64 deployment paths in general, but no candidate-specific Android build, encoder-decoder greedy decode loop, local tokenizer implementation, or RMX3782 execution was verified. The community material targets Python and browser/onnxruntime-web; it is not evidence of Android compatibility.

Even the smallest candidate has ~323 MB ONNX weights before tokenizer/runtime memory. Translation generation requires encoder plus autoregressive decoder state. Its coexistence with Whisper, Room, transport, location, and future TTS on Device A is unmeasured. It must not be assumed to fit comfortably or remain resident.

## 17–18. Recommendation and rejections

**Recommended candidate: none.**

- **FP32:** rejected for first edge benchmark due to ~1.28 GB weights.
- **FP16:** not approved; still ~642 MB weights and may not improve CPU inference on the target.
- **INT8:** smallest serious first candidate (~323 MB weights) if acquisition is separately approved, but provenance/reproducibility, Android generation, quality, RAM, and latency are unverified.
- **Q4F16:** not approved. The exporter itself cautions that 200M/320M quantization can drop sharply; emergency semantic quality must be independently checked before considering its smaller size.

## Required next action, only after explicit acquisition approval

On the designated laptop, acquire only the pinned INT8 Indic-Indic candidate and all its local tokenizer dependencies. Preserve artifact hashes/licenses, disconnect networking after preparation, and benchmark greedy batch-1 translation for the ten HI↔TA fixed sentences. Record literal outputs, cold/first/five-warm latency, median, disk footprint, and peak RSS. Stop before Android work if quality, provenance, memory, or latency is unacceptable. FP16/Q4F16 should be acquired only when a documented INT8 result justifies comparison.
