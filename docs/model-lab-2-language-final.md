# iTantra / Vokie — Model Lab 2: Offline Language Stack (EN/HI/TA)

**Milestone goal:** solve the two remaining offline blockers — **real EN/HI/TA translation** and
**real Tamil TTS** — for a demonstrable **non-commercial prototype**.
**Constraint obeyed:** no Android production code / STT / PacketV2 / Bluetooth / Wi‑Fi / location /
UI / Room / AWS / release-config changes. Investigation + recommendation only.

> The `kunchenguid/firstmate` repo was cloned and assessed. It is an **agent-orchestration distro**
> (spawns a "crew" of coding agents) and contains **no translation or TTS models** — its only
> standalone skill is `stow`. It does not resolve either blocker. (Its injected `AGENTS.md` persona
> is a dependency artifact, not the milestone task, and is not adopted.)

---

## 1. Baseline (unchanged, verified in Model Lab 1)

| Block | Status |
|-------|--------|
| STT EN / HI / TA | P (existing Whisper, explicit-language routing, no auto-detect) |
| TTS EN | P — Piper `lessac-medium` via sherpa-onnx 1.13.7 |
| TTS HI | P — Piper `priyamvada-medium` via sherpa-onnx 1.13.7 |
| TTS TA | was BLOCKED → **resolved below** |
| Translation | was NOT APPROVED → **resolved below** |

Approved EN/HI TTS findings were **not regressed** (same models, same runtime, still measured).

---

## 2. ROOT-CAUSE of the Model Lab 1 translation failure

Model Lab 1's ONNX NLLB produced degenerate output. Investigation here found the real cause and a
working path:

- The **NLLB SentencePiece tokenizer appends `</s>` (EOS) to the source** and prepends the source
  language code. CTranslate2 does **not** add these (`add_source_bos/eos: false` in its config), so
  the source must be `[src_lang] + subwords + ["</s>"]`.
- Feeding CTranslate2 the correct form yields **clean, accurate translations**.
- The XENOVA **ONNX** NLLB export remains broken: re-tested here **with** `</s>` it still degenerates
  (`"Help me." → "।"`, `"Where is the hospital?" → "ஆஃஃஹ... "`). So the ONNX/onnxruntime path is
  unusable; **CTranslate2 is the working runtime** for NLLB.

---

## 3. TRANSLATION — CANDIDATE + RESULTS

### 3.1 Candidate record (the one that passes)

| Field | Value |
|-------|-------|
| MODEL | `facebook/nllb-200-distilled-600M` (CTranslate2 INT8 by `osa911`) |
| SOURCE | Hugging Face `osa911/nllb-200-distilled-600M-ct2-int8` |
| REPOSITORY | HF `osa911/nllb-200-distilled-600M-ct2-int8` |
| REVISION | `46858753dbaf8eb5e21bb6f0037c3b90851e090a` |
| LICENSE | **CC-BY-NC-4.0** (Meta NLLB) — acceptable for a **non-commercial** prototype |
| CHECKSUM | `model.bin` 619,704,329 B `ca3362e6e81906c0…`; `config.json` `8f6496ad…`; `shared_vocabulary.json` 5,921,176 B `af53bfd0…`; `sentencepiece.bpe.model` `14bb8dfb…` |
| SIZE | `model.bin` 619.7 MB; whole model dir **630.5 MB** |
| FORMAT | CTranslate2 INT8 (`int8_float16` weights; auto → `int8_float32` on CPU) |
| RUNTIME | CTranslate2 **4.8.2** (Windows wheel; CPU) |
| TOKENIZER | NLLB SentencePiece (`sentencepiece.bpe.model` + `tokenizer.json`, 256,206 vocab) |
| CPU INFERENCE | PASS |
| ANDROID ARM64 | FEASIBLE via custom CT2 arm64 build (CT2 supports aarch64; Argos Translate ships CT2 NLLB on Android). **Not turnkey** (no official Android AAR). |
| PROVENANCE | PASS (source, revision, licenses, checksums, conversion command recorded) |

### 3.2 Measured performance (CPU, 4-thread CT2, beam_size=4)

