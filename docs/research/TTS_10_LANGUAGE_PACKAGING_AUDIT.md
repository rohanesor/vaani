# TTS 10-Language Packaging Audit

## Root Cause

The APK contained four TTS model packs because packaging was intentionally restricted in two places:

1. `scripts/stage-bundled-models.py` defined `BUNDLED` as the STT model plus `eng`, `hin`, `tam`, and `guj`.
2. `BundledModelStore.kt` defined `BUNDLED_FILES` with the same four TTS languages.

The manifest already described all ten packs, and `TtsLanguage` already routed all ten languages. Thus route/catalog presence was incorrectly being conflated with APK packaging.

The six missing packs were not excluded by Gradle or `res/raw`; they were not copied into `app/src/main/assets/models` by the staging policy.

## Evidence Table

| Language | Artifact | Bytes | Tokens | Local SHA verified | Runtime route | APK in opt-in build | Approval |
|---|---|---:|---|---|---|---|---|
| English (`eng`) | `model.onnx` | 114,016,948 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Hindi (`hin`) | `model.onnx` | 63,145,178 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Tamil (`tam`) | `model.onnx` | 114,032,312 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Gujarati (`guj`) | `model.onnx` | 114,033,848 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Marathi (`mar`) | `model.onnx` | 114,043,832 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Kannada (`kan`) | `model.onnx` | 114,045,368 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Malayalam (`mal`) | `model.onnx` | 114,052,280 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Telugu (`tel`) | `model.onnx` | 114,037,688 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Odia (`ory`) | `model.onnx` | 114,046,136 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |
| Bengali (`ben`) | `model.onnx` | 114,044,600 | yes | yes | yes | yes | RESEARCH / UNAPPROVED |

All local values are from `app/src/main/assets/models/manifest.json` and the recovered local staging directory. Local existence and checksum do not prove provenance, conversion history, license, or quality.

## Distinct Statuses

- **LANGUAGE ROUTE EXISTS:** all ten entries exist in `TtsLanguage`.
- **MODEL ARTIFACT EXISTS LOCALLY:** all ten model/token pairs exist in the local protected staging workspace.
- **MODEL IS PACKAGED:** all ten are present in the opt-in APK built for this phase.
- **MODEL IS RUNTIME VERIFIED:** not yet tested on Android; only APK byte/hash verification is complete.
- **APPROVED FOR DISTRIBUTION:** no; `docs/mms-provenance-audit.md` records incomplete conversion/token provenance and CC-BY-NC-4.0 candidate model licensing.

## Safe Packaging Change

Added a research-only Gradle property:

```bash
-PbundleAllTtsModels=true
```

When enabled, `BuildConfig.BUNDLE_ALL_TTS_MODELS` causes `BundledModelStore` to extract all ten model/token pairs. The default remains the four-pack base policy. `scripts/stage-bundled-models.py` supports `VOKIE_BUNDLE_ALL_TTS=1`, and the APK verifier supports `VOKIE_VERIFY_ALL_TTS=1`.

No `approved` metadata was changed. No frontend code was changed.

## Classification

| Language set | Classification | Reason |
|---|---|---|
| All ten | AVAILABLE_RESEARCH_ONLY | Files and manifest hashes exist; provenance/redistribution approval is incomplete |
| All ten in opt-in APK | READY_TO_PACKAGE for controlled research build | Packaging and extraction verified byte-for-byte |
| All ten for public/production distribution | BLOCKED | Existing provenance audit does not authorize distribution |
| All ten Android synthesis runtime | RUNTIME_UNVERIFIED | Lightweight device runtime initialization was not run in this phase |

## Odia

Odia TTS artifact availability is independent of Odia STT. The corpus's Odia audio uses Hindi TTS fallback and cannot establish genuine Odia STT accuracy. No Odia accuracy claim is made here.
