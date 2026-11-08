# VAANI Milestone 15 Report — External Android Runtime and Instrumentation Validation

## Status

**M15 runtime validation was attempted on an authorized external emulator but did not pass.** The Windows-host `Pixel_10_Pro_XL` emulator reached `device` with `sys.boot_completed=1`, allowing APK installation and direct instrumentation invocation. All required test processes crashed during application startup because the bundled Whisper native library uses 4K ELF alignment while the API-37/16K-page emulator requires 16K alignment. A production fix was applied to make the optional Whisper backend fail gracefully instead of crashing application startup. A rerun was then blocked by the Windows emulator becoming unhealthy and subsequently reporting insufficient disk space.

## Baseline

- **Initial commit:** `f6e1e26 — Document Android runtime provisioning and instrumentation blocker (M14)`
- **Branch:** `vaani/main`
- **Initial working tree:** clean
- **Repository:** `/mnt/d/vibe/vaani`
- `/mnt/d/agent/artemis` was not modified.

## External Runtime Discovery

### Windows-host Android SDK

Discovered on the Windows host:

```text
C:\Users\kille\AppData\Local\Android\Sdk
```

Available Windows tools:

- `platform-tools/adb.exe`
- `emulator/emulator.exe`

Available Windows AVD:

```text
Pixel_10_Pro_XL
```

### Windows emulator attempt

The Windows-host emulator process was started through PowerShell. After restarting the Windows ADB server and emulator, it reached:

```text
emulator-5554 device
sys.boot_completed=1
```

Runtime readiness passed for the initial test attempt.

### WSL2 fallback

The WSL2 fallback remains unsupported for this environment:

- `/dev/kvm` absent
- Existing `iTantra_Test` AVD requires software emulation
- Previous software boot did not complete
- No new repeated WSL software-emulator attempt was used for acceptance

## Runtime Readiness Gate

| Check | Result |
|---|---|
| `adb version` | **PASS** |
| `adb devices -l` | **BLOCKED** — no authorized usable device; Windows emulator was `unauthorized` |
| `sys.boot_completed=1` | **NOT RUN** — readiness failed first |
| Android release/API query | **NOT RUN** |
| Basic authorized shell command | **NOT RUN** |
| APK installation | **NOT RUN** |

## Instrumentation

The aggregate Gradle task was attempted through WSL and Windows tooling. WSL Gradle stalled at ADB monitoring; Windows Gradle encountered host memory/SDK issues. The compiled test APKs were then installed and the AndroidJUnitRunner was invoked directly for the required classes.

Direct invocation used:

```bash
adb -s emulator-5554 shell am instrument -w -r \
  -e class <test-class> \
  com.vaani.app.test/androidx.test.runner.AndroidJUnitRunner
```

All three required classes started but failed before test execution because `VokieApplication` loaded `libvokie_whisper.so` and Android reported:

```text
program alignment (4096) cannot be smaller than system page size (16384)
```

After that finding, the optional Whisper backend was changed to load safely and allow the app to continue with the existing fallback path. The emulator then became unhealthy (`package` service unavailable) and the Windows SDK reported insufficient disk space on restart, so the fixed APK could not be reinstalled and the tests could not be rerun.

The following remain **NOT RUN after the fix**:

- `VokieDatabaseMigrationTest`
- `SecureCredentialStoreTest`
- `CloudSyncAccountIsolationTest`
- Existing Android instrumentation tests

## Runtime Test Results

| Area | Result |
|---|---|
| Room 7→8 | **FAIL before test execution** — application startup native-load crash |
| Room 8→9 | **FAIL before test execution** — application startup native-load crash |
| Room 9→10 | **FAIL before test execution** — application startup native-load crash |
| Direct 7→10 | **FAIL before test execution** — application startup native-load crash |
| Direct 8→10 | **FAIL before test execution** — application startup native-load crash |
| Keystore runtime tests | **FAIL before test execution** — application startup native-load crash |
| Legacy credential migration | **NOT RUN** |
| Account isolation | **FAIL before test execution** — application startup native-load crash |
| WebView/native bridge | **NOT RUN** |
| APK install/startup after fix | **BLOCKED** — emulator package service/disk failure |
| Login/opt-in/logout | **NOT RUN** |
| Nearby/offline Android flow | **NOT RUN** |
| Physical Bluetooth/mesh/Wi-Fi Direct | **NOT RUN** |

## Compile and Regression Results

M15 revalidated the supported non-runtime suites:

| Suite | Result |
|---|---|
| Android compile | **PASS** |
| Android debug APK assembly | **PASS** |
| Android JVM tests | **PASS — 458/458** |
| Android instrumentation compilation | **PASS** |
| Server typecheck | **PASS** |
| Server build | **PASS** |
| Server tests | **PASS — 62/62** |
| Client typecheck | **PASS** |
| Client build | **PASS** |
| Playwright standalone rerun | **PASS — 8/8** |

## Handoff

Created:

```text
docs/ANDROID_RUNTIME_HANDOFF.md
```

It contains the Windows-host AVD procedure, ADB readiness gate, WSL2 connectivity notes, APK/instrumentation commands, required evidence, and safety rules.

## Defect Found and Fixed

`VokieApplication` eagerly constructed `WhisperSttEngine`, whose `WhisperNative` initializer called `System.loadLibrary()` unconditionally. On the API-37/16K-page emulator this crashed the entire app before any instrumentation test could execute because the bundled native library is 4K-aligned.

The fix makes Whisper native loading optional and fail gracefully. Initialization reports the existing STT failure state, allowing the application’s existing fallback engine path to continue instead of crashing startup. Android compilation and JVM tests pass after the change.

## Security Review

- No SDK paths or `local.properties` committed.
- No APKs, build outputs, signing files, credentials, or tokens committed.
- No unrelated repository modified.
- No credential values included in reports.
- Native STT fix does not weaken credential or sync security.

## Remaining Blocker

The Windows-host emulator must be authorized and remain in the `device` state. A physical Android device is an alternative. Until then, M15 remains runtime validation blocked.

## Recommended Next Milestone

**M16: Authorized Windows-host emulator or physical-device execution**, followed by the migration, Keystore, account-isolation, APK startup, WebView/cloud-control, WorkManager, and nearby/offline validation matrix.
