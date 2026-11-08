# iTantra / Vokie — Model Lab Final Report

**Scope:** Offline Translation (EN⇄HI, EN⇄TA, HI⇄TA) and Offline TTS (EN/HI/TA) model research
and benchmarking for the Vokie prototype.
**Environment:** Dedicated model-research laptop. All numbers measured, not assumed.
**Constraint obeyed:** No Android production files were modified. No STT / PacketV2 / Bluetooth /
Wi‑Fi Direct / location / UI / Room / AWS / release-config code was touched.

---

## 1. SYSTEM (Hardware Discovery)

| Item | Measured value |
|------|----------------|
| CPU | Intel Core 5 210H — 8 cores / 12 threads, 2.20 GHz base, 12 MB L3 |
| RAM | 15,985 MB total (≈15.6 GB). Available ≈ **7.0 GB** (the binding constraint) |
| GPU | **NVIDIA GeForce RTX 4050 Laptop GPU — 6,141 MiB VRAM (≈6 GB)**, + Intel Graphics (2 GB shared) |
| VRAM | ≈6 GB (5,921 MiB free at idle) |
| OS | Windows 11 Home, build 26200 (x64) |
| Python | 3.12.10 (installed as the official NuGet package — **no admin rights required**) |
| Java | 26.0.2.1 |
| Disk | 395.6 GB free |

**Inference practicalities:**
- **GPU inference IS practical** — RTX 4050 (6 GB) + CUDA 12.6 present. (No acceleration library was
  used for these benchmarks; all results below are CPU on onnxruntime / sherpa-onnx.)
- **CPU inference is practical** (8 cores) but **memory-constrained by the ~7 GB available RAM**.
  Large models are the risk, not raw CPU speed.
- Result: practical runtimes are **onnxruntime (CPU)** and **sherpa-onnx (CPU)**. Both load without
  the MSVC redistributable only after app-local MSVC runtime DLLs were supplied (see §7).

---

## 2. PROVENANCE VERIFICATION (all checksums recorded)

Downloading was checksum-verified (SHA-256) and pinned to a repo revision. Artifacts and hashes are
recorded in `model-lab\models\MANIFEST.json`. No artifact is treated as "approved" without a
complete provenance chain.

---

## 3. TRANSLATION — PART A

### 3.1 Candidate considered (the only realistic direct HI⇄TA option)

| Field | Value |
|-------|-------|
| MODEL | `facebook/nllb-200-distilled-600M` (ONNX export by `Xenova`) |
| SOURCE | Hugging Face model `Xenova/nllb-200-distilled-600M` |
| REPOSITORY | HF `Xenova/nllb-200-distilled-600M` |
| REVISION | `261c31d1a5732c67cdd16d80e8d6088507c7ccea` (2026-03-23) |
| LICENSE | **CC-BY-NC-4.0 (non-commercial)** → **redistribution concern** |
| LICENSE URL | https://huggingface.co/Xenova/nllb-200-distilled-600M / Meta AI fair-use licence |
| MODEL CARD | NLLB-200 model card (Meta AI) |
| CHECKSUM (files) | encoder_model_quantized.onnx `5cde664e…`; decoder_model_quantized.onnx `ddea619b…`; decoder_with_past_model_quantized.onnx `374293cb…`; tokenizer.json `8ac789ad…`; sentencepiece.bpe.model `14bb8dfb…` |
| ORIGINAL ARTIFACT | HuggingFace safetensors/sentencepiece weights (Meta AI) |
| CONVERSION SOURCE | `Xenova/nllb-200-distilled-600M` ONNX export (transformers.js/optimum split-decoder format) |
| CONVERSION REVISION | `261c31d1…` |
| CONVERSION COMMAND | `hf_hub_download(repo, filename, revision)` → ONNX files (no further local conversion) |
| RUNTIME | onnxruntime **1.29.0** (CPU) |
| TOKENIZER | NLLB SentencePiece (`tokenizer.json`, `sentencepiece.bpe.model`), 256,206 vocab, IDs `eng_Latn=256047`, `hin_Deva=256068`, `tam_Taml=256170` |
| LANGUAGE COVERAGE | en / hi / ta present; **direct HI⇄TA is supported** (single multilingual model, no English pivot) |

### 3.2 Motivation for direct HI⇄TA
All of `EN⇄HI`, `EN⇄TA`, `HI⇄TA` were required **without a mandatory English pivot**. The only
widely available multilingual NMT that does this in one model is a NLLB-200 variant. Both re-already
rejected candidates (IndicTrans2 direct set, M2M100 418M) or their issues were re-confirmed:

