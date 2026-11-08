# VAANI Reconciled Implementation Plan

Status is evidence-based: source inspection is not runtime verification.

## M0 — Preserve and establish canonical source (**current, safe**)

- **Files:** Git metadata only through additive commit(s); `client/`, `server/`, bundle, docs, `.gitignore`.
- **Work:** Preserve `vaani.git.bundle`; import/commit its web source on a preservation branch or after owner-approved reconciliation. Reconcile `.gitignore` as the union of Android and Node/secret/model exclusions. Do not delete `backend/`, `app/`, or `client/_to_delete/`.
- **Acceptance:** bundle checksum unchanged; `client/` and `server/` match preserved bundle source except documented local asset variance; Android files untouched.
- **Risk:** committing the web source directly while tracked Android docs conflict can hide history. Preserve both document histories first.

## M1 — Verify web baseline

- **Dependencies:** Node ≥22.13, locked `npm ci`, Playwright browser installation.
- **Files:** tests/config only; no visual redesign.
- **Work:** run server unit/API/realtime/speech contracts; TypeScript and production builds; add Playwright coverage for the frontend audit matrix using the scripted provider harness; test responsive desktop/tablet/mobile states.
- **Acceptance:** exact test/build reports; no claim of live Claude/Gnani verification.
- **Testing:** Playwright for web; Artemis only after a reachable Google Android device/emulator is verified.

## M2 — Cloud backend hardening (**completed: local verification**)

- **Files:** `server/src/{auth,db,store,realtime,routes}`, server tests, security docs.
- **Delivered:** additive schema v2; canonical integer cloud identity and unique case-insensitive email; user update timestamp; server-recorded JWT sessions; HTTP/WebSocket active-session checks; current-session logout revocation; migration and regression tests.
- **Acceptance evidence:** authentication, cross-user authorization, integrity, migration, typecheck, build and browser regression tests passed locally. See `MILESTONE_2_REPORT.md`.
- **Deferred:** refresh rotation/device management, durable distributed rate limits, backup/recovery and managed production data plane.

## M3 — Shared-client contract and Android cloud-sync design

- **Files:** `docs/API_CONTRACTS.md`, Android new sync/auth module only after M2; server device/sync routes and migrations.
- **Work:** define server/local ID mapping, append-only idempotent sync, conflict rules, secure Android WebSocket handshake, and state mapping between cloud sent/read and nearby ACK/delivery.
- **Acceptance:** contract tests prove no duplicate sends and no fake delivery state. Existing Bluetooth/mesh tests remain unchanged.

## M4 — Shared backend adoption by Android

- **Dependencies:** Android SDK/device, M2/M3 APIs, authenticated Android session UX.
- **Files:** additive Android networking/auth/sync module, Room migration, tests.
- **Work:** opt-in cloud account/session; sync only cloud conversations; retain nearby/offline mode and local STT/TTS.
- **Acceptance:** Android build/device test with Artemis on a connected Google device/emulator; nearby operation still works without network.

## M5 — Product gaps

- **Attachments/profile media:** object store, scanning, limits, upload APIs/UI.
- **Groups:** add-member UI using existing server endpoint; role/removal policy.
- **Messages:** edit/delete and real delivery design.
- **Speech:** verified Gnani configuration, language/voice mapping, durable transcription jobs; local Android alternatives remain supported.
- **Acceptance:** API, browser, authorization, storage, and failure tests for each feature.

## M6 — Production readiness

- Managed PostgreSQL/object storage/job queue/shared realtime; TLS/domain/CORS; monitoring; retention/backups; threat model; load and accessibility tests; provider cost controls.

## Current blockers

- Node dependencies and the Playwright Chromium browser are installed locally; reproducibility remains lockfile-based.
- No connected Android device/emulator, Android SDK, or `adb` was found.
- No live Claude/Gnani credentials are available to this execution environment.
- Android source has pre-existing upstream dirty work in `/mnt/d/vibe/vokie`; it must remain untouched.
