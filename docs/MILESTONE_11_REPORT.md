# VAANI Milestone 11 Report — Android SDK Setup, Build Repair, and Full Validation

## Checkpoint

- **Initial commit:** `9ab5b70 — Validate Android cloud sync and credential migration (M10)`
- **Branch:** `vaani/main`
- **Final commit:** assigned after final review
- **Working tree before M11:** clean

## SDK Installation

The environment is Linux/WSL2 with Java 17 and Gradle 8.9. Network access to the official Google distribution was available.

Installed the official Android command-line tools from:

```text
https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
```

SDK was installed outside the repository at:

```text
/mnt/d/android-sdk
```

This location is not committed and no `local.properties` was created.

Installed packages:

| Package | Version | Result |
|---|---:|---|
| Command-line tools | 12.0 | PASS |
| `platform-tools` | 37.0.1 | PASS |
| `platforms;android-34` | API 34 | PASS |
| `build-tools;34.0.0` | 34.0.0 | PASS |
| `ndk;27.0.12077973` | 27.0.12077973 | PASS |
| `cmake;3.22.1` | 3.22.1 | PASS |

SDK licenses were accepted through `sdkmanager`. `adb version` reported Android Debug Bridge 1.0.41 / platform-tools 37.0.1.

## Build Configuration Confirmed

- `compileSdk = 34`
- `targetSdk = 34`
- `minSdk = 24`
- Java/Kotlin target: 17
- Gradle: 8.9
- Kotlin compiler: 1.9.23 wrapper environment / project toolchain resolved successfully
- NDK: 27.0.12077973
- CMake: 3.22.1

## Android Build Commands and Results

| Command | Result |
|---|---|
| `./gradlew --version` | **PASS** |
| `./gradlew :app:compileDebugKotlin --console=plain` | **PASS** |
| `./gradlew :app:assembleDebug --console=plain` | **PASS** |
| `./gradlew :app:testDebugUnitTest --console=plain` | **PASS** |
| `./gradlew :app:connectedDebugAndroidTest --console=plain` | **BLOCKED** — no connected devices |

The debug APK was assembled successfully. Native CMake emitted the existing informational warning that CT2 native archives were unavailable; the optional `vokie_ct2` target was not built, but the application build completed successfully.

## Build Failures Repaired

The first real SDK-backed compilation exposed missing local data-layer types referenced by existing code. These were repaired additively:

- Added `TrustedPeerEntity` and `TrustedPeerDao`
- Added `PairRequestEntity` and `PairRequestDao`
- Added `ConversationEntity` and `ConversationDao`
- Added `MessageDeliveryEntity` and `MessageDeliveryDao`
- Added `PairingRepository`
- Added `ConversationRepository`
- Added missing `MessageDao.observeByPeer()` and `observeByConversation()` methods
- Added the local `conversationId` field to `MessageEntity`
- Added Room migration 9→10
- Added required Room database registrations and accessors
- Updated the existing in-memory `MessageDao` test fake

No nearby transport architecture was replaced.

## Room Schema and Migration

Room schema now reaches version 10:

- 7→8: cloud sync tables
- 8→9: legacy token transition field for Keystore migration
- 9→10: local conversation reference, trusted peers, pair requests, conversations, and delivery records

Generated schema snapshots for versions 9 and 10 were produced by the real Room KSP build.

The `legacyJwtToken` field remains intentionally retained for safe direct upgrades from version 8. Runtime authorization reads only from Keystore-backed credentials. Removal requires direct-upgrade evidence and will remain a future cleanup migration.

## Android JVM Unit Tests

```text
Task: :app:testDebugUnitTest
Result: PASS
Total tests: 458
Failures: 0
Errors: 0
Skipped: 0
```

The unit suite includes existing nearby/offline communication coverage and the repaired `RoomMessageRepositoryTest` implementation.

## Instrumentation and Device Validation

The instrumentation task compiled the Android test APK and all instrumentation Kotlin sources successfully, then stopped at execution:

```text
Task: :app:connectedDebugAndroidTest
Result: BLOCKED
Reason: No connected devices
```

`adb devices -l` returned an empty device list.

Emulator support was not forced because `/dev/kvm` is absent in the WSL2 environment. No system image or emulator was created without hardware acceleration support.

Therefore the following remain unexecuted:

- Keystore instrumentation tests
- Room migration runtime tests
- WebView/native bridge runtime tests
- Login and opt-in flow on Android runtime
- Account-switch and stale-worker runtime flow
- Nearby Bluetooth/mesh/Wi-Fi Direct device validation

## Web and Server Regression Results

| Suite | Result |
|---|---|
| Server typecheck | **PASS** |
| Server build | **PASS** |
| Server tests | **PASS — 62/62 across 8 files** |
| Client typecheck | **PASS** |
| Client build | **PASS** |
| Playwright first run | **7/8**, one transient desktop startup/internal-response failure |
| Playwright rerun | **PASS — 8/8** |

The Playwright rerun passed all desktop and Pixel 5/mobile-chrome profiles.

## Security and Repository Review

- No SDK files were added to Git.
- No `local.properties` was created or committed.
- No signing keys or credentials were added.
- No bearer tokens are logged or sent through JavaScript bridge state.
- Active credentials remain Keystore-backed.
- `legacyJwtToken` is migration-only and not an active authorization source.
- Generated build directories remain ignored.

## Remaining Blockers

1. No connected emulator or physical Android device.
2. No `/dev/kvm`, so headless emulator setup is not appropriate in this environment.
3. Room upgrade and Keystore instrumentation tests require a device/emulator.
4. Physical nearby radio validation requires real hardware.
5. Optional CT2 native archives remain unavailable, as documented by the existing CMake warning.

## Final Status

M11 successfully removed the SDK blocker, repaired Android compilation defects, produced a debug APK, and passed the complete Android JVM unit suite. Runtime Android integration and instrumentation remain explicitly blocked only by the absence of a connected device/emulator.
