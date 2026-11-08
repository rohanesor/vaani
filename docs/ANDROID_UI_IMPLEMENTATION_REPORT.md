# VAANI Android UI — Implementation Report

## Summary

The Android WebView presentation layer was rebuilt as VAANI's full communication app. It shares the web client's identity and has its own capabilities.
- **Navigation:** Chats (home) · Nearby · Settings, plus a Speak overlay, a Context sheet, and read-only Cloud conversations.
- **Status:** Internet and Nearby are shown separately. Every conversation shows its origin (Nearby, Cloud linked or Cloud) and its sync state, both derived from persistent, account-scoped records.

Native changes are **state exports only**, documented beforehand in [`ANDROID_BRIDGE_STATE_CONTRACT.md`](ANDROID_BRIDGE_STATE_CONTRACT.md). No messaging, transport, pairing or synchronisation behaviour changed.

**Not compiled and not run on Android.** See [`ANDROID_UI_TEST_RESULTS.md`](ANDROID_UI_TEST_RESULTS.md) §B.

![Android, light](android-ui/overview-light-390.png)

## 1. Order followed

1. **Audit:** [`PLATFORM_CAPABILITY_MATRIX.md`](PLATFORM_CAPABILITY_MATRIX.md) was written first, with file evidence for every row.
2. **Contract:** unchanged methods and fields, and the minimal additions, were documented before any Kotlin edit.
3. **Native state exports.**
4. UI changes, in this order: Chats, then Nearby, then conversation and Context, then Settings.
5. **Tests:** browser simulation, an updated Appium suite, and web validation on its own.
6. **Docs.**

## 2. Native changes (additive)

All edits keep the files' CRLF line endings. Diff sizes against the previous version:

| File | Change | Lines |
|---|---|---|
| `ui/bridge/BridgeStateSources.kt` | **New.** `NetworkStatusSource`: default-network callback; `online` requires `NET_CAPABILITY_VALIDATED`, captive portal → `limited`, unvalidated → `connecting`. `CloudStateSource`: Room flows over `cloud_user`, `cloud_id_mapping` (current account only), `cloud_conversations`, `cloud_members`, `cloud_messages`; plus a read-only selection of which cached cloud conversation to export. | +162 (new) |
| `data/local/CloudSyncEntities.kt` | Two read-only `@Query` observers: `observeConversationLinks(accountId)` and `observeMessageSyncCounts(accountId)` (join to `messages`, grouped by conversation and `syncStatus`). Result POJO `ConversationSyncCount`. **No schema change, no migration.** | +14, −0 |
| `ui/bridge/ITantraNativeBridge.kt` | New optional constructor parameters with defaults. New `onOpenCloudConversation` and `onCloseCloudConversation` (selection only). New state fields: `network`, `nearby`, `outgoingPairRequests`, `cloud.configured`, `cloud.lastSyncedAt`, `conversations[].origin`, `conversations[].sync`, `cloudConversations`, `cloudChat`. | +94, −0 |
| `MainActivity.kt` | Creates both sources, passes them to the bridge, re-pushes state when they change, unregisters the network callback in `onDestroy`. | +13, −1 (the bridge constructor line) |

Earlier in this redesign: theme and launcher resources, `adjustResize`, the `aiConfigured`, `speech` and `messageAudio` exports, and translation fields. These are unchanged in this round.

**Explicitly untouched:**
- `CloudSyncRepository`, `SyncCoordinator`, `SyncApiClient`, `AuthClient`
- `MessageRepository` and delivery states
- Bluetooth and Wi-Fi Direct transports
- Pairing and trust
- Call manager
- STT/TTS
- Database version 10

## 3. UI (`app/src/main/assets/www/`)

