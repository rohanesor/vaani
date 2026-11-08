# SIH 10-Language APK Content

**APK:** `app/build/outputs/apk/debug/app-debug.apk`  
**Build:** `-PbundleAllTtsModels=true`  
**Status:** FINAL TEST CANDIDATE (packaging readiness only)

| Property | Value |
|---|---|
| Size | 1,187,125,680 bytes |
| SHA-256 | `6db754dde7248a2cb88db3e094d278210f7ebcb71159b8ef821fe1f6b0cbeda8` |
| Package | `com.vokie` |
| Version | `1.0.0` / code `1` |
| ABI | `arm64-v8a` |
| Source commit before this phase | `1c057e4` |

## Native Runtime

Verified directly in the APK: `libvokie_ct2.so`, `libvokie_whisper.so`, `libonnxruntime.so`, `libsherpa-onnx-jni.so`, `libsherpa-onnx-c-api.so`, `libsherpa-onnx-cxx-api.so`, and `libc++_shared.so` under `lib/arm64-v8a/`.

## Model Contents

| Language | Path | Size bytes | APK |
|---|---|---:|---|
| English | `assets/models/tts/eng/model.onnx` | 114016948 | PASS |
| Hindi | `assets/models/tts/hin/model.onnx` | 63145178 | PASS |
| Tamil | `assets/models/tts/tam/model.onnx` | 114032312 | PASS |
| Gujarati | `assets/models/tts/guj/model.onnx` | 114033848 | PASS |
| Marathi | `assets/models/tts/mar/model.onnx` | 114043832 | PASS |
| Kannada | `assets/models/tts/kan/model.onnx` | 114045368 | PASS |
| Malayalam | `assets/models/tts/mal/model.onnx` | 114052280 | PASS |
| Telugu | `assets/models/tts/tel/model.onnx` | 114037688 | PASS |
| Odia | `assets/models/tts/ory/model.onnx` | 114046136 | PASS |
| Bengali | `assets/models/tts/ben/model.onnx` | 114044600 | PASS |

All ten token files and the STT model are also present and manifest-verified. Total bundled model payload: 1,121,655,841 bytes.

## Translation

`libvokie_ct2.so` is present. The NLLB/CT2 model is intentionally runtime-staged and is **not** an APK asset. CT2 model runtime availability is not claimed by this packaging audit.

## Licensing and Approval

All ten local TTS artifacts remain `RESEARCH / UNAPPROVED`. No approval flag was changed. See `docs/mms-provenance-audit.md`; exact conversion/token provenance and redistribution authorization are incomplete.

## Direct Verification

```bash
python3 scripts/validate-10-language-packaging.py app/build/outputs/apk/debug/app-debug.apk
```

Result: `TEN_LANGUAGE_TTS_PACKAGING=PASS`.

This means byte-level APK packaging passed, not Android synthesis/runtime or speech-quality validation.
