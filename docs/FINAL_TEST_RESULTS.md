# Final Test Results — M18

| Area | Result | Evidence |
|---|---|---|
| Android build/JVM/androidTest compilation | PASS | Gradle commands completed successfully |
| Android critical migration/Keystore/sync instrumentation | PASS | 10/10, M16 runtime run |
| Android Appium UI | PASS | 8/8: startup, permission, WebView, onboarding, transcript nav, language, lifecycle/restart |
| Android Espresso Compose UI | BLOCKED | API 37 removed API used by installed Espresso injector |
| Server full suite | PASS | 62/62, typecheck, build |
| Web production build/typecheck | PASS | Completed M18 |
| Web Playwright | PASS | 8/8 desktop/mobile |
| Gnani deterministic | PASS — MOCK-BACKED | 15/15 server adapter/orchestration tests |
| Gnani live STT/TTS | BLOCKED | `GNANI_API_KEY` and controlled `GNANI_LIVE_WAV` not supplied |
| Android cloud login/sync UI | NOT RUN | Requires controlled deployed backend identity/fixture |
| Local peer messaging/radio | BLOCKED | Requires second device/radio; emulator is not physical evidence |
| Protected corpus | BLOCKED | Authorized external corpus not staged |

## Evidence-based languages

Android local UI selection executed for Tamil. No Gnani STT/TTS language is LIVE-VERIFIED. Server accepts the configured application language codes, but this is not live provider coverage.

## Defects fixed in M18

No product defect was changed. Appium navigation initially revealed that onboarding overlays the main UI on first launch; the test now completes that real first-launch flow before asserting main navigation. This is test sequencing, not a product behavior change.
