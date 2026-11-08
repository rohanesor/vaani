# Final Test APK Candidate

**Status:** FINAL TEST CANDIDATE for later packaging/runtime validation; not a final SIH result.

| Property | Value |
|---|---|
| Filename | `app-debug.apk` |
| Path | `app/build/outputs/apk/debug/app-debug.apk` |
| SHA-256 | `6db754dde7248a2cb88db3e094d278210f7ebcb71159b8ef821fe1f6b0cbeda8` |
| Size | 1,187,125,680 bytes |
| Package | `com.vokie` |
| Version | `1.0.0` |
| Version code | `1` |
| Source commit | `1c057e4` plus packaging fix in this phase |
| Build command | `gradlew.bat assembleDebug -PbundleAllTtsModels=true --no-daemon` |
| Model contents | All ten TTS ONNX/token packs, STT model, native runtimes |
| Translation model | Not bundled; runtime-staged CT2 model required |
| Runtime synthesis | NOT TESTED |
| Corpus/E2E | NOT TESTED |

The APK is a local ignored build artifact and is not committed to Git. Verify with:

```bash
sha256sum app/build/outputs/apk/debug/app-debug.apk
```
