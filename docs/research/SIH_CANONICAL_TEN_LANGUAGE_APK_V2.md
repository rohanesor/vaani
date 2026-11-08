# SIH LAP1 Canonical Ten-Language Test APK v2

## Status

This document establishes the existing, independently verified APK as the **new canonical ten-language test artifact**. No rebuild, installation, or runtime test was performed for this re-baselining phase.

## 1. Previous artifact abandonment

The former expected artifact (`6db754dde7248a2cb88db3e094d278210f7ebcb71159b8ef821fe1f6b0cbeda8`) was not recoverable. It existed only in documentation; no legitimate local APK with that SHA was found. Its byte identity with this artifact is not established.

- Previous SHA: `6db754dde7248a2cb88db3e094d278210f7ebcb71159b8ef821fe1f6b0cbeda8`
- Recovery result: `BLOCKED`
- Previous artifact: `UNRECOVERABLE`

## 2. New artifact identity

- Artifact: `iTANTRA LAP1 Canonical Ten-Language Test APK — v2`
- Path: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `7a947cdb39974f8148cb9d32ef9431d367af926ffbc9693acc2532b781bb7c06`
- Size: `1,187,389,113` bytes
- Package: `com.vokie`
- Version: `1.0.0`
- versionCode: `1`
- ABI: `arm64-v8a`
- Source commit at artifact creation: `7589e0faaa360cd52add664af9468e15b4398e1a`
- Build variant/flag: debug, `-PbundleAllTtsModels=true`
- Filesystem timestamp: `2026-09-12T20:19:15.050467700Z`; embedded build timestamp was not independently established

The APK was not rebuilt merely for preservation. It remains outside Git because of its size; the exact path and SHA above are the preservation requirement.

## 3. APK content verification

`APK_CONTENT_VERIFIED=PASS` for the exact SHA above. Independent ZIP inspection confirmed the following ten model/token pairs:

| Language | Model path / bytes / SHA-256 | Token path / bytes / SHA-256 | Status |
|---|---|---|---|
| English | `assets/models/tts/eng/model.onnx` / 114,016,948 / `e3a198f6a4473429bab138be040e7cd40d2cab7a31b6410ff0a94d5a7fbbc254` | `eng/tokens.txt` / 303 / `dff08580748be688d9112d62d6352422c56d372dfe34b24ea3f66fa1b75cfaa9` | RESEARCH / UNAPPROVED |
| Hindi | `hin/model.onnx` / 63,145,178 / `8871f3e07adb6ca490f8dbcd3956a8647c53c35b5d0a1c2a8d097b3bf721a31b` | `hin/tokens.txt` / 940 / `ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d` | RESEARCH / UNAPPROVED |
| Tamil | `tam/model.onnx` / 114,032,312 / `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88` | `tam/tokens.txt` / 375 / `0b3f692319bb5fae8658e2f84bf252bca92450d0207bbba7273caa1a182d81b8` | RESEARCH / UNAPPROVED |
| Gujarati | `guj/model.onnx` / 114,033,848 / `59f073b2e63771dd7d7972c17577e05a903ae6f4aa8c65a6dc1d9eb1a9812ed2` | `guj/tokens.txt` / 402 / `2d855f2affb7586cc6be095a4382eb0bee2a22242deda32c86aab6b1a810d8c4` | RESEARCH / UNAPPROVED |
| Marathi | `mar/model.onnx` / 114,043,832 / `03021bb722e8a19a44be6bb327a441c4061a0c75cbd565f3455e64c84713f03a` | `mar/tokens.txt` / 490 / `4d968029d0754b41633cb0871cce6796a5ab3d3bc2b9b91c5721cfdf85156083` | RESEARCH / UNAPPROVED |
| Kannada | `kan/model.onnx` / 114,045,368 / `8b6f313ebfdf423991ab6444a4520a72cc506a5b262a5d0c0c467ed7d8555834` | `kan/tokens.txt` / 487 / `a4a44037f7492c9e5b69a4483250e54af9a2d528bd4e19de95a21ff0c5e82a8a` | RESEARCH / UNAPPROVED |
| Malayalam | `mal/model.onnx` / 114,052,280 / `13965d4eeed2f5198c53689e192ce7095e167fb11dd0e72ae5cd8a3bf117374b` | `mal/tokens.txt` / 571 / `3a752c36593cc519193c8caa6c00371369b423366fa6ead8d91945a28b2c46a3` | RESEARCH / UNAPPROVED |
| Telugu | `tel/model.onnx` / 114,037,688 / `e82525ab1e662ba96ef81346ff1943c5bb731853c1f96d02fbff2e7a0284824c` | `tel/tokens.txt` / 435 / `528152cb4121272e6e71f7a1d91c8d4922b92fae414ae599db608cb92a738bb7` | RESEARCH / UNAPPROVED |
| Odia | `ory/model.onnx` / 114,046,136 / `a90e1a48d4aa404c12504f47a89bbe310110a9d43dd2e093346f539efc8728a1` | `ory/tokens.txt` / 495 / `bc90e5423446ca730aef6b56a4656cb07421c7538429a50b9c396fe0758fd587` | RESEARCH / UNAPPROVED |
| Bengali | `ben/model.onnx` / 114,044,600 / `d16d6ea228104876499a8b09b3417e6de0aca91e1ff04d23f5a3fc2f6ddd3ad7` | `ben/tokens.txt` / 480 / `a74190ec42b8f0afb349276430c716f1b585090d8fe6d3c1d6d5fe510798a387` | RESEARCH / UNAPPROVED |

The model bytes match the existing ten-language packaging audit. Token checksums above are from the exact APK contents. Content presence does not establish licensing, provenance, quality, or runtime success.

## 4. STT and native inventory

- STT: `assets/models/stt/ggml-tiny-q5_1.bin`, 32,152,673 bytes, SHA-256 `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7`
- `lib/arm64-v8a/libvokie_whisper.so`, 1,337,912 bytes
- `lib/arm64-v8a/libvokie_ct2.so`, 3,922,672 bytes
- `lib/arm64-v8a/libonnxruntime.so`, 21,684,872 bytes
- `lib/arm64-v8a/libsherpa-onnx-jni.so`, 4,761,536 bytes
- `lib/arm64-v8a/libsherpa-onnx-c-api.so`, 4,456,784 bytes
- `lib/arm64-v8a/libsherpa-onnx-cxx-api.so`, 440,760 bytes
- `lib/arm64-v8a/libc++_shared.so`, 1,292,896 bytes

CT2 model weights are intentionally not bundled; the architecture expects runtime staging under app-private `files/ct2/nllb600m`.

## 5. Licensing/provenance

All ten TTS artifacts remain `RESEARCH / UNAPPROVED`. Provenance, conversion history, and redistribution authorization are incomplete. No approval or licensing metadata was changed.

## 6. Device-validation status and limitations

No device validation was performed in this re-baselining phase. Earlier smoke evidence was collected using a temporary derived debug build and must not be represented as exact-v2 runtime evidence. Odia TTS availability does not provide Odia STT availability or accuracy evidence; `ODIA_STT=RUNTIME_UNAVAILABLE` remains applicable.

Byte reproducibility from the old artifact is not established:

`REPRODUCIBLE_FROM_OLD_ARTIFACT=NOT PASS`

## 7. Next validation phase

Install this exact v2 APK, independently recheck its SHA on the host, and then begin the separately authorized runtime sequence: ten-language TTS runtime, STT runtime, offline CT2 translation, single-phone pipeline, two-phone E2E, and only afterward the 500-sample corpus and accuracy/resource measurements.
