# Phase 3 real-device environment

## Verification date

2026-08-30. Windows PowerShell/Windows Java/Windows Android SDK were used for Gradle, and Windows ADB controlled both endpoints. No personal files, contacts, messages, photos, accounts, or credentials were collected.

## Device A — physical

| Field | Result |
|---|---|
| Type | Physical Android device |
| Manufacturer | realme |
| Model | RMX3782 / narzo 60x 5G |
| Android/API | 15 / 35 |
| ABI | arm64-v8a |
| ADB serial | MJPVXCSG9HYL65YL |
| ADB state | `device` / authorized |
| Display/density | 1080x2400 / 480 dpi |
| Application ID | `com.vokie` |
| Version | 1.0.0 / versionCode 1 |
| Microphone | RECORD_AUDIO granted=true |
| Current APK launch | PASS |

## Device B — virtual

| Field | Result |
|---|---|
| Type | Android Studio emulator |
| AVD name | Pixel_10_Pro_XL |
| Reported manufacturer | Google |
| Reported model | sdk_gphone16k_x86_64 |
| Android/API | 17 / 37 |
| ABI | x86_64 |
| ADB serial | emulator-5554 |
| ADB state | `device` |
| Display/density | 1344x2992 / 480 dpi |
| Application ID | `com.vokie` |
| Version | 1.0.0 / versionCode 1 |
| Microphone | RECORD_AUDIO granted=false; manual permission acceptance is still required |
| Current APK install | PASS |
| Current APK launch | FAIL — native ABI loading crash |

The AVD is named Pixel 10 Pro XL, but the runtime property reports the generic emulator model `sdk_gphone16k_x86_64`; both values are recorded rather than conflated.

## ADB and build/deployment checks

| Capability | Result | Evidence |
|---|---|---|
| Windows Java | PASS | Microsoft OpenJDK 17.0.18 |
| Windows SDK | PASS | `C:\Users\kille\AppData\Local\Android\Sdk` |
| Build Tools 34.0.0 | PASS | `aapt.exe`, `zipalign.exe`, `apksigner.bat` present |
| Device A detected | PASS | `MJPVXCSG9HYL65YL device` |
| Device B detected | PASS | `emulator-5554 device` |
| Both ADB-authorized | PASS | both status values are `device` |
| Windows Gradle tests | PASS | wrapper JAR + Windows Java, `BUILD SUCCESSFUL` |
| Windows debug build | PASS | `assembleDebug --no-daemon`, `BUILD SUCCESSFUL` |
| APK | PASS | `D:\vibe\vokie\app\build\outputs\apk\debug\app-debug.apk`, 198,787,226 bytes |
| APK install A | PASS | `adb install -r`, `Success` |
| APK install B | PASS | `adb install -r`, `Success` |
| App launch A | PASS | `MainActivity` displayed; no app crash observed |
| App launch B | FAIL | process crashed while loading `libvokie_whisper.so` |
| Logcat A | PASS | package-filtered logs captured |
| Logcat B | PASS | crash captured and diagnosed |
| Microphone A | PASS | permission already granted |
| Microphone B | PARTIAL | permission is false; manual acceptance not performed |

## Device B launch blocker

The current Gradle configuration packages only `arm64-v8a` native libraries. Device B is `x86_64`. Logcat reports:

```text
java.lang.UnsatisfiedLinkError: dlopen failed:
libvokie_whisper.so ... lib/arm64-v8a ...
program alignment (4096) cannot be smaller than system page size (16384)
```

The emulator is therefore not a valid current runtime endpoint for this APK. This is an ABI/package compatibility limitation, not a transport result. No product code was changed to work around it.

The realme arm64-v8a endpoint launches the current APK successfully.

## Transport classification

| Capability | Result |
|---|---|
| Two-endpoint ADB | PASS |
| Two-endpoint software testing | PARTIAL — emulator APK launch fails on native ABI |
| Physical phone-to-phone testing | BLOCKED — Device B is virtual |
| Wi-Fi Direct physical validation | BLOCKED |
| Bluetooth Classic RFCOMM physical validation | BLOCKED |
| Emulator/phone Wi-Fi Direct equivalence | NOT CLAIMED |
| Emulator/phone Bluetooth Classic equivalence | NOT CLAIMED |
| Internet-off local transfer | NOT MEASURED |

The emulator is suitable for ADB/install/protocol-only testing only after an x86_64-compatible debug native build exists. Its presence does not prove genuine Wi-Fi Direct or Bluetooth Classic behavior with the physical handset.

## Final classification

```text
DEVICE A = READY
DEVICE B = ADB READY, APP RUNTIME BLOCKED BY ARM64-ONLY APK
TWO-ENDPOINT_ADB = PASS
TWO-ENDPOINT SOFTWARE TESTING = PARTIAL
DEBUG APK BUILD = PASS
DEBUG APK INSTALL A = PASS
DEBUG APK INSTALL B = PASS
APP LAUNCH A = PASS
APP LAUNCH B = FAIL (native ABI mismatch)
LOGCAT A = PASS
LOGCAT B = PASS / blocker diagnosed
MICROPHONE A = PASS
MICROPHONE B = PARTIAL
PHYSICAL TWO-DEVICE TESTING = BLOCKED
```

No release build, dataset/model download, AWS action, training, or product source-code change was performed for this verification.
