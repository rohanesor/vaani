# iTantra / Vokie — Model Integration Plan (Android)

**Status (updated after Model Lab 2):** All four blocks now pass the gate for a **non-commercial**
prototype:
- **TTS EN** — Piper `lessac-medium` (sherpa-onnx 1.13.7)
- **TTS HI** — Piper `priyamvada-medium` (sherpa-onnx 1.13.7)
- **TTS TA** — **MMS-TTS Tamil** (`willwade` ONNX, sherpa-onnx; CC-BY-NC-4.0)
- **Translation** — **NLLB-200-distilled-600M** (CTranslate2 4.8.2 INT8; CC-BY-NC-4.0), direct
  EN⇄HI / EN⇄TA / HI⇄TA with no English pivot.

The sections below were originally written for the TTS EN/HI pair; the **translation** and **Tamil
TTS** additions are described in the addendum at the end. See the measure dbenchmark evidence in
`docs/model-lab-2-language-final.md`.

> This is a *plan* only. **No Android production code was modified** in the model-lab milestone.

---

## 1. Selected models

| Role | Model | Runtime |
|------|-------|---------|
| TTS EN | `en_US-lessac-medium` (Piper VITS) | sherpa-onnx **1.13.7** |
| TTS HI | `hi_IN-priyamvada-medium` (Piper VITS) | sherpa-onnx **1.13.7** |

Both are single-file VITS ONNX + phone tokenizer (espeak-ng based). They do not use a separate
vocoder.

---

## 2. Exact files

| File | Purpose |
|------|---------|
| `en_US-lessac-medium.onnx` | TTS EN model (VITS) |
| `hi_IN-priyamvada-medium.onnx` | TTS HI model (VITS) |
| `en_US-lessac-medium.onnx.json` | piper config (metadata reference — optional sidecar) |
| `hi_IN-priyamvada-medium.onnx.json` | piper config (metadata reference — optional sidecar) |
| `tokens.txt` (EN) | phone→id mapping for English model |
| `tokens.txt` (HI) | phone→id mapping for Hindi model |
| `espeak-ng-data/` | shared phonemizer data (language-independent; **one copy**) |

---

## 3. Exact versions

- sherpa-onnx: **1.13.7** (Python bindings verified; Android AAR / native `sherpa-onnx-*-android`).
- onnxruntime (bundled inside sherpa-onnx): verified present in `sherpa_onnx\lib\onnxruntime.dll`
  (1.27.x line compatible with the sherpa-onnx 1.13.7 build).
- Piper source: `rhasspy/piper-voices` voice models.

---

## 4. Exact checksums

| File | SHA-256 |
|------|---------|
| `en_US-lessac-medium.onnx` | `4ba07d8549906668ee855fd9abf9faf66c5db74742712ff026a159f7277fca9f` |
| `hi_IN-priyamvada-medium.onnx` | `8871f3e07adb6ca490f8dbcd3956a8647c53c35b5d0a1c2a8d097b3bf721a31b` |
| `vits-piper-en_US-lessac-medium.tar.bz2` (download) | `9e3febfacf0abf4270172d29…` |

Re-verify the onnx files by SHA-256 before baking them into assets.

---

## 5. License & attribution (required to ship)

- sherpa-onnx runtime: **Apache-2.0** (attribution in NOTICE/About).
- Piper voice model data:
  - EN `lessac`: piper-voices hub/repo **MIT**, voice dataset Blizzard-2013 “Lessac”
    (**non-commercial / research**).
  - HI `priyamvada`: piper-voices hub/repo **MIT**, voice dataset **CC-BY-NC-SA-4.0**
    (AI4Bharat/indicnlp corpus).
- **Consequence:** this stack is suitable for a **non-commercial emergency prototype** and must be
  clearly labelled (example: “Voice data © respective dataset licences, non-commercial”). For
  commercial distribution, replace EN with a permissive-dataset voice (e.g. `libritts_r` CC-BY-4.0,
  `ljspeech` public-domain) and re-source a Hindi/Tamil voice with a permissive licence.

---

## 6. Runtime / configuration

sherpa-onnx `OfflineTtsConfig` for each locale:

