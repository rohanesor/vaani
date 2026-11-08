# VAANI Android Cloud Sync Contract — v1.0

## Overview

This document specifies the versioned, idempotent cloud sync protocol between the VAANI Android client and the canonical VAANI server. The contract enables Android clients to synchronize locally-originated messages to the cloud and receive messages from other cloud clients.

**Status**: Contract implemented. Server endpoint at `POST /api/sync` is operational. Android client implemented (build verification requires local Android SDK).

## Design Principles

1. **Idempotent**: Repeated requests must not create duplicate data
2. **Server-authoritative**: The server is the source of truth for canonical IDs and conflict resolution
3. **Backward-compatible**: Protocol versioning allows evolution without breaking existing clients
4. **Privacy-preserving**: Clients cannot impersonate other users
5. **Offline-first**: Local operations succeed offline; sync reconciles later

## Protocol Version

```
X-Vaani-Sync-Version: 1
```

All sync requests must include this header. The server will reject requests with unsupported versions.

### Version Negotiation

| Client Version | Server Response |
|---|---|
| Supported | Process request |
| Unknown/future | `400 Bad Request` with `{"code": "unsupported_sync_version", "supportedVersions": [1]}` |
| Missing | `400 Bad Request` with `{"code": "sync_version_required"}` |

---

## Identity Mapping

### Canonical Server IDs

| Entity | Type | Source |
|---|---|---|
| User ID | `integer` | Server-assigned on registration |
| Conversation ID | `integer` | Server-assigned on creation |
| Message ID | `integer` | Server-assigned on persistence |
| Session ID | `string (UUID)` | Server-assigned on login |

### Android Local IDs

| Entity | Type | Source |
|---|---|---|
| Local Message ID | `string (UUID)` | Client-generated |
| Local Conversation ID | `string (UUID)` | Client-generated |
| Peer ID | `string` | Bluetooth MAC or device identifier |
| Nearby Packet ID | `string (UUID)` | Client-generated for mesh packets |

### Mapping Table Schema

The Android client must maintain a mapping table:

```kotlin
@Entity(tableName = "cloud_id_mapping", primaryKeys = ["accountCloudUserId", "localType", "localId"])
data class CloudIdMapping(
    val accountCloudUserId: Long, // Authenticated canonical user ID
    val localType: String,  // "message" | "conversation"
    val localId: String,    // Local UUID
    val cloudId: Long?,     // Server ID (null = not synced)
    val syncedAt: Long?,    // Timestamp of successful sync
    val syncError: String?  // Last sync error, if any
)
```

### Idempotency Keys

For message creation, the client provides a `clientId` (the local UUID). The server uses `(sender_id, client_id)` as a unique constraint.

- If the client retries a message upload with the same `clientId`, the server returns the existing message.
- The `clientId` is permanent and must be preserved across sync attempts.

---

## Authentication

### Device Registration

Android clients authenticate via the standard auth flow:

```
POST /api/auth/register
POST /api/auth/login
POST /api/auth/logout
```

The returned JWT token is used for all sync requests.

### Device Session

Each login creates a server-side session record in `auth_sessions`. Android stores the bearer token only in Keystore-backed encrypted storage; it is not an active Room credential. The client must:

1. Store the JWT token securely (Android Keystore recommended)
2. Include `Authorization: Bearer <token>` in all requests
3. Handle `401 Unauthorized` by prompting re-authentication
4. Call `POST /api/auth/logout` on explicit logout

### Multiple Devices

One user can have multiple device sessions. Each device syncs independently. All devices see the same canonical message history.

### Session Lifecycle

| Event | Client Action | Server Behavior |
|---|---|---|
| Login | Store token | Create session record |
| Logout | Clear token, mapping cache | Revoke session |
| Token expired | Re-authenticate | Reject requests with 401 |
| Device lost | User logs out via another device | Revoke session remotely |

---

## Sync Protocol

### Endpoint

```
POST /api/sync
```

### Request Headers

```
Authorization: Bearer <jwt>
Content-Type: application/json
X-Vaani-Sync-Version: 1
X-Request-Id: <client-generated-uuid>
```

