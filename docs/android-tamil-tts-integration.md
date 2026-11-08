# Android Tamil MMS-TTS integration

## Approved artifacts and runtime

- Model: MMS-TTS Tamil VITS from `willwade/mms-tts-multilingual-models-onnx`, revision `709a74aad80a840eb57f767a7f5d155aaad1ac7b`.
- `model.onnx`: 114,032,312 bytes; SHA-256 `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88`.
- `tokens.txt`: SHA-256 `0b3f692319bb5fae8658e2e84bf252bca92450d0207bbba7273caa1a182d81b8`.
- Runtime: official `com.k2fsa:sherpa-onnx:1.13.7@aar`, fetched from the upstream `v1.13.7` release URL.
- Device: RMX3782, Android 15, `arm64-v8a`; CPU provider, VITS character frontend, output 16 kHz mono float PCM.

Tamil assets are APK assets and `BundledModelStore` verifies their existing asset-manifest size and SHA-256 before atomically extracting them to the app-private `files/models/tts/tam` directory. No model manifest was changed and no model download path is used.

## Phase 3A — PLAYBACK REPAIR PASS

The original physical synthesis test reached `VokieAudioPlayer.playOnce` but the `MODE_STATIC` PCM-float `AudioTrack` had `STATE_UNINITIALIZED`; no write or play call occurred. The narrow repair preserves `AudioBuffer(FloatArray, sampleRate)` and `VokieAudioPlayer` APIs:

- uses `AudioTrack.MODE_STREAM` for normalized `ENCODING_PCM_FLOAT` output;
- uses `CHANNEL_OUT_MONO` and the generated sample rate;
- obtains the exact-format platform minimum via `AudioTrack.getMinBufferSize`;
- chooses `max(minBufferBytes, sampleCount * 4)`;
- logs construction/state and write count; and
- retains existing focus, completion marker, stop, and release lifecycle.

Physical one-utterance result for `எனக்கு உதவி தேவை.`:

```text
Model load:       2310 ms
Synthesis:        2276 ms
Audio duration:   1806 ms
RTF:              1.2602
Samples:          28,900 float samples
Peak amplitude:   0.5362392 (no digital clipping)
AudioTrack:       16,000 Hz, mono, PCM_FLOAT, MODE_STREAM
Minimum buffer:   2,760 bytes
Selected buffer:  115,600 bytes
AudioTrack state: STATE_INITIALIZED (1)
Write result:     28,900 / 28,900 samples
Playback:         completed without TtsException or native crash
```

This proves the Android playback construction, complete float write, and completion path.

## Phase 3 — PASS (physical validation)

A physical speaker listening review of `எனக்கு உதவி தேவை.` was confirmed on RMX3782: audible and intelligible Tamil, natural completion, with no obvious truncation, major distortion, unexpected silence, or obvious clicks/pops.

With Wi-Fi and mobile data disabled before launch (`wifi_on=0`, `mobile_data=0`), one loaded Tamil model completed 10/10 followed by 20/20 synthesis-and-playback cycles. Every returned buffer was checked as non-empty 16 kHz float PCM with finite samples in `[-1, 1]`; every `AudioTrack` write and playback completed. No model reload, TtsException, native crash, or ANR was observed.

| Run set | First | Median | P95 | Min–max | Final | Playback |
|---|---:|---:|---:|---:|---:|---:|
| 10 cycles | 2148 ms | 2204 ms | 2851 ms | 2088–2851 ms | 2217 ms | 10/10 |
| 20 cycles | 2115 ms | 2184 ms | 2282 ms | 2083–2339 ms | 2247 ms | 20/20 |

The 20-cycle audio duration was approximately 1.75–1.97 s per utterance and RTF values were approximately 1.12–1.34. Peak amplitude remained below 1.0 in every validated float buffer.

Memory methodology is Android `Debug.getMemoryInfo().totalPss` (PSS). During the final offline run: 143,823 KB before model load, 314,041 KB after model load, 355,798 KB peak observed across the run, and 352,385 KB immediately after `engine.release()`. The process remained alive, so the final value does not demonstrate immediate return to pre-load baseline.

Known limitation: the listening review is a single physical-device confirmation, not a formal linguistic MOS evaluation. No Whisper, CT2/NLLB, transport, or production conversation UI integration is included in this phase.