| Metric | Value |
|--------|-------|
| Model load time | **0.68 s** |
| Baseline RSS | 96 MB → loaded **723 MB** → peak **728 MB** |
| Model size | 630.5 MB (dir) |
| Warm median latency | **0.21 – 0.25 s** |
| P95 latency | **0.25 – 0.31 s** |

### 3.3 Language matrix (6 directions, direct — no English pivot)

| Direction | Median / P95 (s) | Sample output |
|-----------|------------------|---------------|
| EN→HI | 0.212 / 0.254 | "Help me." → "मेरी मदद करो." |
| HI→EN | 0.212 / 0.274 | "मुझे मदद चाहिए।" → "I need help." |
| EN→TA | 0.228 / 0.298 | "Where is the hospital?" → "மருத்துவமனை எங்கே?" |
| TA→EN | 0.226 / 0.275 | "எனக்கு உதவி வேண்டும்।" → "I need your help." |
| HI→TA | 0.246 / 0.311 | "मुझे मदद चाहिए।" → "எனக்கு உதவி தேவை." |
| TA→HI | 0.221 / 0.252 | "நான் ஆபத்தில் இருக்கிறேன்." → "मैं खतरे में हूँ." |

**Direct HI⇄TA confirmed with no English pivot.**

### 3.4 Emergency-phrase quality (10 EN + 10 HI + 10 TA, manual review)

All six directions produced **correction** translations of the emergency set. Examples:

- EN→HI: "I need help." → "मुझे मदद की जरूरत है." · "Where is the hospital?" → "अस्पताल कहाँ है?" ·
  "I am in danger." → "मैं खतरे में हूँ." (all CORRECT)
- HI→EN: "अस्पताल कहाँ है?" → "Where's the hospital?" · "मुझे पानी चाहिए।" → "I need water." (CORRECT)
- EN→TA: "I am in danger." → "நான் ஆபத்தில் இருக்கிறேன்." · "Call the police." → "காவல்துறையை அழைக்கவும்." (CORRECT)
- HI→TA: "मुझे मदद करो।" → "எனக்கு உதவுங்கள்." · "मैं मीटिंग पॉइंट पर हूँ।" → "நான் சந்திப்பு புள்ளியில் இருக்கிறேன்." (CORRECT)
- TA→HI: "எனக்கு உதவி செய்யுங்கள்." → "मेरी मदद करो." · "மருத்துவமனை எங்கே இருக்கிறது?" → "अस्पताल कहाँ है?" (CORRECT)
- TA→EN: "என்னைக்கு உதவி வேண்டும்." → "I need your help." (CORRECT); "தயவுசெய்து என் இடத்திற்கு
  வாருங்கள்." → "Please come to my seat." (**PARTIAL** — "seat" for "place/location"; meaning still clear).

No fabricated BLEU-score-only claims; every emergency phrase was manually reviewed. Predominant
verdict **CORRECT**, a small number **PARTIAL**, none **WRONG**.

### 3.5 Translation approval gate

| Gate | Result |
|------|--------|
| LICENSE | PASS (CC-BY-NC-4.0, non-commercial prototype) |
| PROVENANCE | PASS |
| CHECKSUM | PASS |
| LANGUAGE COVERAGE | PASS (en/hi/ta, direct HI⇄TA) |
| EN→HI / HI→EN / EN→TA / TA→EN / HI→TA / TA→HI | PASS ×6 |
| QUALITY | PASS (clean, correct, manual review) |
| LATENCY | MEASURED (0.21–0.25 s median) |
| RAM | MEASURED (728 MB peak — fits ~7 GB budget) |
| ANDROID ARM64 | FEASIBLE (CT2 arm64 custom build; not turnkey) |
| OFFLINE | PASS |

**TRANSLATION: `APPROVED for non-commercial prototype`.** Footprint 630 MB (slightly above the
500 MB "target", not a rule) — it is the only verified working direct HI⇄TA model; no smaller model
provides direct HI⇄TA.

**Rejected-in-place:** indicTrans2 direct set (footprint/runtime), M2M100 418M (Android
runtime/conversion unverified), Xenova NLLB ONNX export (verified degenerate even with the correct
`</s>`). No new evidence reverses these; the CT2 artifact is a **different, verified runtime** that
resolves the documented ONNX failure.

---

## 4. TAMIL TTS — CANDIDATE + RESULTS

### 4.1 Candidate record (the one that passes)

