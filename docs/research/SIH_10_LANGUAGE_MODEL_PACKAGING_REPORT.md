# SIH 10-Language Model Packaging Report

## A. Starting Commit

`1c057e49cc2c7d3cbf256be6bde77539e29c8036`

## B. Ending Commit

The commit containing this report; verify with `git rev-parse HEAD`.

## C. Root Cause of Four-Language Packaging

The staging script and `BundledModelStore` hard-coded the same four-pack `BUNDLED_FILES` set: English, Hindi, Tamil, and Gujarati. The manifest and TTS enum already covered ten languages. The six artifacts were therefore intentionally left as downloadable/manifest-only files, not excluded by Gradle.

## D–H. Ten-Language Audit

All ten ONNX/token pairs exist locally and match the checked-in manifest. All ten TTS routes exist. All ten are now packaged by an explicit research-only opt-in build. All ten remain unapproved for distribution because provenance/conversion authorization is incomplete; no approval flags were changed.

See `docs/research/TTS_10_LANGUAGE_PACKAGING_AUDIT.md`.

| Language | STT Route | Translation | TTS Artifact | TTS Packaged | TTS Runtime | Approval | Overall |
|---|---|---|---|---|---|---|---|
| English | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Hindi | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Tamil | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Gujarati | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Marathi | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Kannada | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Malayalam | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Telugu | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Odia | route exists; prior STT limitation | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |
| Bengali | route exists | tag exists; model runtime not tested | exists | PASS | NOT VERIFIED | RESEARCH_ONLY | RESEARCH_ONLY |

## I–K. APK

- Size: `1,187,125,680` bytes
- SHA-256: `6db754dde7248a2cb88db3e094d278210f7ebcb71159b8ef821fe1f6b0cbeda8`
- Package: `com.vokie`
- Version: `1.0.0`, versionCode `1`
- Variant: debug, `-PbundleAllTtsModels=true`
- Direct APK model verifier: `TEN_LANGUAGE_TTS_PACKAGING=PASS`
- Native libraries: CT2, Whisper, ONNX Runtime, Sherpa-ONNX, libc++ shared
- STT model: bundled and present
- CT2 translation model: runtime-staged, not bundled

## L. Runtime Route Verification

Static route/catalog verification: all ten `TtsLanguage` entries resolve to their ISO-639-3 directories. Android model initialization and synthesis were **NOT TESTED** in this phase.

## M. Tests

| Test | Result |
|---|---|
| `./gradlew clean testDebugUnitTest --no-daemon` | PASS |
| `./gradlew lint --no-daemon` | PASS |
| `assembleDebug -PbundleAllTtsModels=true` | PASS |
| `python3 scripts/validate-10-language-packaging.py ...` | PASS |
| Full corpus | NOT TESTED |
| Two-phone E2E | NOT TESTED |
| WER/CER | NOT TESTED |

## N. Frontend Protection

Existing frontend hash verification completed with all protected files matching. No frontend source was intentionally modified.

## O. Remaining Blockers

1. All ten TTS artifacts remain research/unapproved for distribution.
2. Android runtime initialization/synthesis for all ten languages remains unverified.
3. CT2 model must be staged on devices before translation testing.
4. APK size is approximately 1.19 GB; it is not committed to Git and must be transferred via a suitable artifact mechanism.
5. Odia APK availability does not validate genuine Odia STT accuracy; the corpus audio is Hindi fallback.

## Final Verdict

```text
TEN_LANGUAGE_TTS_PACKAGING = PASS
TEN_LANGUAGE_TTS_RUNTIME = NOT TESTED
MODEL_DISTRIBUTION_APPROVAL = BLOCKED
PIPELINE_TESTING = NOT TESTED
```

The packaging configuration is ready for a controlled research build covering all ten local artifacts. This is not a production/distribution approval and no pipeline testing was started.
