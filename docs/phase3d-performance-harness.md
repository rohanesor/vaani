# Phase 3D Device-A Whisper replay benchmark harness

This harness is **Debug APK only**. It is for Device A (`MJPVXCSG9HYL65YL`) and does not alter Release inference defaults.

## Exact-PCM procedure

1. On Device A, record one PTT utterance and wait for its result. The Debug build retains an in-memory copy only of that final capture.
2. Set one configuration with `adb shell setprop`, then press **REPLAY LAST PCM (DEBUG BENCHMARK)** three times. Record the displayed audio duration, STT time, RTF, language source/detected language, and transcript after each replay.
3. Change exactly one property and repeat the same three replays. Do not make a new recording between compared configurations.
4. PTT-release-to-result is only measurable on the original capture; replay measures inference only.

The capture is not written to disk and is cleared when STT is released/app process ends.

## Debug properties

```text
# Always reset unspecified experimental controls before a baseline.
debug.vokie.whisper_threads=4
debug.vokie.whisper_no_timestamps=0
debug.vokie.whisper_audio_ctx=0
debug.vokie.whisper_dynamic_audio_ctx=0

# Fixed context experiment (maximum 1500):
debug.vokie.whisper_audio_ctx=<1..1500>

# Dynamic experiment (takes precedence over fixed):
debug.vokie.whisper_dynamic_audio_ctx=1
```

`debug.vokie.whisper_dynamic_audio_ctx=1` requests `ceil(samples / 320)` context frames (20 ms encoder-output-frame granularity), capped at the model maximum. It is an experiment, not a promoted mapping.

JNI logs `inferenceConfig` and `inferenceTiming`, including thread count, requested/effective context, timestamp mode, requested language, full inference time, encoder-pass count, Auto-LID interval, and transcription interval. Kotlin logs audio duration, total STT duration, RTF, resolved language, and source.

## Important vendored-engine finding

In the current vendored `whisper.cpp`, `whisper_full_with_state()` calls `whisper_lang_auto_detect_with_state()` **before** assigning `state->exp_n_audio_ctx = params.audio_ctx`. Therefore an Auto-LID run's first encoder pass uses the model context (1500); a requested smaller `audio_ctx` can affect only the subsequent transcription pass. This makes same-PCM AUTO versus explicit benchmarking mandatory and means dynamic context must not be promoted from timing alone.

Auto-LID also calls `whisper_encode_with_state()`, while normal transcription later calls `whisper_encode_internal()`. The public API does not provide a supported way to retain/reuse that encoded state. No whisper.cpp fork was made.

## No measurements claimed

The harness was build-tested, but no new physical speech PCM was supplied during this change. Dynamic-context, same-PCM AUTO-versus-explicit, and three-repeat timestamp results are **not measured**. Do not use the historical differently-recorded runs to make an optimization claim.