### Request Body

```typescript
interface SyncRequest {
  // Client's sync cursor (null for initial sync)
  cursor: string | null;
  
  // Messages to upload (pending outbox)
  upload: SyncUploadMessage[];
  
  // Read cursor updates
  readCursors: SyncReadCursor[];
  
  // Edits to existing messages
  edits: SyncEdit[];
  
  // Deletions (soft delete)
  deletions: SyncDeletion[];
}

interface SyncUploadMessage {
  clientId: string;           // Local UUID (idempotency key)
  conversationCloudId: number; // Server conversation ID
  body: string;
  kind: "text" | "voice";
  replyTo?: number;           // Server message ID to reply to
  sourceLanguage?: string;
  clientTimestamp: string;    // ISO 8601, informational only
}

interface SyncReadCursor {
  conversationCloudId: number;
  lastReadMessageId: number;
}

interface SyncEdit {
  messageCloudId: number;
  newBody: string;
  clientTimestamp: string;
}

interface SyncDeletion {
  messageCloudId: number;
  clientTimestamp: string;
}
```

### Response Body

```typescript
interface SyncResponse {
  // Updated cursor for next sync
  cursor: string;
  
  // Upload results (one per upload item)
  uploadResults: SyncUploadResult[];
  
  // New/updated messages from server
  messages: SyncMessage[];
  
  // Conversations with membership changes
  conversations: SyncConversation[];
  
  // True when another request should be made with the returned cursor
  hasMore: boolean;

  // Server timestamp
  serverTime: string;
}

interface SyncUploadResult {
  clientId: string;
  status: "created" | "exists" | "error";
  cloudId?: number;        // Server-assigned ID
  error?: SyncError;
}

interface SyncMessage {
  id: number;              // Server message ID
  conversationId: number;
  senderId: number;
  senderName: string;
  kind: "text" | "voice" | "system";
  body: string | null;
  replyTo?: number;
  clientId: string | null;
  sourceLanguage: string | null;
  createdAt: string;
  editedAt: string | null;
  deletedAt: string | null;
}

interface SyncConversation {
  id: number;
  kind: "direct" | "group";
  title: string | null;
  members: SyncMember[];
  lastMessageId: number | null;
  updatedAt: string;
  myRole: "owner" | "member";
}

interface SyncMember {
  id: number;
  displayName: string;
  avatarHue: number;
}

interface SyncError {
  code: string;
  message: string;
  retryable: boolean;
}
```

---

## Cursor Semantics

### Cursor Format

The cursor is an opaque string encoding sync state. Internally it contains:

```
base64(json({
  "v": 1,
  "ts": "<iso8601>",
  "msgId": <highest-known-message-id>,
  "convVer": <conversation-version>
}))
```

Clients must not parse or construct cursors. Always use the cursor returned by the previous sync.

### Initial Sync

First sync: `cursor: null`

Response includes all messages in all conversations the user is a member of, paginated.

### Incremental Sync

Subsequent syncs: `cursor: "<previous-response-cursor>"`

Response includes only messages with `id > cursor.msgId` and conversations updated since `cursor.ts`.

### Pagination

If more data exists, the response includes `hasMore: true`. The client should immediately sync again with the returned cursor until `hasMore: false`. The cursor itself remains opaque.

### Reconnect Recovery

After connection loss:

1. Client syncs with last-known cursor
2. Server returns all changes since that cursor
3. Client merges into local database
4. Client resolves any local edits against server state

---

## Conflict Resolution Rules

### Message Creation Conflicts

| Scenario | Resolution |
|---|---|
| Same `clientId` uploaded twice | Server returns existing message (idempotent) |
| Local ID conflicts with different server ID | Client updates mapping table |

### Edit Conflicts

| Scenario | Resolution |
|---|---|
| Client edits message that was deleted on server | Edit rejected with `message_deleted` error |
| Client edits with older timestamp than server | Server-wins (last-write based on server receipt time) |
| Multiple clients edit same message | Last received by server wins |

