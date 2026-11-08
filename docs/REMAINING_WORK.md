# VAANI Remaining Work and Release Blockers

## Product-readiness status

Core Android build/JVM validation, critical Android migration/Keystore/account-isolation runtime coverage, Android Appium UI validation (8/8), server regression (62/62), and client browser regression (8/8) pass. The 16K native Whisper crash and optional CT2 startup crash are fixed.

## Must complete before a public launch claim

| Priority | Work | Status |
|---|---|---|
| P0 | Configure a controlled live Gnani account and capture real STT/TTS smoke evidence for the exact model/voice/languages promoted | BLOCKED by live provider configuration |
| P0 | Configure production signing, stable JWT secret, HTTPS CORS origin, persistent DB volume, and backup/restore drill | NEEDS DEPLOYMENT ENVIRONMENT |
| P0 | Record the staged fictional demo only after live provider verification | PLANNED |
| P1 | Run Compose/UI instrumentation on a supported stable Android image or update the Espresso test dependency for API 37 | BLOCKED by API-37 test-framework incompatibility; Appium native smoke coverage now passes |
| P1 | Physical two-device Bluetooth/mesh/Wi-Fi Direct validation | BLOCKED by hardware |
| P1 | Validate login/opt-in/logout and WorkManager/cloud lifecycle against a deployed staging server | NOT RUN — requires controlled Android server fixture |
| P2 | Refresh rotation, device-session management, password reset/MFA policy | NOT IMPLEMENTED |
| P2 | Shared DB/realtime/rate limiting for multi-instance operation | NOT IMPLEMENTED — deploy one instance only |

## M19 launch gate

M19 confirms that full launch approval remains blocked by three exact external dependencies: controlled Gnani credential plus WAV fixture; controlled deployed backend plus two disposable identities; and at least two physical Android devices. The emulator/Playwright suites are not substitutes for those tests.

## Recording references

- `GNANI_INTEGRATION_ARCHITECTURE.md`
- `GNANI_INTEGRATION_DEMO.md`
- `PRODUCT_LAUNCH_RECORDING_PLAN.md`
- `RELEASE_READINESS.md`

No recording file, live latency figure, provider accuracy figure, or unverified language support should be presented as completed.
