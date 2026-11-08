# M18 — Final Product Validation and Release Freeze

## Baseline and result

- Initial commit: `6f214f3`
- Branch: `vaani/main`
- Baseline root suite: PASS (Gnani deterministic 15/15, Playwright 8/8, Appium 5/5).
- Final Appium suite: PASS 8/8.

## Android validation

Windows-host Pixel: `emulator-5554`, `device`, boot complete, API 37, `sdk_gphone16k_x86_64`, 16 KB page image. Appium/UiAutomator2 validated native permission control, WebView automation, first-launch completion, transcript navigation, Tamil local-UI selection, foregrounding, and restart. It avoids the API-37 Espresso input-injection defect.

Android login, server-backed sync UI, end-to-end messaging with another peer, live speech, and radio communication were not represented as passing: controlled server/peer/provider fixtures were unavailable.

## Web/server/Gnani

- Server full tests/typecheck/build: PASS 62/62.
- Client typecheck/production build/Playwright: PASS, 8/8.
- Gnani deterministic tests: PASS 15/15, **MOCK-BACKED**.
- `npm run test:gnani:live`: BLOCKED clearly without `GNANI_API_KEY` plus controlled external WAV. No keys, audio, request bodies, latency, accuracy, or provider language claims were recorded.

## Freeze

A debug recording candidate and checksum are recorded in `RECORDING_BUILD_MANIFEST.md`. It is a validated local/UI candidate, not a production release freeze: signing, controlled live Gnani verification, deployed cloud lifecycle tests, backup/restore, physical transport tests, and protected corpus execution remain gates.

## Evidence

- Appium failure diagnostics: ignored `tests/android/reports/`
- Playwright traces/screenshots: ignored client result/report directories
- Detailed classifications: `FINAL_TEST_RESULTS.md` and `TEST_COVERAGE_MATRIX.md`
