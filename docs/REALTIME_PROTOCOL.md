# VAANI Real-Time Protocol (v1)

## Transport and authorization

The canonical web transport is `WSS /ws?token=<VAANI JWT>`. Upgrade authentication verifies the JWT signature/expiry and its active `auth_sessions` record. Connections are associated only with the authenticated `sub`; there is no client-controlled subscription operation. Fan-out uses persisted `members` rows, so guessed conversation IDs cannot add a connection to a private conversation.

The query-token form is retained for the existing web client. Android cloud sync is not implemented and must define a header/handshake alternative before release.

## Envelope

Every server event is JSON with the compatibility fields `event` and `data`, plus:

```json
{
  "event": "message.new",
  "data": { "...": "event-specific payload" },
  "eventId": "UUID",
  "eventType": "message.new",
  "eventVersion": 1,
  "conversationId": 42,
  "timestamp": "2026-01-01T00:00:00.000Z"
}
```

`conversationId` is present for conversation-scoped events. `eventId` identifies a delivery attempt, not a durable globally replayable event-log record.

## Events

| Event | Authorized recipients | `data` |
|---|---|---|
| `hello` | authenticated connection | `{ userId, online, serverTime }` |
| `presence` | current conversation peers | `{ userId, online }` |
| `conversation.updated` | current members | `{ conversationId }` |
| `message.new` | current members, including sender | canonical message DTO |
| `message.updated` | current members | canonical edited message DTO |
| `message.deleted` | current members | canonical soft-deleted message DTO (`body: null`, `deletedAt`) |
| `read` | other current members | `{ conversationId, userId, messageId }` |
| `typing` | other current members | `{ conversationId, userId }` |

A `message.new` is published only after the HTTP persistence operation returns its canonical record. A socket event is not a delivery receipt.

## Reconnection and ordering

SQLite message IDs are canonical increasing cursors. The client reconnects with exponential backoff, reloads conversations, and requests `GET /conversations/:id/messages?after=<highest-known-id>` for each already-open transcript. It deduplicates by canonical message ID and sender/client ID. The database remains the recovery source; the server has no in-memory durable event queue.

Events can be duplicated, delayed, or missed across a disconnect. Clients must treat message IDs and read cursors as monotonic. `before` and `after` are mutually exclusive message-history cursors; results are ascending and bounded by `limit` (1–200).

## Delivery and read semantics

- **Pending:** client-side submission not yet confirmed.
- **Sent:** server persisted the canonical message.
- **Failed:** submission failed and remains retryable with its original `clientId`.
- **Delivered:** not implemented; requires an explicit recipient acknowledgement protocol.
- **Read:** a member advanced their persistent conversation `last_read_id`; it is not delivery.

## Client-originated events and errors

Clients may send `typing { conversationId }` and `ping`. The server checks membership before fan-out. Malformed input and unauthorized upgrade attempts are rejected/ignored without broadcasting sensitive data. HTTP remains the authoritative channel for write validation and error responses.
