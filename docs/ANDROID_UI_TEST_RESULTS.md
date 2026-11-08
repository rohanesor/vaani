# VAANI Android UI — Test Results

There are three kinds of evidence. They are reported separately and must not be mixed.

| Kind | What it proves | What it does **not** prove |
|---|---|---|
| **A. Browser simulation** | The WebView UI renders every exported state correctly and calls the right bridge methods | Anything about real Android connectivity, sync, Bluetooth, Wi-Fi Direct, STT/TTS or the Kotlin code |
| **B. Native Android validation** | Real runtime behaviour on a device or emulator | — |
| **C. Web validation** | The web app against its own capabilities | Anything about Android |

## A. Browser simulation — 36/36 PASS (2026-11-07)

**Runner:** `node tests/android-webview/run.cjs` (Chromium, recording fake of `ITantraNative`, fixtures shaped like `buildStateJson()`)
**Results:** `docs/android-ui/screens/results.json` (`"kind": "browser-simulation"`)
**Screenshots:** 116 in `docs/android-ui/screens/`

**Connectivity states in this section are fixture values pushed into the page.** They show that the UI presents each state correctly. They are not evidence of Android connectivity behaviour.

| Group | Tests | Result |
|---|---|---|
| Bridge contract: every `call()` is in the fake. The fake matches the 36 Kotlin `@JavascriptInterface` methods, checked against the edited Kotlin bridge on your computer. | 1 | PASS (35 of 36 used; `stopDiscovery` is unused) |
| Static: no `fetch`, XHR, WebSocket, `Math.random`, `navigator.onLine`, icon font, prototype data, or storage keys beyond `it_onboarded_v2` | 1 | PASS |
| Layout at 320 / 360 / 375 / 390 / 412 / 430 px × light and dark. Chats, Conversation, Cloud conversation, Speak, Nearby and Settings each get checked for: no horizontal scroll, no overflow, every icon resolving, tap targets ≥ 32px, no page errors. | 12 | PASS |
| Startup (title, fonts for 5 scripts, Chats home, three tabs); system back stack; notification tap; onboarding | 4 | PASS |
| **[simulated]** Online: Internet and Nearby shown as separate indicators | 1 | PASS |
| **[simulated]** No internet + nearby connected: push-to-talk and nearby send still work, the pending sync line reads "needs internet", AI explains and is disabled, Sync now is disabled | 1 | PASS |
| **[simulated]** Wi-Fi connected but not validated → "No internet yet", never "Online" | 1 | PASS |
| **[simulated]** Captive portal → "Sign-in needed" | 1 | PASS |
| **[simulated]** Reconnect: pending line clears when native reports synced | 1 | PASS |
| Origins: badges taken from the exported `origin`; filters; unread total; search over cloud previews | 1 | PASS |
| Signed out: no cloud badges; every local conversation is Nearby | 1 | PASS |
| Cloud conversation: read-only transcript from `cloudChat`, no composer, explanation; open and close calls | 1 | PASS |
| Linking: `linkCloudConversation(conversations[].id, cloudId)` | 1 | PASS |
| Cloud-linked header shows the exported pending count | 1 | PASS |
| Nearby: four separate counts; scan, Wi-Fi Direct, connect, pair; outgoing "Request sent"; incoming pair accept | 1 | PASS |
| Speak: press and release forwarded; release outside the key still ends; native states; disabled for an offline peer; 10 languages | 1 | PASS |
| Conversation: turns, gloss, delivery, TTS state, mark-read, send, keyboard | 1 | PASS |
| AI Context: four operations, Stop, draft goes to the composer, **never sends**, translate target, unconfigured build | 1 | PASS |
| Calls: start, cancel, accept, mute, speaker, end, failure reason | 1 | PASS |
| Settings: name, speed, sign-in (password redacted and never in the DOM), sync opt-in, unconfigured cloud | 1 | PASS |
| Empty, loading and missing-bridge states; dark offline, Context and call | 2 | PASS |

![Simulated connectivity states](android-ui/overview-connectivity-simulated.png)
![Origins and sync](android-ui/overview-origins-sync.png)
![Nearby and calls](android-ui/overview-nearby-calls.png)

## B. Native Android validation — NOT RUN

**Nothing in this section has been executed.**

