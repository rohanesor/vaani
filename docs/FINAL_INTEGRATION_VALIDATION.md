# Final Integration Validation — M19

## External dependency preflight

| Workstream | Result | Exact dependency |
|---|---|---|
| Live Gnani STT | BLOCKED | `GNANI_API_KEY` and controlled external WAV fixture absent |
| Live Gnani TTS | BLOCKED | Same live STT prerequisite; no provider request made |
| Android cloud login/sync | BLOCKED | Recording build has blank `AI_BACKEND_BASE_URL`; no controlled deployed backend/test identities supplied |
| Physical nearby direct messaging | BLOCKED | Only `emulator-5554` available; no physical Android devices |
| Mesh/multi-hop | BLOCKED | No three-device physical topology |

## Executed regression evidence

- Android build/JVM/androidTest source compilation: PASS.
- Android Appium: PASS 8/8 on API 37 Windows Pixel.
- Android critical migration/Keystore/account-isolation instrumentation: PASS 10/10 (existing runtime evidence).
- Server full suite/typecheck/build: PASS 62/62.
- Web Playwright: PASS 8/8 desktop/mobile.
- Gnani deterministic adapter tests: PASS 15/15 **MOCK-BACKED**.
- `npm run test:gnani:live`: BLOCKED with clear, secret-free prerequisite diagnostic.

## Verified language evidence

Tamil local Android UI selection is PASS. No Gnani STT/TTS language is LIVE-VERIFIED; no live latency or transcript exists.

## Evidence directory convention

When dependencies are supplied, place non-secret generated evidence outside git under `reports/m19/gnani/`, `reports/m19/android-cloud/`, and `reports/m19/nearby/`. Include fixture checksum/configuration identifiers, timings, safe account labels, request IDs, screenshots, and logs—but never audio fixtures, passwords, tokens, API keys, or device identifiers.
