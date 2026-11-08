# VAANI Canonical Data Model

## Scope and non-migration decision

This document defines the shared cloud model. No data migration, Room schema change, or server schema change is performed by this reconciliation milestone.

The server SQLite schema in `server/src/db.ts` is the current cloud MVP source of truth. Android Room (`app/src/main/java/com/vokie/data/local`) is a separate device/nearby transport store, not a competing cloud database.

## Identifier strategy

- New cloud entities use server-generated opaque identifiers. The current MVP uses SQLite integers; external API clients must treat them as opaque.
- Android must retain a mapping between `server_message_id` and its local UUID/packet message ID. It must never infer that a Bluetooth address, device UUID, or Room conversation ID is a server user/conversation ID.
- Future public API v2 should adopt UUID/ULID string IDs before multi-client production rollout. This is a planned compatibility migration, not a change in this milestone.

## Existing server entities

| Entity | Existing fields / constraints | Purpose |
|---|---|---|
| `users` | id, case-insensitive unique email, password_hash, display_name, avatar_hue, preferred_language, created_at, updated_at | Cloud user identity; email is the username-equivalent |
| `user_settings` | one row per user; AI/privacy/voice/theme/notification preferences | User-controlled behavior |
| `auth_sessions` | random session ID, user foreign key, expiry, creation, revocation timestamp | Server-side session validation and logout revocation |
| `conversations` | id, direct/group kind, title, created_by, timestamps, unique `direct_key` | Direct and group conversation container |
| `members` | composite conversation/user key, role owner/member, joined_at, last_read_id | Authorization and read cursor |
| `messages` | conversation, nullable sender, text/voice/system kind, body, source language, reply target, idempotency client_id, AI-attribution, edit/delete timestamps | Ordered transcript record; soft deletion retains the row with null body |
| `reactions` | message/user/emoji composite key | Reactions |
| `voice_notes` | one per message; WAV MIME, duration, waveform, audio BLOB, transcript state | Voice payload and transcription lifecycle |
| `translations` | message/target-language key, source language, text/note | Preserve source content while caching translations |
| `ai_usage` | user, feature, model, input/output tokens, status | Cost/audit data |
| `ai_actions` | user, conversation, source message, type/payload/status/result | Proposed then confirmed actions |
| `tasks` | user, optional conversation/action, title/due/done | Confirmed personal tasks |

Existing indexes support member lookup, conversation ordering, message pagination, and AI usage. FTS5 maintains searchable message/transcript text through triggers.

## Required canonical additions (planned)

| Entity/change | Why | Dependency |
|---|---|---|
| `attachments` metadata + object-storage key | Do not keep large images/files in message rows | Object storage and upload service |
| `user_profiles.avatar_key` | Profile photos | Object storage and validation pipeline |
| `message_delivery` / `device_delivery` | Distinguish accepted, delivered-to-device, and read | Defined multi-device delivery protocol |
| `client_devices` and refresh/session-device metadata | Android/web device management and refresh-token rotation | Auth upgrade; current `auth_sessions` supports revocation |
| `external_message_map` | Idempotent Android offline/cloud sync mapping | Android sync module |
| immutable numbered migration files | Safe reviewable schema evolution | Server migration refactor |

## Relationships and authorization

- A user may access a conversation, message, voice payload, translation, or search result only through membership.
- Group owner privileges are currently required to add members; server role policy must be centralized before adding more roles.
- A direct conversation is unique by sorted `direct_key`.
- Messages are ordered by server ID. `client_id` is idempotent per sender, not globally.
- AI output is stored in usage/action/translation records; it is not an original user message. An explicitly sent AI-assisted draft becomes a normal message with attribution.

## Android mapping model (planned)

A future Android local table, owned by Android only, should contain: `local_message_uuid`, `server_message_id`, `local_conversation_id`, `server_conversation_id`, `sync_state`, `last_synced_at`, and conflict/error metadata. It must preserve existing Room entities and be introduced only through a versioned Room migration after the server sync contract is tested.
