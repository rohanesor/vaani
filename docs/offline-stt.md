# Offline multilingual STT

iTantra uses **whisper.cpp v1.7.6** through one JNI library (`vokie_whisper`). There is no speech service, network client, runtime model download, or Android/Google speech recognizer in this path.

## Runtime path

`Compose push-to-talk → AudioRecord (16 kHz mono PCM16) → EnergyVadEngine → WhisperSttEngine → whisper.cpp JNI → text preview → MessageRepository → Room queue → existing BluetoothTransport`

Audio is bounded to 30 seconds and exists only in memory. Whisper runs once after release, 1.2 seconds of post-speech silence, or the 30-second ceiling. The engine owns one native context and releases it explicitly.

## Selected model

| Property | Value |
|---|---|
| Model | OpenAI Whisper tiny multilingual, converted for whisper.cpp |
| Expected filename | `ggml-tiny-q5_1.bin` |
| Format | whisper.cpp GGML |
| Quantization | Q5_1 |
| Exact reference artifact size | 32,152,673 bytes (about 30.7 MiB) |
| SHA-256 | `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7` |
| Approximate runtime model memory | 273 MiB, plus audio/inference working memory |
| CPU | CPU-only; up to four inference threads; ARM64 or ARMv7 APK ABI |
| Source/version | `ggerganov/whisper.cpp`, v1.7.6 |

The tiny multilingual model is a hackathon-oriented size/latency choice. Accuracy, especially for low-resource languages and noisy emergency audio, must be measured on target phones. The `SttModel` interface keeps model metadata and storage independent from capture, VAD, UI, and transport, so a better compatible model can replace it later.

Configured language codes are `en`, `hi`, `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, and `bn` for English, Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, and Bengali.

## Bundled installation

The production APK contains the verified `ggml-tiny-q5_1.bin` asset. It is supplied only to the protected release build environment and is never committed to Git. On first launch `BundledModelStore` atomically extracts the APK asset to `files/models/stt/`, verifies its SHA-256 and size, and then whisper.cpp opens that local filesystem path. There is no model picker, downloader, cloud service, or user installation step. Interrupted extraction is discarded or resumed by checksum validation on the next launch.

## VAD and limits

`EnergyVadEngine` is replaceable. Current internal defaults:

- RMS speech threshold: `0.015`
- minimum speech: `200 ms`
- utterance-finalizing silence: `1,200 ms`
- frame size: `100 ms`
- maximum utterance: `30 s`

These are initial engineering defaults, not claimed universal accuracy settings. Physical-device testing must tune them against phone microphones and ambient noise.

## Metrics and verification

Every real result records audio duration, inference processing time, timestamp, and real-time factor (`processing / audio`). Confidence remains unavailable because this integration does not expose a calibrated utterance confidence. Model load time is measured separately.

JVM CI tests cover language mapping, state transitions, VAD, duration/RTF, errors, and STT-result-to-message language flow. Actual Android JNI transcription requires a model and ARM Android device and is intentionally not replaced with fake production inference.

A development-host smoke test used the exact pinned model, upstream whisper.cpp v1.7.6, and upstream 11.000-second mono 16 kHz `jfk.wav`. Recognized text was: `And so, my fellow Americans, ask not what your country can do for you, ask what you can do for your country.` Wall time was 0.98 seconds (wall RTF 0.089) and reported maximum host RSS was 203,628 KiB. These are real measurements on the development host, **not Android performance claims**. Android processing time/RTF is measured by the app but remains pending physical-device execution.
