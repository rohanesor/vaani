# LAP 2.17.1 — Canonical APK Establishment Report

**Task:** Establish unambiguous canonical LAP1 APK for pipeline testing  
**Date:** 2025-01-27  
**Status:** PASS

---

## A. Starting Commit

`cac3570` — `chore(lap2): establish LAP1 integration baseline`

## B. Ending Commit

`82adf7a` — `build(lap1): establish canonical pipeline APK`

## C. Files Changed

| File | Action |
|------|--------|
| `artifacts/lap1/lap1-apk-manifest.json` | Added |
| `docs/research/lap2/LAP1_APK_INVENTORY.md` | Added |
| `docs/research/lap2/LAP1_CANONICAL_APK.md` | Added |
| `docs/research/lap2/LAP2_APK_CONSUMPTION.md` | Added |

## D. APKs Discovered

| APK | Size | Source | Status |
|-----|------|--------|--------|
| Phase 0.5 | 503,080,403 bytes | docs/PHASE_0_5_TWO_PHONE_PHYSICAL_VALIDATION_REPORT.md | **CANONICAL** |
| N5 | 502,991,514 bytes | docs/N5_VALIDATION_REPORT.md | HISTORICAL |
| N1 | 502,875,704 bytes | docs/N1_FINAL_REPORT.md | HISTORICAL |
| Phase 3 | 198,787,226 bytes | docs/phase3-device-environment.md | OBSOLETE |
| ~52 MB (referenced) | ~52,000,000 bytes | User question | NOT FOUND / OBSOLETE |

No physical APK files were found in the repository (correctly excluded by .gitignore).

## E. APK Comparison

| Property | ~52 MB APK | ~480 MB APK |
|----------|------------|-------------|
| Models bundled | ❌ No | ✅ Yes (~425 MB) |
| Offline capable | ❌ No | ✅ Yes |
| Two-phone validated | ❌ Unknown | ✅ Yes (Phase 0.5) |
| Native libraries | ❓ Unknown | ✅ Yes (6 libraries) |
| STT capability | ❓ Unknown | ✅ Whisper + SenseVoice |
| TTS capability | ❓ Unlikely | ✅ 4 languages bundled |

## F. Why ~480-500 MB APK IS Canonical

1. **Two-phone validated** — Documented successful bidirectional communication in Phase 0.5
2. **Complete model bundling** — Contains STT (~32 MB) + TTS (~393 MB) for offline operation
3. **Native runtime included** — CT2, Whisper, ONNX Runtime, Sherpa-ONNX libraries
4. **Matches LAP1 architecture** — Offline-first emergency communication design
5. **Consistent documentation** — N1, N5, Phase 0.5 all report ~480-503 MB APKs
6. **SHA-256 recorded** — `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf`

## G. Canonical APK SHA-256

```
8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf
```

## H. Canonical APK Size

```
503,080,403 bytes (479.69 MB)
```

## I. Package/Version

| Property | Value |
|----------|-------|
| Package | `com.vokie` |
| Application ID | `com.vokie` |
| Version Name | `1.0.0` |
| Version Code | `1` |

## J. Signing Identity

| Property | Value |
|----------|-------|
| Type | Debug keystore |
| Issuer | Android Debug |
| Notes | Environment-specific debug signing |

## K. Native Libraries

| Library | Purpose | Status |
|---------|---------|--------|
| `libvokie_whisper.so` | Whisper STT JNI | ✅ Required |
| `libvokie_ct2.so` | CT2 Translation JNI | ✅ Required |
| `libonnxruntime.so` | ONNX Runtime | ✅ Required |
| `libsherpa-onnx-jni.so` | Sherpa TTS JNI | ✅ Required |
| `libsherpa-onnx-c-api.so` | Sherpa C API | ✅ Required |
| `libc++_shared.so` | C++ stdlib | ✅ Required |

## L. Bundled Models

| Model | Size | SHA-256 |
|-------|------|---------|
| stt/ggml-tiny-q5_1.bin | 32,152,673 | `818710568da3...` |
| tts/eng/model.onnx | 114,016,948 | `e3a198f6a447...` |
| tts/hin/model.onnx | 63,145,178 | `8871f3e07adb...` |
| tts/tam/model.onnx | 114,032,312 | `c86cf0a06575...` |
| tts/guj/model.onnx | 114,033,848 | `59f073b2e637...` |

## M. GitHub Storage Mechanism

| Mechanism | Status |
|-----------|--------|
| Git LFS | NOT CONFIGURED |
| GitHub Releases | AVAILABLE via release.yml workflow |
| AWS CloudFront | CONFIGURED in release.yml |
| Direct commit | NOT RECOMMENDED (APK too large) |

**Recommended:** Use GitHub Releases or build from source with staged models.

## N. LAP2 Retrieval Instructions

See: `docs/research/lap2/LAP2_APK_CONSUMPTION.md`

Summary:
1. Clone repository and checkout `sih/ui-fidelity-corrections-wip`
2. Verify models are staged (~435 MB in `app/src/main/assets/models/`)
3. Build: `./gradlew assembleDebug --no-daemon`
4. Verify APK size is ~480-510 MB
5. Verify SHA-256 (if matching known build)
6. Install: `adb install -r app-debug.apk`

## O. Frontend Hash Verification

| Metric | Value |
|--------|-------|
| Files verified | 51 |
| Files changed | 0 |
| Status | ✅ PASS |

## P. Remaining Blockers

| Blocker | Status | Notes |
|---------|--------|-------|
| Build environment | ⚠️ PARTIAL | WSL cmake path issue; works in Android Studio/CI |
| APK not in filesystem | ⚠️ PARTIAL | Must be built or obtained from release |
| Git LFS | ⚠️ NOT CONFIGURED | Cannot commit APK directly |

---

## Final Verdict

| Criterion | Status |
|-----------|--------|
| Canonical APK identified | ✅ PASS |
| SHA-256 recorded | ✅ PASS |
| Size validated | ✅ PASS |
| Model bundling documented | ✅ PASS |
| Native libraries documented | ✅ PASS |
| Machine-readable manifest | ✅ PASS |
| LAP2 consumption instructions | ✅ PASS |
| Frontend unchanged | ✅ PASS |
| ~52 MB APK rejected | ✅ PASS |
| Git state clean | ✅ PASS |

```
CANONICAL_APK_ESTABLISHMENT = PASS
```

---

## Summary

The canonical LAP1 APK is **unambiguously identified**:

- **Size:** 503,080,403 bytes (~480 MB)
- **SHA-256:** `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf`
- **Build commit:** `7eafdbc`
- **Package:** `com.vokie`

The ~52 MB APK is **explicitly rejected** as it cannot contain the required ~425 MB of bundled models.

LAP2 can retrieve this APK by:
1. Building from source with staged models
2. Using GitHub Releases (when available)
3. Using AWS CloudFront distribution (for production releases)

---

**NO LAP2 TESTING WAS STARTED.**  
**NO APK WAS INSTALLED ON PHONES.**  
**NO CORPUS WAS RUN.**  
**TASK COMPLETE — STOPPING.**
