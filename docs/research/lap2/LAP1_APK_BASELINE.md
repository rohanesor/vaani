# LAP1 APK Baseline

**Created:** 2025-01-27
**Purpose:** Reference APK characteristics for post-integration comparison

## Build Environment Note

⚠️ **Current build environment has cmake path resolution issues (WSL/Windows interop)**

The LAP1 tests pass (Kotlin/Java code validated), but native APK build requires:
- Native cmake to resolve paths correctly
- Alternative: Build from Android Studio on Windows directly

## Known Working APK (From Validation Report)

From `docs/PHASE_0_5_TWO_PHONE_PHYSICAL_VALIDATION_REPORT.md`:

| Property | Value |
|----------|-------|
| **Build Commit** | `7eafdbc` (earlier than current baseline) |
| **Branch** | `sih/ui-fidelity-corrections-wip` |
| **Build Type** | Debug |
| **APK Path** | `app/build/outputs/apk/debug/app-debug.apk` |
| **APK Size** | 503,080,403 bytes (~480 MB) |
| **APK SHA-256** | `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf` |
| **Build Command** | `./gradlew assembleDebug --no-daemon` |
| **Build Result** | PASS |

## Expected APK Structure

Based on build configuration:

### Package Identity

| Property | Value |
|----------|-------|
| **Package Name** | `com.vokie` |
| **Application ID** | `com.vokie` |
| **Version Name** | `1.0.0` (default) |
| **Version Code** | `1` (default) |

### Native Libraries

| Library | ABI | Purpose |
|---------|-----|---------|
| `libvokie_whisper.so` | `arm64-v8a` | Whisper.cpp STT inference |
| `libvokie_ct2.so` | `arm64-v8a` | CTranslate2 translation (if built) |
| `libc++_shared.so` | `arm64-v8a` | C++ runtime |

### Embedded Models (Expected)

Based on APK size (~480MB), bundled models include:
- SenseVoice STT models
- VITS TTS models (13 languages)
- CT2 translation models

### Signing

| Property | Value |
|----------|-------|
| **Debug Signing** | Android debug keystore |
| **Release Signing** | Requires environment variables |

## Current Build Status

| Phase | Status |
|-------|--------|
| Unit Tests | ✅ PASS |
| Lint | ⏳ Not attempted |
| Debug APK Build | ❌ BLOCKED (cmake path issue) |
| Release APK Build | ⏳ Not attempted |

## Build Environment Requirements

To build the APK:

1. **Option A:** Build from Windows Android Studio
2. **Option B:** Configure cmake paths for WSL/Windows interop
3. **Option C:** Use a native Linux build environment

### Required External Dependencies

| Dependency | Path | Purpose |
|------------|------|---------|
| CT2 Source | `D:/vibe/ct2-4.8.2-source` | CTranslate2 headers |
| CT2 Build | `D:/vibe/ct2-android-build` | CTranslate2 static library |
| SPM Source | `D:/vibe/sentencepiece-source` | SentencePiece headers |
| SPM Build | `D:/vibe/sentencepiece-android-build` | SentencePiece static library |

All dependencies verified present.

---

**This document establishes the APK baseline reference for LAP2 integration.**

**Note:** The actual APK build verification should be performed in a working build environment before LAP2 integration begins.
