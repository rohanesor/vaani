# LAP1 Canonical APK Specification

**Created:** 2025-01-27  
**Purpose:** Define the single authoritative LAP1 APK for pipeline testing

## Canonical APK Reference

| Property | Value |
|----------|-------|
| **APK Name** | `app-debug.apk` (build artifact) |
| **Canonical Reference** | Phase 0.5 Two-Phone Validation |
| **APK Size** | 503,080,403 bytes (479.69 MB) |
| **SHA-256** | `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf` |
| **Package** | `com.vokie` |
| **Application ID** | `com.vokie` |
| **Version Code** | `1` |
| **Version Name** | `1.0.0` |
| **Build Variant** | Debug |
| **Build Commit** | `7eafdbc` |
| **Build Command** | `./gradlew assembleDebug --no-daemon` |
| **Build Date** | Phase 0.5 validation session |
| **ABI** | `arm64-v8a` |

## Signing Certificate

| Property | Value |
|----------|-------|
| **Type** | Debug keystore |
| **Issuer** | Android Debug |
| **Fingerprint** | Device/environment-specific |

Note: Debug builds use the local debug keystore. Release builds require configured signing credentials.

## Native Libraries (lib/arm64-v8a/)

| Library | Purpose | Status |
|---------|---------|--------|
| `libvokie_whisper.so` | Whisper.cpp STT JNI | ✅ Required |
| `libvokie_ct2.so` | CTranslate2 Translation JNI | ✅ Required |
| `libonnxruntime.so` | ONNX Runtime | ✅ Required |
| `libsherpa-onnx-jni.so` | Sherpa-ONNX TTS JNI | ✅ Required |
| `libsherpa-onnx-c-api.so` | Sherpa-ONNX C API | ✅ Required |
| `libc++_shared.so` | C++ Standard Library | ✅ Required |

## Bundled Models (assets/models/)

| Model | Path | Size | SHA-256 |
|-------|------|------|---------|
| Whisper Tiny Q5_1 | `stt/ggml-tiny-q5_1.bin` | 32,152,673 | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` |
| TTS English | `tts/eng/model.onnx` | 114,016,948 | `e3a198f6a4473429bab138be040e7cd40d2cab7a31b6410ff0a94d5a7fbbc254` |
| TTS Hindi | `tts/hin/model.onnx` | 63,145,178 | `8871f3e07adb6ca490f8dbcd3956a8647c53c35b5d0a1c2a8d097b3bf721a31b` |
| TTS Tamil | `tts/tam/model.onnx` | 114,032,312 | `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88` |
| TTS Gujarati | `tts/guj/model.onnx` | 114,033,848 | `59f073b2e63771dd7d7972c17577e05a903ae6f4aa8c65a6dc1d9eb1a9812ed2` |

## APK Content Verification

### Expected Contents

```
assets/
├── models/
│   ├── manifest.json
│   ├── stt/
│   │   └── ggml-tiny-q5_1.bin
│   └── tts/
│       ├── eng/{model.onnx, tokens.txt}
│       ├── hin/{model.onnx, tokens.txt}
│       ├── tam/{model.onnx, tokens.txt}
│       ├── guj/{model.onnx, tokens.txt}
│       └── espeak-ng-data/
└── map/
    └── (map tiles)

lib/
└── arm64-v8a/
    ├── libvokie_whisper.so
    ├── libvokie_ct2.so
    ├── libonnxruntime.so
    ├── libsherpa-onnx-jni.so
    ├── libsherpa-onnx-c-api.so
    └── libc++_shared.so

classes.dex (Android application code)
resources.arsc (compiled resources)
AndroidManifest.xml
res/ (UI resources)
```

### Verification Commands

```bash
# Verify SHA-256
sha256sum app-debug.apk
# Expected: 8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf

# List APK contents
unzip -l app-debug.apk | grep -E "lib/|assets/models/"

# Verify native libraries
unzip -l app-debug.apk | grep "lib/arm64-v8a"

# Verify bundled models
unzip -l app-debug.apk | grep "assets/models"
```

## Build Reproduction

### Prerequisites

1. **Android SDK** with NDK 27.0.12077973
2. **cmake** 3.22.1
3. **Java 17** (Temurin recommended)
4. **Model archive** staged via `scripts/stage-bundled-models.py`
5. **CT2/SentencePiece** native libraries (pre-built)

### Build Command

```bash
./gradlew assembleDebug --no-daemon
```

### Expected Output

```
app/build/outputs/apk/debug/app-debug.apk
```

### Size Validation

The APK MUST be approximately **480-510 MB**.

If the APK is significantly smaller (e.g., ~52 MB or ~190 MB):
- Models are not bundled
- Native libraries may be missing
- Build configuration is incorrect

## Current Build Status

| Environment | Status | Notes |
|-------------|--------|-------|
| WSL (current) | ❌ BLOCKED | cmake path resolution |
| Android Studio Windows | ✅ Expected to work | Native cmake |
| GitHub Actions CI | ✅ Works | release.yml workflow |
| Native Linux | ✅ Expected to work | Standard cmake |

## Canonical APK Storage Strategy

### Option 1: GitHub Releases (Recommended)

The release.yml workflow publishes signed APKs to GitHub Releases.

LAP2 retrieval:
1. Navigate to GitHub Releases
2. Download the appropriate release APK
3. Verify SHA-256

### Option 2: Build from Source

1. Checkout commit `7eafdbc` or later LAP1 commit
2. Stage models via `scripts/stage-bundled-models.py`
3. Build with `./gradlew assembleDebug --no-daemon`
4. Verify APK size is ~480-510 MB
5. Verify SHA-256 of staged models

### Option 3: AWS Distribution (Production)

Production releases are distributed via CloudFront:
- APK: `https://{cloudfront-domain}/downloads/v{version}/Vokie-v{version}.apk`
- Checksum: `https://{cloudfront-domain}/downloads/v{version}/Vokie-v{version}.apk.sha256`

## LAP2 Consumption Requirements

1. **SHA-256 Verification** — MUST verify APK hash before installation
2. **Size Validation** — MUST be ~480-510 MB
3. **Model Verification** — MUST contain all bundled models
4. **Native Library Verification** — MUST contain arm64-v8a libraries
5. **Package Identity** — MUST be `com.vokie`

## Explicit Rejection Criteria

The following APKs are NOT canonical:

| APK Description | Reason for Rejection |
|-----------------|---------------------|
| ~52 MB APK | Missing bundled models |
| ~190 MB APK | Incomplete model bundling |
| Any APK without native libs | Cannot perform inference |
| Any APK < 400 MB | Insufficient for bundled models |
| Release APK without proper signing | Cannot install alongside debug |

---

**CANONICAL_APK_IDENTITY = ESTABLISHED**

The canonical LAP1 APK is the ~503 MB debug build from Phase 0.5 validation with SHA-256 `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf`.
