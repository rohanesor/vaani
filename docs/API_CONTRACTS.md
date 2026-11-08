# VAANI Canonical API Contracts

**Base:** `/api` over HTTPS. **Auth:** `Authorization: Bearer <VAANI session token>`. Provider keys are never client credentials. Existing server routes are retained; this document makes them the shared-client contract.

## Response and error rules

- JSON errors: `{ "error": { "code": "…", "message": "…", "details": { … } } }`.
- Conversation authorization failures use `404` to avoid membership probing.
- Message create accepts a sender-scoped `clientId` for idempotent retry.
- `201` means persisted by the server; it does **not** mean delivered to another device. `read` is the only current recipient acknowledgement.
- Cursor pagination uses exactly one of `before=<message id>` (older) or `after=<message id>` (recovery), with `limit=1..200`; clients treat IDs as opaque.

## Existing endpoints

| Endpoint | Auth | Contract / status |
|---|---|---|
| `POST /auth/register`, `POST /auth/login` | Public | Email/password registration/login; returns token, user, settings and creates a 14-day revocable server session. |
| `GET /health`, `GET /languages` | Public | Safe service/provider configuration summary; supported language list. |
| `POST /auth/logout` | Required | Revokes the current server session; returns `204`. |
| `GET/PATCH /me`, `GET/PATCH /me/settings` | Required | Profile and preference reads/updates. |
| `GET /users?q=`, `GET /users/recent` | Required | Server account directory/recent contacts. |
| `GET/POST /conversations` | Required | List; create unique direct or group conversation. |
| `GET/PATCH /conversations/:id` | Required member | Read; rename group. |
| `POST /conversations/:id/members`, `DELETE /conversations/:id/members/me` | Required | Owner adds members; member leaves. Add-member UI is not implemented. |
| `GET/POST /conversations/:id/messages` | Required member | Ascending bounded history (`before` older or `after` recovery cursor); create idempotent text/reply/AI-attributed message. Text accepts optional `sourceLanguage`. |
| `POST /conversations/:id/read` | Required member | Advance own read cursor. |
| `PATCH /messages/:id`, `DELETE /messages/:id` | Required sender | Edit active own text; soft-delete own active message. Both return the canonical message lifecycle DTO. |
| `POST /messages/:id/reactions` | Required member | Toggle emoji reaction. |
| `GET /search` | Required | Membership-scoped FTS search. |
| `POST /conversations/:id/voice`, `GET /messages/:id/audio`, `POST /messages/:id/transcribe` | Required member | PCM WAV voice note, authorized audio retrieval, transcription request. |
| `POST /tts` | Required | Cloud TTS of supplied text/language. |
| `GET /tasks`, `PATCH /tasks/:id` | Required | Personal confirmed tasks. |
| `GET /ai/status`, `POST /ai/assist` | Required | Provider/settings status; SSE assistant events `context`, `delta`, `done`, `error`. |
| `POST /ai/translate`, `/ai/summarize`, `/ai/actions/extract`, `/ai/actions/:id/confirm`, `/ai/actions/:id/dismiss` | Required | Claude-mediated translation, summaries, proposed-action lifecycle. |
| `POST /sync` | Required | Android cloud sync: upload pending messages, download server changes, update read cursors. Requires `X-Vaani-Sync-Version: 1` header. See [ANDROID_CLOUD_SYNC_CONTRACT.md](./ANDROID_CLOUD_SYNC_CONTRACT.md). |

## Realtime contract

`WSS /ws?token=<session>` is the current web client transport. The token must be signed, unexpired, and reference an active server session. Server events: `hello`, `presence`, `message.new`, `message.updated`, `conversation.updated`, `read`, `typing`. Client events: `typing`, `ping`.

For Android, query-token WebSocket authentication is **not** a preferred long-term pattern. The Android sync implementation must use a documented secure handshake/header mechanism before release, while web compatibility remains supported.

## Missing shared APIs (planned, not implemented)

| API | Reason |
|---|---|
| Refresh/session-device APIs | Current-session revocation exists; refresh rotation and device management do not. |
| Message edit/delete endpoints | Required product capability; define authorization/audit semantics first. |
| Attachment upload/download/metadata APIs | Need object storage, size/type validation, malware scanning, retention policy. |
| Profile photo APIs | Need object storage and image transformation/validation. |
| ~~Device registration and sync pull/push APIs~~ | Implemented: `POST /api/sync` with cursor-based incremental sync. |
| Delivery receipt APIs | Need a defined device acknowledgement model; do not fake delivery. |
| Notification registration/push APIs | Browser notifications only work while app context is available. |

## AI and speech requirements

Claude and Gnani calls remain server-to-server. AI APIs must retrieve only authorized conversation context, enforce user AI preferences, rate/budget limits, cancellation and redaction, and represent actions as proposals until confirmation. Gnani cloud STT/TTS is optional and must return an explicit unconfigured/error state without breaking normal messaging.
