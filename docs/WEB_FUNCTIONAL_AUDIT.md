# VAANI Canonical Web Functional Audit

## Scope

`client/` is the canonical responsive VAANI web application. `app/src/main/assets/www/` is a separate Android legacy/offline WebView surface and is not evaluated as the canonical web frontend.

## Actual architecture

The web client is a React/Vite SPA with hash routing, `SessionProvider`, and an external `ChatStore`. It talks directly to the single canonical Node/Express backend in `server/`; there is no inherited independent iTantra microservice runtime in this repository.

| Service | Boundary | Responsibility | Web integration | Validation |
|---|---|---|---|---|
| VAANI server | Independent deployable | Auth, conversations, messages, search, settings, sync, realtime | `client/src/lib/api.ts`, `realtime.ts` | PASS — server 62/62; web 8/8 |
| SQLite store | Internal backend module | Persistent users, sessions, messages, cursors, AI usage | HTTP/WebSocket responses | PASS — integration tests |
| WebSocket hub | Internal backend module | Authenticated presence, typing, message/read fanout | `RealtimeClient` | PASS — realtime tests/browser flows |
| Claude adapter | External provider adapter | Optional assist/translation/summaries/actions | `AI.tsx` → `/api/ai/*` | MOCK-BACKED; no live claim |
| Gnani adapter | External provider adapter | Optional server STT/TTS | Voice/TTS API paths | MOCK-BACKED; live BLOCKED |
| Android nearby/native services | Android-native | Bluetooth/Wi-Fi Direct, local speech, calls | None in canonical web client | NOT APPLICABLE |

## Confirmed client/backend traces

- Authentication: `Entry.tsx` → `SessionProvider.login/register` → `api.ts` → `/api/auth/*` → `routes/core.ts` → Store/session → token/user/settings → route gate.
- Conversations/messages: `ConversationList/View/Composer` → `chat.ts` → `/api/conversations/*` → `core.ts` → Store/Hub → REST reconciliation plus WebSocket events → transcript state.
- Search/settings: `Screens.tsx` → `api.ts` → `/api/search`, `/api/me`, `/api/me/settings` → Store → rendered result/settings state.
- AI: `AI.tsx` → `streamSSE`/`api` → `routes/ai.ts` → orchestrator/provider → draft/result UI. Results are never automatically sent.
- Voice notes: `Voice.tsx`/recorder → `chat.sendVoice` → WAV route → Gnani only when configured; unavailable provider remains explicit.
- Android sync endpoint: `routes/sync.ts` is Android contract support, not a browser web-sync feature.

## Audit conclusion

No confirmed frontend-to-backend endpoint mismatch, missing auth guard, or stale mock production path was found in the tested web scope. The audit also repaired durable pending-text recovery across reload; voice capture remains explicitly non-durable. See `WEB_FEATURE_VALIDATION.md` for classifications and limitations.
