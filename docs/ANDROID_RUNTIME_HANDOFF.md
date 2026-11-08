# VAANI Android Runtime Handoff

## Purpose

This document is an executable handoff for completing M15/M16 Android runtime validation from a supported Windows-host emulator or physical Android device.

## Current Environment

- VAANI repository: `/mnt/d/vibe/vaani`
- Branch: `vaani/main`
- M15 attempted from WSL2
- WSL2 kernel: `6.6.87.2-microsoft-standard-WSL2`
- `/dev/kvm`: unavailable
- Linux SDK: `/mnt/d/android-sdk`
- Windows SDK discovered at: `C:\Users\kille\AppData\Local\Android\Sdk`
- Windows AVD discovered: `Pixel_10_Pro_XL`

## Current Blocker

The Windows-host emulator process starts, but Windows ADB reports:

```text
emulator-5554 unauthorized
```

A usable runtime must report:

```text
emulator-5554 device
```

The Linux WSL2 AVD `iTantra_Test` is not a supported fallback here: it requires software emulation, lacks KVM, and did not complete boot.

## Windows-Host Runtime Procedure

Run these steps from an authorized Windows PowerShell session using the Windows SDK:

```powershell
$Sdk = "$env:LOCALAPPDATA\Android\Sdk"
$Adb = "$Sdk\platform-tools\adb.exe"
$Emulator = "$Sdk\emulator\emulator.exe"

& $Adb kill-server
& $Adb start-server
& $Emulator -list-avds
& $Emulator -avd Pixel_10_Pro_XL
```

Wait for the emulator to reach the Android home screen. Then verify on Windows:

```powershell
& $Adb devices -l
& $Adb -s emulator-5554 shell getprop sys.boot_completed
& $Adb -s emulator-5554 shell getprop ro.build.version.release
& $Adb -s emulator-5554 shell getprop ro.build.version.sdk
& $Adb -s emulator-5554 shell getprop ro.product.model
```

Expected readiness:

- `adb devices -l` state is `device`, not `offline` or `unauthorized`
- `sys.boot_completed` is `1`
- Basic `adb shell` commands return successfully

If the emulator remains `unauthorized`, accept the RSA authorization dialog in the emulator UI. Do not delete personal Windows ADB keys without authorization.

## WSL2 ADB Connectivity

The existing Artemis helper documents the expected pattern:

```text
ADB_HOST=<WSL default gateway>
ADB_PORT=5037
```

From WSL2:

```bash
ip route show default
ADB_HOST="$(ip route show default | awk '{print $3}')"
export ADB_HOST ADB_PORT=5037
adb devices -l
```

If the Windows ADB server is not reachable, start it on Windows with a supported firewall/network configuration. Do not make broad firewall changes. The Windows ADB server must bind an address reachable from WSL2, or tests can run directly from Windows using the repository checkout/mounted path.

## VAANI Test Commands

From `/mnt/d/vibe/vaani` with a configured SDK:

```bash
./gradlew --no-daemon :app:compileDebugKotlin --console=plain
./gradlew --no-daemon :app:assembleDebug --console=plain
./gradlew --no-daemon :app:testDebugUnitTest --console=plain
./gradlew --no-daemon :app:compileDebugAndroidTestKotlin --console=plain
./gradlew --no-daemon :app:connectedDebugAndroidTest --console=plain
```

Install the debug APK only after readiness passes:

```bash
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 shell monkey -p com.vaani.app 1
adb -s emulator-5554 logcat -c
adb -s emulator-5554 logcat
```

## Required Runtime Evidence

Record:

1. Device model and Android release/API level
2. `sys.boot_completed=1`
3. `adb devices -l` state `device`
4. Instrumentation command output
5. Test count and failures
6. Room migration results for 7→8, 8→9, 9→10, direct 7→10, direct 8→10
7. Keystore test results
8. Account-isolation test results
9. APK install/startup result
10. Login, opt-in, sync, logout, and account-switch results

## Current Unexecuted Tests

- `VokieDatabaseMigrationTest`
- `SecureCredentialStoreTest`
- `CloudSyncAccountIsolationTest`
- Existing Android UI/corpus instrumentation
- WebView/native bridge runtime flows
- Physical Bluetooth/mesh/Wi-Fi Direct flows

These tests are compiled but not runtime-passed.

## Safety Rules

- Use fictional test accounts and data.
- Do not record or report tokens.
- Do not commit SDK paths, `local.properties`, APKs, signing files, or logs containing credentials.
- An emulator does not count as physical radio validation.
- Do not report instrumentation as passed unless the test process executed successfully.
