# VAANI Milestone 9 Report — Android Cloud-Sync Integration and Security Completion

## Checkpoint and Baseline

- **Initial checkpoint:** `841156d — Implement Android cloud-sync client and canonical sync endpoint (M8)`
- **Branch:** `vaani/main`
- **Working tree before M9:** clean
- **Status:** M9 implementation completed and checkpointed after available validation.

`docs/API.md` requested by the milestone brief does not exist in this repository. The canonical API documentation is `docs/API_CONTRACTS.md`, which was updated.

## Implemented Features

### Cloud account UI integration

The existing Android UI is a WebView application backed by `ITantraNativeBridge`. M9 extends that existing surface rather than introducing a second UI architecture:

- Sign-in form in the existing Settings screen
- Active account display
- Sign-out action
- Explicit cloud-sync toggle
- Manual “Sync now” action
- User-visible login errors without tokens or raw backend payloads
- Nearby/offline messaging remains available while signed out

Sync remains disabled by default and login does not enable it.

### Automatic local lifecycle integration

`RoomMessageRepository.createMessage()` now accepts a central `onLocalMessageCreated` callback. The application wires this callback to the cloud repository.

A message is eligible for cloud queueing only when:

- A cloud account is active
- Sync opt-in is enabled
- The message has a local conversation ID
- That local conversation has an account-scoped canonical cloud mapping

Messages created while offline remain locally persisted. Nearby transport behavior is not changed.

### Cloud conversation linking

`CloudSyncRepository.linkLocalConversation()` now requires the canonical cloud conversation to have been downloaded and membership-authorized by the server. It then persists:

```text
(accountCloudUserId, localType="conversation", localId) -> cloudId
```

The local conversation ID is unchanged. A bridge/VM entry point is available for explicit linking. Pending local messages remain local until a valid mapping exists.

### Secure credential storage

Added `SecureCredentialStore` using:

- Android Keystore AES key
- AES/GCM encryption
- Encrypted payload in private SharedPreferences
- Explicit clear operation on logout

Bearer tokens are no longer represented as the active credential field in the Room entity.

### Legacy credential migration

Room migration **8 → 9** changes the legacy `jwtToken` column to a temporary `legacyJwtToken` migration field. On application startup:

1. The legacy token is read once.
2. It is written to Keystore-backed storage.
3. The legacy Room field is cleared only after secure storage succeeds.
4. If migration fails, secure credentials are cleared and reauthentication is required.

The temporary migration field remains nullable in the schema for compatibility, but active requests read only from `SecureCredentialStore`.

### Account isolation and stale-worker protection

- Mapping primary key includes `accountCloudUserId`.
- Pending message queries are account-scoped.
- Logout clears account mappings, cloud cache, sync queue, credentials, and disables opt-in.
- Local nearby messages and conversations are not deleted on logout.
- A session-generation nonce is rotated on login/logout.
- WorkManager requests carry the generation and exit without network access if stale.
- Workers re-check opt-in and Keystore credentials immediately before sync.

### Retry behavior

Existing WorkManager exponential backoff is preserved and strengthened:

- Network/server/rate-limit failures retry
- Auth failures stop and require login
- Disabled sync exits successfully without uploading
- Permanent validation failures do not retry indefinitely
- Unique work prevents duplicate sync jobs
- Periodic sync starts only after explicit opt-in

## Files Added or Modified

### Added

- `app/src/main/java/com/vokie/cloud/SecureCredentialStore.kt`
- `docs/MILESTONE_9_REPORT.md`

### Modified

- `app/src/main/java/com/vokie/data/local/CloudSyncEntities.kt`
- `app/src/main/java/com/vokie/data/local/VokieDatabase.kt`
- `app/src/main/java/com/vokie/cloud/CloudSyncRepository.kt`
- `app/src/main/java/com/vokie/cloud/SyncCoordinator.kt`
- `app/src/main/java/com/vokie/ui/communication/CommunicationViewModel.kt`
- `app/src/main/java/com/vokie/ui/bridge/ITantraNativeBridge.kt`
- `app/src/main/java/com/vokie/VokieApplication.kt`
- `app/src/main/java/com/vokie/data/MessageRepository.kt`
- `app/src/main/assets/www/index.html`
- `docs/ANDROID_CLOUD_SYNC_CONTRACT.md`
- `docs/ANDROID_SYNC_DATA_MODEL.md`
- `docs/ARCHITECTURE.md`
- `docs/API_CONTRACTS.md`
- `docs/TESTING.md`
- `docs/REMAINING_WORK.md`

## Nearby/Offline Preservation

The following were not replaced or redesigned:

- Bluetooth communication
- Mesh/store-and-forward behavior
- Wi-Fi Direct transport
- Local Room message persistence
- Local STT/TTS
- Nearby IDs and packet IDs
- Existing local outbox and delivery states

Cloud sync is additive and unavailable cloud services do not block local message creation or nearby transport.

## Validation Results

### Server

```text
Server typecheck: passed
Server build: passed
Server tests: 62/62 passed across 8 files
```

### Web client

```text
Client typecheck: passed
Client build: passed
Playwright: 8/8 passed
  - desktop Chrome
  - Pixel 5 / mobile-chrome
```

### Android

Attempted command:

```text
./gradlew :app:compileDebugKotlin --console=plain
```

Result:

```text
BLOCKED: SDK location not found.
ANDROID_HOME and ANDROID_SDK_ROOT are unset.
local.properties with sdk.dir is absent.
```

Therefore:

- Android compilation: not verified
- Android JVM/unit tests: not run because Gradle project configuration requires the Android SDK
- Instrumentation tests: not run
- Emulator/device validation: not available

No Android build or device success is claimed.

## Known Limitations

1. Android cloud login and opt-in are integrated into the existing WebView Settings surface, but no new account-registration UI was added.
2. Cloud conversation linking requires a canonical conversation already present in the downloaded cloud cache; creating a new cloud conversation from a nearby-only peer remains future work.
3. Room’s temporary `legacyJwtToken` field exists solely for migration compatibility and should be removed in a later cleanup migration after supported upgrade coverage is complete.
4. Physical radio/device regression was not possible in this environment.
5. The existing Android source had no available SDK/toolchain in this environment, so Kotlin/Room schema compilation remains a required external validation step.

## Security Review

- No API keys were added.
- No tokens are logged.
- No authorization headers are logged.
- No local SDK paths, signing files, or environment files were added.
- Active bearer credentials are read from Keystore-backed storage.
- Logout clears secure credentials and invalidates sync work.

## Remaining Work

- Configure a real Android SDK and run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- Run Room migration tests and instrumentation tests.
- Validate login, opt-in, sync, logout, and nearby messaging on an emulator/device.
- Add a user-facing cloud conversation creation/link picker for nearby peers.
- Remove the temporary legacy token field after migration adoption is verified.
