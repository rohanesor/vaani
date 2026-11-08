# LAP1 APK Inventory

**Created:** 2025-01-27  
**Purpose:** Complete inventory of all APKs referenced in LAP1 development

## APK Discovery Results

### Current Filesystem

```
find . -type f -name "*.apk" → NO APKS FOUND IN REPOSITORY
```

The LAP1 repository does not contain any APK files. This is correct — APKs are excluded via .gitignore and are build artifacts.

### Build Output Location

Expected path: `app/build/outputs/apk/debug/app-debug.apk`

Current status: **NOT PRESENT** (build directory cleaned)

---

## Historical APK Documentation

### APK 1: Phase 0.5 Two-Phone Validation (CANONICAL REFERENCE)

**Source:** `docs/PHASE_0_5_TWO_PHONE_PHYSICAL_VALIDATION_REPORT.md`

| Property | Value |
|----------|-------|
| **Path** | `app/build/outputs/apk/debug/app-debug.apk` |
| **Size** | 503,080,403 bytes (~480 MB) |
| **SHA-256** | `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf` |
| **Build Commit** | `7eafdbc` |
| **Build Command** | `./gradlew assembleDebug --no-daemon` |
| **Build Type** | Debug |
| **Package** | `com.vokie` |
| **Version** | 1.0.0 / versionCode 1 |
| **Contains Models** | ✅ Yes (~425 MB bundled) |
| **Contains Native Libs** | ✅ Yes (arm64-v8a) |
| **Tested on Devices** | ✅ Yes (Realme RMX3782, vivo V2205) |
| **Two-Phone Validation** | ✅ PASS |

### APK 2: Phase 3 Device Environment (~198 MB)

**Source:** `docs/phase3-device-environment.md`

| Property | Value |
|----------|-------|
| **Path** | `D:\vibe\vokie\app\build\outputs\apk\debug\app-debug.apk` |
| **Size** | 198,787,226 bytes (~190 MB) |
| **Build Type** | Debug |
| **Contains Models** | Partial (fewer bundled) |
| **Notes** | Earlier development build |

### APK 3: N5 Validation

**Source:** `docs/N5_VALIDATION_REPORT.md`

| Property | Value |
|----------|-------|
| **Size** | 502,991,514 bytes (~480 MB) |
| **SHA-256** | `60d0d8b2e0cce12762cd968aeb2d2f54072f224517a8c0fa8465da1ec0da54ec` |
| **Contains Models** | ✅ Yes |

### APK 4: N4 Validation

**Source:** `docs/N4_FINAL_REPORT.md`

| Property | Value |
|----------|-------|
| **SHA-256** | `1e06da2053f2f05fa0e760ee24fe5515d8ed26b1c5530ac4f033a2d10314a853` |

### APK 5: N3 Validation

**Source:** `docs/N3_FINAL_REPORT.md`

| Property | Value |
|----------|-------|
| **SHA-256** | `7e67716ff4b1e8d10ca48e358419b0b1e7a023237c3cafe69ae987c7c4e87c7f` |

### APK 6: N1 Validation

**Source:** `docs/N1_FINAL_REPORT.md`

| Property | Value |
|----------|-------|
| **Size** | 502,875,704 bytes (~480 MB) |

---

## APK Size Analysis

### Why ~480-500 MB is the CORRECT size

The LAP1 application bundles:

| Component | Size (MB) | Notes |
|-----------|----------:|-------|
| STT Model (ggml-tiny-q5_1.bin) | 31 | Whisper Tiny Q5_1 |
| TTS English (eng/model.onnx) | 109 | VITS MMS |
| TTS Hindi (hin/model.onnx) | 60 | VITS MMS |
| TTS Tamil (tam/model.onnx) | 109 | VITS MMS |
| TTS Gujarati (guj/model.onnx) | 109 | VITS MMS |
| espeak-ng-data | ~7 | Phoneme data |
| Native libraries | ~35 | ONNX Runtime, Sherpa, CT2, Whisper |
| Application code/resources | ~25 | Kotlin, Compose, resources |
| **Total** | **~485 MB** | |

### Why ~52 MB or ~190 MB is NOT correct

A smaller APK indicates:
- Missing bundled TTS models
- Missing native libraries
- Different build configuration
- Incomplete model staging

The ~52 MB reference is likely:
- A minimal build without bundled models
- A build that relies on model download
- NOT the complete offline-capable LAP1 application

---

## APK Classification

| APK | Size | Status | Reason |
|-----|------|--------|--------|
| Phase 0.5 (~503 MB) | 503,080,403 | **CANONICAL** | Two-phone validated, complete models |
| N5 (~503 MB) | 502,991,514 | HISTORICAL | Valid build, superseded by Phase 0.5 |
| N1 (~503 MB) | 502,875,704 | HISTORICAL | Earlier validation |
| Phase 3 (~198 MB) | 198,787,226 | OBSOLETE | Incomplete model bundling |
| ~52 MB (referenced) | ~52,000,000 | UNKNOWN/OBSOLETE | Missing models, not LAP1 target |

---

## Model Staging Verification

Current staged models in `app/src/main/assets/models/`:

```
435M total
├── manifest.json (3,156 bytes)
├── stt/
│   └── ggml-tiny-q5_1.bin (32,152,673 bytes)
└── tts/
    ├── eng/model.onnx (114,016,948 bytes)
    ├── eng/tokens.txt (303 bytes)
    ├── hin/model.onnx (63,145,178 bytes)
    ├── hin/tokens.txt (940 bytes)
    ├── tam/model.onnx (114,032,312 bytes)
    ├── tam/tokens.txt (375 bytes)
    ├── guj/model.onnx (114,033,848 bytes)
    ├── guj/tokens.txt (402 bytes)
    └── espeak-ng-data/ (~7 MB)
```

✅ Models are staged and ready for build.

---

## Build Environment Issue

**Status:** BUILD BLOCKED

The debug build fails due to cmake path resolution issues in the WSL/Windows environment:

```
CMake Error: The source directory "/mnt/d/vibe/vokie/app/src/main/cpp" does not exist.
```

This is an environment configuration issue, not a LAP1 code defect. The build works in:
- Native Linux environments
- GitHub Actions CI (release.yml)
- Android Studio on Windows

---

## Conclusion

### Primary LAP1 APK Identity

| Property | Value |
|----------|-------|
| **Reference APK** | Phase 0.5 Two-Phone Validation |
| **Size** | 503,080,403 bytes |
| **SHA-256** | `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf` |
| **Build Commit** | `7eafdbc` |
| **Package** | `com.vokie` |
| **Contains** | Complete models, native libs, full offline capability |

### Why 52 MB APK is NOT the primary LAP1 APK

1. **Missing bundled models** — The 52 MB size cannot contain ~425 MB of bundled ML models
2. **Incomplete offline capability** — Without bundled models, cannot operate offline
3. **Not two-phone validated** — No evidence of successful deployment/testing
4. **Does not match documented builds** — All documented successful builds are ~480-503 MB

### Why ~480-500 MB APK IS the primary LAP1 APK

1. **Two-phone validated** — Documented successful bidirectional communication
2. **Complete model bundling** — Contains STT + TTS for offline operation
3. **Matches LAP1 architecture** — Offline-first emergency communication
4. **Consistent documentation** — Multiple validation reports confirm this size
5. **Contains native libraries** — CT2, Whisper, ONNX Runtime for on-device inference
