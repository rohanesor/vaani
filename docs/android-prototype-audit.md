# Android prototype integration audit

**Audit date:** current workspace state. This document is an assessment only; no production implementation was changed during Phase 1.

## Repository and build

- Android module: `:app`
- Application ID / namespace: `com.vokie`
- Branch: `feat/itrantra-production-transceiver`
- AGP: 8.5.2; Gradle wrapper: 8.9
- Kotlin: 1.9.24; Java/Kotlin target: 17
- `compileSdk` / `targetSdk` / `minSdk`: 34 / 34 / 24
- NDK: 27.0.12077973; CMake: 3.22.1
- ABI: `arm64-v8a` only
- Native source: `app/src/main/cpp`; existing `vokie_whisper` JNI library builds vendored `whisper.cpp` statically.
- Sherpa dependency currently resolves `com.k2fsa:sherpa-onnx:1.13.6@aar`, not the approved 1.13.7.

## Existing architecture

### Input/STT

`MicrophoneAudioRecorder` captures microphone audio under `WhisperSttEngine`. `WhisperSttEngine` owns a single lazy whisper.cpp context, calls the minimal `WhisperNative` JNI boundary, and runs initialization/inference through coroutines and mutexes. Source language is explicitly selected through `UserLanguageProfile` / `SttLanguagePreferences`; it is not automatic language ID.

### Translation

`TranslationEngine` is the translation abstraction. `ReceiverTranslationCoordinator` already implements receiver-local output language selection and a correct same-language bypass. The production application currently constructs `EmergencyPhraseDemoTranslationEngine`, explicitly a demo mapping; `UnavailableTranslationEngine` is the honest no-model fallback. There is no CTranslate2 source, native library, tokenizer, SentencePiece model, NLLB asset, or JNI bridge.

### TTS/audio output

`SherpaOnnxTtsEngine` is a usable lazy, mutex-protected sherpa-onnx VITS implementation that returns 16 kHz (model-defined) float PCM and plays it through `VokieAudioPlayer`. It supports EN/HI/TA routing via `TtsLanguage`, but `VokieApplication` currently deliberately instantiates `UnavailableTtsEngine`, so no production synthesis/playback is enabled. `BundledModelStore` and `TtsModelManager` verify/extract only STT plus English TTS; Tamil/Hindi assets are not currently bundled/extracted.

### Model loading

`BundledModelStore` extracts stored APK assets atomically to `filesDir/models`, validates length and SHA-256 from `app/src/main/assets/models/manifest.json`, then native engines load filesystem paths. This is the appropriate prototype strategy for assets that fit the installed APK/storage budget: packaged once, extracted once into app-private storage, integrity checked, and reused. It avoids asset-file-descriptor assumptions and duplicate permanent model stores. It must be extended to all approved files and fail explicitly for insufficient storage/integrity/load errors.

### UI/navigation

Compose navigation is in `MainActivity`: Chat, Locate, More, onboarding, and an emergency sheet. Chat already owns PTT/STT status and receiver translation presentation. The minimum source/target selectors, transcription/translation display, playback control, pipeline state, and model-status indicator can be added principally to Chat/More and `CommunicationViewModel`; a new whole-app navigation design is unnecessary.

## Required approved artifacts: availability audit

The requested Phase 1 inputs are **not present at the stated paths** in this checkout:

```text
docs/model-integration-plan.md
docs/model-lab-final-report.md
docs/model-lab-2-language-final.md
models/MANIFEST.json
model-lab/bench/out/*
```

`models/` currently contains an existing tiny Whisper binary and several legacy MMS VITS directories. The APK asset manifest contains STT plus legacy English/Hindi/Tamil filenames/hashes, but no approved NLLB CTranslate2 INT8 directory, tokenizer/SentencePiece asset, NLLB revision, or the stated Model Lab manifest. The on-disk model identity/license/revision/hashes therefore cannot be verified from this repository. The existing Tamil model may not be the approved willwade ONNX artifact; it must not be relabelled as approved without the supplied manifest/provenance.

This is an implementation blocker, not a reason to substitute another model or invent hashes.

