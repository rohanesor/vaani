# VAANI Milestone 10 Report — Android Build, Migration, and Integration Validation

## Status

**Superseded by M11 validation.** M10 originally recorded an unavailable Android SDK. M11 installed the official SDK outside the repository, repaired the Android data-layer compilation failures, and completed Android compilation, APK assembly, and JVM unit-test validation. Instrumentation execution remains device-blocked.

## Initial State

- **Initial commit:** `2656704 — Integrate Android cloud sync and secure account credentials (M9)`
- **Branch:** `vaani/main`
- **Initial working tree:** clean
- `/mnt/d/agent/artemis` was inspected. It contains Artemis Python Android-driver modules, but no Android SDK, `adb`, emulator, or `sdkmanager`.
- `docs/API.md` does not exist; `docs/API_CONTRACTS.md` is the canonical API document.

## SDK Inspection

Checked:

- `ANDROID_HOME`: unset
- `ANDROID_SDK_ROOT`: unset
- `/opt/android-sdk`: absent
- `/opt/android-sdk-linux`: absent
- `/usr/local/android-sdk`: absent
- `/home/rose/Android/Sdk`: absent
- `/root/Android/Sdk`: absent
- common Windows-mounted Android SDK paths: absent
- `adb`: unavailable
- `sdkmanager`: unavailable
- `emulator`: unavailable

## Exact Android Commands

M11 re-ran the Android validation with the SDK configured externally:

| Command | Result |
|---|---|
| `./gradlew --version` | **PASS** — Gradle 8.9, Java 17 |
| `./gradlew :app:compileDebugKotlin --console=plain` | **PASS** |
| `./gradlew :app:assembleDebug --console=plain` | **PASS** |
| `./gradlew :app:testDebugUnitTest --console=plain` | **PASS** |
| `./gradlew :app:connectedDebugAndroidTest --console=plain` | **BLOCKED** — no connected devices |

The SDK was installed outside the repository. No machine-specific configuration was committed.

## M10 Changes

### Credential instrumentation coverage

Added:

- `app/src/androidTest/java/com/vokie/cloud/SecureCredentialStoreTest.kt`
- `app/src/androidTest/java/com/vokie/cloud/CloudSyncAccountIsolationTest.kt`

Coverage includes:

- Store/retrieve
- Replacement
- Clear/missing credential
- Corrupted ciphertext fail-closed behavior
- Empty token rejection
- No plaintext bearer token in the encrypted preference value
- Explicit opt-in default/disable behavior
- Session-generation rotation

These tests were added but are **not claimed as executed** because Gradle cannot configure the Android project without the SDK.

### Legacy credential field decision

`legacyJwtToken` is retained temporarily.

Reason: a direct upgrade from a version-8 database to a future version that removes the field would bypass application startup migration and could lose the only legacy token before it can be transferred to Keystore. The current safe path is:

1. Room migration 8→9 renames/copies `jwtToken` to `legacyJwtToken`.
2. Application startup transfers it to Keystore.
3. The DAO clears it only after successful secure storage.
4. A future cleanup migration may remove the field after supported upgrade adoption is demonstrated.

Active credential reads use `SecureCredentialStore` only. No runtime path reads `legacyJwtToken` for authorization.

### Account isolation review

Verified statically:

- Mapping primary key includes `accountCloudUserId`.
- Cursor is stored in the active cloud-user row and is cleared on logout.
- Session generation rotates on login/logout.
- Worker input includes the generation.
- Worker checks opt-in, generation, and Keystore token before creating the sync request.
- Logout cancels unique work and clears secure credentials.
- Local nearby messages are not deleted.

### Bridge review

Verified statically:

- Existing WebView bridge remains the integration point.
- Login, logout, opt-in, manual sync, and linking methods were added.
- Native repository checks enforce authentication, opt-in, account mapping, and membership-derived cloud conversations.
- Password fields are cleared after successful login.
- Tokens are never sent to JavaScript or included in bridge state JSON.

Bridge behavior could not be exercised on an Android runtime in this environment.

## Regression Results

| Suite | Result |
|---|---|
| Server typecheck | **PASS** |
| Server build | **PASS** |
| Server tests | **PASS — 62/62 across 8 files** |
| Client typecheck | **PASS** |
| Client build | **PASS** |
| Playwright | **PASS — 8/8 desktop + Pixel 5 profiles** |
| Android compile | **PASS** |
| Android APK assembly | **PASS** |
| Android unit tests | **PASS — 458 tests, 0 failures** |
| Room migration tests | **NOT RUN** — no connected device; migration included in compile/schema generation |
| Android instrumentation | **BLOCKED** — no connected devices |
| Emulator/device | **NOT AVAILABLE** |
| Physical nearby radio | **NOT AVAILABLE** |

## Nearby/Offline Preservation

No Bluetooth, mesh, Wi-Fi Direct, local STT/TTS, nearby ID, or local message transport code was redesigned. Cloud sync remains additive and local message creation does not require authentication or network connectivity.

Runtime radio regression could not be claimed without an Android device or emulator.

## Known Limitations and Blockers

1. Android SDK/toolchain is unavailable in the current environment.
2. Android-specific tests are present but unexecuted.
3. `legacyJwtToken` remains as a temporary safe-upgrade field.
4. Cloud conversation linking still requires a canonical conversation already downloaded into the cloud cache.
5. No emulator or physical device is available for login, sync, logout, or nearby/offline runtime validation.

## Security Review

- No secrets committed.
- No SDK paths or `local.properties` committed.
- No bearer tokens added to logs or UI state.
- Keystore-backed credential storage remains the active credential path.
- Corrupted secure storage fails closed.

## Final Checkpoint

The final M10 checkpoint is recorded in Git after the available validation and documentation updates. The final commit hash and working-tree state are reported in the completion response.
