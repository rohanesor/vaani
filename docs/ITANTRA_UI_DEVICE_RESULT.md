# iTantra design — physical UI build result

## Git safety

Starting SHA: `1c205066f1084687b29a0353e8d4704fc4dff140`.
Checkpoint: `sih/before-design-physical-validation` (committed starting state). Earlier design changes were already uncommitted when this checkpoint was created and are included in the implementation commit; the checkpoint does not contain those working-tree edits.

Final implementation SHA: resolve the commit introducing this report with `git log -1 --format=%H -- docs/ITANTRA_UI_DEVICE_RESULT.md`. The exact SHA is also reported in the task response; a commit cannot embed its own hash.

## Build and install

- Unit tests: passed (`testDebugUnitTest`).
- App and instrumentation APK builds: passed (`assembleDebug assembleDebugAndroidTest`).
- App APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA-256 (verified on installed base.apk): `2382c417818f9fd9f6a1c6c2be01bffcacb12a9c7bd658ce23332a8c6fba88df`.
- Test APK SHA: `5f365ce2c1785f97398d1c2bf421fc21756a82df534d81eb64b6ff07c0256dad`.
- Device: Realme RMX3782, `MJPVXCSG9HYL65YL`, Android 15/API 35, arm64-v8a, authorized.
- Installation: app and test APK installed successfully, without uninstall or app-data clear.
- Instrumentation: `OK (3 tests)`, 9.911 seconds. Tests cover introduction/attribution/search, tap start/stop, single microphone permission request. Audio callbacks in those tests are doubles; this is NOT voice-loop validation.
- Normal launch: `Status: ok`, MainActivity, launch time 611 ms (activity launch, not model-load or inference latency).
- Screenshot: home visually inspected, showing wordmark, actual offline/no-peer state, English input/output, circular microphone and Speak/Messages/Devices navigation. No overlap observed in this screenshot. Other screens/densities remain to be inspected.
- Crash buffer empty; no matching fatal/ANR entries in the inspected recent logcat. This is limited smoke evidence, not proof of long-run stability.

## Thermal stop / remaining gates

After launch the device reported thermal status **3**. App was immediately force-stopped. Candidate APK remains installed for later testing after cooling; no more inference or UI tests were run.

Only one device connected and UI showed no linked peer. The following are **NOT TESTED / NOT PASSED**:

- real microphone capture / transcript quality
- Bluetooth TX/ACK and receiver RX
- CT2 translation, TTS generation and playback
- one turn = one submission/message/TX/RX/TTS/playback
- T0<T1<T2<T3<T4 and T5<T6<T7<T8
- queue_depth=0 / active_jobs=0 after a real turn
- vivo layout and physical behavior
- actual CDN download/install/retry with missing models
- all-screen pixel matching, TalkBack, large-font/rotation acceptance

Task is not fully accepted or promoted as production. Cool both devices, connect a receiver, and complete the manual checklist before claiming full success. No latency figures from this UI session are compared to production inference benchmarks.

## Implementation scope

See ITANTRA_DESIGN_IMPLEMENTATION.md for screen-by-screen mapping and deviations. Native Compose, existing state flows and callbacks are retained. Setup attribution now persists locally off-main-thread. Existing independent input/output routing configuration remains authoritative. Language-pack UI uses existing ModelDownloadManager and configured CDN, with confirmation and real state; no invented endpoint or automatic download.

No inference models, PacketV2, transport/ACK, native libraries, routing, timing events or TTS queue changes. A debug-only Compose test-host dependency was added for instrumentation. The PTT pointer recognizer remains independent of STT state, preserving the hold race fix. Existing PTT preference is wired to tap/hold behavior and accessibility click actions.

## Local evidence / rollback

Evidence and SHA-verified pre-install backup:
`C:\Users\kille\AppData\Local\Temp\itantra-ui-20260909-224434-517`.

Backup `before-ui-test.apk` SHA: `c9396e991031b16097170e90ecf248b742bf35cf5cbf1f853ffec1c98f87119f`.

This restores the pre-test profiling APK, **not** validated production SHA `072f0387…`. No exact-production restoration is claimed. APKs/screenshots/logcat are kept out of git. Existing models/WAVs/untracked artifacts were preserved.
