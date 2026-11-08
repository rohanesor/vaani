# VAANI Claude Design Frontend Functionality Audit

**Audit date:** 2026-11-02  
**Method:** source and contract inspection only. No browser, Playwright, real microphone, Android device, Claude, or Gnani live run has been performed yet.

## Identified frontend

The Claude Design implementation is `client/` in the valid bundle `vaani.git.bundle` commit `081854c`. It is a React 19/Vite/TypeScript application, not the Android WebView UI under `app/`.

Design-source files are present and match the v2 bundle:

- Application/session/router: `src/App.tsx`, `src/state/session.tsx`, `src/state/chat.ts`, `src/lib/router.ts`
- API/realtime/recording: `src/lib/api.ts`, `src/lib/realtime.ts`, `src/lib/recorder.ts`
- Conversation UI: `src/components/{ConversationList,ConversationView,Transcript,Message,Composer,Voice,AI}.tsx`
- Screens: `src/screens/{Entry,Screens}.tsx`
- Design system: `src/styles/{tokens,app}.css` and `docs/{DESIGN_SYSTEM,SCREEN_INVENTORY,INTERACTION_SPEC,RESPONSIVE_SPEC}.md`

The implementation preserves the specified transcript style, Peacock brand, Iris AI annotations, Marigold voice treatment, light/dark themes, responsive index/stage layout, and self-hosted Indic-script fonts. Legacy pre-v2 visual components are retained in `client/_to_delete/` and must not be deleted during reconciliation.

## Frontend functionality matrix

| Feature | Source evidence | Classification | Verification needed |
|---|---|---|---|
| Registration/login/session | `state/session.tsx`, `/api/auth/*` | Implemented but unverified | Browser happy/error/expired-session tests |
| Conversation list and unread count | `ConversationList.tsx`, `state/chat.ts` | Implemented but unverified | Multi-user UI test |
| 1:1 conversation creation and messaging | `NewConversation.tsx`, composer, `/conversations` routes | Implemented but unverified | Two-user API + browser test |
| Group creation/rename/leave | `NewConversation.tsx`, `ConversationView.tsx` | Implemented but unverified | Owner/member permission UI test |
| Add member to existing group | API exists: `POST /conversations/:id/members`; details UI explicitly says unavailable | Partially implemented | Add owner-only UI and browser coverage |
| Message history/pagination/replies/reactions | `state/chat.ts`, transcript/message components | Implemented but unverified | Pagination/reply/reaction UI tests |
| Read receipts | `readState` and realtime `read` event | Partially implemented | Verify presentation semantics; it is a read marker, not proof of physical delivery |
| Delivered status | No server delivery-ack entity/event beyond accepted HTTP response and read state | Missing as a separately verified state | Define delivery semantics before adding indicator |
| Search | `/search` + FTS5 and `SearchScreen` | Implemented but unverified | Unicode/authorization/browser result tests |
| Offline outbox | In-memory `Map` in `state/chat.ts`, reconnect flush | Partially implemented | Network interruption test; refresh loses queued items |
| Realtime presence/typing | `lib/realtime.ts`, `server/realtime.ts` | Implemented but unverified | Multi-context WebSocket tests and browser test |
| AI reply/rewrite/explain/ask | `AI.tsx`, SSE `/ai/assist` | Implemented but externally unverified | Mocked Playwright flow; live Claude smoke test only with approved secret |
| Translation/summary | AI routes + inline/context UI | Implemented but externally unverified | Mocked Playwright flow; live provider smoke test |
| Voice-to-action | `InlinePlans`, confirm/dismiss flow | Implemented but externally unverified | Mocked browser confirmation test; assert no auto-execution |
| Voice recording | `VoiceStage`, `MediaRecorder`/Web Audio recorder code | Implemented but unverified | Chromium permission/mic test using a controlled fake audio device |
| Voice upload/playback/waveform | WAV upload, binary audio fetch, `VoicePlayer` | Implemented but unverified | API and browser playback/control test |
| Transcription/TTS | Gnani endpoints through server | Implemented but live-provider unverified | Mocked HTTP tests; real Gnani smoke test with approved key |
| Language-aware typography | `tokens.css` and Noto font packages | Implemented but unverified | Playwright screenshots/text rendering at target viewport sizes |
| Light/dark/responsive | tokens, session preferences, CSS | Implemented but unverified | Playwright visual/function smoke at desktop/tablet/mobile widths |
| Connection indicator | `ConnectionIndicator`, realtime state | Implemented but unverified | Offline/reconnect browser test |
| Attachments/images | No upload/storage UI/API | Missing | Separate storage/upload milestone |
| Profile photos | Settings explicitly says unsupported | Missing | Separate profile-media milestone |

## Backend integration points

The frontend does not call provider APIs directly. `lib/api.ts` uses a VAANI bearer token for `/api`; `lib/realtime.ts` uses that token for `/ws`. AI calls go to the VAANI server, which is the only place intended to hold Anthropic/Gnani credentials. This is the correct integration shape.

## Test readiness and blockers

- `client/node_modules` and `server/node_modules` are not present.
- Playwright is not installed in the workspace or globally.
- No Chrome/Chromium executable was discovered in this environment.
- No Android SDK, `adb`, or emulator executable was discovered.
- `/mnt/d/agent/artemis` exists and must be inspected/configured before any requested phone/device validation. It must not be assumed to provide a Google phone until its configured device inventory is verified.

## Required next verification sequence

1. Preserve/import the bundle-derived web source without overwriting Android files.
2. Install locked Node dependencies from `client/package-lock.json` and `server/package-lock.json`.
3. Run server unit/API/realtime/speech contract tests, TypeScript checks, and production builds.
4. Add Playwright as a dev dependency plus a reproducible browser install; test each matrix feature against the scripted-provider harness first.
5. Inspect Artemis configuration/device availability and use an actual Google Android device/emulator only if it is listed and reachable; run Android separately from the web PWA test suite.
6. Run live Claude/Gnani smoke tests only with server-side credentials and explicitly report them as live tests.
