# Release Readiness

| Area | Status | Evidence / action |
|---|---|---|
| Android debug compile and APK | READY | Built after native/optional-backend fixes. |
| Android JVM tests | READY | 458/458 previously; rerun successful in this milestone. |
| Critical Android instrumentation | READY | 10/10: Room 7→8→9→10/direct paths, Keystore, account isolation. |
| Android full UI instrumentation | BLOCKED | API 37 image is incompatible with installed Espresso input injection (`InputManager.getInstance` removed). Run on supported stable image after updating test stack. |
| Android application process launch | READY | Fixed APK installed/launched on 16K Pixel; Appium M18 lifecycle/onboarding/navigation suite passed 8/8. |
| Whisper 16K compatibility | READY | Packaged `libvokie_whisper.so` PT_LOAD alignment verified at 0x4000. |
| Optional CT2 availability | READY | Missing library is explicit unavailable state, no app-startup crash. Production CT2 assets/library still require release staging. |
| Server typecheck/build/tests | READY | Typecheck/build pass; 62/62 Vitest pass. |
| Client typecheck/build/browser tests | READY | Typecheck/build pass; Playwright 8/8 pass. |
| Gnani adapter | BLOCKED | Contract tests pass; live smoke command is available but controlled key and WAV fixture were not supplied. |
| Cloud sync/auth | READY (tested scope) | Server sync/auth tests and Android credential/account-isolation instrumentation pass. |
| Production secrets | BLOCKED | Set stable `VAANI_JWT_SECRET`, `GNANI_API_KEY` and optional AI key only in deployment environment. |
| Production signing | BLOCKED | Configure release keystore outside repository; no signing material committed. |
| CORS/HTTPS | NEEDS VERIFICATION | Set production HTTPS origin and run deployment smoke test. |
| Database backup/restore | NEEDS VERIFICATION | SQLite persistent volume and restore drill required. |
| Multi-instance deployment | BLOCKED | In-memory rate limiter/WebSocket hub require single instance. |
| Physical nearby/Bluetooth/Wi-Fi Direct | BLOCKED | Requires two physical devices/radios. |
| Live recording clips | BLOCKED | M19 did not approve full launch recording: live Gnani, Android cloud, and physical nearby evidence are absent. |

## Release gate

A single-instance web/server release may proceed only after deployment configuration, live provider smoke tests (if speech is promoted), signing, backup/restore, and supported-device UI validation are completed. Do not claim Gnani live capability in launch media until live evidence exists.
