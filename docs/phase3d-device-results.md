# Phase 3D Device A speech-input results

## Baseline before this pass

Device A: realme RMX3782 / Android 15 / API 35 / arm64-v8a / `MJPVXCSG9HYL65YL`.

The current APK launched and loaded the local Whisper model. Baseline UI showed `Push-to-talk`, `Auto Detect`, and the stale text `silence finalizes after 1.2 seconds`. An automated long-press input attempt entered `LISTENING` / `WAITING FOR SPEECH`; it did not produce a speech sample or transcript. No human speech was injected, so baseline recording-stop latency, transcription latency, and language accuracy are **NOT MEASURED**. The run cannot establish whether a human release waited for VAD.

Relevant baseline log evidence included:

```text
VOKIE][STT: Model loaded in 1430ms: whisper-tiny-multilingual-q5_1
```

## Changes validated in code

- PTT release now stops capture and snapshots independently of `vad.hasSpeech`.
- Captures under 300 ms are rejected.
- Tap-to-Talk VAD calibrates approximately 200 ms of ambient RMS and uses a fixed configurable threshold afterward, with a default approximately 750 ms silence finalization.
- Whisper JNI accepts `auto`, calls the vendored `whisper_full` path, and exposes `whisper_full_lang_id`/`whisper_lang_str` through `nativeDetectedLanguage`.
- Kotlin retains requested and detected language separately; confidence remains null.
- Auto Detect is the default stored STT mode.
- UI displays the detected language from `SttResult.detectedLanguage`.

## Device validation after changes

**Pending manual speech interaction on Device A.** Required samples: English, Hindi, Tamil, Telugu, Gujarati, Marathi, Kannada, Malayalam, and Odia/Bengali. The agent cannot claim a transcript or language result without actual captured speech.

```text
AUTO_LID_CODE_PATH = BUILD-VALIDATED
AUTO_LID_PHYSICAL_ACCURACY = NOT MEASURED
PTT_RELEASE_LATENCY = NOT MEASURED
STT_LATENCY = NOT MEASURED
PTT_TO_MESSAGE_LATENCY = NOT MEASURED
```

Device B remains unsuitable for the native app runtime because it is x86_64 while the APK contains arm64-v8a native libraries.

No transport, TTS, dataset, model, AWS, or release work was performed.
