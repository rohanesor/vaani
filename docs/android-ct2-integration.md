# Android CTranslate2 arm64 integration status

## Scope

Phase 2 is limited to approved NLLB CTranslate2 Android arm64 work. No PacketV2, Bluetooth, Wi-Fi Direct, STT, TTS, or UI code was modified in this native-build attempt.

## Source and model

- CTranslate2: `v4.8.2`, resolved source commit `d44d2d069eb88c7b7804da864c10c201501cb4a9`.
- Model: `osa911/nllb-200-distilled-600M-ct2-int8` revision `46858753dbaf8eb5e21bb6f0037c3b90851e090a`.
- `model.bin`: 619,704,329 bytes, SHA-256 `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`.
- Model files remain in `model-lab/models/ct2/nllb600m/`; they are not APK assets and no manifest was changed.

## Native arm64 build — CODE/BUILD VERIFIED

A CPU-only static CTranslate2 archive was successfully cross-compiled:

```text
ABI: arm64-v8a / AArch64
min platform: android-24
NDK: 27.0.12077973 (Clang 18.0.1)
CMake: 3.22.1
CTranslate2: 4.8.2
output: libctranslate2.a (105,511,500 bytes)
```

Build settings:

```text
BUILD_SHARED_LIBS=OFF  BUILD_CLI=OFF  BUILD_TESTS=OFF
WITH_MKL=OFF  WITH_DNNL=OFF  WITH_ACCELERATE=OFF
WITH_OPENBLAS=OFF  WITH_RUY=ON
WITH_CUDA=OFF  WITH_CUDNN=OFF  WITH_HIP=OFF
OPENMP_RUNTIME=NONE  ENABLE_CPU_DISPATCH=OFF
```

Reproduce with `scripts/build-ct2-android-arm64.sh`. The first attempt correctly failed because the shallow checkout omitted CT2 submodules. After `git submodule update --init --recursive`, the only source portability error was Android bionic lacking `pthread_setaffinity_np`. The reproducible script applies a narrow Android-only change to CT2’s optional thread-affinity guard; it does not alter translation behavior.

SentencePiece 0.2.1 was also cross-compiled as `libsentencepiece.a` (26,199,892 bytes) for JNI tokenization. Its static library target succeeds. Building SentencePiece command-line tools fails because the upstream tool executables do not link Android `log`; those tools are not required by the JNI tokenizer.

## JNI linkage — APK BUILD VERIFIED

`vokie_ct2_jni.cpp` and `Ctranslate2Native.kt` implement the narrow handle-based JNI boundary. The APK now links and packages `lib/arm64-v8a/libvokie_ct2.so` (3,920,672 bytes before APK compression).

The initial JNI link failed after the CT2 PIC correction with unresolved `cpuinfo_initialize`, `cpuinfo_deinitialize`, `cpuinfo_get_processors_count`, and `cpuinfo_isa`. Inspection established the exact graph: `ruy_cpuinfo -> libcpuinfo.a -> libclog.a`. Both archives are Android AArch64 ELF relocatable archives under the CT2 Ruy build tree. `CMakeLists.txt` now links the Ruy archives followed by `third_party/ruy/third_party/cpuinfo/libcpuinfo.a` and its `deps/clog/libclog.a`; this preserves static-library dependency order. All linked CT2 objects were rebuilt with `CMAKE_POSITION_INDEPENDENT_CODE=ON`.

## Android physical-device validation

Validation used RMX3782 (Android 15, `arm64-v8a`) with a debug-only `Ct2SmokeActivity`; `MainActivity` remains the production launcher. The approved model was placed once in the app-private filesystem directory `files/models/ct2/nllb600m`, not read from compressed APK assets. Before every native load, the harness verifies `model.bin` length (619,704,329 bytes), SHA-256 (`ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`), and the three companion files.

The Android JNI path is proven: JNI library load succeeded, model load succeeded, and native code performs `SentencePiece -> [src_lang] + pieces + ["</s>"] -> CT2` with direct NLLB target prefixes and UTF-8 decode. The model is loaded once per harness run and reused for all translations.

### Offline physical results

Wi-Fi and mobile data were disabled before launch (`wifi_on=0`, `mobile_data=0`). No network fallback, download, cloud inference, or model reload is in this path.

| Direction | Input | Output | Latency |
|---|---|---|---:|
| EN → HI | Help me. | मेरी मदद करो. | 1796 ms |
| EN → TA | Help me. | எனக்கு உதவுங்கள். | 2029 ms |
| HI → EN | मुझे मदद चाहिए। | I need help. | 2019 ms |
| HI → TA | मुझे मदद चाहिए। | எனக்கு உதவி தேவை. | 1955 ms |
| TA → EN | எனக்கு உதவி தேவை. | I need help. | 2041 ms |
| TA → HI | எனக்கு உதவி தேவை. | मुझे मदद की जरूरत है. | 1708 ms |

HI → TA and TA → HI each made one direct `nativeTranslate` call with `hin_Deva → tam_Taml` and `tam_Taml → hin_Deva`; there is no English pivot. EN→EN, HI→HI, and TA→TA were explicitly bypassed in Kotlin, with no `nativeTranslate` call.

### Retained-model stability and memory

The same loaded CT2 handle completed 20 consecutive EN→HI warm translations without crash, ANR, or model reload: first 1420 ms, median 1228 ms, P95 1302 ms, final 1302 ms. PSS was 177,896 KB before load, 860,077 KB after load, peak 1,115,245 KB during the run, and 1,110,238 KB immediately after unload (the process remained alive, so this is not a post-process-exit measurement). The device-observed model load was 2027 ms in this run; an earlier initial physical load was 3629 ms. Android results are not host Model Lab measurements.

## Phase 2 result

- APK / packaged arm64 JNI: **PASS**.
- Physical JNI load, approved-model integrity and CT2 load: **PASS**.
- Offline Android six-direction translation, direct HI↔TA, same-language bypass, and 20-call retained-model stability: **PASS**.
- Phase 2 acceptance: **PASS for the debug physical CT2 validation scope**.

No TTS, Whisper, transport, or production UI integration was performed.
