# VAANI Baseline Verification

**Date:** 2026-11-02  
**Environment:** Node `v22.22.3`; no production database or provider credentials used.

## Source preservation

- Bundle verification passed: `git bundle verify vaani.git.bundle`.
- Bundle SHA-256: `5e6fac3ab141357889c2178ac53696901cda043901de29701ef254a96823b698`.
- Bundle web revision: `081854c547c29f2e2d1373cbadcaeff0e105aaf1`.
- `server/` exactly matched that revision at comparison time.
- `client/` differed only by the local `public/favicon.svg` and preserved `client/_to_delete/` legacy components.
- `.gitignore` was reconciled as a union of Node, Android/Gradle, model, signing-key, corpus, log, test-result, local database, and environment exclusions. Package manifests, lockfiles, templates, tests, source, and documentation remain trackable.

## Dependency installation

| Command | Result |
|---|---|
| `cd server && npm ci --ignore-scripts` | Passed: 152 packages, 0 reported vulnerabilities |
| `cd client && npm ci --ignore-scripts` | Passed: 41 packages, 0 reported vulnerabilities |
| `cd client && npm install --save-dev --save-exact @playwright/test@1.64.1` | Passed; explicit test-only dependency and lockfile update |
| `cd client && npx playwright install chromium` | Chromium available for Playwright 1.64.1 |

## Backend verification

| Command | Result |
|---|---|
| `cd server && npm test` | **33/33 tests passed**, 4 test files, 11.93 s |
| `cd server && npm run typecheck` | Passed |
| `cd server && npm run build` | Passed |

The suite uses in-memory SQLite and fake AI/speech providers where appropriate. It verifies server/auth validation, authorization, conversations/messages, WebSocket fan-out, AI orchestration contracts, Gnani HTTP contracts, WAV validation, and action validation. It does **not** verify live Claude or Gnani services.

## Frontend verification

| Command | Result |
|---|---|
| `cd client && npm run typecheck` | Passed |
| `cd client && npm run build` | Passed; Vite production bundle generated |
| `cd client && npm test` | **6/6 Playwright tests passed** in 19.8 s |

Playwright runs the existing server scripted-provider harness against the built Claude Design frontend on desktop Chrome and Pixel 5 viewport profiles. It verified: login, conversation index, group transcript view, search, AI draft/use-and-edit UI, context summary, dark theme, responsive mobile rendering, and the voice-recorder UI entry state.

## Verification boundaries

- Playwright AI/content assertions used the scripted test provider, not live Claude.
- The voice test verifies recorder UI entry/state only. It did not record real microphone audio or validate hardware permissions/audio playback.
- No real Gnani request, Claude request, production SQLite database, deployment, Android build, nearby transport, or Artemis device test was run.
- Artemis was inspected; it requires a connected USB-debuggable Android device or emulator. None was available via `adb`/emulator in this environment.

## Known warnings/failures

- Node emitted the expected experimental `node:sqlite` warning during server/harness execution.
- The first Playwright configuration attempt used an incorrect relative harness path; it was corrected before the final 6/6 passing run. No product source behavior was changed.

## Readiness decision

The bundle-derived web source is now dependency-installed, buildable, server-tested, and browser-verified against the scripted integration harness. It is ready for the next milestone **only after the preservation checkpoint is committed**. Database/auth feature work itself is not required yet: both already exist in MVP form and should be hardened rather than duplicated.
