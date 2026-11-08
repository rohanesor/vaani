# Web Feature Validation

| Feature | UI | API/backend | Evidence | Status |
|---|---|---|---|---|
| Registration/login/session restoration/logout | Yes | Yes | Server auth tests; Playwright auth | PASS |
| Protected routes/unauthorized session | Yes | Yes | Session middleware/tests | PASS |
| Direct/group conversations | Yes | Yes | Server API tests; web flows | PASS |
| Send/idempotent message persistence | Yes | Yes | Playwright refresh test; server lifecycle tests | PASS |
| Pagination/read/edit/delete/reactions | Yes | Yes | Server lifecycle/API tests | PASS (backend); UI NOT RUN individually |
| WebSocket presence/typing/message/read fanout | Yes | Yes | `realtime.test.ts` | PASS |
| Search | Yes | Yes | Playwright search; server FTS tests | PASS |
| Settings/profile/language metadata | Yes | Yes | Source trace; server validation | PASS |
| Browser offline text outbox durability | Yes | N/A | Playwright intercepted-network reload/reconnect regression | PASS — account-scoped text queue; voice blobs excluded |
| AI draft/context/translation/etc. | Yes | Yes | Playwright mock flow; server provider tests | MOCK-BACKED |
| Browser voice recorder permission/error | Yes | Yes | Playwright unavailable-mic path | PASS for error UI; live speech BLOCKED |
| Gnani STT/TTS | Optional UI/API | Adapter exists | 15 deterministic tests | MOCK-BACKED; LIVE BLOCKED |
| Web cloud sync | No | Android endpoint only | Source trace | NOT APPLICABLE |
| Nearby, calls, physical offline transport | No | Android-native | Platform audit | NOT APPLICABLE |

Post-repair runs: client typecheck/build PASS; Playwright 10/10 PASS; server test/typecheck/build PASS (62/62, audit baseline).
