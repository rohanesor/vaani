# VAANI Product Readiness Report

## Scope and baseline

- Starting checkpoint: `d529229` on `vaani/main`
- Repository: `/mnt/d/vibe/vaani`
- No changes made to Artemis or the original Vokie repository.

## Defects fixed

1. **16 KB Android native compatibility:** `libvokie_whisper.so` previously had 4 KB PT_LOAD alignment and crashed on the 16 KB Pixel. CMake now links it with 16 KB maximum/common page sizes; packaged ELF inspection confirms `0x4000` alignment.
2. **Optional CT2 startup crash:** CT2 is optional in native build staging but was loaded unconditionally. Its missing native library now produces explicit offline-translation unavailability rather than terminating the app.
3. **Room migration runtime coverage:** historic schemas are now packaged into the androidTest APK. Corrected migration-test inputs/assertions exposed by the real device run.

## Executed tests

| Area | Result |
|---|---|
| Android compile, APK, JVM tests, androidTest compilation | PASS |
| Critical Android runtime tests (Pixel API 37, 16 KB page) | PASS — 10/10 |
| Android full instrumentation suite | FAIL/BLOCKED — 12/17 failures: protected corpus absent (1), API-37 Espresso incompatibility (6), pre-fix migration test defects (5; resolved and rerun separately) |
| Server test/typecheck/build | PASS — 62/62 |
| Client typecheck/build/Playwright | PASS — 8/8 |
| Live Gnani STT/TTS | NOT RUN |
| Physical nearby transport | NOT RUN |

## Product and Gnani audit

VAANI has transcript-oriented web communication, authenticated messaging, cloud sync, AI assistance, voice-note lifecycle, and Android nearby/offline components. Gnani is a real server-side Prisma STT/Timbre TTS integration with authenticated server-only configuration, bounded WAV chunking, validated inputs, and explicit failure states. It is not yet live-validated in this milestone; no language/latency/accuracy claims are made.

## Recording readiness

A repeatable fictional scenario and individual-shot plan are in `PRODUCT_LAUNCH_RECORDING_PLAN.md`. No video, screenshot, or audio clip was created; none is claimed. The hero Gnani clips are blocked until an approved live configured account confirms actual model/voice/language behavior.

## Release blockers

- Live Gnani smoke evidence if Gnani is a launch claim.
- Release signing/configuration and backup/restore verification.
- Supported Android image/device UI test run (API-37 Espresso incompatibility).
- Physical nearby/offline validation.
- Single-instance deployment restriction.

See `RELEASE_READINESS.md` for classifications and `GNANI_INTEGRATION_ARCHITECTURE.md` for the verified flow.
