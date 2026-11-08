# VAANI Milestone 16 Report — Android 16 KB Compatibility Fix and Runtime Validation

## Status

**Native compatibility defect fixed; runtime rerun remains blocked by emulator health/storage.** M16 identified and corrected the actual 16 KB-page native alignment defect. The rebuilt packaged library is 16 KB aligned. The external Windows emulator could not be recovered for a second installation/test run after its package service/disk failure.

## Baseline

- **Initial commit:** `d529229 — Harden optional Whisper startup for Android runtime validation (M15)`
- **Branch:** `vaani/main`
- **Initial working tree:** clean
- **Repository:** `/mnt/d/vibe/vaani`
- No changes were made to `/mnt/d/agent/artemis` or `/mnt/d/vibe/vokie`.

## Native Root Cause

The packaged library was inspected with `file` and `readelf`:

```text
ELF 64-bit LSB shared object, ARM aarch64
NDK r27
Original PT_LOAD alignment: 0x1000 (4096)
```

The Windows Pixel emulator uses a 16 KB system page size. Android rejected the library before the application process could start:

```text
program alignment (4096) cannot be smaller than system page size (16384)
```

## Native Fix

Updated `app/src/main/cpp/CMakeLists.txt` for `vokie_whisper`:

```cmake
-Wl,-z,max-page-size=16384
-Wl,-z,common-page-size=16384
```

The rebuilt packaged library was inspected directly from the APK:

```text
PT_LOAD alignment: 0x4000 (16384)
```

This is a genuine native compatibility fix, separate from the M15 guarded Kotlin loader. The guarded loader remains useful for unavailable/incompatible optional STT backends.

## Build and JVM Validation

| Task | Result |
|---|---|
| Android compile | **PASS** |
| Debug APK assembly | **PASS** |
| Packaged ELF alignment inspection | **PASS** — PT_LOAD segments 0x4000 |
| Android JVM tests | **PASS** — 458/458 |
| Android instrumentation compilation | **PASS** |

## Runtime Attempt

The Windows-host `Pixel_10_Pro_XL` emulator was previously authorized and reached `sys.boot_completed=1` in M15. M16 attempted to recover it and encountered:

- Emulator package service failure
- Broken-pipe APK installation errors
- Windows C: storage exhaustion
- Existing AVD writable data partition requiring approximately 7.3 GB while only approximately 124 MB was available

A disposable replacement AVD was created on D: with a reduced partition setting, but it remained `offline` and did not reach a usable `device` state within the controlled boot attempt.

The fixed APK therefore could not be installed on a healthy runtime after rebuilding.

## Instrumentation Results

The initial M15 direct instrumentation run on the authorized emulator produced **FAIL — BEFORE TEST EXECUTION** for all three required classes due to the original 4K alignment defect:

- `VokieDatabaseMigrationTest`
- `SecureCredentialStoreTest`
- `CloudSyncAccountIsolationTest`

After the native fix:

| Test area | Result |
|---|---|
| Migration instrumentation rerun | **BLOCKED** — emulator unhealthy/offline |
| Keystore instrumentation rerun | **BLOCKED** — emulator unhealthy/offline |
| Account-isolation instrumentation rerun | **BLOCKED** — emulator unhealthy/offline |
| APK install after fix | **BLOCKED** — package service/disk failure |
| App startup after fix | **NOT RUN** |
| Login/opt-in/logout | **NOT RUN** |
| Nearby/offline runtime flow | **NOT RUN** |

No instrumentation test is claimed as passing after the fix.

## Existing Regression Status

Previously executed and still applicable:

- Server tests: **PASS — 62/62**
- Server typecheck/build: **PASS**
- Client typecheck/build: **PASS**
- Playwright: **PASS — 8/8**

M16 changed only native build configuration and retained the M15 guarded startup behavior; Android compile/JVM validation passed after the change.

## Security Review

- No credentials or signing files committed.
- No SDK paths or `local.properties` committed.
- No APK/build outputs committed.
- No user data included.
- Nearby/offline transport code unchanged.
- Native alignment flags do not weaken credential or sync security.

## Remaining Blockers

1. Recover Windows emulator disk space or use a disposable test runtime with sufficient writable storage.
2. Obtain a stable authorized `device` ADB state.
3. Install the fixed APK.
4. Execute migration, Keystore, account-isolation, WebView, WorkManager, and app-flow tests.
5. Physical radio validation still requires physical hardware.

## Product-readiness validation addendum

After storage recovery, the existing Windows 16K Pixel reached `device` and `sys.boot_completed=1`. The rebuilt APK/test APK installed through Windows ADB. The next startup defect was missing optional `libvokie_ct2.so`; it was corrected so the optional offline translation backend reports unavailable rather than crashing application startup. Historical Room schemas were then packaged into test assets and the migration test inputs/assertions corrected.

The critical direct AndroidJUnitRunner run passed **10/10**: five Room migration paths, four Keystore credential tests, and cloud-sync account isolation. The full Android suite still requires a supported UI test image/dependency: API 37 rejects the installed Espresso input injector's use of removed `InputManager.getInstance()`. See `PRODUCT_READINESS_REPORT.md` for current release classifications.