| Requirement | Implementation |
|---|---|
| Internet ≠ network | The pill reads native `network.state`. Wi-Fi without validation shows "No internet yet"; a captive portal shows "Sign-in needed". The UI never infers connectivity (no `navigator.onLine`). |
| Nearby counts kept separate | Nearby tab: Connected · Nearby · Trusted · Pairing (incoming + outgoing). Chats pill: connected, or else discovered. Tab badge: incoming requests. |
| Sync from persistent records | `conversations[].sync` comes from the account-scoped `cloud_id_mapping` joined to `messages`. The UI only renders it. |
| Nearby / Cloud / Cloud-linked | Origin badges from native `origin`. Cloud rows come only from `cloudConversations`, which native limits to rows **not** mapped for the current account. A local row is "Nearby" only because it is a peer channel (`ensureConversation(peerId)`) **and** has no mapping. |
| Local and cloud kept apart | Cloud conversations open read-only. Linking is an explicit choice from stored cloud conversations, through the existing `linkCloudConversation(localId, cloudId)`. |
| Speak is not home | Speak button on Chats, push-to-talk overlay, composer Speak key. Works without internet; disabled only when no peer is connected. |
| AI only where supported | Context sheet: Reply, Rewrite, Translate, Summarize. Disabled with a reason when the build is unconfigured or there's no validated internet. Drafts go to the composer; tests assert no send. |

## 4. Earlier defects (still fixed)

Icon font replaced by an SVG sprite · VAANI branding · no fake waveforms or durations · no hardcoded groups or unread counts · AI draft enters the composer · four AI operations · 10 languages with real availability · Appium screenshots for every test.

## 5. Files

- **UI:** `app/src/main/assets/www/` (`index.html`, `vaani-app.js`, `vaani-mobile.css`, `tokens.css`, `fonts.css`, `fonts/`)
- **Native:** `ui/bridge/BridgeStateSources.kt` (new), `ui/bridge/ITantraNativeBridge.kt`, `data/local/CloudSyncEntities.kt`, `MainActivity.kt`
- **Tests:** `tests/android-webview/` (`run.cjs`, `fake-native.js`, `fixtures.cjs`), `tests/android/run.mjs`
- **Docs:**
  - `PLATFORM_CAPABILITY_MATRIX.md`
  - `ANDROID_BRIDGE_STATE_CONTRACT.md`
  - this report
  - `ANDROID_UI_DESIGN_SYSTEM.md`
  - `ANDROID_WEB_DESIGN_PARITY.md`
  - `ANDROID_UI_TEST_RESULTS.md`
  - `android-ui/` (116 screenshots, 9 overviews, 2 web-vs-Android side-by-sides, `results.json`)

## 6. Known limitations

- **Compile risk:** Room checks the new SQL at build time. Column names were confirmed against the entities (`messages.id`, `messages.conversationId`, `cloud_id_mapping.localId/localType/syncStatus/accountCloudUserId`), but it has not been compiled.
- **Sync status needs linking:** `queueForSync` only records mappings for linked conversations when sync is on. Waiting counts therefore appear only after linking, which is correct, but means an unlinked conversation never shows a sync line.
- **Network status is the default network only.** A Wi-Fi Direct group is not the default network and never counts as internet, which is intended.
- **Mute and speaker** are still tracked from the user's toggles, because the call manager doesn't export them.

## 7. Post-redesign build verification

The earlier compile-risk statement is superseded by an actual Windows debug build: `gradlew.bat :app:assembleDebug` completed successfully with the installed Android SDK. Kotlin compilation, Room/KSP generation, resource and manifest processing, CMake/native packaging, and WebView/font/launcher/splash APK packaging passed. The debug APK is `app/build/outputs/apk/debug/app-debug.apk` with SHA-256 `28eba8262d9c17f7f50a684cbf2eb8f50d4643a7127b694a8e1d4d32e2a23fa2`.

A pair of invalid XML comments containing `--` in the light/dark VAANI colour resources were the only confirmed build defects; their comment text was corrected without changing visual values or behavior. JVM tests passed 458/458 and instrumentation Kotlin compilation passed.

Runtime Appium E2E is still blocked: the configured `Pixel_10_Pro_XL` AVD cannot boot because the host has insufficient disk space, and no alternate device is attached. No runtime result is claimed until a booted emulator/device is available.