```
OfflineTtsConfig(
  model = OfflineTtsModelConfig(
    vits = OfflineTtsVitsModelConfig(
      model   = "assets/tts/<locale>/<model>.onnx",
      tokens  = "assets/tts/<locale>/tokens.txt",
      data_dir = "assets/tts/espeak-ng-data"     # shared; NOT per-locale
    ),
    num_threads = 1,
    provider    = "cpu",
    debug       = 0
  ),
  max_num_sentences = 1
)
```

- `num_threads = 1` recommended for low idle memory; use 2–4 only if the device has cores to spare.
- `data_dir` points to the **single shared** `espeak-ng-data`, never duplicated per language.
- Generate with `GenerationConfig(sid=0, speed=1.0, silence_scale=0.2)`.

---

## 7. Android ABI / native requirements

- ABIs: **arm64-v8a** (primary), optionally armeabi-v7a and x86_64 for emulators.
- Native libraries (from sherpa-onnx Android release, `sherpa-onnx-v1.13.7-android`):
  - `libsherpa-onnx-c-api.so` (per-ABI)
  - `libsherpa-onnx-cxx-api.so` (per-ABI)
  - `libonnxruntime.so` (per-ABI, bundled)
- No other native dependencies required (sherpa-onnx bundles onnxruntime).
- MSVC runtime is **not** needed on Android (the native libs are self-contained); it was only a
  Windows host-side issue.

---

## 8. Gradle / build requirements

- Add the sherpa-onnx AAR or jniLibs to the app: e.g. `app/libs/sherpa-onnx-1.13.7.aar`, or unpack
  the per-ABI `.so` into `app/src/main/jniLibs/<abi>/`.
- `ndk.abiFilters += listOf("arm64-v8a")` (add armeabi-v7a/x86_64 only if needed).
- `assets` located under `app/src/main/assets/tts/…` (kept as raw assets; do NOT compress the `.onnx`
  `.so` — use `android { aaptOptions { noCompress "onnx" "so" } }`).
- `minSdk`: sherpa-onnx supports a broad range; set ≥ 26 to match the VT-translate reference apps, but
  verify with the chosen release.

---

## 9. Asset layout

```
app/src/main/assets/
  tts/
    en/
      en_US-lessac-medium.onnx
      en_US-lessac-medium.onnx.json      (optional)
      tokens.txt
    hi/
      hi_IN-priyamvada-medium.onnx
      hi_IN-priyamvada-medium.onnx.json  (optional)
      tokens.txt
    espeak-ng-data/                      (ONE shared copy)
      lang/, *_dict, phondata, phonindex, phontab, voices/...
```

Total assets (EN+HI, shared espeak): **≈ 305 MB**.

---

## 10. Initialization sequence

1. App start.
2. Determine required UI locale (`en` or `hi`) — default `en`.
3. **Load only that locale's TTS model + the shared espeak-ng-data** into a single `OfflineTts`.
4. Keep runtime warm; reuse the instance for all synthesis (avoid reload/null).
5. On locale switch, dispose the old `OfflineTts` and load the new one; do **not** hold both if RAM is
   constrained (measured: EN 232 MB, HI 289 MB, **both 234 MB** combined — co-loading is acceptable,
   but lazy per-locale is still preferred for cold-start and correctness).
6. On incoming text → `tts.generate(text, GenerationConfig(speed=1.0))` → play PCM via AudioTrack.

**Warm/persistence note:** do not re-create the `OfflineTts` per message. Rewarming is the biggest
avoidable cost (EN load 0.73 s, HI load 1.99 s).

---

## 11. Memory strategy

| Scenario (laptop measurement) | Peak RSS |
|-------------------------------|----------|
| EN TTS only | 232 MB |
| HI TTS only | 289 MB |
| EN+HI simultaneously | 234 MB |

- Load-on-demand, keep warm, release on locale switch.
- If STT + TTS must coexist, keep models in separate native contexts and prefer single-thread
  inference to lower peak.
- Do **not** attempt to load the (rejected) translation model — it measured 5.1 GB RSS and would not
  fit. Revisit only after a working translation artifact is approved.

---

## 12. Routing

### 12.1 Language routing (translation)
Not applicable — no translation model is approved. Until an approved translation artifact exists,
TTS receives text from the existing pipeline unchanged; there is no translation step to route.

