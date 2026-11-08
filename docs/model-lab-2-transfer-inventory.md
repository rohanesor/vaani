# iTantra / Vokie — Model Lab 2 Artifact Transfer Inventory

**Purpose:** Identify the exact ORIGINAL Model Lab 2 artifacts so they can be transferred to **Laptop 1**
and verified on arrival. Everything below is the measured inventory of the artifacts produced during
Model Lab 2 (offline EN/HI/TA translation + Tamil TTS).

- No files were downloaded, recreated, changed, or deleted to produce this inventory.
- Reproduce checksums on the receiving machine with SHA-256.

---

## 1. Git status (run at inventory time)

### `C:\tts` (the Model Lab 2 workspace)
**NOT a git repository** — `git status` / `git branch --show-current` / `git log --oneline -10` all
return "no repo". The Model Lab 2 artifacts are plain filesystem files and are NOT under version control.

### `C:\tts\firstmate` (the `kunchenguid/firstmate` clone)
```
On branch main
Your branch is up to date with 'origin/main'.
nothing to commit, working tree clean
```
- `git branch --show-current` → `main`
- `git log --oneline -10` →
  ```
  a5f3cbe fix: support first public-followup registration on Bash 3.2 (#3420)
  ```
- It is a `--depth 1` shallow clone (1 commit). It is an **agent-orchestration distro** and contains
  **no translation/TTS models**. It is **NOT part of the transfer set**.

---

## 2. Transfer set — full inventory

### A. Documentation (SHA-256 verified)

| # | Absolute path | File | bytes | SHA-256 | Provenance / License |
|---|---------------|------|-------|---------|----------------------|
| 1 | `C:\tts\docs\model-lab-2-language-final.md` | report (Model Lab 2) | 18,123 | `00ddc0ba9426999798db92a2e1eaf31a461348d1e46564de6c1f2256598b3dd1` | Written during Model Lab 2 |
| 2 | `C:\tts\docs\model-lab-final-report.md` | report (Model Lab 1) | 19,291 | `bb7f65ef585950f97ac29b82a594517719c033483160df5174ebb798aae3dd8b` | Written during Model Lab 1 (context baseline) |
| 3 | `C:\tts\docs\model-integration-plan.md` | integration plan | 10,967 | `c6fc3fcef0f3d333cc421f4319b4524526943dbd19602d8b5302253f88b34999` | Model Lab 1, extended §15 in Model Lab 2 |

### B. Provenance / manifest

| # | Absolute path | File | bytes | SHA-256 | Provenance / License |
|---|---------------|------|-------|---------|----------------------|
| 4 | `C:\tts\model-lab\models\MANIFEST.json` | provenance manifest | 9,735 | `84fb866a3b5b3412c42e72d656fc205cfeb162b9fae4ec2336d0c17b029b57cb` | Source-of-truth provenance (repo, revision, size, sha256, downloaded_utc) for all acquired artifacts |

### C. Benchmark outputs (`C:\tts\model-lab\bench\out\`)

| # | Absolute path | File | bytes | SHA-256 | Notes |
|---|---------------|------|-------|---------|-------|
| 5 | `C:\tts\model-lab\bench\out\trans_results.json` | translation results | 11,197 | `46a341d84a5efdd79b91b9c94d57db203728cfda3fb64dad222ff21c12880096` | 6 directions, latency + outputs. Model = CT2 NLLB int8 |
| 6 | `C:\tts\model-lab\bench\out\ta_tts_results.json` | Tamil TTS results | 1,881 | `ef2dfa5f11173170610f015362f0a79dc213612370c3e4e47dfa0bb134151e5e` | MMS-Tamil metrics |
| 7 | `C:\tts\model-lab\bench\out\e2e_results.json` | end-to-end results | 1,702 | `eb4caae7e4af0455c41425b5866082efdc5e37300fc5ea1cb010d00a8e8c9cfd` | translation + TTS pipelines |

Tamil TTS WAV artifacts (audio for human review; generated from `ta_mms_*.wav`):

