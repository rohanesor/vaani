# Playwright Test Strategy

The existing `client/e2e/baseline.spec.ts` remains the canonical web UI suite. It runs against `server/test/e2e-server.ts`, uses fixture identities, and exercises desktop and mobile-Chrome projects.

## Executed coverage

The 8 passing tests cover both projects: authentication, transcript conversation navigation, search, AI draft/context UI without live providers, persisted idempotent message submission, voice-recorder unavailable-microphone feedback, dark theme, and responsive navigation.

Playwright retains screenshots and traces on failure (`client/test-results/`, `client/playwright-report/`, ignored by git). The suite deliberately does not represent test-double AI or unavailable microphone UI as live Gnani/provider validation.

## Expansion boundary

Cloud-sync and nearby/offline behavior are Android-specific rather than web browser capabilities. Live provider tests remain a separately gated smoke suite because browser E2E must not use real provider credentials.
