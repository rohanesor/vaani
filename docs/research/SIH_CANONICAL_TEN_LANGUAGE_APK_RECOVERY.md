# LAP1 Canonical Ten-Language APK Recovery

## Required artifact

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- Required SHA-256: `6db754dde7248a2cb88db3e094d278210f7ebcb71159b8ef821fe1f6b0cbeda8`
- Expected size: `1,187,125,680` bytes
- Package: `com.vokie`
- Version: `1.0.0`, versionCode `1`
- Source commit recorded by packaging reports: `049b2b159922dd4ffdd245c443cb2321ed950366`

## Current artifact

- Current path: `app/build/outputs/apk/debug/app-debug.apk`
- Current SHA-256: `7a947cdb39974f8148cb9d32ef9431d367af926ffbc9693acc2532b781bb7c06`
- Current size: `1,187,125,680` bytes
- Result: **NOT PASS**; it is not the required canonical artifact and was not substituted.

## Search performed

The following legitimate local/project locations were searched:

- Repository files and Git-tracked metadata
- `artifacts/`
- `app/build/` outputs and intermediates
- `/mnt/d/vibe/` project workspace
- Local temporary/build locations under `/tmp`
- Local Gradle cache and Android-related local cache locations where accessible

The required SHA was present in reports including `SIH_10_LANGUAGE_MODEL_PACKAGING_REPORT.md`, `SIH_10_LANGUAGE_APK_CONTENT.md`, `FINAL_TEST_APK.md`, and related manifests. No APK file with that SHA was found. No preserved APK of the expected size was found.

## Recovery result

`CANONICAL_TEN_LANGUAGE_APK_RECOVERED=BLOCKED`

The exact APK binary is missing locally. The current output was not replaced, modified, or relabeled.

## Input/reproducibility assessment

The source commit and principal build inputs are present or documented:

- Branch: `sih/ui-fidelity-corrections-wip`
- Commit: `049b2b1` (full hash above)
- Gradle wrapper: Gradle `8.9`
- Android Gradle Plugin: declared through the version catalog/build configuration
- Compile/target SDK: `34`
- JDK target: Java/Kotlin `17`
- NDK: `27.0.12077973`
- CMake: `3.22.1`
- Build flag: `-PbundleAllTtsModels=true`
- ABI: `arm64-v8a`
- Model checksums and packaging contents: documented and locally staged

However, byte-identical reproducibility is **NOT ESTABLISHED**. The exact prior APK binary, complete build-environment capture, signing/debug-keystore state, and relevant APK timestamp/build-metadata provenance are not preserved as a verified reproducibility bundle. A same-source rebuild must not be called canonical unless its SHA exactly matches the required value.

`REPRODUCIBLE_FROM_SOURCE=NOT PASS`

## Content verification

The current APK was intentionally not treated as canonical. Therefore canonical content verification is not claimed:

`APK_CONTENT_VERIFIED=BLOCKED`

The prior packaging audit records all ten TTS ONNX/token pairs and the required STT/native components for the required SHA, but those records do not recover the binary.

## Next validation step

Obtain the exact APK from the original protected build/artifact location or another legitimate preserved copy. Then independently verify its SHA, package/version, ABI, ten TTS pairs, STT model, CT2 native library, Whisper native library, ONNX Runtime, and Sherpa-ONNX components. Stop after artifact verification; do not rerun runtime, corpus, or E2E testing in this recovery phase.