| # | Absolute path | File | bytes | SHA-256 |
|---|---------------|------|-------|---------|
| 8 | `C:\tts\model-lab\bench\out\ta_mms_0.wav` | `ta_mms_0.wav` | 64,866 | `df11bda5e65365f1ef84b567ced31a9d45c3b6340045fff989b4531c96e36276` |
| 9 | `C:\tts\model-lab\bench\out\ta_mms_1.wav` | `ta_mms_1.wav` | 58,138 | `80385ffe2d75eea49c4cc9a503c2a56282aeedfaad4202219b88f613e1cf6d04` |
| 10 | `C:\tts\model-lab\bench\out\ta_mms_2.wav` | `ta_mms_2.wav` | 64,674 | `f4acf7ecd2777a17acec305b36c6235fde5fb5ce84290c39f1c01c8f2827bd5f` |
| 11 | `C:\tts\model-lab\bench\out\ta_mms_3.wav` | `ta_mms_3.wav` | 56,812 | `2faae509b678c4736dfb176a94ef8ccd0490e5c5c195661fea9a31b0a1aed298` |
| 12 | `C:\tts\model-lab\bench\out\ta_mms_4.wav` | `ta_mms_4.wav` | 90,434 | `7b69082283cb668dab3b21f67e8e218e525423803d6a6242ad0da10b7c3537b4` |

(EN/HI TTS WAVs `en_0..5.wav`, `hi_0..5.wav` are also present in `bench\out` but belong to the
already-approved TTS set from Model Lab 1. They are optional to transfer.)

### D. Translation model — NLLB-200-distilled-600M (CTranslate2 INT8)
Directory: **`C:\tts\model-lab\models\ct2\nllb600m\`**

| File | bytes | SHA-256 | Revision / Provenance | License |
|------|-------|---------|----------------------|---------|
| `model.bin` | 619,704,329 | `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` | `osa911/nllb-200-distilled-600M-ct2-int8` @ `46858753dbaf8eb5e21bb6f0037c3b90851e090a` | **CC-BY-NC-4.0** (Meta NLLB-200); converter = MIT |
| `config.json` | 223 | `8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af` | same repo/revision | CT2 format config (`add_source_eos:false`, etc.) |
| `shared_vocabulary.json` | 5,921,176 | `af53bfd0e6f726209e7325e45b87ab3b14e5856f7d42d7b9be91de3287c45267` | same repo/revision | NLLB id↔token vocabulary |
| `sentencepiece.bpe.model` | 4,852,054 | `14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a` | same repo/revision | NLLB SentencePiece tokenizer |

- **Runtime:** CTranslate2 **4.8.2** (device CPU). Saved compute `int8_float16`; auto-converted to
  `int8_float32` on CPU.
- **Conversion:** `ct2-transformers-converter --model facebook/nllb-200-distilled-600M
  --output_dir <dir> --quantization int8` (community conversion by `osa911`).
- Model size (dir) ≈ 630 MB.

### E. Translation tokenizer / configuration assets (used by the CT2 wrapper)
Directory: **`C:\tts\model-lab\models\nllb\`** (these are the sidecar files the wrapper loads via
`tokenizers.Tokenizer.from_file` + the HF NLLB config)

| File | bytes | SHA-256 | Revision | License |
|------|-------|---------|----------|---------|
| `tokenizer.json` | 17,331,224 | `8ac789ad7dabea44d41537822d48c516ba358374c51813e2cba78c006e150c94` | `Xenova/nllb-200-distilled-600M` @ `261c31d1a5732c67cdd16d80e8d6088507c7ccea` | CC-BY-NC-4.0 |
| `sentencepiece.bpe.model` | 4,852,054 | `14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a` | same | CC-BY-NC-4.0 |
| `tokenizer_config.json` | 544 | `ddf411c9f790d081e72de76bb8e8b714d74e160e61bc10c9bd8f56022dcd7fd7` | same | CC-BY-NC-4.0 |
| `config.json` | 873 | `52f035acb54ac80e5ef7fe78d6967f8ddf8e8799f078d1a92a2c8168d8ff4a20` | same | CC-BY-NC-4.0 |
| `generation_config.json` | 189 | `1ce846259ae04c572ae1863e358534bbc376ff3d3f100b1494feda6200b51de8` | same | CC-BY-NC-4.0 |
| `special_tokens_map.json` | 3,548 | `992bd4ed610d644d6823081937bcc91bb8878dd556cea4ae5327f2480361330e` | same | CC-BY-NC-4.0 |

### F. Tamil TTS — MMS-TTS Tamil (willwade ONNX, sherpa-onnx)
Directory: **`C:\tts\model-lab\models\tts\mms-ta\tam\`**

| File | bytes | SHA-256 | Revision / Provenance | License |
|------|-------|---------|----------------------|---------|
| `model.onnx` | 114,032,312 | `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88` | `willwade/mms-tts-multilingual-models-onnx` @ `709a74aad80a840eb57f767a7f5d155aaad1ac7b` (`tam/`) | **CC-BY-NC-4.0** (Meta MMS-TTS); conversion MIT → sherpa-onnx |
| `tokens.txt` | 375 | `0b3f692319bb5fae8658e2f84bf252bca92450d0207bbba7273caa1a182d81b8` | same | CC-BY-NC-4.0 |
| `sample.wav` | 121,388 | `ce39e5eb554c138c85eda01dce4caeaa262350e727173a81cc19a7260e6ece12` | same (reference sample; not required for inference) | CC-BY-NC-4.0 |

- **Runtime:** sherpa-onnx **1.13.7** (VITS, `frontend=characters`, no espeak-ng required).
- Model size ≈ 114 MB. Sample rate 16000 Hz.

---

## 3. What to include in the transfer

Copy these directories/files as-is (preserve the layout):

```
C:\tts\docs\
  model-lab-final-report.md
  model-lab-2-language-final.md
  model-integration-plan.md
  model-lab-2-transfer-inventory.md      (this file — optional)
