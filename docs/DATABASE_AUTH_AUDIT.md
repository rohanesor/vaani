# Database and Authentication Audit — Milestone 2

**Audited source:** `server/` at checkpoint `de788c2`. Source inspection completed before Milestone 2 code changes.

| Capability | Finding | Classification |
|---|---|---|
| Database engine/client | Node 22 built-in `node:sqlite` / `DatabaseSync`; WAL and foreign keys enabled | Implemented and baseline-verified |
| Schema/migrations | One in-code `PRAGMA user_version` migration; transactional apply | Implemented but incomplete: no immutable migration modules or migration regression test |
| User identity | Integer primary key, unique case-insensitive email, display name, preferred language, created timestamp | Implemented but incomplete: email is the current username-equivalent; no `updated_at` |
| Password storage | scrypt N=16384 with random 16-byte salt and timing-safe comparison | Implemented and baseline-verified |
| Login/register | Validated email/password endpoints and per-IP in-process limiter | Implemented and baseline-verified |
| Session | HS256 JWT, 14-day expiry, browser localStorage | Implemented but incomplete: no server revocation/logout/session record |
| Protected HTTP routes | Bearer middleware derives user ID; membership gate returns 404 for non-members | Implemented and baseline-verified |
| WebSocket auth | Signed JWT query parameter verified on upgrade | Implemented but incomplete: no revoked-session check |
| Authorization tests | Conversation/message/search/AI/audio and non-owner group-add coverage exists | Implemented and baseline-verified |
| Message edit/delete permissions | No edit or delete endpoint/model behavior exists to authorize | Missing; explicitly deferred rather than inventing semantics |
| Frontend auth integration | Session bootstraps `/me`, clears local token on 401, local-only logout | Implemented but incomplete: logout does not revoke server session |
| Referential integrity | Users/conversations/members/messages have foreign keys; FK PRAGMA enabled | Implemented and baseline-verified |
| Ordering/pagination/idempotency | Message server ID order, bounded `before` pagination, sender/client ID uniqueness | Implemented and baseline-verified |
| External auth provider | None | Not used; a custom local password system is appropriate for the current self-contained MVP |

## Smallest safe hardening set

1. Add a non-destructive schema version for `users.updated_at` and server-side session records.
2. Issue JWTs with a server-recorded random session ID; verify active session for HTTP and WebSocket access.
3. Add authenticated logout that revokes only the current session; retain client token clearing.
4. Add migration and revocation/authorization regression tests.
5. Do not change Android, cloud conversation IDs, AI, Gnani, or existing message semantics.

## Canonical identity decision

The existing `users.id` integer is the stable canonical cloud user ID for the current API. `email` is its unique, case-insensitive username-equivalent. Display name and preferred language remain mutable profile attributes. A future UUID/ULID public-ID migration is deferred; no identifier replacement is needed for this milestone.
