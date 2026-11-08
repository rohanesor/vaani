# VAANI Milestone 3/4 Report — Persistent Messaging and Real-Time Communication

## Architecture

The existing canonical `server/` communication path was hardened rather than replaced: Express HTTP writes persist to SQLite, then the existing authenticated WebSocket hub fans out only through database-backed conversation membership. SQLite remains the source of truth; the client reconnects, reloads conversation metadata, and fetches message IDs after its highest known cursor. Android nearby/offline behavior and the legacy proxy were untouched.

## Database migration

Additive schema migration **v3** adds `messages.source_language`, `messages.edited_at`, `messages.deleted_at`, and a conversation/time ordering index. Existing messages, IDs, membership, client-id idempotency constraint, and data are retained. Delete is soft: the message row remains and its body becomes null. The migration regression test starts from v1 and applies all current versions without opening a production database.

## API and realtime changes

- Message history supports exclusive `before` (older) and `after` (missed-message recovery) cursors.
- Text submission remains sender-authenticated, membership-authorized, size-limited, reply-validated, and sender/client-ID idempotent.
- Sender-only `PATCH /api/messages/:id` edits active text messages; sender-only `DELETE /api/messages/:id` soft deletes active messages.
- Realtime now adds a v1 envelope (`eventId`, `eventType`, `eventVersion`, timestamp and, when relevant, conversation ID) while retaining existing `event`/`data` compatibility fields.
- `message.updated` and `message.deleted` fan out only to current conversation members. Protocol details are in `REALTIME_PROTOCOL.md`.
- Persistent member read cursors remain the read mechanism. “Delivered” is intentionally not claimed or displayed because no recipient acknowledgement protocol exists.

## Frontend integration

The existing transcript interface, visual identity and navigation were preserved. Its existing optimistic outbox continues to generate client IDs, show pending/failed state, and reconcile canonical responses/events. On reconnect, already-open transcripts request `after=<highest-known-id>` and deduplicate by canonical/server client IDs. The browser types now include message lifecycle and language fields.

## Tests executed

- `server/npm test`: **38/38 passed**, 6 files.
- `server/npm run typecheck`: passed before final suite.
- `server/npm run build`: passed.
- `client/npm run typecheck`: passed before final suite.
- `client/npm run build`: passed.
- `client/npm test`: **8/8 passed** (desktop Chrome and Pixel 5 profiles), including real local text submission and refresh persistence.

The server suite includes a real local multi-user WebSocket test: authenticated members receive message/typing/read/presence events; an unauthenticated socket is rejected and a third non-member cannot receive or spoof a private conversation. Lifecycle tests validate two-user pagination/recovery, retry idempotency, sender-only edits and soft deletion.

## Limitations and blockers

- The WebSocket hub and SQLite deployment are single-process; no durable broker/event log or cross-node fan-out exists.
- Reconnect recovery is persisted-message cursor recovery for opened web transcripts, not a generic device sync API.
- Browser events are delivery attempts, not delivery acknowledgements. Group per-recipient delivery requires a later explicit device acknowledgement design.
- No attachments, Android cloud sync, voice/video calls, live Claude/Gnani verification, Android SDK/device verification, or physical microphone validation was performed.

## Recommended next milestone

Proceed to Claude AI orchestration and Gnani integration only after production deployment concerns (durable shared realtime, backup/recovery, limits) are separately planned. Keep attachments and Android cloud synchronization as independent milestones.
