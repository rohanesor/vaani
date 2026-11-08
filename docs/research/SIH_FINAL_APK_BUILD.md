# SIH Final APK Build

## Status

**BUILD = PASS**

The complete LAP1 debug APK was built from the current source commit. The 500-sample corpus was not packaged into the APK.

| Property | Value |
|---|---|
| Source branch | `sih/ui-fidelity-corrections-wip` |
| Source commit | `49dfe705f222430e36e59160d130b3043e00fa66` |
| Build command | Windows Gradle: `gradlew.bat clean assembleDebug --no-daemon` |
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Size | 502,851,112 bytes |
| SHA-256 | `2521042d37a2662b4b4e05b7cb860e03c6c69c557ad17849a32d6cf4b9647bf4` |
| Package | `com.vokie` |
| Label | `iTantra` |
| Version | `1.0.0` |
| Version code | `1` |
| ABI | `arm64-v8a` |
| Certificate | Android Debug; SHA-256 `2944d8e4d0bbe1d42b53277c765f53bfabd24cd86207981d891ff7c127035ddf` |

## APK Contents Audited

Present under `lib/arm64-v8a/`:

- `libvokie_ct2.so`
- `libvokie_whisper.so`
- `libonnxruntime.so`
- `libsherpa-onnx-jni.so`
- `libsherpa-onnx-c-api.so`
- `libsherpa-onnx-cxx-api.so`
- `libc++_shared.so`

Present under `assets/models/`:

- `manifest.json`
- `stt/ggml-tiny-q5_1.bin`
- `tts/eng/model.onnx`
- `tts/hin/model.onnx`
- `tts/tam/model.onnx`
- `tts/guj/model.onnx`

The CT2/NLLB translation model is **not in the APK**; project architecture stages it into app-private storage. Its runtime availability on the devices was not established by this build step.

## Build Checks

| Check | Result |
|---|---|
| `./gradlew testDebugUnitTest --no-daemon` | PASS |
| `./gradlew lint --no-daemon` | PASS |
| `assembleDebug` | PASS |
| APK size | Complete-build scale (~503 MB) |

## Devices

The exact APK was installed successfully with `adb install -r` on:

- `MJPVXCSG9HYL65YL` — RMX3782, Android 15, arm64-v8a
- `RZCX41PAVLB` — SM-M356B, Android 16, arm64-v8a

Both report package `com.vokie`, version code `1`, version name `1.0.0`.