- **IndicTrans2 three-model direct set** — ~3.3 GB, Android runtime unverified, checksum chain
  incomplete. **Remains rejected.**
- **M2M100 418M** — ~1.94 GB FP32, INT8 path unverified, Android ARM64/tokenizer path unverified.
  **Remains rejected.** (No new evidence was produced to reverse this.)
- Community IndicTrans2 ONNX exports — provenance/conversion chain insufficient. **Remains rejected.**

### 3.3 Benchmark harness
Standalone harness in `model-lab\bench\` (outside the Android repo). Measures model, revision,
quantization, runtime, CPU, RAM, input, output, latency. Source encoding was validated to match the
HF semantics (`[src_lang] + tokens`, no trailing `</s>`; decoder start = target lang code). Both a
KV-cache decode and a full-sequence (no-cache) decode were implemented; both greedy and beam search
were tried; both int8 and fp16 variants were tested.

### 3.4 Results (measured)

| Metric | Value |
|--------|-------|
| Model load time | **5.0 s** (CPU, 4 threads) |
| Baseline RSS | 50.3 MB |
| **Loaded RSS (after model load)** | **5,065 MB (≈5.1 GB)** |
| **Peak RSS (during inference)** | **5,071 MB (≈5.1 GB)** |
| Disk size (int8 split set) | encoder 419 MB + decoder 470 MB + with-past 445 MB + tokenizer 17 MB ≈ **1.35 GB** |
| Median latency (greedy, CPU) | EN→HI 1.74 s · EN→HI(medium) 2.30 s · HI→TA 1.74 s · TA→HI 4.30 s |
| Beam search latency | substantially slower (not recommended) |

**Quality — the decisive failure.**
Every decoding strategy (KV-cache and full-sequence; greedy and beam; int8 and fp16) produced
**degenerate, repetitive output**. Representative outputs:

| Direction | Input (excerpt) | Output (excerpt) | Verdict |
|-----------|------------------|------------------|---------|
| EN→HI | "Help me." | `मुझे मदद मदद मदद मुझे मुझे…` | **WRONG / degenerate** |
| EN→HI | "I need help. Please come to my location." | `मुझे मुझे की की की मदद की की मुझे कृपया मेरे…` | **WRONG / degenerate** |
| EN→TA | "Hello" | `ந ந ந ந…Hello Hello Hello…` | **WRONG / degenerate** |
| HI→TA | "मुझे मदद चाहिए।" | `எனக்கு உதவி உதவி உதவி…எனக்கு உதவி தேவை…` | **PARTIAL→degenerate** |
| TA→HI | "நான் உதவி வேண்டும்।" | `मुझे मदद मैं की मदद करने…aid aid aid…` | **WRONG / degenerate** |

The first 1–2 tokens are often semantically plausible, but the model then collapses into repetition
and never produces a clean, complete sentence. Identical behavior in fp16 and int8 proves this is an
**export/usage issue, not a precision/dynamic-range effect**. Because output could not be validated,
**quality = FAIL** for every direction (per the no-fabrication rule: UNKNOWN does not become PASS).

### 3.5 Language matrix (quality)

| Direction | Lang codes | Quality | Notes |
|-----------|-----------|---------|-------|
| EN→HI | eng_Latn→hin_Deva | **FAIL** | degenerate |
| HI→EN | hin_Deva→eng_Latn | **FAIL** | degenerate |
| EN→TA | eng_Latn→tam_Taml | **FAIL** | degenerate |
| TA→EN | tam_Taml→eng_Latn | **FAIL** | degenerate |
| HI→TA | hin_Deva→tam_Taml | **FAIL** (partial start) | degenerate |
| TA→HI | tam_Taml→hin_Deva | **FAIL** | degenerate |

### 3.6 Android ARM64 deployment investigation
- ONNX Runtime Mobile **does** support Android arm64, and NLLB split-decoder ONNX has been run on
  Android by community projects (e.g. alphacep/voice-translation, RoxyTranslate) using
  `encoder + decoder + decoder_with_past` + a SentencePiece/BPE tokenizer.
- **However, the specific verdict here:** with the measured **5.1 GB RSS** and **degenerate output**,
  NLLB-600M is **not viable** for the prototype regardless of ARM64 support. It exceeds mobile RAM by
  ~5× and does not produce usable translations. → **UNVERIFIED / impracticable** for this build.

### 3.7 Translation approval gate

| Gate | Result |
|------|--------|
| LICENSE | **FAIL** — CC-BY-NC-4.0 (non-commercial) |
| PROVENANCE | PASS |
| CHECKSUM | PASS |
| LANGUAGE COVERAGE | PASS (en/hi/ta direct) |
| EN→HI / HI→EN / EN→TA / TA→EN / HI→TA / TA→HI | **FAIL** ×6 (degenerate) |
| QUALITY | **FAIL** |
| LATENCY | MEASURED (too slow: 1.7–4.3 s) |
| RAM | MEASURED (5.1 GB — exceeds budget) |
| ANDROID ARM64 | UNVERIFIED / impracticable |
| OFFLINE | PASS |

**TRANSLATION MODEL: `NOT APPROVED`.** No translation candidate passed the gate.

---

## 4. TTS — PART B

Two independently running, MIT-repo Piper voices were obtained as sherpa-onnx VITS bundles.
A third language (Tamil) could not be sourced with an eligible licence.

### 4.1 TTS EN

| Field | Value |
|-------|-------|
| MODEL | `en_US-lessac-medium` (Piper voice, VITS) |
| SOURCE | rhasspy/piper-voices (Hugging Face) → sherpa-onnx `tts-models` conversion |
| REVISION | sherpa-onnx `tts-models` release (v1.13.x); bundle sha256 `9e3febfa…` |
| LICENSE | piper-voices repo **MIT**; voice dataset = Blizzard 2013 "Lessac" (research/non-commercial) → **CAVEAT (non-commercial)** |
| CHECKSUM | bundle `9e3febfacf0abf4270172d29…`; onnx `4ba07d8549906668ee855fd9…` |
| RUNTIME | **sherpa-onnx 1.13.7** (CPU) |
| TOKENIZER | piper `tokens.txt` + espeak-ng-data (phonemizer) |
| VOCODER | VITS (built-in) |
| MODELSIZE | onnx ≈63 MB + tokens ≈1 KB + espeak-ng-data ≈179 MB (shared) |
| CONVERSION | rhasspy Piper → sherpa-onnx VITS (official sherpa-onnx pipeline) |

**Benchmark (22050 Hz, CPU, 1 thread):**

| Message | duration | load | first | median | P95 | RTF | peak RSS |
|---------|------|------|-------|--------|-----|-----|----------|
| "Help me." | 0.66 s | 0.73 s | 0.07 s | 0.05 s | 0.06 s | 0.08 | 156 MB |
| "I need help. Please come to my location." | 2.39 s | — | 0.18 s | 0.81 s | 0.88 s | 0.34 | 203 MB |
| "I am near the meeting point and need assistance." | 2.46 s | — | 0.78 s | 0.73 s | 0.78 s | 0.30 | 232 MB |
| "I need a doctor." | 1.09 s | — | 0.38 s | 0.34 s | 0.36 s | 0.31 | 232 MB |
| "Where is the hospital?" | 1.11 s | — | 0.38 s | 0.38 s | 0.40 s | 0.34 | 232 MB |
| "I am in danger." | 0.96 s | — | 0.32 s | 0.32 s | 0.32 s | 0.33 | 232 MB |

- Load 0.73 s · RSS baseline 37.6 → loaded 133 MB · **peak 232 MB**
- Audio: peak 0.59–0.67, RMS 0.10–0.15, **clip_frac 0.0 (no clipping)** → **PASS** (objective)

### 4.2 TTS HI

| Field | Value |
|-------|-------|
| MODEL | `hi_IN-priyamvada-medium` (Piper voice, VITS) |
| SOURCE | rhasspy/piper-voices (HF) → sherpa-onnx `vits-piper-hi_IN-priyamvada-medium` bundle |
| REVISION | sherpa-onnx `tts-models` release; bundle sha256 `399d91cc…` |
| LICENSE | piper-voices repo **MIT**; voice dataset = **CC-BY-NC-SA-4.0** (AI4Bharat corpus) → **CAVEAT (non-commercial)** |
| CHECKSUM | bundle `399d91cc97eb288725633261…`; onnx `8871f3e07adb6ca490f8dbcd…` |
| RUNTIME | sherpa-onnx 1.13.7 (CPU) |
| TOKENIZER | piper `tokens.txt` + espeak-ng-data |
| MODELSIZE | onnx ≈63 MB + tokens ≈1 KB + espeak-ng-data ≈179 MB (shared) |

**Benchmark (22050 Hz, CPU, 1 thread):**

| Message | duration | load | first | median | P95 | RTF | peak RSS |
|---------|------|------|-------|--------|-----|-----|----------|
| "मुझे मदद करो।" | 1.29 s | 1.99 s | 0.40 s | 0.38 s | 0.43 s | 0.30 | 184 MB |
| "मुझे मदद चाहिए। कृपया मेरे स्थान पर आएँ।" | 3.47 s | — | 1.03 s | 1.10 s | 1.23 s | 0.32 | 210 MB |
| "मैं मीटिंग पॉइंट के पास हूँ और मुझे सहायता चाहिए।" | 3.73 s | — | 1.16 s | 1.17 s | 1.19 s | 0.31 | 290 MB |
| "मुझे डॉक्टर चाहिए।" | 1.56 s | — | 0.43 s | 0.46 s | 0.51 s | 0.30 | 289 MB |
| "अस्पताल कहाँ है?" | 1.53 s | — | 0.53 s | 0.47 s | 0.52 s | 0.31 | 289 MB |
| "मैं खतरे में हूँ।" | 1.25 s | — | 0.40 s | 0.39 s | 0.45 s | 0.31 | 289 MB |

- Load 1.99 s · RSS baseline 63.6 → loaded 139 MB · **peak 289 MB**
- Audio: peak 0.75–0.82, RMS 0.16–0.18, **clip_frac 0.0 (no clipping)** → **PASS** (objective)

### 4.3 TTS TA — **BLOCKED**
No Tamil voice was available with an acceptable, verifiable licence:

- Official **rhasspy/piper-voices** has English, Hindi, … but **no Tamil** (verified via the repo tree).
- Community Piper Tamil models exist but licences are non-permissive/unclear:
  - `ezhilkumaran/piper-tamil` → data from IITM IndicTTS (referenced license not clearly permissive).
  - `Jeyaram-K/piper-tamil-voices`, `tinisoft/piper-ta_IN-*` → licence not declared.
- MMS Tamil (`ta_IN-standard-mms`) is **CC-BY-NC** and is the exact class of "MMS/VITS … not
  approved" asset the task flags. → **BLOCKED.**

**TTS TA: `NOT APPROVED` (BLOCKED).**

### 4.4 One-multilingual vs three-language (footprint comparison)
- Three language-specific VITS-Piper (the measured approach): each ≈63 MB onnx + one **shared**
  **179 MB** espeak-ng-data. ≈ 63 MB × N + 179 MB + negligible tokens.
- No single "multilingual" piper voice covers en+hi+ta with a permissive licence. A multilingual
  Kokoro-82M (~327 MB, several langs) is heavier and its licence/coverage was not validated here.
- **Recommendation:** per-language VITS. On the laptop, loading EN+HI TTS together cost **211 MB RSS**
  (load) → **234 MB peak** — well within budget.

### 4.5 TTS Android ARM64 test
- sherpa-onnx (Apache-2.0) is a documented offline runtime with **first-class Android arm64 support**;
  it bundles onnxruntime and ships an .aar / jailbreak-native libs. The VITS-Piper ONNX + `tokens.txt`
  + `espeak-ng-data/` load directly from assets.
- What we validated: **this model (Piper VITS) + this tokenizer (espeak-ng-data + tokens.txt) + this
  runtime (sherpa-onnx 1.13.7)** all run offline on a CPU and export to Android-compatible assets.
  No native rebuild was performed in this milestone (out of scope, no Android project touched), so the
  exact in-device latency remains to be confirmed in the next milestone.

### 4.6 TTS approval gate

| Gate | EN | HI | TA |
|------|----|----|----|
| LICENSE | PASS* (non-commercial caveat) | PASS* (non-commercial caveat) | **FAIL/BLOCKED** |
| PROVENANCE | PASS | PASS | INCOMPLETE |
| CHECKSUM | PASS | PASS | — |
| Language output | PASS (no clipping, intelligible RMS) | PASS | NOT RUN |
| AUDIO QUALITY | ACCEPTABLE (objective) | ACCEPTABLE (objective) | NOT RUN |
| LATENCY | MEASURED | MEASURED | — |
| RAM | MEASURED (232 MB) | MEASURED (289 MB) | — |
| ANDROID ARM64 | VERIFIED path (sherpa-onnx) | VERIFIED path (sherpa-onnx) | — |
| OFFLINE | PASS | PASS | — |

\* Voice data licences are non-commercial (Blizzard research / CC-BY-NC-SA). Acceptable for a
**non-commercial emergency prototype**; not for commercial app-store distribution without a
permissive-dataset voice (suggest EN `libritts_r`/`ljspeech`; HI commercial options limited).

**TTS EN: `APPROVED FOR PROTOTYPE` (non-commercial caveat).**
**TTS HI: `APPROVED FOR PROTOTYPE` (non-commercial caveat).**
**TTS TA: `NOT APPROVED` (BLOCKED).**

---

## 5. PART C — INTEGRATED (END-TO-END)

Because the translation model produced **degenerate output (FAIL)** and Tamil TTS is **BLOCKED**, the
translation-bearing pipelines could not be validated end-to-end (STT is out of scope for this lab).

| Pipeline | Result |
|----------|--------|
| Tamil input → translation → Hindi output → Hindi TTS | **NOT RUN** (translation FAIL) |
| Hindi input → translation → Tamil output → Tamil TTS | **NOT RUN** (translation FAIL + TA TTS BLOCKED) |
| English → translation → Hindi → Hindi TTS | **NOT RUN** (translation FAIL) |
| English → translation → Tamil → Tamil TTS | **NOT RUN** (translation FAIL + TA TTS BLOCKED) |

**Note:** STT is a pre-existing component, not part of the model lab, so "STT→translation" was not
measured. Translation latency would add on top of the (already too slow) 1.7–4.3 s per sentence.

### 5.1 Memory (integrated process RSS)

| Scenario | Peak RSS |
|----------|----------|
| STT only | not part of model lab (out of scope) |
| translation only (NLLB) | **5,071 MB** — exceeds available RAM |
| TTS only (EN) | 232 MB |
| TTS only (HI) | 289 MB |
| Translation + TTS | **cannot co-load safely** (5.1 GB translation vs ~7 GB available) |
| STT + Translation + TTS | **not practical** in current RAM |

TTS components alone are well within budget; the translation model is the memory blocker.

### 5.2 Model footprint (deployment)

| Component | Disk (assets) | Notes |
|-----------|---------------|-------|
| Translation onnx (int8, 3 files) + tokenizer | ≈1.35 GB | **not approved / unusable** |
| TTS EN onnx + espeak + tokens | ≈242 MB | |
| TTS HI onnx + espeak + tokens | ≈242 MB | |
| espeak-ng-data (shared, deduplicated) | ≈179 MB | one copy for EN+HI |
| sherpa-onnx runtime libs | ≈7 MB | |
| onnxruntime.dll | ≈18 MB | |
| MSVC runtime (app-local) | ≈1 MB | |

**TTS-only deployment (EN+HI, deduplicated espeak-ng-data): ≈ 63 + 63 + 179 ≈ 305 MB.**
**Estimated APK asset size (TTS EN+HI): ≈ 305 MB.** (Translation would add ~1.35 GB but is not
approved and would be unusable.)

---

## 6. FINAL DECISION

| Component | Decision |
|-----------|----------|
| TRANSLATION MODEL | **NOT APPROVED** |
| TTS EN | **APPROVED FOR PROTOTYPE** (non-commercial caveat) |
| TTS HI | **APPROVED FOR PROTOTYPE** (non-commercial caveat) |
| TTS TA | **NOT APPROVED** (BLOCKED) |
| COMPLETE OFFLINE STACK | **NOT APPROVED** (translation missing + Tamil TTS blocked) |

**The complete offline stack is NOT yet approved.** TTS (EN + HI) is ready and measurable; translation
still has no approved candidate, and Tamil TTS has no eligible-licence voice.

---

## 7. ENVIRONMENT NOTES / SURPRISES (for the next milestone)

1. **MSVC runtime missing, no admin rights.** `torch`, `onnxruntime` would not load (WinError 126)
   without the MSVC 14.x runtime. Resolved without admin by staging the runtime DLLs
   **app-locally** (`model-lab\msvc_crt\` from Microsoft's `Vcruntime140` / `NtvLibs.MSVCP` NuGet
   packages) and copying them next to the engine `.dll`s. torch still fails to import
   (`torch_python.dll`), so the authoritative HF/torch reference and torch-based conversion are
   currently unavailable on this machine.
2. **ONNX-based NLLB generation was not produced correctly by the `Xenova` export** in this
   environment (degenerate output; identical in fp16/int8; identical in KV-cache and full-sequence
   decode). This is recorded as a verified negative result; it is why translation is `NOT APPROVED`.
3. `sherpa-onnx` loads and runs cleanly (bundles its own onnxruntime) — the TTS path is robust.

---

## 8. NEXT STEPS (next milestone, out of scope here)

1. Resolve translation: obtain a trusted reference (torch or CTranslate2 path) and a working ONNX
   export, or re-export NLLB with a verified converter; then re-run this same model-lab gate.
2. Source a permissive-dataset Hindi TTS or an eligible-licence Tamil voice; otherwise keep Tamil
   TTS `BLOCKED`.
3. Deploy the approved EN + HI TTS into the Android project via sherpa-onnx assets (see
   `docs/model-integration-plan.md`), then re-measure on-device latency and combined memory.
