# Android Hindi TTS integration

## Approved artifact

- Identity: `hi_IN-priyamvada-medium` (Piper VITS)
- Runtime: official sherpa-onnx `1.13.7`, CPU
- Provenance: `rhasspy/piper-voices`, distributed by the pinned official sherpa-onnx `tts-models` release
- Bundle: `vits-piper-hi_IN-priyamvada-medium.tar.bz2`
- Bundle SHA-256: `399d91cc97eb288725633261f26b715f9a971e3bf7ec4fa1d7910cd0080d37eb`
- ONNX SHA-256: `8871f3e07adb6ca490f8dbcd3956a8647c53c35b5d0a1c2a8d097b3bf721a31b`
- ONNX size: 63,145,178 bytes
- `tokens.txt` SHA-256: `ef3a7e4a8d1af0c9d4dc45aaae1a6242ebe24a7ed6f3d025a49eb29682784c6d`
- License: Piper hub MIT; voice dataset CC-BY-NC-SA-4.0; non-commercial prototype only
- Shared Piper `espeak-ng-data` is required and is staged once under the common TTS model root.

The archive checksum was verified before extraction. The approved ONNX and token checksums were verified before APK packaging and again by the device-side model manager.

## Android physical validation

Device: Phone A, V2205, ADB `10BCAC2HM5000CR`. The debug APK was installed explicitly with `adb -s`. The normal production launcher remains `MainActivity`. Model staging uses the existing `BundledModelStore` into app-private storage:

```text
/data/user/0/com.vokie/files/models/tts/hin
/data/user/0/com.vokie/files/models/tts/espeak-ng-data
```

The existing `SherpaOnnxTtsEngine` was reused; no competing engine or player was added. Piper Hindi correctly outputs 22,050 Hz audio, while MMS Tamil remains 16,000 Hz. The existing `VokieAudioPlayer` accepts the model-defined sample rate and retains PCM_FLOAT, mono, `AudioTrack.MODE_STREAM`.

### Measured run

Test text: `मुझे मदद चाहिए।`

```text
Model staging/hash: PASS
Model load:         2309 ms
First synthesis:    524 ms
Audio sample rate:  22,050 Hz
Audio samples:      30,720
Audio duration:     1,393 ms
RTF:                0.3762
Peak amplitude:     0.76087105
```

Audio validation passed: non-empty, finite float samples, all within `[-1, 1]`. The first playback completed successfully. Repeated cycle logs showed `AudioTrack` initialized and the complete buffer written for every cycle.

### Stability

One model instance was loaded and reused for the repeated tests; no intentional model reload occurred between cycles.

```text
10 cycles: first=543 ms, median=532 ms, P95=543 ms,
           min=511 ms, max=543 ms, final=523 ms, playback=10/10

20 cycles: first=512 ms, median=528 ms, P95=568 ms,
           min=505 ms, max=600 ms, final=512 ms, playback=20/20
```

No TTS exception, AudioTrack initialization/write failure, native crash, or ANR was observed. PSS methodology was `Debug.getMemoryInfo().totalPss`:

```text
Before load: 260,663 KB
After load:  304,296 KB
Peak observed during run: 379,317 KB
After release: 256,788 KB
```

The post-release value returned close to the pre-load value in this run, although process-level PSS is not a proof that every allocation was reclaimed.

### Offline result

Mobile data was disabled (`mobile_data=0`) while Wi-Fi remained available. Hindi model load, synthesis, and playback completed using only local APK-staged assets, sherpa-onnx, and Android AudioTrack. No network or model download path was used.

## Final status

**Hindi TTS physical/integration status: NOT PASS — listening review pending.**

Objective gates passed: approved artifact, staging, checksum, model load, synthesis, 16/22.05-kHz model-defined audio validation, playback completion, 10/20 stability, offline execution, and memory measurement. A human must listen to the Hindi utterance on the physical Phone A speaker before the subjective intelligibility/noise/distortion gate can honestly be marked PASS. No Hindi E2E or microphone E2E was started.
