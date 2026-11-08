# SIH-L9-P2 — Ten-Language TTS Artifact Acquisition and Immutable Provenance Gate

## Scope and method

Read-only evidence gate. Sources inspected: APK model manifest, `TtsLanguage`, `TtsModelManager`, `ModelDownloadManager`, existing Hindi/Tamil Android validation records, and prior model-lab report. No artifact was downloaded, added, replaced, or integrated.

## Critical gate finding

`app/src/main/assets/models/manifest.json` contains verified model/token hashes for `eng`, `hin`, `guj`, `mar`, `kan`, `mal`, `tam`, `tel`, `ory`, and `ben`. This is **integrity evidence**, not complete provenance/licensing evidence. Actual `TtsLanguage` and `TtsModelManager` support only EN/HI/TA. Seven staged asset entries are not selectable by production TTS code.

The manifest and prior English report disagree: manifest `tts/eng/model.onnx` is 114,016,948 bytes, while the prior Piper English report described an approximately 63 MB ONNX with a different checksum. This is a provenance mismatch. English cannot be approved until the staged asset is reconciled to an immutable source record.

## Current asset matrix

| Language | Manifest model bytes / SHA recorded | Runtime route | Provenance/license evidence | Gate result |
|---|---|---|---|---|
| EN | 114,016,948 / `e3a198...` | sherpa VITS architecture; not proven this exact asset | conflicting prior Piper record; source/revision/license unresolved | **BLOCKED** |
| HI | 63,145,178 / `8871f3...` | sherpa-onnx 1.13.7, Android ARM64 | Piper `hi_IN-priyamvada-medium`; bundle and token hashes recorded; dataset CC-BY-NC-SA-4.0 | **CONDITIONAL: non-commercial prototype only** |
| GU | 114,033,848 / `59f073...` | no production language route | source/revision/license/frontend record absent | **NEEDS EVIDENCE** |
| MR | 114,043,832 / `03021b...` | no production language route | source/revision/license/frontend record absent | **NEEDS EVIDENCE** |
| KN | 114,045,368 / `8b6f31...` | no production language route | source/revision/license/frontend record absent | **NEEDS EVIDENCE** |
| ML | 114,052,280 / `13965d...` | no production language route | source/revision/license/frontend record absent | **NEEDS EVIDENCE** |
| TA | 114,032,312 / `c86cf...` | sherpa-onnx 1.13.7 Android ARM64 physical path | `willwade/mms-tts-multilingual-models-onnx@709a74...`; MMS licensing record requires non-commercial gate | **CONDITIONAL/BLOCKED for general deployment** |
| TE | 114,037,688 / `e82525...` | no production language route | source/revision/license/frontend record absent | **NEEDS EVIDENCE** |
| OR | 114,046,136 / `a90e1a...` | no production language route | source/revision/license/frontend record absent | **NEEDS EVIDENCE** |
| BN | 114,044,600 / `d16d6e...` | no production language route | source/revision/license/frontend record absent | **NEEDS EVIDENCE** |

All displayed hashes are abbreviated only in this human report; full SHA-256 values remain in the immutable APK manifest.

## Verified Hindi evidence

- Model: Piper `hi_IN-priyamvada-medium`, VITS.
- Runtime: official sherpa-onnx 1.13.7, CPU/ARM64.
- ONNX: 63,145,178 bytes; full SHA in `docs/android-hindi-tts-integration.md` and manifest.
- Physical Android evidence: model load, 22,050 Hz synthesis, playback, 10/20 repeated cycles, offline local assets, PSS measurements.
- Measured RTF around 0.376 for the recorded first utterance; no formal human listening/intelligibility sign-off in that record.
- License: model hub code MIT is not sufficient; recorded voice dataset CC-BY-NC-SA-4.0 means non-commercial prototype decision is required.

## Verified Tamil evidence

- Model: MMS Tamil VITS ONNX from `willwade/mms-tts-multilingual-models-onnx@709a74aad80a840eb57f767a7f5d155aaad1ac7f`.
- Runtime: official sherpa-onnx 1.13.7, CPU Android ARM64.
- Model: 114,032,312 bytes; full SHA in manifest/documentation.
- Physical Android evidence: offline synthesis/playback and 10/20 cycles; recorded RTF approximately 1.12–1.34; one speaker listening review.
- License/prototype disposition: prior records flag MMS-style non-commercial restrictions. This is not general redistributable approval.

## Android/runtime gate

`sherpa-onnx` VITS runtime has existing Android ARM64 evidence for HI/TA. This does **not** establish that GU/MR/KN/ML/TE/OR/BN staged ONNX files have a compatible frontend, sample rate, quality, or legal provenance. `TtsLanguage` currently selects only EN/HI/TA.

## Offline gate finding

Runtime synthesis uses local staged assets. However, `ModelDownloadManager` contains `HttpURLConnection`/`MODEL_CDN_BASE_URL` language-pack delivery and asks for Wi-Fi. It is not used by runtime synthesis but is incompatible with calling the full acquisition workflow “fully offline.” Final SIH deployment must bundle/pre-stage approved assets or remove/disable that delivery path in a separately authorized implementation phase.

## Ranking and first approval decision

1. **Hindi Piper — conditional first prototype candidate.** Strongest exact artifact/runtime/device evidence, but non-commercial dataset condition must be accepted explicitly for the intended SIH prototype.
2. **Tamil MMS ONNX — conditional second candidate.** Strong device evidence but licensing restriction blocks general deployment.
3. **English staged asset — blocked.** Manifest/report provenance mismatch.
4. **GU/MR/KN/ML/TE/OR/BN staged assets — needs evidence.** Hash alone is insufficient.

**No artifact receives unconditional APPROVED status in L9-P2.** The integrator must not integrate a new voice until its full source/revision/license/frontend record passes.

## Required next evidence acquisition

For each staged asset, obtain an immutable source manifest: repository URL, commit/revision, model and token/frontend checksums, architecture, sample rate, weight/voice/dataset licenses, redistribution analysis, Android sherpa compatibility proof, and a language-specific listening/device protocol. Reconcile English first, then the seven unproven staged assets.
