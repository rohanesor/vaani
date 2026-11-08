# LAP 1.9 — Release Engineering Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `695085b`

## Audit Summary

✅ **PASS** — Release engineering infrastructure is production-ready.

## Build Configuration

### Version Management

```kotlin
val configuredVersionName = providers.gradleProperty("versionName").orElse("1.0.0")
val configuredVersionCode = providers.gradleProperty("versionCode").orElse("1")
```

- ✅ Externalized version config
- ✅ Gradle property override support

### Signing Configuration

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(keystorePath.get())
        storePassword = keystorePassword.get()
        keyAlias = keyAlias.get()
        keyPassword = keyPassword.get()
    }
}
```

Environment variables required:
- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

### Release Gate

```kotlin
if (productionRelease && !signingConfigured) {
    throw GradleException("Production release signing is not configured...")
}
```

- ✅ Fails build if production release without signing
- ✅ Debug builds work without signing config

## Minification (R8/ProGuard)

### Release Build Type

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

### ProGuard Rules

```proguard
# Vokie ProGuard rules
-keep class com.vokie.data.local.** { *; }
-keep class com.vokie.stt.WhisperNative { *; }
-keep class com.k2fsa.** { *; }
```

Preserved:
- ✅ Room entities (reflection)
- ✅ Whisper JNI class
- ✅ Sherpa-ONNX AAR classes

## Model Asset Pipeline

### Stage Script

```bash
scripts/stage-bundled-models.py ARCHIVE_PATH
```

Features:
- ✅ SHA-256 verification per file
- ✅ Size verification
- ✅ Pin validation for core models
- ✅ Atomic staging (deletes old, copies new)

### Production Gate

```kotlin
tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") {
        doFirst {
            if (productionRelease && !file("src/main/assets/models/manifest.json").isFile) {
                throw GradleException("Bundled model assets are missing...")
            }
        }
    }
}
```

- ✅ Prevents release without models
- ✅ Debug builds work without full models

## CI Scripts

### Branding & Security Check

```bash
scripts/ci-branding-and-security-check.py
```

Validates:
- ✅ No "Vokie" in user-visible UI (iTantra brand)
- ✅ Technical identifiers preserved (com.vokie, etc.)
- ✅ No hardcoded secrets (AWS keys, private keys)
- ✅ Website asset validity

### Deploy Script

```bash
scripts/deploy-aws.sh
```

For model CDN deployment (not app distribution).

### Icon Generation

```bash
scripts/generate-itantra-icons.py
```

Generates all Android icon sizes from source.

## ABI Configuration

```kotlin
ndk { abiFilters += "arm64-v8a" }
```

- ✅ arm64-v8a only (modern devices)
- ✅ Reduces APK size
- ✅ Enables FP16/dot-product optimizations

## Native Build

```kotlin
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}
ndkVersion = "27.0.12077973"
```

- ✅ CMake for native JNI
- ✅ Pinned NDK version
- ✅ c++_shared STL

## Asset Handling

```kotlin
androidResources {
    noCompress += listOf("bin", "onnx", "txt", "json")
}
```

- ✅ Models not recompressed
- ✅ Direct file access for native engines

## Dependencies

### Pinned Versions

| Dependency | Version | Purpose |
|---|---|---|
| Compose BOM | 2024.06.00 | UI framework |
| Room | 2.6.1 | Database |
| Coroutines | 1.8.1 | Async |
| Sherpa-ONNX | 1.13.7 | TTS engine |
| KSP | (plugin) | Code generation |

### Test Dependencies

| Dependency | Version | Purpose |
|---|---|---|
| JUnit | 4.13.2 | Unit tests |
| Espresso | 3.6.1 | UI tests |

## Lint Configuration

```kotlin
lint {
    abortOnError = false
}
```

- ⚠️ Lint warnings don't fail build (development convenience)
- 📝 Consider enabling for release

## Room Schema

```kotlin
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
```

- ✅ Schema exported for migration verification

## Release Checklist

### Before Release Build

1. ✅ Set version in `gradle.properties`:
   ```properties
   versionName=1.0.0
   versionCode=1
   ```

2. ✅ Stage models:
   ```bash
   python scripts/stage-bundled-models.py /path/to/models.zip
   ```

3. ✅ Set signing environment:
   ```bash
   export ANDROID_KEYSTORE_PATH=/path/to/keystore.jks
   export ANDROID_KEYSTORE_PASSWORD=...
   export ANDROID_KEY_ALIAS=...
   export ANDROID_KEY_PASSWORD=...
   ```

4. ✅ Run CI checks:
   ```bash
   python scripts/ci-branding-and-security-check.py
   ```

### Build Release APK

```bash
./gradlew assembleRelease -PproductionRelease=true
```

### Verify APK

```bash
python scripts/verify-bundled-model-apk.py app/build/outputs/apk/release/app-release.apk
```

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.9 RELEASE ENGINEERING: PASS**

Release infrastructure is production-ready:
- ✅ Externalized version config
- ✅ Secure signing (env vars)
- ✅ R8 minification enabled
- ✅ ProGuard rules for JNI/Room
- ✅ Model staging pipeline with verification
- ✅ Production gates (models, signing)
- ✅ CI scripts for branding/security
- ✅ Pinned dependencies
- ✅ Schema export for migrations