C:\tts\model-lab\models\
  MANIFEST.json
  ct2\nllb600m\model.bin
  ct2\nllb600m\config.json
  ct2\nllb600m\shared_vocabulary.json
  ct2\nllb600m\sentencepiece.bpe.model
  tts\mms-ta\tam\model.onnx
  tts\mms-ta\tam\tokens.txt
  tts\mms-ta\tam\sample.wav
  nllb\tokenizer.json                     (tokenizer sidecar)
  nllb\sentencepiece.bpe.model
  nllb\tokenizer_config.json
  nllb\config.json
  nllb\generation_config.json
  nllb\special_tokens_map.json
C:\tts\model-lab\bench\out\
  trans_results.json
  ta_tts_results.json
  e2e_results.json
  ta_mms_0.wav ... ta_mms_4.wav
```

**Excluded (do NOT transfer):**
- `C:\tts\firstmate\` — agent-orchestration distro, no models (excluded).
- `C:\tts\model-lab\models\nllb\onnx\*.onnx` — the **rejected** Xenova NLLB ONNX exports
  (confirmed degenerate). Retained only as negative-result evidence; not a production artifact.
- `C:\tts\model-lab\models\tts\vits-piper-*` and `espeak-ng-data` — the already-approved EN/HI TTS
  (only needed if Laptop 1 also needs EN/HI TTS; not part of the Model Lab 2 *new* set).

## 4. Verify on arrival (Laptop 1)

Re-check the two big binaries first:

```text
model.bin      sha256 = ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8
model.onnx     sha256 = c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88
```

Then the small text assets and docs (see tables above). Optionally `certutil -hashfile <file> SHA256`
on Windows.

## 5. License summary (keep with the bundle)

| Asset | License |
|-------|---------|
| CTranslate2 runtime | MIT |
| sherpa-onnx runtime | Apache-2.0 |
| NLLB-200-distilled-600M (CTranslate2 int8) | **CC-BY-NC-4.0** (non-commercial) |
| MMS-TTS Tamil (willwade ONNX) | **CC-BY-NC-4.0** (non-commercial) |
| Piper lessac / priyamvada (EN/HI TTS) | MIT hub; dataset non-commercial |

→ Both models are **CC-BY-NC-4.0**. Ship clear **non-commercial** attribution/NOTICE with the bundle.
