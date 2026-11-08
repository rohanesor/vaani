# Web Repair Report

## Scope

Fresh functional audit of canonical `client/` against canonical `server/` at the audit checkpoint.

## Confirmed defect and repair

**Problem:** Browser pending-message outbox was in-memory. A reload could lose unsent text.

**Repair:** `client/src/state/chat.ts` now persists up to 100 pending text messages in account-scoped browser storage (`vaani.outbox.v1.<userId>`), restores them only after the same account starts, and removes each entry on acknowledgement, permanent failure, or discard. Existing client IDs remain the server idempotency key.

**Intentional boundary:** Voice blobs are not persisted. Durable captured-audio storage needs an explicit storage, quota, retention, and privacy design; this repair does not falsely imply it.

## Tests

- Client typecheck/build: PASS.
- Playwright: PASS 10/10, including intercepted-network pending text → reload → server reconnection on desktop and mobile Chrome.
- Server tests/typecheck/build: PASS 62/62 at audit baseline.

## External limitations

Live Claude/Gnani providers remain unverified without credentials. Web does not implement Android native nearby, calls, or Android cloud-sync UI.