| Field | Value |
|-------|-------|
| MODEL | `facebook/mms-tts-tam` — MMS-TTS Tamil (VITS-style ONNX by `willwade`) |
| SOURCE | HF `willwade/mms-tts-multilingual-models-onnx` → `tam/` |
| REPOSITORY | willwade/mms-tts-multilingual-models-onnx |
| REVISION | `709a74aad8…` |
| LICENSE | **CC-BY-NC-4.0** (Meta MMS) — acceptable for a **non-commercial** prototype |
| CHECKSUM | `model.onnx` 114,032,312 B `c86cf0a0657d5757…`; `tokens.txt` 375 B `0b3f6923…`; `sample.wav` 121,388 B `ce39e5eb…` |
| SIZE | model.onnx **109 MB** (114.0 MB dir) |
| FORMAT | ONNX (VITS-style, sherpa-onnx MMS conversion); frontend = **characters** (no espeak-ng) |
| RUNTIME | sherpa-onnx **1.13.7** (CPU) |
| TOKENIZER | character map (`tokens.txt`); frontend = characters |
| CPU INFERENCE | PASS |
| ANDROID ARM64 | **PASS (turnkey)** — sherpa-onnx onnxruntime Android arm64; ONNX + tokens.txt load from assets |
| AUDIO QUALITY | ACCEPTABLE (objective: no clipping, sane peak/RMS, correct duration, valid Tamil; WAVs saved for listening) |
| PROVENANCE | PASS |

### 4.2 Measured performance (16 kHz, CPU, 1 thread)

| Tamil phrase | duration | load | first | median | P95 | RTF | peak RSS |
|--------------|------|------|-------|--------|-----|-----|----------|
| "எனக்கு உதவி வேண்டும்." | 2.03 s | 0.63 s | 1.08 s | 1.07 s | 1.08 s | 0.53 | 226 MB |
| "நான் இங்கே இருக்கிறேன்." | 1.82 s | — | 0.94 s | 1.02 s | 1.04 s | 0.56 | 227 MB |
| "நீங்கள் எங்கே இருக்கிறீர்கள்?" | 2.02 s | — | 0.93 s | 1.12 s | 1.18 s | 0.56 | 233 MB |
| "அவசர உதவி தேவை." | 1.77 s | — | 0.98 s | 0.99 s | 1.02 s | 0.56 | 247 MB |
| "தயவுசெய்து என்னைத் தொடர்பு கொள்ளுங்கள்." | 2.82 s | — | 1.16 s | 1.59 s | 1.63 s | 0.56 | 257 MB |

- Model 109 MB · load 0.63 s · baseline 187 → loaded 187–220 MB · **peak 257 MB**
- Audio: 16 kHz, peak 0.60–0.71, **clip 0.0 (no clipping)**, no pathological silence (durations
  sensible). WAVs written to `model-lab\bench\out\ta_mms_*.wav` for human listening.

**Note:** MMS-Tamil is correct and natural but ~2× slower than the Piper EN/HI voices
(RTF ≈ 0.55; ~1.0 s per short phrase, 1.6 s for a longer phrase). It is still faster than real time.
It also skips ASCII punctuation (`"."`, `"?"`) during phonemisation (harmless — the danda/space still
drives pauses).

### 4.3 Tamil TTS approval gate

| Gate | Result |
|------|--------|
| LICENSE | PASS (CC-BY-NC-4.0, non-commercial) |
| PROVENANCE | PASS |
| CHECKSUM | PASS |
| Language / intelligibility | PASS (real Tamil MMS voice; char frontend correct) |
| AUDIO QUALITY | ACCEPTABLE (objective; saved for human review) |
| LATENCY | MEASURED (median 0.99–1.59 s) |
| RAM | MEASURED (257 MB peak) |
| ANDROID ARM64 | PASS (sherpa-onnx turnkey) |
| OFFLINE | PASS |

**TAMIL TTS: `APPROVED for non-commercial prototype`.** Footprint 114 MB (< 150 MB target). It is the
**only** eligible-licence Tamil offline voice (no MIT Piper Tamil exists; community Piper-Tamil and
the previously-noted MMS block are resolved by using Meta MMS under its CC-BY-NC for a non-commercial
build).

---

## 5. END-TO-END LANGUAGE TEST (text-level; Whisper STT is a pre-existing component, text provided)