### 12.2 TTS routing
| Intended speak language | Loaded TTS model | asset path |
|-------------------------|------------------|------------|
| English | `en_US-lessac-medium` | `assets/tts/en/en_US-lessac-medium.onnx` + `tokens.txt` |
| Hindi | `hi_IN-priyamvada-medium` | `assets/tts/hi/hi_IN-priyamvada-medium.onnx` + `tokens.txt` |
| Tamil | — (NO eligible model) | **blocked** |

Map UI/ASR locale → model handle; if `ta` is requested, fall back to the closest supported locale or
silence until a Tamil voice is licensed, per the no-fabrication rule (a Tamil model with an unclear
licence must not be shipped).

---

## 13. Verification checklist before shipping

- [ ] ONNX SHA-256 matches §4.
- [ ] Model loads from assets with a single shared `espeak-ng-data`.
- [ ] EN and HI each synthesize the emergency phrase set without clipping (peak < 1.0, peak RSS < 320 MB).
- [ ] Device test for arm64-v8a; confirm on-device median/P95 ≈ laptop RTF (< 0.35).
- [ ] No re-instantiation per message; warm-instance reuse verified.
- [ ] Licence attribution/NOTICE shipped; non-commercial label present.

---

## 14. Explicit non-goals (this milestone)

- No Android production file was changed.
- No STT / PacketV2 / Bluetooth / Wi‑Fi Direct / location / UI / Room / AWS / release-config change.

---

## 15. ADDENDUM (Model Lab 2) — Translation + Tamil TTS

### 15.1 Translation (CTranslate2 NLLB INT8)
- **Runtime:** CTranslate2 **4.8.2**. **Model dir** (assets):
  `model.bin` (619.7 MB), `config.json`, `shared_vocabulary.json`, `sentencepiece.bpe.model`
  (whole dir ≈630 MB). Source `osa911/nllb-200-distilled-600M-ct2-int8` @ `46858753…`.
- **Call sequence**
  ```
  source = [src_lang] + subwords   + ["</s>"]         # CRITICAL: trailing </s> required
  out    = translator.translate_batch([source],
             target_prefix=[[tgt_lang]], beam_size=4, max_decoding_length=256)
  text   = decode(out.hypotheses[0][1:])               # drop the tgt_lang prefix
  ```
  lang codes: `en=eng_Latn`, `hi=hin_Deva`, `ta=tam_Taml`. Keep the `Translator` warm; load once.
- **Android ARM64:** requires a **custom CTranslate2 arm64 build** (JNI). CT2 supports aarch64;
  Argos Translate ships CT2 on Android as precedent. Not a turnkey AAR — verify in the integration
  milestone. (The turnkey onnxruntime/sherpa-onnx path does NOT produce correct NLLB here.)
- **Memory:** ~728 MB peak (on this lab). Latency median 0.21–0.25 s.

### 15.2 Tamil TTS (MMS-TTS Tamil via sherpa-onnx)
- **Runtime:** sherpa-onnx **1.13.7**. **Model dir** (assets): `model.onnx` (109 MB)+ `tokens.txt`
  (char frontend). Source `willwade/mms-tts-multilingual-models-onnx/tam` @ `709a74aa…`.
- **Config**
  ```
  OfflineTtsVitsModelConfig(model = "assets/tts/ta/model.onnx",
                            tokens = "assets/tts/ta/tokens.txt")   # NO data_dir (char frontend, no espeak)
  ```
- **Note:** 16 kHz output (Piper EN/HI are 22.05 kHz); ~2× slower than Piper (RTF ≈0.55, ~1.0 s per
  short phrase). ASCII `.`/`?` are skipped during phonemisation (harmless).

### 15.3 Routing (updated)
| Source text lang | Translate to | Speak with |
|------------------|--------------|------------|
| en | hi / ta (or bypass) | lessac (en) / priyamvada (hi) / mms-tamil (ta) |
| hi | en / ta (or bypass) | priyamvada (hi) / lessac (en) / mms-tamil (ta) |
| ta | en / hi (or bypass) | mms-tamil (ta) / lessac (en) / priyamvada (hi) |

Same-language pairs bypass translation and feed the source-language TTS directly.

### 15.4 Licences (non-commercial only)
CTranslate2 = MIT; sherpa-onnx = Apache-2.0; **NLLB-200 and MMS-TTS = CC-BY-NC-4.0** (non-commercial).
Ship clear non-commercial attribution/NOTICE.
