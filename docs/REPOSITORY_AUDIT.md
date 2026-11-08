# VAANI Repository Reconciliation Audit

**Audit date:** 2026-11-02  
**Scope:** read-only inspection completed before reconciliation work. No reset, checkout, merge, deletion, or overwrite was performed.

## Executive finding

`D:\vibe\vaani` and `D:\vibe\VAANI` resolve to the **same case-insensitive Windows directory**. It is one Git worktree, not two directories. That one worktree currently contains two independent implementations:

1. **Android/iTantra-derived implementation** — tracked on Git branch `vaani/main`, commit `f228b92`; Kotlin Android module at `app/`, native/JNI speech, Room, Bluetooth/Wi-Fi Direct/mesh infrastructure.
2. **VAANI v2 web implementation** — currently untracked working-tree directories `client/` and `server/`, but recoverable in full from `vaani.git.bundle`, branch `main`, commit `081854c` (parent `a55390d`). It is a React/Vite frontend and Node/Express/WebSocket/SQLite backend.

The v2 web implementation is the implementation matching the reported **Claude Design** frontend (Peacock/Iris/Marigold, transcript interface). It is not connected to the Android client. The Android app is not part of the web MVP runtime today.

## Filesystem and Git findings

| Location | Git status / role | Findings |
|---|---|---|
| `D:\vibe\vaani` / `D:\vibe\VAANI` | One worktree, branch `vaani/main`, `f228b92` | Case aliases of one directory. Contains tracked Android foundation plus untracked web product. |
| `D:\vibe\vokie` | `sih/ui-fidelity-corrections-wip`, commit `c8f6bb1`, dirty | Original iTantra-derived source. Do not modify; it has substantial pre-existing staged and unstaged work. |
| `D:\vibe\vaani\vaani.git.bundle` | Valid complete Git bundle | Contains web commits `a55390d` and `081854c`; this is the recovery source for the web implementation and its original product docs. |
| `D:\vibe\vaani\client` | Untracked | React 19/Vite/TypeScript Claude Design frontend. Source matches bundle `081854c` except `public/favicon.svg`; legacy components are retained under `client/_to_delete/`. |
| `D:\vibe\vaani\server` | Untracked | Node ≥22.13/Express 5/WebSocket backend. Source matches bundle `081854c`. |
| `D:\vibe\vaani\app` | Tracked | Android Kotlin module adapted from iTantra/Vokie. Independent application ID is `com.vaani.app`; implementation packages and native/database compatibility names remain `com.vokie`. |
| `D:\vibe\vaani\backend` | Tracked | Earlier minimal Node Claude proxy added to the Android foundation. It overlaps conceptually with the much more complete untracked `server/`; it must not become a second production backend. |

### Branches and working trees

- `/mnt/d/vibe/vokie`: `sih/ui-fidelity-corrections-wip` at `c8f6bb1`, dirty before this audit.
- `/mnt/d/vibe/VAANI`: `vaani/main` at `f228b92`, with pre-existing uncommitted web/frontend/backend additions and documentation replacements.
- `/tmp/vokie-p115a`: detached at `7ef8509`; outside product scope.

No branch was switched or altered during this audit.

## Competing implementation comparison

| Component | Android/iTantra-derived implementation | Claude Design web implementation | Recommended action |
|---|---|---|---|
| Frontend | Android WebView presentation with legacy emergency/nearby communication UI | React transcript UI, light/dark themes, Peacock/Iris/Marigold tokens, Indic fonts, responsive index/stage layout | **Canonical web UI:** preserve `client/` and its design system. Do not redesign it. |
| Backend | No cloud communication backend; `backend/` is only a minimal Claude proxy | `server/` has Express APIs, WebSocket hub, auth, SQLite store, AI orchestration and speech adapters | **Canonical cloud backend:** preserve/adopt `server/`; retire the earlier `backend/` proxy from production use after an explicit migration decision. |
| Database | Room SQLite on-device: peers/messages/transport/offline state | Server SQLite (`node:sqlite`) with FTS5, users, conversations, members, messages, reactions, voice, translations, AI usage/actions/tasks | Keep both for different scopes. Room is offline-device state; server SQLite is web/cloud conversation persistence. Do not merge schemas. |
| Communication | Bluetooth RFCOMM, queued packets, mesh/store-forward, experimental Wi-Fi Direct; local STT/TTS | HTTPS API, WebSocket messages/presence/typing/read events, browser outbox | Keep transports separate. Define future Android sync adapter against server APIs; do not treat mesh as WebSocket transport. |
| Claude | Android request/provider contracts and an unused server proxy bootstrap | Server-side Anthropic SDK provider with orchestration, privacy filtering, streaming and tests using a fake provider | Use web `server/src/ai/` as the canonical Claude path. Live credentials have not been verified. |
| Gnani | No Gnani integration; local Whisper/IndicConformer and sherpa-onnx implementations | Server Gnani STT/TTS REST adapters, WAV parsing/slicing, mocked contract tests | Preserve local Android speech as an offline option. Treat Gnani integration as present but **not live-verified**. |