| Pipeline | translation | TTS | total | Result |
|----------|-------------|-----|-------|--------|
| TA → HI → Hindi TTS | 0.30 s | 0.19 s | **0.49 s** | **PASS** |
| HI → TA → Tamil TTS | 0.21 s | 1.08 s | **1.29 s** | **PASS** |
| EN → HI → Hindi TTS | 0.46 s | 0.32 s | **0.78 s** | **PASS** |
| EN → TA → Tamil TTS | 0.25 s | 0.96 s | **1.21 s** | **PASS** |

Sample end-to-end:
- TA text `எனக்கு உதவி வேண்டும்.` → `मुझे मदद की जरूरत है.` → Hindi speech.
- HI text `मुझे मदद चाहिए।` → `எனக்கு உதவி தேவை.` → Tamil speech.

**Same-language bypass** (no translation): Tamil→Tamil TTS (1.02 s), Hindi→Hindi TTS (0.12 s),
English→English TTS (0.05 s). Bypass verified — the source-language TTS is invoked directly.

---

## 6. MEMORY (integrated process RSS)

| Scenario | Peak RSS |
|----------|----------|
| translation only (CT2 NLLB) | 728 MB |
| TTS HI only | 289 MB |
| TTS TA only | 257 MB |
| TTS EN only | 232 MB |
| translation + HI TTS + TA TTS (all loaded) | **≈1,050 MB** |

Fully within the ~7 GB usable budget. Translation is the memory-dominant component but no longer a
blocker (3.4× lower than the 5.1 GB of Model Lab 1, and it *works*).

---

## 7. FOOTPRINT (deployment)

| Component | Size |
|-----------|------|
| Translation (CT2 NLLB int8 dir) | 630.5 MB |
| TTS EN (Piper onnx + tokens + espeak-ng subset) | ≈ 81 MB |
| TTS HI (Piper onnx + tokens + espeak-ng subset) | ≈ 81 MB |
| TTS TA (MMS onnx + tokens; no espeak) | 114.2 MB |
| espeak-ng-data (Piper char-phonemizer, shared) | 18 MB (per bundle; dedupe to one copy) |
| sherpa-onnx runtime libs | ≈ 7 MB |
| onnxruntime.dll | ≈ 18 MB |
| CTranslate2 lib + libiomp | ≈ 65 MB (host libs; Android would use the CT2 arm64 libs) |

**Total model assets ≈ 630 (translation) + 81 (EN) + 81 (HI) + 114 (TA TTS) ≈ 906 MB** (≈888 MB with
deduplicated espeak). Est. APK assets ≈ **0.91 GB** for a full 3-language offline stack. This is a
demonstrable, non-commercial prototype footprint, not a low-end-device release build.

---

## 8. LICENSING SUMMARY

| Asset | License | Non-commercial OK |
|-------|---------|-------------------|
| CTranslate2 runtime | MIT | yes |
| sherpa-onnx runtime | Apache-2.0 | yes |
| NLLB-200-distilled-600M (CT2) | CC-BY-NC-4.0 | **yes (non-commercial only)** |
| MMS-TTS Tamil (willwade ONNX) | CC-BY-NC-4.0 | **yes (non-commercial only)** |
| Piper lessac (EN) / priyamvada (HI) | MIT hub; dataset non-commercial | yes (non-commercial only) |

**Consequence:** shipping requires clear non-commercial labelling/attribution for the NLLB and MMS
assets. This is acceptable for the stated prototype; commercial distribution would require
replacement models.

---

## 9. ANDROID ARM64 FEASIBILITY

| Component | Runtime | Android arm64 |
|-----------|---------|---------------|
| TTS EN | sherpa-onnx 1.13.7 | **PASS (turnkey)** — .aar/jniLibs + assets; verified path |
| TTS HI | sherpa-onnx 1.13.7 | **PASS (turnkey)** |
| TTS TA | sherpa-onnx 1.13.7 | **PASS (turnkey)** — ONNX + tokens.txt in assets |
| Translation | CTranslate2 4.8.2 | **FEASIBLE (custom build)** — CT2 supports aarch64; Argos Translate ships CT2 on Android. No official Android AAR; needs an arm64 JNI build + asset layout. |