### Deletion Conflicts

| Scenario | Resolution |
|---|---|
| Client deletes message that was edited on server | Deletion wins (soft-delete preserves edit history) |
| Client deletes already-deleted message | No-op (idempotent) |

### Read Cursor Conflicts

| Scenario | Resolution |
|---|---|
| Client sends older read cursor | Server ignores (max-wins) |
| Client sends cursor for non-member conversation | Rejected with `forbidden` |

### Offline Message Upload

| Scenario | Resolution |
|---|---|
| Message created offline, uploaded hours later | Server assigns `created_at` based on receipt time |
| Client timestamp differs from server time | Server time is authoritative; client time is metadata only |

### Access Revocation

| Scenario | Resolution |
|---|---|
| User removed from group while offline | Sync returns `conversation_access_revoked` error for pending uploads to that conversation |
| Conversation deleted while offline | Sync returns `conversation_not_found` error |

---

## Error Codes

### Request-Level Errors (HTTP 4xx/5xx)

| Code | HTTP Status | Retryable | Description |
|---|---|---|---|
| `unauthorized` | 401 | No | Invalid or expired token |
| `sync_version_required` | 400 | No | Missing sync version header |
| `unsupported_sync_version` | 400 | No | Protocol version not supported |
| `invalid_request` | 400 | No | Malformed request body |
| `rate_limited` | 429 | Yes | Too many requests |
| `server_error` | 500 | Yes | Internal server error |

### Item-Level Errors (in `uploadResults`)

| Code | Retryable | Description |
|---|---|---|
| `conversation_not_found` | No | Conversation doesn't exist or was deleted |
| `conversation_access_denied` | No | User is not a member |
| `message_not_found` | No | Message ID doesn't exist |
| `message_access_denied` | No | Not authorized to edit/delete |
| `message_deleted` | No | Cannot edit deleted message |
| `invalid_body` | No | Message body is empty or too long |

---

## Rate Limiting

### Sync Endpoint Limits

| Limit | Value |
|---|---|
| Requests per minute | 30 |
| Max upload batch size | 50 messages |
| Max body size | 256KB |

### Backoff Strategy

On `429 Too Many Requests`:

1. Read `Retry-After` header (seconds)
2. Wait specified duration
3. Retry with exponential backoff if header absent

---

## Privacy and Security

### Authorization Rules

1. All sync requests require valid JWT
2. Users can only upload messages to conversations they're members of
3. Users can only edit/delete their own messages
4. `senderId` is always set by server (cannot be spoofed)
5. Conversation membership is validated server-side

### Data Validation

1. `clientId` must be valid UUID format
2. `body` is trimmed and limited to 8000 characters
3. `sourceLanguage` must be valid language code or null
4. Client timestamps are informational only; server time is authoritative

### Transport Security

1. All sync requests must use HTTPS
2. JWT tokens should be stored in Android Keystore
3. Local database should use SQLCipher for encryption at rest

---

## Nearby/Offline Message Handling

### Scope

The cloud sync contract does **not** modify nearby/offline behavior:

- Bluetooth mesh messages remain local-only by default
- Nearby packet IDs are distinct from cloud message IDs
- Local peer-to-peer delivery continues to work offline

### Opt-In Cloud Sync for Nearby Messages

A future extension may allow users to opt-in to syncing nearby messages to cloud:

1. User enables "Backup to Cloud" in settings
2. Successfully delivered nearby messages are queued for cloud sync
3. Cloud sync uploads them with a `source: "nearby"` flag
4. Cloud recipients receive them as normal messages

**This is out of scope for v1.0.**

---

## Example Sync Exchanges

### Initial Sync

```http
POST /api/sync
Authorization: Bearer eyJhbG...
X-Vaani-Sync-Version: 1

{
  "cursor": null,
  "upload": [],
  "readCursors": [],
  "edits": [],
  "deletions": []
}
```

