# LAP 1.7 — Offline-First Hardening Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `695085b`

## Audit Summary

✅ **PASS** — The app is fully offline-first by design.

## Network Dependencies Audit

### Internet Usage

| Component | Network Required | Purpose |
|---|---|---|
| ModelDownloadManager | User-initiated Wi-Fi | Download additional TTS languages |
| STT (SenseVoice) | ❌ None | Bundled model |
| Translation (CT2) | ❌ None | Staged model |
| TTS (Sherpa) | ❌ None | Bundled/downloaded models |
| Bluetooth Transport | ❌ None | P2P |
| Wi-Fi Direct | ❌ None | P2P |
| Room Database | ❌ None | Local storage |
| Preferences | ❌ None | DataStore |

### Network Permission Analysis

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
```

**Purpose:** Model download only. Not required for core communication.

### Model Download Gate

```kotlin
fun requireWifi() {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    check(cm.activeNetwork != null && !cm.isActiveNetworkMetered) { 
        "Connect to Wi-Fi to download this offline language pack." 
    }
}
```

- ✅ Requires Wi-Fi (not metered)
- ✅ User-initiated only
- ✅ Downloaded models stored permanently

## No Cloud Dependencies

### Analytics / Crash Reporting

```
Firebase: NOT USED
Crashlytics: NOT USED
Sentry: NOT USED
Amplitude: NOT USED
Mixpanel: NOT USED
```

### Backend APIs

```
api.vokie.*: NOT USED
REST/GraphQL: NOT USED
WebSocket: NOT USED
Push notifications: NOT USED
```

### Model CDN

```kotlin
val modelCdnBaseUrl = "https://d2sv8yw5k4h6gl.cloudfront.net"
```

Used **only** for optional language pack downloads. Core functionality works without network.

## Offline Behavior by Feature

### 1. Speech-to-Text

| Scenario | Behavior |
|---|---|
| No network | ✅ Works (bundled model) |
| Network available | ✅ Works (ignores network) |
| Airplane mode | ✅ Works |

### 2. Translation

| Scenario | Behavior |
|---|---|
| No network | ✅ Works (staged CT2 model) |
| Model not staged | ❌ Returns UNAVAILABLE status |
| Network available | ✅ Works (ignores network) |

### 3. Text-to-Speech

| Scenario | Behavior |
|---|---|
| Bundled language (EN/HI/TA/GU) | ✅ Works offline |
| Downloaded language | ✅ Works offline after download |
| Missing language | ❌ Returns error (prompts download) |

### 4. Bluetooth Transport

| Scenario | Behavior |
|---|---|
| No network | ✅ Works (P2P) |
| BT enabled | ✅ Discovery/connect/message |
| BT disabled | ❌ Cannot communicate |

### 5. Wi-Fi Direct Transport

| Scenario | Behavior |
|---|---|
| No internet | ✅ Works (P2P, not router) |
| Wi-Fi Direct enabled | ✅ Discovery/connect/message |
| Wi-Fi Direct disabled | ❌ Cannot communicate |

### 6. Message Persistence

| Scenario | Behavior |
|---|---|
| No network | ✅ Room database (local SQLite) |
| App restart | ✅ Messages preserved |
| Peer offline | ✅ Store-and-forward (N2) |

## First Launch Experience

### With Network

1. App starts
2. Bundled models extracted
3. User can optionally download additional languages
4. All features work

### Without Network

1. App starts
2. Bundled models extracted
3. EN/HI/TA/GU languages available
4. Additional languages cannot be downloaded
5. Core features work

## Translation Model Staging

The NLLB CT2 model is **staged** (not bundled in APK) to keep APK size reasonable:

```kotlin
// Ctranslate2TranslationEngine.kt
private val directory = File(context.filesDir, "ct2/nllb600m")
```

Model is copied during development/provisioning. In production:
- APK includes bundled models
- CT2 model is staged separately
- If model missing, translation returns UNAVAILABLE

## Resilience Patterns

### 1. Model Missing

```kotlin
if (model.length() != MODEL_BYTES || !tokenizer.isFile) {
    return TranslationResult(..., status = TranslationStatus.UNAVAILABLE,
        error = "Approved local CT2 model is not staged.")
}
```

App continues; translation feature degraded.

### 2. Download Interrupted

```kotlin
// Retains partial bytes for resume
if (cancelRequested.get() || error is CancellationException) {
    _state.value = DownloadState.Cancelled(language, retained(), total)
}
```

### 3. BT/WiFi Unavailable

```kotlin
sealed class TransportState {
    object Disconnected : TransportState()
    data class Connecting(...) : TransportState()
    data class Connected(...) : TransportState()
}
```

UI shows connection state; queues messages for later delivery.

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.7 OFFLINE-FIRST HARDENING: PASS**

The app is offline-first by design:
- ✅ No cloud analytics/crash reporting
- ✅ No backend API dependencies
- ✅ All ML inference is local
- ✅ P2P transport (BT/Wi-Fi Direct)
- ✅ Local Room database
- ✅ Network used only for optional model downloads
- ✅ Graceful degradation when models missing
- ✅ Works in airplane mode (with BT/Wi-Fi enabled)