## Exact implementation surface after artifacts are supplied

1. Add reproducible third-party CTranslate2 Android arm64 source acquisition/build script and pinned revision/patch manifest, outside generated build directories.
2. Add a separate native `vokie_ct2` JNI library and narrow Kotlin wrapper, e.g. handle-based `load(modelPath, tokenizerPath)`, `translate(handle, sourceTag, text)`, `free(handle)`. It must construct NLLB input as `[src_lang] + SentencePiece subwords + ["</s>"]`; EOS must be explicit. Native code owns no UI/business policy.
3. Add `Ctranslate2TranslationEngine` implementing `TranslationEngine`, a serialized/lazy model owner, typed load/translation errors, direct EN/HI/TA pair routing, and metrics. Preserve the existing coordinator’s same-language bypass.
4. Extend `BundledModelStore` and the asset manifest with exactly the approved model files and supplied SHA-256 values. Add translation path resolution/verification without duplicating the files. Stage prototype assets from a protected archive rather than committing ~906 MB binaries.
5. Upgrade/re-pin sherpa-onnx to the approved 1.13.7 only after verifying the AAR coordinate/artifact checksum; extend the existing TTS asset extraction to approved English, Hindi, and willwade Tamil model/token assets. Switch `VokieApplication` from `UnavailableTtsEngine` to `SherpaOnnxTtsEngine` only when all approved assets validate.
6. Add a pipeline coordinator/ViewModel state (`Ready`, `Transcribing`, `Translating`, `Synthesizing`, `Playing`, `Error`) that runs STT/CT2/TTS off the UI thread and records real device metrics. Integrate minimally with the current chat voice/result flow.
7. Add JVM routing/bypass/missing-model tests and Android device checklist/instrumentation for native load, repeated inference, offline mode, audio, memory and latency.

Likely files to change/create: `app/build.gradle.kts`, `app/src/main/cpp/CMakeLists.txt`, new `app/src/main/cpp/ct2_*`, `VokieApplication.kt`, `BundledModelStore.kt`, new translation engine/model manager files, `TtsModelManager.kt`, `TtsModels.kt`, `CommunicationViewModel.kt`, Chat/More Compose components, manifests/staging scripts, and focused tests/docs. PacketV2 and Bluetooth/Wi-Fi/location code are not in scope.

## Proposed JNI and packaging boundary

CTranslate2 needs its own C++ runtime/model instance. Kotlin provides verified absolute app-private paths and language/text; JNI performs only UTF-8 conversion, explicit SentencePiece + EOS input construction, CT2 execution, output detokenization, exception mapping, and handle release. A Kotlin mutex/single dispatcher serializes the one large model instance and prevents duplicate ~630 MB loads. No JNI calls may execute on the main thread.

For a ~906 MB model set, use stored APK assets for a controlled prototype only if the final APK/install path/device storage supports it; extract atomically to `filesDir` and validate every manifest entry before ready. Otherwise use an explicitly operator-installed, app-specific external model pack with the same manifest verification. Do not download models at runtime. The choice cannot be finalized until the actual file inventory and distribution constraint are supplied.

## Risks/build requirements

- CTranslate2 Android arm64 is the primary unresolved engineering task and must be cross-compiled with its exact dependencies (including SentencePiece/tokenizer path) reproducibly.
- Peak model-lab RAM (~1.05 GB) creates serious low-RAM/OOM risk; no Android performance claim is valid until measured on an actual device.
- A ~906 MB asset footprint may exceed practical debug install/storage limits and needs a real staging/installation test.
- Current app build configuration contains a CDN BuildConfig value and model-download infrastructure. The inference path must remain asset/local-pack only; it must not invoke those facilities.
- Existing user work is extensively uncommitted. No reset/cleanup is appropriate.

## Gate to begin Phase 2

Provide the missing approved Model Lab documents, `models/MANIFEST.json`, and the actual approved CT2 model/tokenizer plus Tamil/English/Hindi TTS artifact inventory (including hashes and licensing provenance), or restore them into this workspace. Without those artifacts, a CT2 Android integration cannot be built or integrity-tested honestly.