| Item | Status | Blocker |
|---|---|---|
| Kotlin compile (`:app:assembleDebug`) | **Not run** | No Android SDK is reachable from this session. The cloud proxy returns 403 for `dl.google.com` and Google Maven, and the Linux VM on your computer has no SDK. |
| Room query compile check for the two new DAO queries | **Not run** | Same. Room validates `@Query` SQL at build time. |
| `tests/android/run.mjs` (Appium) | **Not run** | No emulator or device reachable (`172.29.80.1:4723`). |
| Online test (sign-in, sync, AI) | **Not run** | Also needs a build with `aiBackendBaseUrl` and a test account. |
| Offline test (airplane / radios off, local persistence, discovery, pairing, exchange, offline STT, reconnect and sync) | **Not run** | Needs two physical devices for nearby exchange. |

**The Appium suite is ready for these runs.** `tests/android/run.mjs` now includes:
- **Native state checks:** `network.state` is one of the four values, and the `nearby` counts are integers.
- **Offline:** `svc wifi disable` plus `svc data disable`, then wait for native `network.state === "none"`. The UI must say "No internet" and Nearby must still respond.
- **Reconnect:** re-enable the radios and wait up to 45s for `online`. It logs the native states seen on the way (for example `none → connecting → online`). This is how validated versus merely-connected is proven on a device.
- **Online cloud:** sign in with `VAANI_TEST_EMAIL` / `VAANI_TEST_PASSWORD`, opt in, Sync now, expect `Synced`. It is skipped with a reason when the build isn't configured.
- **Screenshots:** every test saves `NN-PASS|FAIL|SKIP-<name>.png` plus the page source, and `summary.json`.

**Two-device nearby checklist** (manual, with both apps on this build):

1. Turn on airplane mode, then re-enable Bluetooth and Wi-Fi only (no internet).
2. Check that discovery fills **Nearby** and pairing works.
3. Exchange a message both ways and check that the delivery state reaches Delivered.
4. Use push-to-talk and check that STT and TTS work offline.
5. Restart the app and check that messages persist.
6. On a signed-in device with a linked conversation, check that the sync line shows "N waiting to sync · needs internet".
7. Turn internet back on and check that the count clears after sync.

Run commands:

```powershell
cd D:\vibe\vaani
.\gradlew.bat :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
appium --allow-insecure=adb_shell
$env:APPIUM_HOST="127.0.0.1"; node tests\android\run.mjs
```

## C. Web validation (canonical `client/` + `server/`) — run on an unchanged copy

All files were copied from `D:\vibe\vaani` on 2026-11-07. **No web file was modified.**

| Check | Result |
|---|---|
| `server`: `vitest run` | **62/62 PASS** (8 files, including `sync.test.ts`, `production.test.ts`, `communication-lifecycle.test.ts`) |
| `server`: `tsc --noEmit` | PASS |
| `client`: `tsc -b`, `vite build` | PASS |
| `client`: `playwright test` (existing `e2e/baseline.spec.ts`, desktop and Pixel 5 projects) | **9/10 PASS, 1 FAIL** |

The failure is `[mobile-chrome] exposes voice recorder UI`:
- The test expects `getByRole('group', { name: 'Voice recorder' })`.
- In the no-microphone path the app shows "No microphone was found. · Back to typing" without that group role.
- The honest error is rendered (see `test-results/…/test-failed-1.png` in a local run). This is a web test or accessibility issue owned by the web client, and is left unchanged as instructed.
- The runner used `@playwright/test` 1.63.0 (the pre-installed browser) instead of the pinned 1.64.1.

## D. Post-redesign Android build and emulator E2E attempt

| Check | Result | Evidence |
|---|---|---|
| Windows `gradlew.bat :app:assembleDebug` | PASS | Debug Kotlin compilation, resource merge, manifest/resource processing, CMake/native packaging, and APK assembly completed. |
| Debug APK | PASS | `app/build/outputs/apk/debug/app-debug.apk`, 51 MB; SHA-256 `28eba8262d9c17f7f50a684cbf2eb8f50d4643a7127b694a8e1d4d32e2a23fa2`. |
| JVM tests | PASS — 458/458 | 0 failures, errors, or skips. |
| `:app:compileDebugAndroidTestKotlin` | PASS | Instrumentation Kotlin compilation completed. |
| Android WebView browser harness | PASS — 32/32 | Real packaged source files with fake recording bridge. |
| Appium `node tests/android/run.mjs` | BLOCKED before test 01 | Appium was reachable, but no Android device was attached. The available `Pixel_10_Pro_XL` AVD then refused to boot because host disk space was insufficient. |

Two confirmed XML resource defects were corrected during this build: invalid XML comments containing `--` in light and dark `colors.xml` stopped Android resource merging. Only the comment text changed; no UI/resource values changed.

APK installation, runtime startup, launcher/splash runtime, WebView lifecycle, real IME/back behavior, and native/device E2E remain BLOCKED until the AVD has sufficient disk space or another booted Android device is provided.
