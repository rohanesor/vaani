# Offline MMS-TTS with sherpa-onnx

iTantra uses the official **sherpa-onnx 1.13.6 Android AAR** and its offline VITS API. The AAR is resolved at build time from the pinned official GitHub release (`sherpa-onnx-1.13.6.aar`, 49,097,942 bytes, SHA-256 `0012d9a28f15bd6fb966b62b70a75da3990512fdccce28b83098248ce4be1698`). The installed app has no Internet permission and contains no model downloader, cloud TTS, Android system-TTS fallback, or server integration.

Runtime architecture:

`Room-persisted received text → TextToSpeechUseCase → priority TtsPlaybackQueue → SherpaOnnxTtsEngine → MMS/VITS ONNX → float PCM → VokieAudioPlayer → AudioTrack`

The Bluetooth ACK remains immediately after successful Room persistence. TTS enqueueing happens afterwards and cannot delay or redefine peer receipt.

## Production model inventory and packaging

The production APK contains verified MMS/VITS assets for `eng`, `hin`, `guj`, `mar`, `kan`, `mal`, `tam`, `tel`, `ory`, and `ben`. The protected release archive manifest is the source of truth for every model and token SHA-256. Model files are supplied to CI from private S3, never committed to Git, never exposed through the public download site, and never downloaded by the application.

At first launch `BundledModelStore` atomically extracts the APK assets to `files/models/tts/<iso6393>/` and re-verifies every file before it is eligible for loading. Failed or interrupted extraction is not marked ready and is retried locally on next launch; no network or user action is involved.

Only one sherpa native context is loaded at a time. Changing language releases the previous context before loading the selected language. The currently supplied models are FP32; dynamic INT8 candidates are intentionally not packaged because host validation found changed waveform lengths and slower inference. See [`production-benchmark.md`](production-benchmark.md). The model license requires legal/product review before commercial distribution.

## Playback and emergency behavior

Normal speech uses transient-may-duck audio focus and accessibility/speech audio attributes. SOS speech is placed at the front of the pending queue, can preempt normal playback, uses alarm usage with transient-exclusive focus, requests maximum application gain, and repeats twice. iTantra does **not** override system volume or claim non-interruptible playback. Explicit **Play message**, **Stop speech**, and **Acknowledged — stop alert** controls remain available while text is always readable.

PCM is played directly from memory with `AudioTrack` (`ENCODING_PCM_FLOAT`, mono). Audio is bounded to 120 seconds and no temporary WAV is created.

## Real host smoke measurement

The official sherpa-onnx 1.13.6 Linux binary and exact English package synthesized `Help me` successfully:

| Metric | Measured value |
|---|---:|
| Text length | 7 characters |
| sherpa synthesis time | 315 ms |
| Generated audio | 564 ms, 9,019 samples, mono 16 kHz |
| Synthesis RTF | 0.559 |
| Whole-process wall time including model startup | 2.57 s |
| Maximum host RSS | 171,072 KiB |

These are real development-host results, not Android performance claims. Android model load, first/subsequent synthesis, speaker playback, memory, and thermal behavior remain pending physical-device testing.