```http
HTTP/1.1 200 OK

{
  "cursor": "eyJ2IjoxLCJ0cyI6IjIwMjYtMTEtMDJUMTI...",
  "uploadResults": [],
  "messages": [
    { "id": 1, "conversationId": 1, "senderId": 2, "body": "Hello!", ... },
    { "id": 2, "conversationId": 1, "senderId": 1, "body": "Hi there!", ... }
  ],
  "conversations": [
    { "id": 1, "kind": "direct", "members": [...], ... }
  ],
  "serverTime": "2026-11-02T12:34:56.789Z"
}
```

### Message Upload

```http
POST /api/sync
Authorization: Bearer eyJhbG...
X-Vaani-Sync-Version: 1

{
  "cursor": "eyJ2IjoxLCJ0cyI6IjIwMjYtMTEtMDJUMTI...",
  "upload": [
    {
      "clientId": "550e8400-e29b-41d4-a716-446655440000",
      "conversationCloudId": 1,
      "body": "See you at 4pm!",
      "kind": "text",
      "clientTimestamp": "2026-11-02T12:35:00.000Z"
    }
  ],
  "readCursors": [{ "conversationCloudId": 1, "lastReadMessageId": 2 }],
  "edits": [],
  "deletions": []
}
```

```http
HTTP/1.1 200 OK

{
  "cursor": "eyJ2IjoxLCJ0cyI6IjIwMjYtMTEtMDJUMTI...",
  "uploadResults": [
    { "clientId": "550e8400-e29b-41d4-a716-446655440000", "status": "created", "cloudId": 3 }
  ],
  "messages": [],
  "conversations": [],
  "serverTime": "2026-11-02T12:35:01.234Z"
}
```

### Duplicate Upload (Idempotent)

Same request repeated:

```http
HTTP/1.1 200 OK

{
  "cursor": "...",
  "uploadResults": [
    { "clientId": "550e8400-e29b-41d4-a716-446655440000", "status": "exists", "cloudId": 3 }
  ],
  ...
}
```

---

## Test Scenarios for Android Implementation

### Unit Tests

1. **Cursor handling**: Parse null cursor, parse valid cursor, reject invalid cursor
2. **Upload batching**: Batch messages ≤50, split larger batches
3. **Idempotency**: Same clientId returns same cloudId
4. **Mapping table**: Insert, update, query local→cloud mappings
5. **Error handling**: Handle each error code appropriately

### Integration Tests

1. **Initial sync**: Fresh user, fetch all conversations
2. **Incremental sync**: Receive new messages since last cursor
3. **Upload flow**: Queue→Upload→ConfirmMapping
4. **Conflict resolution**: Edit race, delete race
5. **Offline recovery**: Queue during offline, sync when online
6. **Session expiry**: Handle 401, prompt re-auth
7. **Rate limiting**: Handle 429, backoff and retry

### E2E Tests

1. **Cross-device**: Send from web, receive on Android
2. **Cross-platform**: Send from Android, receive on web
3. **Multi-device**: Same user on two Android devices
4. **Long offline**: Offline for hours, sync successfully

---

## Implementation Roadmap

### Phase 1: Core Sync (Future Milestone)

1. Implement `CloudIdMapping` Room entity
2. Implement `SyncRepository` with upload queue
3. Add WorkManager sync job
4. Handle initial sync and incremental sync
5. Map local conversations to cloud conversations

### Phase 2: Conflict Resolution (Future Milestone)

1. Implement edit/delete sync
2. Add retry logic with exponential backoff
3. Handle access revocation gracefully

### Phase 3: Optimizations (Future Milestone)

1. Background sync scheduling
2. Delta compression
3. Conflict-free CRDT for concurrent edits (optional)

---

## Appendix: Related Documents

- [API_CONTRACTS.md](./API_CONTRACTS.md) — Core API specifications
- [REALTIME_PROTOCOL.md](./REALTIME_PROTOCOL.md) — WebSocket protocol
- [ANDROID_SYNC_DATA_MODEL.md](./ANDROID_SYNC_DATA_MODEL.md) — Android local schema extensions
- [DATA_MODEL.md](./DATA_MODEL.md) — Server database schema
