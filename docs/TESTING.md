# VAANI Testing

## Locally verified coverage

Server Vitest coverage exercises:
- Registration/session authorization
- Direct conversation reuse
- Groups and owner authorization
- Message persistence/idempotency
- Read cursors and membership isolation
- FTS search
- Voice note contracts
- Migration compatibility
- Session revocation
- Message pagination/recovery cursor
- Edit ownership and soft deletion
- AI privacy filtering (email, phone, OTP, card redaction)
- AI assist streaming with context reporting
- AI rate limiting and budget enforcement
- Translation caching
- Summary reference validation
- Voice-to-action extraction and lifecycle
- Claude provider error mapping
- Gnani STT/TTS contracts (via test doubles)
- Health and readiness endpoints
- Production configuration validation
- Request correlation (X-Request-Id)
- Store isReady and close methods
- Sync protocol version negotiation
- Idempotent sync uploads and canonical ID responses
- Membership authorization for sync uploads
- Cursor-based initial and incremental downloads
- Sync read cursor, edit, and soft-delete operations

The Windows-host 16K-page Pixel emulator was recovered for product-readiness validation. The bundled Whisper library's PT_LOAD alignment was repaired from 4K to 16K and verified from the packaged APK. Critical runtime instrumentation then passed **10/10**: all Room 7→8→9→10/direct migration paths, four Keystore credential cases, and cloud-sync opt-in/account-generation isolation. Missing optional CT2 native artifacts now return an explicit unavailable state instead of crashing startup. The full instrumentation suite is not clean on API 37 because installed Espresso input injection calls removed `InputManager.getInstance()`; the protected corpus pipeline also requires deliberately unstaged corpus assets. Exact classifications are in `docs/PRODUCT_READINESS_REPORT.md` and `docs/RELEASE_READINESS.md`.

`server/test/realtime.test.ts` runs a real local HTTP/WebSocket server with three authenticated users. It verifies rejected unauthenticated upgrade, membership-limited typing/presence/message/read fan-out, and exclusion of a non-member.

`server/test/communication-lifecycle.test.ts` verifies two-user message pagination with `before`/`after`, idempotent retry, edit/delete ownership, lifecycle timestamps, and soft deletion.

## Commands

```sh
cd server && npm test && npm run typecheck && npm run build
cd ../client && npm run typecheck && npm run build && npm test
```

Playwright runs the existing browser suite against a local server and scripted/mock external providers. It is browser functional coverage, not proof of live Claude/Gnani, physical microphone, Android, or production multi-node realtime behavior.

## M17 Appium layer

`npm run test:android` runs the repository-local WebdriverIO/Appium UiAutomator2 suite against a healthy emulator. In M18 it passed 8/8 on the Windows API-37 Pixel: startup, permission prompts, WebView context/title, first-launch flow, transcript navigation, Tamil local-UI selection, foreground persistence, and restart. Appium diagnostics are captured in ignored `tests/android/reports/`. This removes Espresso as a blocker for native automation, but it does not turn unconfigured cloud/peer/provider workflows into tested journeys; see `docs/TEST_COVERAGE_MATRIX.md`.

`npm run test:gnani:live` is intentionally separate. It exits with a clear BLOCKED result unless `GNANI_API_KEY` and a controlled external `GNANI_LIVE_WAV` are provided; it never logs the key. M19 executed this prerequisite check and it was BLOCKED; no live request was made.