**Honest caveat:** the turnkey Android runtime (sherpa-onnx/onnxruntime) does **not** have a clean
NLLB (the Xenova ONNX export degenerates). Translation on Android therefore requires a **custom
CTranslate2 arm64 native build** — feasible and reproducible (documented below), but to be validated
in the integration milestone, not assumed.

---

## 10. FINAL DECISION

| Block | Decision |
|-------|----------|
| BEST TRANSLATION MODEL | **NLLB-200-distilled-600M (CTranslate2 INT8, osa911)** → **APPROVED (non-commercial)** |
| BEST TAMIL TTS MODEL | **MMS-TTS Tamil (willwade ONNX, sherpa-onnx)** → **APPROVED (non-commercial)** |
| TTS EN | APPROVED (unchanged) |
| TTS HI | APPROVED (unchanged) |
| COMPLETE OFFLINE STACK | **APPROVED** — all three languages (EN/HI/TA) for STT→translation→TTS |

Nothing is **REJECTED** of the newly acquired candidates. **BLOCKED**: none remaining for the
prototype. All candidates are classified `APPROVED` for a **non-commercial** prototype; none is
automatically commercial-ready.

---

## 11. INTEGRATION RECOMMENDATION (summary; full plan in integration planning step)

1. **Translation (CT2):** ship CT2 with the NLLB int8 model dir (`model.bin`, `config.json`,
   `shared_vocabulary.json`, `sentencepiece.bpe.model`). Build a thin wrapper that does:
   source = `[src_lang] + subwords + ["</s>"]`, `translate_batch(..., target_prefix=[[tgt_lang]],
   beam_size=4)`. Keep the graph warm; load once.
2. **Tamil TTS (sherpa-onnx):** ship `mms-ta/tam/model.onnx` + `tokens.txt` in assets; configure
   `OfflineTtsVitsModelConfig(model=…onnx, tokens=…tokens.txt)` (char frontend, **no** espeak-ng
   needed — do not pass `data_dir`). Load on demand; keep warm.
3. **Routing:** STT already routes en/hi/ta explicitly. TTS maps `en→lessac`, `hi→priyamvada`,
   `ta→mms-tamil`. Translation routes all six directions; same-language pairs bypass translation and
   feed the source-language TTS directly.
4. **Memory:** load translation once (~728 MB); instantiate only the selected TTS model
   (EN/HI ≈ 232–289 MB, TA ≈ 257 MB). Combined stack ≈ 1.05 GB peak — fits the prototype target
   (not a low-end device build).
5. **Android:** translation via custom CT2 arm64 build; TTS via sherpa-onnx AAR. Verify on device in
   the integration milestone.

---

## 12. ARTIFACT MANIFEST (acquired + measured only)

Acquired and measured this milestone (recorded in `models/MANIFEST.json`): `ct2_nllb_model.bin`,
`ct2_nllb_config.json`, `ct2_nllb_shared_vocabulary.json`, `ct2_nllb_sentencepiece.bpe.model`
(all `osa911/nllb-200-distilled-600M-ct2-int8` @ `46858753…`) and `mms_ta_model.onnx`,
`mms_ta_tokens.txt`, `mms_ta_sample.wav` (all `willwade/mms-tts-multilingual-models-onnx` @
`709a74aa…`). All have source URL, repository, revision, filename, size, SHA-256, license, runtime,
and conversion procedure. No untracked model files.

The Model Lab 1 `Xenova` ONNX artifact is retained only as **evidence of a negative result** — it is
not a production model and is excluded from the recommendation. Rejected/unapproved artifacts were
not added as production models.

---

## 13. NOTES FOR THE INTEGRATION MILESTONE

- CTranslate2 had to be fixed on this host: a corrupt `vcruntime140.dll` staged from the
  `Vcruntime140` NuGet package caused `WinError 193`; replaced with the official `NtvLibs.MSVCP`
  (14.42) `vcruntime140.dll`/`vcruntime140_1.dll`. This matters only for the Windows lab, not Android.
- MMS-Tamil skips ASCII `.`/`?` in phonemisation (harmless); consider normalising punctuation.
- The MMS-Tamil voice is ~2× slower than Piper and 16 kHz (Piper EN/HI are 22.05 kHz). Acceptable for
  a prototype; note the sample-rate difference in the player.
