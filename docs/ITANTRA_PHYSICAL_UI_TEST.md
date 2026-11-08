# Physical UI test readiness

Historical preparation snapshot: the later installation, final APK SHA and physical results are in `ITANTRA_UI_DEVICE_RESULT.md`. The checklist below remains applicable.

## Prepared and verified

- `testDebugUnitTest`, `assembleDebug`, and `assembleDebugAndroidTest`: BUILD SUCCESSFUL.
- Realme RMX3782, serial `MJPVXCSG9HYL65YL`, authorized, API 35, arm64-v8a.
- Latest preparation preflight: thermal status **1**. Recheck before testing; this is not a permanent clearance.
- No APK installed, app launched, app data cleared, or device settings changed during preparation.
- Instrumentation suite compiled/packaged, **not executed**.

Artifacts (do not commit):

| Artifact | Path | SHA-256 |
|---|---|---|
| App | `app/build/outputs/apk/debug/app-debug.apk` | `0d176458331e8c4f4dc3819fec3d914ab26fcb07a400c3fec1e24800bddacbc3` |
| UI tests | `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `5f365ce2c1785f97398d1c2bf421fc21756a82df534d81eb64b6ff07c0256dad` |

## Rollback

The pre-test installation was pulled and its SHA verified:

`C:\Users\kille\AppData\Local\Temp\itantra-ui-20260909-223633-531\before-ui-test.apk`

SHA: `c9396e991031b16097170e90ecf248b742bf35cf5cbf1f853ffec1c98f87119f`.

**This is the previously installed profiling APK, NOT the validated production APK** (`072f0387…`). UI test rollback is available, but exact production restoration remains a separate unresolved prerequisite for any production/latency promotion. The backup restores APK code only, not preference/database changes made manually during testing. Record original languages, selected peer, PTT mode and speech speed and restore them manually.

## Run from Windows PowerShell

Keep the device unlocked. Close other workloads and let it cool. Ensure the phone is not needed for a live emergency.

```powershell
cd D:\vibe\vokie
# Read-only preflight; makes another verified rollback APK in Windows TEMP.
.\scripts\test-itantra-ui.ps1

# Explicit opt-in: installs app + test APK and runs only ITantraDesignTest.
.\scripts\test-itantra-ui.ps1 -InstallAndTest
```

The runner checks authorization, verifies the APK backup before installation, requires thermal status below 2 at start, verifies the installed candidate SHA, saves the instrumentation output and recent logcat, then stops the app. It leaves the candidate installed for manual checks. It does not uninstall or clear app data, clear logcat, reset models, or change device settings. It prints a rollback command. Logs may contain private message text: keep them local; redact before sharing.

Tests exercise introduction/attribution/search, tap-mode start/stop, and a single permission request. Their microphone callbacks are test doubles: they do **not** validate real audio, packet counts, transport, or TTS. App initialization can still load native resources.

There is no continuous thermal watchdog in this runner. Monitor in another terminal during all testing:

```powershell
adb -s MJPVXCSG9HYL65YL shell dumpsys thermalservice
```

Stop immediately at status 3, unsafe temperature, crash, ANR, or instability:

```powershell
adb -s MJPVXCSG9HYL65YL shell am force-stop com.vokie.test
adb -s MJPVXCSG9HYL65YL shell am force-stop com.vokie
```

Do not run CT2 long-text/latency benchmarks as part of UI acceptance.

## Manual acceptance checklist

Launch normally after automated tests: `adb -s MJPVXCSG9HYL65YL shell am start -n com.vokie/.MainActivity`.

- [ ] Splash and existing profile startup: no forced setup, blank screen or artificial progress.
- [ ] Speak / Messages / Devices navigation; Settings header; Android Back; Locate and SOS still reachable.
- [ ] Settings language editor preserves independent input/output selections; search/no-match/clear/swap work.
- [ ] First-run flow: use an approved spare profile/device or explicitly consent to the existing debug onboarding reset. Never `pm clear`/uninstall to reach it. Test intro Next/Skip/Back, optional attribution, selection and completion.
- [ ] Rotation, background/foreground, system font scaling: no lost selections or unreachable buttons.
- [ ] Voice pack dialog: actual stored sizes, unavailable states, close/back; no imaginary downloads or Telugu/Kannada enabled.
- [ ] TalkBack labels and click actions; controls reachable at large font size.
- [ ] Permission denied/granted path; one dialog per press; no accidental speech start while engine unavailable.
- [ ] Hold mode: one start on down, one stop on release/cancellation; recognizer remains responsive after status changes.
- [ ] Tap mode: one start per tap, next tap stops; restore original mode after testing.
- [ ] Single short voice turn with a consenting peer: no duplicate packet/delivery/TTS; queue drains. Requires a second connected device; not covered by automated UI tests.
- [ ] Real incoming playback shows actual text/translation. Message replay/stop and SOS acknowledgement preserve existing policy.
- [ ] Message delivery/failure/retry status and timestamps; text composer remains usable with keyboard.
- [ ] Settings speed, installed packs, About, advanced/maps/diagnostics work; restore original settings.
- [ ] No crash/ANR/restart; inspect local logcat. Record thermal before/after and screenshot names.

Screenshot example (Windows-safe binary transfer):

```powershell
adb -s MJPVXCSG9HYL65YL shell screencap -p /sdcard/itantra-ui.png
adb -s MJPVXCSG9HYL65YL pull /sdcard/itantra-ui.png "$env:TEMP\itantra-ui.png"
```

Record PASS/FAIL/NOT TESTED for each item. Consult `ITANTRA_DESIGN_IMPLEMENTATION.md` for intentional deviations: this is a native adaptation, not a pixel-identical implementation of every unsupported mock capability.

## Restore pre-test code

Use the exact backup path printed by the runner:

```powershell
adb -s MJPVXCSG9HYL65YL install -r 'C:\Users\kille\AppData\Local\Temp\itantra-ui-20260909-223633-531\before-ui-test.apk'
adb -s MJPVXCSG9HYL65YL shell pm path com.vokie
# Run shell sha256sum on the returned base.apk path and compare with c9396e991…
```

Never claim restoration solely from an install success message. Verify the installed SHA. Neither UI acceptance nor this profiling-APK rollback qualifies as production latency validation.
