# CT2 NLLB artifact reproduction report

## Scope

This report distinguishes the missing historical local file from a **reproduced artifact with exact historical SHA-256 match**. No Android integration was started and `model-lab/models/MANIFEST.json` was not modified.

## Source revision

| Field | Value |
|---|---|
| Repository | `osa911/nllb-200-distilled-600M-ct2-int8` |
| Requested/resolved revision | `46858753dbaf8eb5e21bb6f0037c3b90851e090a` |
| Resolution method | Hugging Face revision API; returned the same full SHA |
| License/provenance | CC-BY-NC-4.0 NLLB provenance as recorded by recovered Model Lab manifest/inventory; CT2 runtime MIT |
| Acquisition timestamp | current Phase 1.6 run |

The recovered `model-lab/fetch_ml2.py` shows that Model Lab 2 did **not** locally run `ct2-transformers-converter`: it downloaded the four already-converted CT2 files with `huggingface_hub.hf_hub_download(repo_id="osa911/nllb-200-distilled-600M-ct2-int8", filename=<file>, revision="46858753dbaf8eb5e21bb6f0037c3b90851e090a", local_dir=<dir>)`.

The equivalent reproduction command used the pinned resolve URL:

```text
curl -fL https://huggingface.co/osa911/nllb-200-distilled-600M-ct2-int8/resolve/46858753dbaf8eb5e21bb6f0037c3b90851e090a/<file>
```

It was deliberately written first to `model-lab/reproduction/ct2-nllb600m/`, checked, then copied unchanged to `model-lab/models/ct2/nllb600m/`.

## Reproduced CT2 directory verification

| File | Size (bytes) | SHA-256 | Manifest match |
|---|---:|---|---|
| `model.bin` | 619,704,329 | `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` | YES |
| `config.json` | 223 | `8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af` | YES |
| `shared_vocabulary.json` | 5,921,176 | `af53bfd0e6f726209e7325e45b87ab3b14e5856f7d42d7b9be91de3287c45267` | YES |
| `sentencepiece.bpe.model` | 4,852,054 | `14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a` | YES |

**Historical model SHA-256:** `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`  
**Reproduced model SHA-256:** `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`  
**Exact match:** **YES**.

`config.json` is unmodified and records `add_source_bos: false` and `add_source_eos: false`. Therefore callers must explicitly use `[src_lang] + subwords + ["</s>"]`.

## Environment

| Component | Reproduction environment |
|---|---|
| OS | Linux WSL2, `6.6.87.2-microsoft-standard-WSL2` |
| CPU architecture | x86_64 host |
| Python | 3.11.15 |
| CTranslate2 | 4.8.2 |
| SentencePiece | 0.2.1 |
| tokenizers | 0.20.3 |
| psutil | 6.1.1 |

This differs from the recovered Model Lab’s Windows host environment. It does not affect byte identity because the CT2 artifact was retrieved exactly from the pinned upstream converted-model revision rather than locally re-quantized.

## Functional validation

The recovered `trans_bench2.py` was copied to `/tmp`, with only its Windows paths redirected to the reproduced model and existing recovered tokenizer. The historical harness and `model-lab/bench/out/trans_results.json` were not overwritten.

Input construction remained:

```text
[src_lang] + tokenizer subwords + ["</s>"]
target_prefix = [[tgt_lang]]
beam_size = 4
```

| Direction | First validation output | Result |
|---|---|---|
| EN → HI | `मेरी मदद करो.` | PASS |
| HI → EN | `Please help me.` | PASS |
| EN → TA | `எனக்கு உதவுங்கள்.` | PASS |
| TA → EN | `Please help me.` | PASS |
| HI → TA | `எனக்கு உதவுங்கள்.` | PASS — direct, no English pivot |
| TA → HI | `मेरी मदद करो.` | PASS — direct, no English pivot |

## Benchmark comparison

| Measure | Historical recovered result | Current reproduction run | Interpretation |
|---|---:|---:|---|
| CT2 load | 0.679 s | 6.404 s | different WSL host/runtime/cache environment |
| Peak RSS | 728.0 MB | 767.1 MB | different host/process/runtime environment |
| Direction medians | ~0.212–0.228 s | ~0.373–0.426 s | different host; functionality passed |
| Direction P95 | ~0.254–0.31 s | ~0.415–0.551 s | different host; not an Android result |

CTranslate2 reported automatic `int8_float16` to `int8_float32` conversion on this x86_64 host due to unavailable efficient float16 int8 execution. This explains why host runtime measurements must not replace historical numbers or be used as Android performance claims.

## Files created/changed

- Created: `model-lab/reproduction/ct2-nllb600m/` (isolated exact reproduction).
- Created: `model-lab/models/ct2/nllb600m/` (verified copy, four files).
- Created: `docs/ct2-reproduction-report.md`.
- Unchanged: `model-lab/models/MANIFEST.json`; historical benchmark output files; Android production code.

## Gate

**CT2 ORIGINAL BINARY:** not recovered locally; **reproduced artifact with exact historical SHA-256 match**.  
**Phase 1.5 / 1.6 artifact gate:** PASS for the CT2 model directory.  
**Android integration:** READY for the next explicitly authorized Phase 2 CTranslate2 Android arm64 milestone. No Android work was performed here.
