# VAANI Milestone 2 Report — Database and Authentication Hardening

## Result

Milestone 2 is complete for the locally verified SQLite/web deployment. It incrementally hardens the existing backend; it does not replace the database, authentication UI, Android app, identifiers, or AI/speech integrations.

## Files changed

- `server/src/db.ts` — additive schema version 2 and exported migration list for regression testing.
- `server/src/auth.ts` — session-ID JWT claim and active-session enforcement hook.
- `server/src/store.ts` — session create/validate/revoke operations and profile update timestamp.
- `server/src/routes/core.ts` — server-session issuance and `POST /api/auth/logout`.
- `server/src/realtime.ts` — active-session verification at WebSocket upgrade.
- `client/src/state/session.tsx`, `client/src/lib/types.ts` — preserves UI, invokes server logout while always clearing local state, and mirrors canonical user timestamps.
- `server/test/database-auth-hardening.test.ts` — migration, integrity, expiry, and revocation coverage.
- Documentation listed in the Milestone request.

No Android or legacy `backend/` files were changed.

## Database changes

Schema migration v2 is additive and transactional:

- Adds nullable-then-backfilled `users.updated_at`; existing IDs, emails and passwords remain unchanged.
- Adds `auth_sessions(id, user_id, expires_at, created_at, revoked_at)` and an active-session index.
- Retains SQLite, `node:sqlite`, WAL, foreign-key enforcement, existing tables and existing migration v1.
- The migration test creates a v1 schema, sets `user_version=1`, and applies v2. No production database was opened or changed.

Canonical cloud identity remains `users.id` (integer). Email is the unique case-insensitive username-equivalent. Display name and preferred language are profile fields. There is no external identity provider in the current implementation.

## Authentication and authorization changes

- Registration and login create a random UUID session record and issue an HS256 JWT with `sub`, `iat`, `exp`, and `sid`.
- Protected HTTP routes and WebSocket upgrades require a signed, unexpired, non-revoked session owned by the claimed user.
- `POST /api/auth/logout` revokes only the calling session and returns `204`; the frontend also clears local browser state regardless of network outcome.
- Authorization continues to derive identity exclusively from the verified token. Conversation-scoped store methods enforce membership; group member additions remain owner-only.
- No edit/delete message API exists, so no unsupported edit/delete permission behavior was introduced.

## Tests executed

- `server/npm test`: **36/36 passed** across 5 files (includes all prior 33 plus new migration/session coverage).
- `server/npm run typecheck`: passed.
- `server/npm run build`: passed.
- `client/npm run typecheck`: passed.
- `client/npm run build`: passed.
- `client/npm test`: **6/6 Playwright tests passed** on desktop Chrome and Pixel 5 mobile Chrome profiles.

## Known limitations and blockers

- Node's SQLite module emitted its expected experimental-feature warning.
- JWT remains in browser localStorage; refresh-token rotation, device management, password reset, MFA, CSP/XSS/CSRF production hardening, durable distributed rate limits, encrypted backups, and audit retention remain future work.
- Query-string WebSocket tokens remain for current web compatibility; future Android cloud sync requires a secure handshake/header contract.
- No live Claude/Gnani validation, physical microphone test, Android SDK build, or Artemis-connected device test was performed.

## Recommended next milestone

M3: define and test the shared Android/cloud contract: opt-in authentication, public/local ID mapping, idempotent push/pull sync, conflict policy, secure Android realtime handshake, and a Room mapping migration. Do not change Android nearby/mesh or offline speech behavior until that contract is approved.