## Frontend integration finding

The Claude Design frontend is connected in source to a real backend contract:

- `client/src/lib/api.ts` sends authenticated `/api/*` requests and SSE assistant requests.
- `client/src/lib/realtime.ts` creates authenticated `/ws?token=` WebSockets.
- `client/src/state/chat.ts` loads conversations, messages, sends idempotent text/voice messages, maintains a browser-memory outbox, and consumes realtime events.
- `server/src/app.ts`, `routes/core.ts`, `routes/ai.ts`, and `realtime.ts` implement the matching endpoints/events.

This is an implemented integration, not a disconnected mock UI. It is **not independently runtime-verified in this audit** because dependencies are not installed and no browser/live-provider run was performed. The repository’s claim of 33 server tests is supported by inspection: there are 33 `it`/`test` cases across server tests, but they still require execution.

## Database and API finding

The web server already has persistent SQLite storage in `server/src/db.ts` (`VAANI_DB_PATH`, default `./data/vaani.db`) with `PRAGMA user_version` migration execution. Its initial versioned schema contains users/settings, conversations/members, messages, FTS5 search, reactions, voice-note BLOBs/transcript metadata, translations, AI usage/actions, and tasks. Membership is centrally enforced by `Store.assertMember` for conversation-scoped access.

There are no separate SQL migration files; migrations are an in-code array. This is adequate for the current initial schema but needs immutable, numbered migration modules before production deployments.

## Documentation reconciliation and recoverability

The following documents have two conflicting versions in the same worktree:

- `README.md`, `docs/PRD.md`, `docs/ARCHITECTURE.md` (case-preserved as `architecture.md` by the original worktree), `docs/UI_UX_SPEC.md`, and `docs/IMPLEMENTATION_PLAN.md`.
- Git commit `f228b92` contains the Android/iTantra-derived product decisions.
- The current uncommitted versions describe the web VAANI v2 product and match the valid bundle’s committed content.

Both are recoverable: Android versions are in `f228b92`; web versions are in `vaani.git.bundle` commit `081854c`. No content is considered lost. The bundle also preserves the original Claude Design documents: `DESIGN_SYSTEM.md`, `INTERACTION_SPEC.md`, `RESPONSIVE_SPEC.md`, and `SCREEN_INVENTORY.md`.

## Protection list

Do **not** overwrite, reset, delete, or blindly normalize these paths:

- `vaani.git.bundle` — recovery source for the full web product history.
- `client/` and `server/` — untracked but complete web implementation.
- Current uncommitted root docs and `.gitignore` — they are web-product content/configuration and conflict with Android versions.
- `app/`, `backend/`, all Android docs/assets/models/JNI files — Android foundation; no mass rename or deletion.
- `/mnt/d/vibe/vokie` working tree — original source and existing user work.
- `client/_to_delete/` — legacy visual components; retain until the owner explicitly approves cleanup.

## Risks

1. **Two backends:** tracked `backend/` and untracked `server/` would create duplicate Claude services if both are deployed.
2. **`.gitignore` regression:** the current uncommitted web-focused `.gitignore` removes Android/model/key exclusions from the tracked Android foundation. This is a security and artifact-risk; reconcile by unioning exclusions, not replacing either list.
3. **Documentation collision:** web docs assert “no iTantra source was available,” which is false in this worktree. Android docs assert a nearby/offline MVP that differs from the web MVP.
4. **Authentication limitations:** web sessions are stateless HS256 JWTs in browser localStorage; logout is client-side token removal and there is no token revocation/refresh mechanism.
5. **Production persistence:** SQLite and in-process rate limiting are viable for local/single-instance MVP only; no backup, durable jobs, object storage, attachment storage, or multi-instance realtime adapter exists.
6. **External providers:** Claude and Gnani integrations are mocked/contract-tested only. Model names/endpoints must be live-verified with approved credentials before being marked operational.
7. **Android verification:** Android source has not built in this environment because the Android SDK is not configured; nearby/mesh functionality cannot be claimed verified here.

## Canonical architecture recommendation

Establish a monorepo with explicit client boundaries without merging their runtime concerns:

```text
client/                 canonical Claude Design web/PWA client
server/                 canonical internet backend + SQLite MVP store + WebSockets
app/                    retained Android offline/nearby client (future server-sync adapter)
backend/                deprecated prototype; do not deploy alongside server/
vaani.git.bundle        retain until web history is imported and verified
```

The immediate next milestone is to safely import/commit the bundle-derived web implementation into `vaani/main` (or a dedicated preservation branch), reconcile `.gitignore` as a union, and then run its tests/builds. Only after that should backend changes be made. The Android app remains a separately verified client track and should later share an explicit versioned HTTP/WebSocket API contract with `server/`.
