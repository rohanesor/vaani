# VAANI Milestone 8 Report — Android Cloud-Sync Client Implementation

## Checkpoint

- **Implementation checkpoint:** M8 changes are committed in the current Git checkpoint.
- **Base checkpoint:** `2890066 — Harden production deployment and design Android cloud-sync contract (M7)`
- **Branch:** `vaani/main`
- **Status:** Implementation complete for the server sync endpoint and Android sync data/client modules. Android build and device validation are blocked by the unavailable Android SDK in this environment.

## Baseline and Contract Audit

The M7 contract specified `POST /api/sync`, but the server did not implement that endpoint at the start of M8. The existing server already provided the required canonical primitives:

- Server-assigned integer message IDs
- Sender-scoped `(sender_id, client_id)` idempotency
- Membership authorization
- Cursor-style message history using `after`
- Server-authoritative edit and soft-delete behavior
- Persistent read cursors

M8 added the missing sync endpoint rather than introducing a competing message system.

## Server Implementation

### `POST /api/sync`

Implemented in `server/src/routes/sync.ts` and mounted behind the existing authenticated middleware.

Supported behavior:

- Requires `X-Vaani-Sync-Version: 1`
- Rejects missing and unsupported protocol versions
- Uploads up to 50 pending messages per request
- Uses the authenticated user as sender; client sender IDs are ignored
- Reuses canonical `Store.insertMessage()` idempotency
- Returns `created` for a new upload and `exists` for a replay
- Returns canonical server message IDs
- Enforces conversation membership
- Processes read cursor updates with existing max-wins semantics
- Processes sender-authorized edits and soft deletions
- Downloads messages after the opaque cursor's message ID
- Returns conversation/member snapshots and server time
- Publishes newly-created messages and lifecycle changes through the existing WebSocket hub

### Server Tests

Added `server/test/sync.test.ts` covering:

- Protocol version negotiation
- Initial sync
- Upload and canonical ID response
- Duplicate upload handling
- Non-member upload rejection
- Incremental cursor download
- Read cursor update
- Edit through sync
- Soft delete through sync

## Android Implementation

### Room schema version 8

Added `MIGRATION_7_8` without destructive recreation. New entities and DAOs are in:

`app/src/main/java/com/vokie/data/local/CloudSyncEntities.kt`

Implemented tables:

- `cloud_user`
- `cloud_id_mapping`
- `cloud_conversations`
- `cloud_members`
- `cloud_messages`
- `sync_queue`

The mapping table is scoped by authenticated cloud user:

```text
(accountCloudUserId, localType, localId) -> cloudId
```

This preserves local UUIDs, prevents cross-account mapping reuse, and keeps nearby packet IDs separate from cloud IDs.

### Sync API client

`app/src/main/java/com/vokie/cloud/SyncApiClient.kt`

Implements:

- Authenticated HTTPS requests
- Protocol version header
- Request correlation ID
- Upload/read/edit/delete request serialization
- Response parsing
- Per-item upload errors
- Retryable versus permanent exception classes
- 401, 429, 4xx, 5xx, timeout, and network handling

### Authentication client

`app/src/main/java/com/vokie/cloud/AuthClient.kt`

Implements the existing server authentication endpoints:

- Registration
- Login
- Logout

No provider keys or hardcoded credentials are included.

### Repository

`app/src/main/java/com/vokie/cloud/CloudSyncRepository.kt`

Implements:

- Account-scoped authentication state
- Persistent cursor storage
- Pending message mapping lookup
- Idempotent upload result reconciliation
- Cloud conversation/member persistence
- Cloud message persistence with repeated-page-safe `REPLACE`
- Local/cloud ID lookup
- Logout cleanup of account-scoped sync state

### Coordinator and background worker

`app/src/main/java/com/vokie/cloud/SyncCoordinator.kt`

Implements:

- Manual sync trigger
- Unique one-time WorkManager sync
- Periodic network-constrained sync
- Exponential backoff
- Auth failure termination
- Retryable failure handling
- Logout cancellation
- UI-facing sync states

Added dependency:

```kotlin
implementation("androidx.work:work-runtime-ktx:2.9.0")
```

## Preservation Decisions

- Existing Bluetooth, mesh, Wi-Fi Direct, local STT/TTS, and local message persistence were not rewritten.
- Existing local message IDs remain UUID strings.
- Nearby packet IDs are not used as cloud IDs.
- Cloud sync is a separate module and does not imply nearby delivery.
- Upload success is not reported as peer delivery.
- The existing canonical server remains the only cloud backend.

## Important Current Limitations

1. **Android SDK unavailable:** `./gradlew :app:tasks` fails because `ANDROID_HOME` and `local.properties` SDK configuration are absent.
2. **No emulator/device:** Android instrumentation and device validation were not run.
3. **UI login/opt-in wiring remains:** The client module exposes repository/coordinator APIs but does not add a redesigned authentication screen.
4. **Conversation mapping is explicit:** A local conversation must be linked to a cloud conversation before its queued messages can upload.
5. **Pending queue integration is explicit:** Callers must invoke `queueForSync()` for messages selected for cloud sync; nearby outbox behavior remains unchanged.
6. **Large-history pagination:** The server returns a bounded global page with an explicit `hasMore` flag; the Android repository follows the returned cursor after persisting each page.
7. **Token persistence security:** `CloudUserEntity` contains the token field for the existing Room-backed implementation; production UI integration should move token storage to Android Keystore/encrypted storage before release.

## Validation Results

### Server

```text
Typecheck: passed
Tests: 62/62 passed across 8 files
```

### Web client

```text
Typecheck: passed
Build: passed
Playwright: 8/8 passed
  - desktop Chrome profile
  - Pixel 5 / mobile-chrome profile
```

### Android

```text
Build: blocked
Command: ./gradlew :app:compileDebugKotlin --console=plain
Reason: SDK location not found; ANDROID_HOME and local.properties are not configured.
Unit tests: not run because Gradle project configuration requires the Android SDK.
Instrumentation/device tests: not run; no SDK, emulator, or device available.
```

No Android build or device result is claimed as passing.

## Files Added or Modified

### Added

- `server/src/routes/sync.ts`
- `server/test/sync.test.ts`
- `app/src/main/java/com/vokie/cloud/SyncApiClient.kt`
- `app/src/main/java/com/vokie/cloud/AuthClient.kt`
- `app/src/main/java/com/vokie/cloud/CloudSyncRepository.kt`
- `app/src/main/java/com/vokie/cloud/SyncCoordinator.kt`
- `app/src/main/java/com/vokie/data/local/CloudSyncEntities.kt`
- `docs/MILESTONE_8_REPORT.md`

### Modified

- `app/build.gradle.kts`
- `app/src/main/java/com/vokie/data/local/VokieDatabase.kt`
- `server/src/app.ts`
- `server/src/store.ts`
- `docs/ANDROID_CLOUD_SYNC_CONTRACT.md`
- `docs/ANDROID_SYNC_DATA_MODEL.md`
- `docs/ARCHITECTURE.md`
- `docs/API_CONTRACTS.md`
- `docs/TESTING.md`
- `docs/REMAINING_WORK.md`

## Conclusion

The canonical sync endpoint and Android client data/network/coordinator layers are implemented without changing nearby/offline transport semantics. Server and web regression suites pass. Android compilation and runtime validation remain explicitly blocked by the missing SDK/device environment and must be completed before production Android release.
