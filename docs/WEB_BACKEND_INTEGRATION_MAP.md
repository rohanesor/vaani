# Web–Backend Integration Map

| Web feature | UI/state | Transport | Backend handler/service | Authz/data | Result |
|---|---|---|---|---|---|
| Login/register/logout/session restore | `Entry.tsx`, `session.tsx` | `/auth/*`, `/me` | `publicRoutes`, `coreRoutes`, auth/session Store | Bearer JWT + server session | PASS |
| Conversation list/create/group | `ConversationList`, `NewConversation`, `chat.ts` | `/conversations` | `coreRoutes` + Store | Membership/owner checks | PASS |
| Message history/send/retry | `ConversationView`, `Composer`, `chat.ts` | REST messages + WS | `coreRoutes`, Hub | Membership, sender idempotency | PASS |
| Read/reactions/edit/delete | Transcript/message components, `chat.ts` | REST + WS update | `coreRoutes`, Hub | Membership/ownership | PASS in server tests; UI controls covered selectively |
| Search | `SearchScreen` | `/search` | `coreRoutes` + FTS Store | User/member-scoped | PASS |
| Settings/profile | `SettingsScreen`, `session.tsx` | `/me`, `/me/settings` | `coreRoutes` | Current authenticated user | PASS |
| AI assistance | `AI.tsx` | SSE `/ai/assist`, `/ai/*` | `aiRoutes`, orchestrator | Membership + AI opt-in | MOCK-BACKED provider; PASS contract |
| Voice note/TTS | `Voice.tsx`, `recorder.ts`, `api.ts` | WAV/TTS HTTP | `aiRoutes`, Gnani adapter | Member-scoped, configured provider | MOCK-BACKED; live BLOCKED |
| Presence/typing/realtime | `RealtimeClient`, `ChatStore` | `/ws?token=` | `attachRealtime` | Session validity + membership fanout | PASS |
| Cloud sync | No canonical web sync UI | N/A | `/api/sync` for Android | Account/membership scoped | NOT APPLICABLE to web UI |

## Error and recovery behavior

`api.ts` maps network/API failures into `ApiError`; session 401 invokes the session logout handler. `ChatStore` uses temporary client IDs and server idempotency for message reconciliation. Pending text messages are stored account-scoped in browser storage and are flushed after WebSocket reconnect; permanent failures expose retry/discard state. Captured voice blobs are intentionally not persisted.
