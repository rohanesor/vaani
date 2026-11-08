# LAP2 APK Consumption Instructions

**Purpose:** Instructions for LAP2 to obtain and verify the canonical LAP1 APK

---

## 1. Canonical APK Identity

| Property | Value |
|----------|-------|
| **Package** | `com.vokie` |
| **Version** | 1.0.0 (versionCode 1) |
| **Size** | 503,080,403 bytes (~480 MB) |
| **SHA-256** | `8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf` |
| **Build Commit** | `7eafdbc` |
| **Manifest** | `artifacts/lap1/lap1-apk-manifest.json` |

---

## 2. APK Retrieval Options

### Option A: Build from Source (Recommended)

This ensures you have the exact LAP1 application with all bundled models.

```bash
# 1. Clone and checkout
git clone https://github.com/rohanesor/vokie.git
cd vokie
git checkout sih/ui-fidelity-corrections-wip

# 2. Verify models are staged
ls -la app/src/main/assets/models/
# Should show ~435 MB of models

# 3. Build
./gradlew assembleDebug --no-daemon

# 4. Verify output
ls -la app/build/outputs/apk/debug/app-debug.apk
# MUST be approximately 480-510 MB

# 5. Verify SHA-256 (if matching known build)
sha256sum app/build/outputs/apk/debug/app-debug.apk
```

### Option B: GitHub Release

If a tagged release exists:

1. Navigate to: https://github.com/rohanesor/vokie/releases
2. Download the APK for the appropriate version
3. Verify SHA-256 against the release checksum file

### Option C: AWS CloudFront Distribution

For production releases (if deployed):

```bash
# Download APK
curl -O https://{cloudfront-domain}/downloads/v{version}/Vokie-v{version}.apk

# Download checksum
curl -O https://{cloudfront-domain}/downloads/v{version}/Vokie-v{version}.apk.sha256

# Verify
sha256sum -c Vokie-v{version}.apk.sha256
```

---

## 3. SHA-256 Verification

### Linux/macOS/WSL

```bash
sha256sum app-debug.apk
```

Expected output:
```
8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf  app-debug.apk
```

### Windows PowerShell

```powershell
(Get-FileHash -Algorithm SHA256 app-debug.apk).Hash.ToLower()
```

Expected output:
```
8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf
```

### Verification Script

```bash
#!/bin/bash
set -euo pipefail

APK_PATH="$1"
EXPECTED_SHA="8b04aa459df750c1f34e130028dd1427de7ee4999163afa1c82a948167f0f3cf"
MIN_SIZE=400000000
MAX_SIZE=600000000

# Check file exists
if [[ ! -f "$APK_PATH" ]]; then
    echo "ERROR: APK not found: $APK_PATH"
    exit 1
fi

# Check size
SIZE=$(stat -c%s "$APK_PATH" 2>/dev/null || stat -f%z "$APK_PATH")
if [[ $SIZE -lt $MIN_SIZE || $SIZE -gt $MAX_SIZE ]]; then
    echo "ERROR: APK size $SIZE is outside expected range [$MIN_SIZE, $MAX_SIZE]"
    echo "This may not be the canonical LAP1 APK with bundled models."
    exit 1
fi

# Check SHA-256
ACTUAL_SHA=$(sha256sum "$APK_PATH" | awk '{print $1}')
if [[ "$ACTUAL_SHA" != "$EXPECTED_SHA" ]]; then
    echo "WARNING: SHA-256 mismatch"
    echo "  Expected: $EXPECTED_SHA"
    echo "  Actual:   $ACTUAL_SHA"
    echo "This APK may be from a different build. Verify it contains bundled models."
fi

echo "APK verification:"
echo "  Path: $APK_PATH"
echo "  Size: $SIZE bytes"
echo "  SHA-256: $ACTUAL_SHA"
echo "PASS: APK is within expected size range."
```

---

## 4. Installation

```bash
# Install on connected device
adb install -r app-debug.apk

# Verify installation
adb shell pm list packages | grep vokie
# Expected: package:com.vokie

# Verify installed size
adb shell pm path com.vokie
adb shell du -h /data/app/*/com.vokie*/base.apk
# Should show ~480 MB
```

---

## 5. Critical Requirements

### MUST Verify

- [ ] APK size is 480-510 MB
- [ ] SHA-256 matches expected value (or is documented new build)
- [ ] Package name is `com.vokie`
- [ ] Contains `lib/arm64-v8a/` native libraries
- [ ] Contains `assets/models/` bundled models

### MUST NOT

- ❌ Use a ~52 MB APK (missing models)
- ❌ Use a ~190 MB APK (incomplete models)
- ❌ Skip SHA-256 verification
- ❌ Rebuild APK without staged models
- ❌ Substitute with a different package

---

## 6. APK Content Inspection

```bash
# List native libraries
unzip -l app-debug.apk | grep "lib/arm64"
# Expected:
#   lib/arm64-v8a/libvokie_whisper.so
#   lib/arm64-v8a/libvokie_ct2.so
#   lib/arm64-v8a/libonnxruntime.so
#   lib/arm64-v8a/libsherpa-onnx-jni.so
#   lib/arm64-v8a/libsherpa-onnx-c-api.so
#   lib/arm64-v8a/libc++_shared.so

# List bundled models
unzip -l app-debug.apk | grep "assets/models"
# Expected:
#   assets/models/manifest.json
#   assets/models/stt/ggml-tiny-q5_1.bin
#   assets/models/tts/eng/model.onnx
#   assets/models/tts/hin/model.onnx
#   assets/models/tts/tam/model.onnx
#   assets/models/tts/guj/model.onnx

# Verify package info
aapt dump badging app-debug.apk | grep -E "package:|versionName|versionCode"
```

---

## 7. Troubleshooting

### APK is ~52 MB

**Problem:** Models not bundled.

**Solution:**
1. Ensure models are staged: `ls -la app/src/main/assets/models/`
2. Models should total ~435 MB
3. Run `scripts/stage-bundled-models.py` with the model archive
4. Rebuild

### APK is ~190 MB

**Problem:** Partial model bundling or build configuration issue.

**Solution:**
1. Verify all TTS models are present
2. Check that `manifest.json` includes all required models
3. Clean and rebuild: `./gradlew clean assembleDebug --no-daemon`

### Native build fails

**Problem:** cmake/NDK configuration.

**Solution:**
1. Use Android Studio on Windows
2. Or build on native Linux
3. Or use GitHub Actions CI

### Installation fails

**Problem:** Package conflict or signature mismatch.

**Solution:**
```bash
# Uninstall existing app
adb uninstall com.vokie

# Install fresh
adb install app-debug.apk
```

---

## 8. Contact

If the canonical APK cannot be obtained or verified, do NOT proceed with testing.
Report the issue to the LAP1 maintainer.

---

**IMPORTANT:** The APK MUST be ~480-510 MB with bundled models. Do NOT use a smaller APK.
