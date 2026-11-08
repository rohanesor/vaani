# iTANTRA LAP1 Ten-Language Runtime Validation

## Objective
Post-packaging Android runtime gate for the LAP1 ten-language test APK. No 500-file corpus, accuracy evaluation, or full E2E benchmark was run.

## APK identity
The supplied canonical APK was verified before testing:

- Path: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `6db754dde7248a2cb88db3e094d278210f7ebcb71159b8ef821fe1f6b0cbeda8`
- Size: 1,187,125,680 bytes
- Package/version: `com.vokie`, 1.0.0, versionCode 1
- ABI: arm64-v8a
- Packaging verifier: `TEN_LANGUAGE_TTS_PACKAGING=PASS`

The exact APK installed successfully on both devices and launched to `com.vokie/.MainActivity` without an observed immediate crash. It was then replaced during testing by a debug-only validation build containing a temporary ten-language smoke activity. That derived build had SHA-256 `3847e0aa30141935942631b03665d45f99f9b6b93a04f5471b56c6f4c244cf65` and must not be confused with the canonical APK. Consequently, per-language synthesis evidence below is runtime evidence for the unchanged TTS engine/model path in the derived validation build, but exact-canonical revalidation is BLOCKED until the supplied SHA artifact is restored.

## Device matrix

| Device | Serial | Manufacturer/model | Android | ABI | Available storage | Thermal observation |
|---|---|---|---:|---|---|---|
| A | `MJPVXCSG9HYL65YL` | realme RMX3782 | 15 | arm64-v8a | 57G available (`/storage/emulated/0/Android/obb` query) | Thermal status 3; severe throttling state, no performance claims |
| B | `RZCX41PAVLB` | Samsung SM-M356B | 16 | arm64-v8a | 35G available (`/data/user/0` query) | Thermal status 0; normal at observation |

## TTS runtime matrix
The temporary debug gate performed model installed check, initialization, synthesis, non-empty finite audio check, and playback. Timings are smoke-test observations only, not benchmarks or quality evidence.

| Language | Packaged | Load | Synthesis | Audio/playback | Result |
|---|---|---|---|---|---|
| English | PASS | PASS | PASS | PASS | PASS on A/B |
| Hindi | PASS | PASS | PASS | PASS | PASS on A/B |
| Tamil | PASS | PASS | PASS | PASS | PASS on A/B |
| Gujarati | PASS | PASS | PASS | PASS | PASS on A/B |
| Marathi | PASS | PASS | PASS | PASS | PASS on A/B |
| Kannada | PASS | PASS | PASS | PASS | PASS on A/B |
| Malayalam | PASS | PASS | PASS | PASS | PASS on A/B |
| Telugu | PASS | PASS | PASS | PASS | PASS on A/B |
| Odia | PASS | PASS | PASS | PASS | PASS on A/B; this is TTS only and used the packaged artifact, not Odia STT evidence |
| Bengali | PASS | PASS | PASS | PASS | PASS on A/B |

Representative observed smoke results ranged from approximately 355–9,872 ms synthesis and 723–2,069 ms audio duration across devices. These values are retained only as logs and are invalid for comparative performance conclusions because A was thermally throttled and the test was not instrumented as a benchmark.

## STT runtime matrix

| Language | Route/model evidence | Runtime result |
|---|---|---|
| English, Hindi, Tamil, Gujarati, Marathi, Kannada, Malayalam, Telugu, Bengali | Multilingual Whisper model loaded on device B (`whisper-tiny-multilingual-q5_1`, log evidence) | PARTIAL: model startup verified; per-language recorded-audio transcription was not run |
| Odia | Existing documented limitation | `RUNTIME_UNAVAILABLE` |

No transcription accuracy or WER/CER result was generated.

## Translation runtime matrix

| Coverage | Result |
|---|---|
| Device A | CT2 files were present at `files/ct2/nllb600m`; representative translation calls were not executed in this gate |
| Device B | CT2 directory was absent; engine is expected to return unavailable per architecture |
| Ten-language representative routes | BLOCKED / NOT TESTED |

No online translation was used and no translation-quality claim is made.

## Single-phone pipeline
`FULL_PIPELINE` was not run. Speech input → STT → translation → TTS → playback was not exercised for the ten languages. Odia is additionally blocked by `ODIA_STT=RUNTIME_UNAVAILABLE`. Status: **BLOCKED**.

## Two-phone readiness
Both phones installed and launched the app. Peer discovery, Wi-Fi/Bluetooth controls, connection, messaging, ACK handling, and receiver TTS handoff were not exercised in this gate. Status: **BLOCKED**.

## Thermal observations
Phone A reported Android thermal status 3 before testing. Functional smoke results were recorded independently, but any performance benchmark on A is invalid. Phone B reported status 0. No thermal failure was inferred from these observations.

## Known limitations and licensing
- Synthetic audio was not used in this phase and cannot support accuracy claims.
- All ten TTS artifacts remain `RESEARCH / UNAPPROVED`; provenance and redistribution authorization are incomplete.
- CT2 is runtime-staged rather than bundled and was not validated end-to-end here.
- The exact canonical APK SHA artifact was not recoverable after the temporary debug validation rebuild; do not distribute the current output as the canonical APK.

## Independent statuses

- `TEN_LANGUAGE_TTS_PACKAGING=PASS` (canonical APK inspection)
- `TEN_LANGUAGE_TTS_RUNTIME=PARTIAL` (all ten smoke-passed on derived validation build; exact canonical revalidation blocked)
- `TEN_LANGUAGE_STT_RUNTIME=PARTIAL`
- `TEN_LANGUAGE_TRANSLATION_RUNTIME=BLOCKED`
- `TEN_LANGUAGE_SINGLE_PHONE_PIPELINE=BLOCKED`
- `TWO_PHONE_READINESS=BLOCKED`
- `ACCURACY_VALIDATION=NOT YET TESTED`
- `LICENSING_APPROVAL=RESEARCH/UNAPPROVED`
- `ODIA_STT=RUNTIME_UNAVAILABLE`

## Evidence and exact next step
Filtered TTS log evidence is preserved under `artifacts/runtime-validation/`. The exact next step is to restore the APK with SHA `6db754...beda8` (or obtain it again from the canonical build artifact), verify its hash, reinstall it on both serials, and rerun only the ten-language smoke gate before any corpus or full pipeline work.
