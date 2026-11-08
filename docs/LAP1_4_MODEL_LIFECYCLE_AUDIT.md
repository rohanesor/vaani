# LAP 1.4 — Model Lifecycle / Storage Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `1c0e6cd`

## Audit Summary

✅ **PASS** — Model lifecycle and storage handling is robust.

## Model Lifecycle State Machine

### BundledModelStore (APK-bundled models)

```
APK Assets
    │
    ▼
[prepare()]
    │
    ├─> Check existing extraction valid? ─────> YES ─> READY
    │                                              
    ▼ NO
    │
    ├─> Create .models.extracting/
    │
    ├─> Extract each bundled file
    │       └─> Verify SHA-256 ✓
    │
    ├─> Copy espeak-ng-data phonemizer
    │
    ├─> Write manifest.json
    │
    ├─> Atomic directory swap:
    │       .models.extracting → models
    │       (previous → .models.previous, then deleted)
    │
    ▼
READY (models/)
```

### ModelDownloadManager (User-initiated downloads)

```
DownloadState.Idle
    │
    ▼ download(language)
    │
DownloadState.Downloading
    │
    ├─> Check Wi-Fi connected (not metered)
    │
    ├─> Check free storage (150+ MB)
    │
    ├─> Create .{lang}.downloading/
    │
    ├─> Download model.onnx
    │       ├─> HTTP Range header (for resume)
    │       ├─> Validate Content-Range if HTTP 206
    │       └─> Check size <= expected
    │
    ├─> Download tokens.txt
    │
    ├─> Verify SHA-256 checksums ✓
    │
    ├─> Atomic directory swap
    │
    ▼
DownloadState.Complete
```

## Handled Edge Cases

### 1. Interrupted Download

```kotlin
// Retain incomplete bytes on network failure/cancel for Range resume on retry.
if (cancelRequested.get() || error is CancellationException) {
    _state.value = DownloadState.Cancelled(language, retained(), total)
    ...
}
```

- Partial files are **retained** for resume
- Next download attempt uses `Range: bytes=$existing-`
- Server `Content-Range` header validated

### 2. Resume Validation (HTTP 206)

```kotlin
if (append) check(validResumeRange(
    connection.getHeaderField("Content-Range"), 
    existing, 
    spec.sizeBytes
)) { "Server returned an invalid resume range" }
```

Validates:
- Start offset matches local file length
- End offset is within expected file size
- Total matches expected file size

### 3. Server Ignoring Range (HTTP 200)

```kotlin
// A server ignoring Range returns 200: truncate safely, do not append duplicate bytes.
val append = code == HttpURLConnection.HTTP_PARTIAL
```

If server returns 200 instead of 206, file is truncated and re-downloaded.

### 4. Checksum Mismatch

```kotlin
check(digest.digest().joinToString("") { "%02x".format(it) } == spec.sha256) { 
    "Downloaded file checksum does not match release manifest" 
}
```

- SHA-256 computed during download
- Mismatch triggers failure and file deletion
- Re-download required

### 5. Corrupt Model

```kotlin
if (target.length() > spec.sizeBytes) 
    check(target.delete()) { "Could not remove invalid partial pack" }
```

- Oversized files deleted before download
- Undersized files re-downloaded

### 6. Insufficient Storage

```kotlin
fun requireFreeSpace(required: Long) {
    ...
    check(StatFs(context.filesDir.path).availableBytes >= required) { 
        "At least 150 MB of free storage is required." 
    }
}
```

### 7. Partial Model (Incomplete Pack)

```kotlin
if (model.length() != MODEL_BYTES || !tokenizer.isFile || !config.isFile || !vocabulary.isFile) {
    return@withLock TranslationResult(..., status = TranslationStatus.UNAVAILABLE, 
        error = "Approved local CT2 model is not staged.")
}
```

Translation engine validates all required files exist.

### 8. App Restart During Download

- Partial files in `.{lang}.downloading/` preserved
- `transferMutex.tryLock()` prevents concurrent downloads
- Resume continues from partial file

### 9. Concurrent Download Attempt

```kotlin
check(transferMutex.tryLock()) { "A language download is already in progress." }
```

### 10. Version Update / Replacement

```kotlin
// Keep the previously installed pack intact if atomic directory replacement fails.
val previous = File(root, ".${language.iso6393}.previous")
check(!previous.exists()) { 
    "A previous installation needs recovery before downloading again." 
}
if (target.exists()) check(target.renameTo(previous)) { 
    "Could not preserve installed pack." 
}
```

Atomic swap preserves previous version until new version confirmed.

### 11. Deletion Recovery

```kotlin
if (!temporary.renameTo(target)) {
    if (previous.exists()) check(previous.renameTo(target)) { 
        "Could not restore installed pack." 
    }
    error("Could not finalize downloaded ${language.nativeName} pack")
}
previous.deleteRecursively()
```

## UI State Reflection

### OnboardingScreen

```kotlin
var installedPacks by remember { mutableStateOf<Set<VokieLanguage>>(setOf(VokieLanguage.EN)) }

LaunchedEffect(currentScreen) {
    withContext(Dispatchers.IO) {
        val manager = TtsModelManager(context)
        val installed = mutableSetOf(VokieLanguage.EN)
        TtsLanguage.entries.forEach { tts ->
            if (manager.isInstalled(tts)) {
                installed.add(tts.messageLanguage)
            }
        }
        installedPacks = installed
    }
}
```

"Installed" means model files **actually exist** with correct sizes.

### TtsModelManager.isInstalled()

```kotlin
fun isInstalled(language: TtsLanguage): Boolean = try {
    val model = bundled.spec("tts/${language.iso6393}/model.onnx")
    val tokens = bundled.spec("tts/${language.iso6393}/tokens.txt")
    modelFile(language).length() == model.sizeBytes && 
        tokensFile(language).length() == tokens.sizeBytes
} catch (_: Throwable) { false }
```

Checks actual file size against manifest.

## Test Coverage

### ModelDownloadManagerTest

| Test | Coverage |
|---|---|
| `downloadPercent` | Boundary conditions, negative values |
| `validResumeRange` | Valid ranges, mismatched offsets/totals, malformed headers |

## Persistence Identifiers

The following identifiers are **compatibility-sensitive** and unchanged:

- `vokie.db` — Room database name
- `models/` — Extracted model directory
- `ct2/nllb600m/` — CT2 model staging directory
- `manifest.json` — Model specification file

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.4 MODEL LIFECYCLE: PASS**

The model lifecycle implementation handles:
- ✅ Interrupted downloads (resume with Range headers)
- ✅ Content-Range validation
- ✅ SHA-256 checksum verification
- ✅ Corrupt/oversized file detection
- ✅ Insufficient storage check
- ✅ Atomic directory replacement
- ✅ Concurrent download prevention
- ✅ Version update with rollback
- ✅ UI reflects actual file existence
