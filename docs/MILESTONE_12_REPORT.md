# VAANI Milestone 12 Report — Runtime Validation Readiness and Migration Safety

> M13 attempted runtime execution. Android instrumentation remains blocked because no device/emulator is available; see `docs/MILESTONE_13_REPORT.md`.

## Baseline and Repository State

- **Repository used:** `/mnt/d/vibe/vaani` (the requested `/mnt/d/agent/artemis` is a separate Artemis repository on `main` and does not contain VAANI)
- **Initial commit:** `35c8e61 — Install Android SDK and validate VAANI cloud sync (M11)`
- **Branch:** `vaani/main`
- **Initial working tree:** clean
- **Final commit:** assigned after final review

## Migration-Chain Audit

### 7 → 8

Adds the cloud-sync schema:

- `cloud_user` with the legacy `jwtToken` field
- `cloud_id_mapping` with account-scoped composite identity
- `cloud_conversations`
- `cloud_members`
- `cloud_messages`
- `sync_queue`

Existing version-7 local message, peer, transport, emergency, packet, and settings rows are preserved. New fields are nullable or have explicit defaults. No foreign keys are introduced by this migration.

### 8 → 9

Rebuilds `cloud_user` and replaces the active `jwtToken` column with nullable `legacyJwtToken`.

Existing cloud-user metadata and the legacy token value are copied. The migration itself does not access Android Keystore; application startup subsequently transfers the legacy value into Keystore and clears the migration field only after successful secure persistence.

This field is intentionally retained for safe direct upgrades from version 8. Removing it in a SQL-only migration would risk losing a legacy credential before application code can migrate it.

### 9 → 10

Adds:

- Nullable `messages.conversationId`
- `trusted_peers`
- `pair_requests`
- `conversations`
- `message_deliveries`

Existing messages and cloud tables are preserved. The new tables are additive and have no destructive fallback. The local conversation identifier remains distinct from canonical cloud IDs.

### Direct upgrades

The registered Room chain supports:

- 7 → 8 → 9 → 10
- 8 → 9 → 10
- 9 → 10

The committed historical schema snapshots now include versions 8, 9, and 10. Version 8 was reconstructed from the committed version-9 schema with the documented M8 `jwtToken` shape because M8 had not committed a version-8 snapshot.

## Migration Tests Added

Added:

```text
app/src/androidTest/java/com/vokie/data/local/VokieDatabaseMigrationTest.kt
```

Coverage authored:

- 7 → 8
- 8 → 9
- 9 → 10
- Direct 7 → 10
- Direct 8 → 10
- Existing local message preservation
- Cloud table creation
- Legacy token copy into `legacyJwtToken`
- Legacy token retention until application-level Keystore transfer
- New local communication table creation

Status: **AUTHORED AND COMPILES; NOT EXECUTED** because no Android device/emulator is connected.

## Keystore Credential-Transition Audit

Verified by source review and existing instrumentation coverage:

- Active token reads use `SecureCredentialStore`, not Room.
- Storage uses Android Keystore AES/GCM.
- Corrupted ciphertext fails closed and returns no token.
- Empty tokens are rejected.
- Logout clears the encrypted token and disables sync.
- Legacy migration writes to Keystore before clearing `legacyJwtToken`.
- Migration failures clear the secure credential and require reauthentication.
- Tokens are not placed in WebView state JSON or logged.

The existing `SecureCredentialStoreTest` remains instrumentation-only and is compiled but not executed without a device.

## Instrumentation Readiness Matrix

| Test area | Test location | Environment | Status |
|---|---|---|---|
| Keystore store/replace/clear/corruption | `SecureCredentialStoreTest` | Android instrumentation | Authored, compiled, not executed |
| Opt-in/session generation | `CloudSyncAccountIsolationTest` | Android instrumentation | Authored, compiled, not executed |
| Room 7→8/8→9/9→10 | `VokieDatabaseMigrationTest` | Android instrumentation | Authored, compiled, not executed |
| Direct 7→10/8→10 | `VokieDatabaseMigrationTest` | Android instrumentation | Authored, compiled, not executed |
| Local message repository | Existing JVM suite | JVM | Executed and passing |
| Nearby protocol/routing | Existing JVM suite | JVM | Executed and passing |
| WebView/native bridge | Android runtime | Device/emulator | Not executed |
| Bluetooth/mesh/Wi-Fi Direct | Physical hardware | Device required | Not executed |

## Validation Results

### Android

| Command | Result |
|---|---|
| `./gradlew --version` | **PASS** |
| `./gradlew :app:compileDebugKotlin --console=plain` | **PASS** |
| `./gradlew :app:assembleDebug --console=plain` | **PASS** |
| `./gradlew :app:testDebugUnitTest --console=plain` | **PASS — 458 tests, 0 failures** |
| `./gradlew :app:compileDebugAndroidTestKotlin --console=plain` | **PASS** |
| `./gradlew :app:connectedDebugAndroidTest --console=plain` | **BLOCKED** — no connected devices |

### Server and web

- Server typecheck: **PASS**
- Server build: **PASS**
- Server tests: **PASS — 62/62**
- Client typecheck: **PASS**
- Client build: **PASS**
- Playwright: **PASS — 8/8** on desktop and Pixel 5 profiles

## Device and Hardware Blockers

- `adb devices -l` returned no devices.
- `/dev/kvm` is unavailable in WSL2.
- No physical Android device is connected.
- No emulator was forced into an environment without hardware acceleration.

Consequently, no runtime login, Keystore, migration, WebView bridge, cloud sync, Bluetooth, mesh, or Wi-Fi Direct behavior is claimed as executed in M12.

## Security Review

- No credentials, tokens, signing files, SDK paths, or `local.properties` were committed.
- The external SDK remains outside the repository.
- `legacyJwtToken` is not an active authorization source.
- Migration tests use fictional values only.
- Nearby/offline transport code was not changed by M12.

## Remaining Risks

1. Migration tests require execution on an Android runtime.
2. Keystore behavior under key invalidation requires instrumentation coverage.
3. WebView bridge and cloud account lifecycle require a device/emulator.
4. Physical radio behavior remains unverified.
5. `legacyJwtToken` cannot be removed until direct-upgrade adoption evidence exists.

## Recommended Next Milestone

**M13: Device-backed Android runtime validation**, covering migration execution, Keystore behavior, login/opt-in/logout, stale-worker handling, WebView bridge flows, and available nearby transport tests.
