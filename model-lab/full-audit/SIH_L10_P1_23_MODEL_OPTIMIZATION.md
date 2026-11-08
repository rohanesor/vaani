# SIH-L10-P1.23 — Model Size + Receiver Latency Optimization

## Final decision

**P1.23 — NO SAFE OPTIMIZATION FOUND**

Inventory was completed. Physical optimization was not run because the connected device was not running the validated APK and had elevated thermal status. No production change was made.

## Safety checkpoint

- Starting branch: `sih/laptop1-c1-c2-integration`
- Starting SHA: `dde6efe33a53ff7d9760190c2b624e229ffe32e4`
- Checkpoint branch: `sih/laptop1-before-p1-23`
- Existing untracked WAVs, models, logs, and artifacts were preserved.
- No production model was overwritten.

## Device preflight

| Field | Result |
|---|---|
| ADB | 1.0.41 / platform-tools 37.0.0-14910828 |
| Device | Realme RMX3782, `MJPVXCSG9HYL65YL` |
| Android | API 35 |
| ABI | arm64-v8a |
| Authorization | authorized |
| Validated APK SHA | `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9` |
| Observed APK SHA | `c9396e991031b16097170e90ecf248b742bf35cf5cbf1f853ffec1c98f87119f` |
| Thermal status | 2 |
| Skin temperature | 43.621°C |

The observed APK is a debug/profiling APK, not the validated production APK. Therefore no physical latency or quality result is admissible. Thermal status was already elevated after the prior blocked runs; no experiment was started.

## Baseline inventory

| Model | Runtime | Current size | Precision | Candidate | Loading / footprint |
|---|---|---:|---|---|---|
| SenseVoice-Small `model.int8.onnx` | sherpa-onnx OfflineRecognizer | 239,233,841 B | INT8 | lower precision | Lazy SenseVoice load; validated STT |
| SenseVoice `tokens.txt` | sherpa-onnx | 315,894 B | text | none | Required tokenizer |
| NLLB CT2 `model.bin` | CTranslate2 Android JNI 4.8.2 | 619,704,329 B | INT8 | INT4/lower precision | One translator session |
| CT2 config + SentencePiece + vocabulary | CT2/SentencePiece | 10,753,453 B | metadata/tokenizer | removal/minimization | Runtime-required files |
| Hindi MMS/VITS `model.onnx` + tokens | sherpa-onnx OfflineTts | 63,146,118 B | current approved ONNX | supported lower precision | One lazy TTS engine/session |
| English TTS pack | sherpa-onnx | 114,017,251 B | current approved ONNX | removal/quantization | Bundled/extracted lazily |
| Tamil TTS pack | sherpa-onnx | 114,032,687 B | current approved ONNX | removal/quantization | Bundled/extracted lazily |
| Gujarati TTS pack | sherpa-onnx | 114,034,250 B | current approved ONNX | removal/quantization | Bundled/extracted lazily |
| espeak-ng-data | sherpa-onnx phonemizer | 19,266,560 B | phonemizer data | pruning | Shared by voices |

The APK asset manifest contains additional candidate language metadata, but `BundledModelStore.BUNDLED_FILES` currently packages EN/HI/TA/Gujarati production packs. Telugu/Kannada work was not started.

## Footprint measurements

Observed on-device inventory, not a production-size claim:

- Debug APK file: 506,604,221 B
- Debug APK SHA: `c9396e991031b16097170e90ecf248b742bf35cf5cbf1f853ffec1c98f87119f`
- Installed app data: 1,297,625 KiB
- CT2 data: 616,323 KiB
- Extracted model data: 680,607 KiB

Deployment accounting remains:

`install APK size + downloaded/extracted model size = total deployment footprint`.

## Candidate review

### SenseVoice

SenseVoice is already the validated INT8 artifact. No redundant production artifact or qualified lower-precision runtime/model pair was identified. **Retained.**

### CT2

The CT2 model and all tokenizer/vocabulary files are runtime-required. INT4 or pruning was not promoted because no qualified Android runtime evidence exists, and prior CT2 architecture work found no safe replacement. **Retained.**

### TTS

Hindi is the validated warm synthesis path (~190 ms). No exact-model, officially supported lower-precision sherpa-onnx artifact was available for physical validation. Removing bundled languages would alter supported behavior and was not performed. The one-engine/session and one-worker architecture was preserved. **Retained.**

### Pipeline

No changes were made to tokenizer reuse, native sessions, PCM handling, queueing, duplicate prevention, or playback. No parallel workers were introduced.

## Measurements

No cold/warm P50/P95, STT, CT2, TTS, T5→T8, PSS, native-heap, voice-quality, or crash dataset was collected. No desktop measurement was substituted. No latency improvement is claimed.

## Regression and promotion

- Production source change: none
- Model change: none
- APK promotion: none
- Physical EN→HI loop: not run because APK SHA did not match validated SHA
- T0–T8 ordering/queue/duplicate validation: not run
- Crashes/ANRs: no experiment was started

## Final state

The validated production configuration remains the fallback:

- SenseVoice-Small INT8
- CT2 NLLB distilled 600M INT8
- Hindi sherpa-onnx TTS
- EN→HI direct routing
- short-text beam 1
- one translation session
- one TTS engine/session
- one queue worker
- offline execution

No Telugu/Kannada integration was started.

**Final commit SHA:** 2ae8072421956f187223ffe42e9533219fc20097
