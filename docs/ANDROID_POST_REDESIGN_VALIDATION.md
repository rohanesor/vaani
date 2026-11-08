# VAANI Android — Post-Redesign Validation

## Scope and repository safety

- Repository verified: `/mnt/d/vibe/vaani` (the mounted `D:\vibe\vaani`).
- Git root verified: `/mnt/d/vibe/vaani`; branch: `vaani/main`.
- Existing uncommitted Android/WebView redesign work and unrelated changes were preserved. No reset, clean, stash, or parent-Vokie Git access was performed.
- This document records execution evidence, not expected results.

## Build and APK

| Requested check | Result | Detail |
|---|---|---|
| Windows `gradlew.bat :app:assembleDebug` | PASS | Ran through `cmd.exe` with the installed Windows SDK at `C:\Users\kille\AppData\Local\Android\Sdk`. |
| Build variant | `debug` | `:app:assembleDebug` completed successfully. |
| APK location / SHA-256 | PASS | `app/build/outputs/apk/debug/app-debug.apk`; `28eba8262d9c17f7f50a684cbf2eb8f50d4643a7127b694a8e1d4d32e2a23fa2`. |
| Android SDK | Available | Windows SDK discovered and used; emulator package version 37.2.12.0. |

Kotlin compilation, launcher icon/splash resources, manifest processing, WebView/font asset packaging, native library packaging, and APK assembly are validated. The APK zip includes the VAANI WebView files, fonts stylesheet, adaptive launcher icon, and splash drawable.

## Bridge assessment

### Static/native trace

`ITantraNativeBridge.kt` contains **34** `@JavascriptInterface` methods. The redesigned JavaScript uses 33; `stopDiscovery` is deliberately unused. The browser bridge harness confirms every JavaScript `call()` target exists in that interface.

The actual native serializer (`buildStateJson`) provides the redesigned state fields:

| Field | Native source behavior | UI handling |
|---|---|---|
| `chatMessages[].translatedText`, `translatedLanguage` | Values are serialized or `JSONObject.NULL` | Gloss renders only with translated text. |
| `speech.sttState`, `speech.ttsInstalled[]` | STT enum name plus installed language codes | Language availability is derived from these fields. |
| `messageAudio[id]` | Per-message `MessageTtsState.name` | UI clears local play marker only for `COMPLETED`/`FAILED`. |
| `aiConfigured` | `BuildConfig.AI_BACKEND_BASE_URL.isNotBlank()` | UI disables actions with an explicit unavailable message when false. |
| identity/language/TTS/cloud | Serializer emits nullable cloud properties as JSON null; `MainActivity` observes identity, language, speed, cloud user/sync state | UI reads state snapshots and handles absent/null fields. |

`MainActivity` pushes state on page finish and observes the base communication flows plus identity, language, TTS speed, cloud user/sync state, message TTS state, installed TTS languages, and STT-state changes. Its manifest declares `adjustResize`; its back callback delegates to `window.handleAndroidBack()`.

This is source-level traceability plus a **32/32 browser fake-bridge contract run** and a successful Kotlin compilation. It is still **not proof of a real Android WebView bridge**; that requires installation and runtime E2E.

## Executed tests

| Command | Result |
|---|---|
| `node scripts/sync-android-webview-tokens.mjs --check` | PASS |
| `NODE_PATH=/mnt/d/vibe/vaani/client/node_modules node tests/android-webview/run.cjs` | PASS — 32/32 |
| `cd client && npm run typecheck && npm run build && npm test` | PASS — typecheck/build; Playwright 10/10 |
| Windows `gradlew.bat :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin` | PASS — JVM 458/458; instrumentation Kotlin compilation passed |
| `ANDROID_UI_REPORT_DIR=/tmp/vaani-android-post-redesign node tests/android/run.mjs` | BLOCKED — Appium reported no connected Android device; the available `Pixel_10_Pro_XL` AVD then refused boot for insufficient host disk space |

The Appium command was run with its artifact directory outside the repository. It created no test session, so no Appium case can honestly be labeled PASS, FAIL, or SKIP; the suite is **BLOCKED before test 01** and produced no device screenshot evidence.

## Runtime and native-function matrix

| Area | Status |
|---|---|
| APK installation and startup | BLOCKED — debug APK exists, but no booted device |
| VAANI launcher and splash branding | Build/package PASS; runtime BLOCKED — no booted device |
| Onboarding, Speak, Messages, conversation, Nearby, pairing, calls, Settings | Browser contract PASS; Android runtime BLOCKED |
| Language selection, AI unavailable, cloud state, light/dark | Browser contract PASS; Android runtime BLOCKED |
| Android back and keyboard/IME resizing | Browser simulated contract PASS; Android runtime BLOCKED |
| PTT press/release and STT updates | Browser event/state contract PASS; microphone/STT BLOCKED |
| Native send/playback/discovery/pairing/calls | Browser bridge-call contract PASS; native transport BLOCKED |
| TTS speed | Browser bridge-call/state contract PASS; native execution BLOCKED |
| Cloud login/sync/logout | Browser bridge-call contract PASS; controlled backend/account E2E BLOCKED |

## Remaining defects and required next step

Two confirmed redesign-adjacent build defects were fixed: invalid XML comments containing `--` in light and dark `colors.xml` prevented Android resource merging. Only comment text changed. The remaining limiting failure is emulator disk space, which prevents runtime execution.

To complete the validation on a controlled Android workstation, provide/configure an Android SDK and an emulator or device, then run:

```powershell
cd D:\vibe\vaani
.\gradlew.bat :app:assembleDebug
Get-FileHash app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
adb install -r app\build\outputs\apk\debug\app-debug.apk
# Start Appium UiAutomator2 against that device, then:
$env:APPIUM_HOST="127.0.0.1"
$env:ANDROID_UDID="<device-serial>"
node tests\android\run.mjs
```

Then rerun the Android JVM/instrumentation commands and record actual per-test Appium artifacts. Physical peer discovery, pairing, calls, and controlled cloud login/sync additionally need the appropriate multi-device topology and backend/test identities.

## Runtime follow-up: booted emulator

A non-destructive runtime partition-size override allowed `Pixel_10_Pro_XL` (API 37, `emulator-5554`) to boot despite the original disk-space guard. The freshly built APK was installed after removing an incompatible differently-signed prior emulator package. `am start` then confirmed `com.vaani.app/com.vokie.MainActivity` in focus. Real ADB screenshot and UI hierarchy evidence are at `tests/android/reports/post-redesign-e2e-20261107/00-ADB-runtime-startup.png` and `.xml`; they show the VAANI onboarding screen.

The full Appium suite remains BLOCKED before test 01, but for a narrower, reproducible reason: UiAutomator2's helper instrumentation server does not initialize on this API-37 device through the WSL-hosted Appium 3 / Windows ADB boundary. It is not an app startup failure. No Appium test is recorded as PASS, FAIL, or SKIP. A native Windows-hosted Appium/ADB session, or a compatible Linux SDK/emulator stack, is required to complete the suite.

A read-only Chrome DevTools evaluation against the actual remote-debuggable WebView additionally confirmed: title `VAANI — Every language. One conversation.`, `typeof window.ITantraNative === "object"`, a native `currentAppState`, `mode === "onboarding"`, and `window.handleAndroidBack` present. This validates actual WebView startup/bridge injection on the installed APK, but does not replace interaction E2E.
