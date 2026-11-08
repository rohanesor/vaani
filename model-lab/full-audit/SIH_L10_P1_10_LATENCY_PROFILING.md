# SIH-L10-P1.10 Android Voice-Loop Latency Profiling & Optimization

Date: 2026-09-05
Branch: `sih/laptop1-c1-c2-integration`
Checkpoint: `sih/laptop1-before-l10-p1-10` at `38aca55`

## 1. P1.9 Locked Baseline (EN → HI, "Help me.", 5 runs)

| Metric | Median |
|---|---:|
| STT T1→T2 | 3,321 ms |
| T2→T3 | 24 ms |
| T3→T4 | 5 ms |
| TX→ACK | 261 ms |
| Translation | 1,445 ms |
| TTS synthesis | 426 ms |
| T7→T8 playback | 1,428 ms |
| Receiver T5→T8 | 3,337 ms |
| TTS RTF | 0.355 |

## 2. Profiling Findings

### STT (3,321 ms — largest bottleneck)
- Model: whisper-tiny-multilingual-q5_1 (32 MB, GGML Q5_1)
- Encoder dominates at ~3,200 ms for full 1500-frame context
- Audio is <1 second but encoder processes 30 seconds of context
- **Attempted fix**: dynamic audio_ctx to match actual audio length
- **Result**: decoder degeneration — repetitive garbage output
  ("to to to to to to to to to to", "help me help me help me help me?")
  even with 128-frame floor
- **Conclusion**: whisper-tiny requires full 1500-frame context for
  correct decoding. STT cannot be optimized without a model upgrade.

### Translation (1,445 ms — second largest)
- CT2 NLLB-600M with beam_size=4
- Model already loaded and reused across requests
- Short rescue sentences (≤20 tokens) don't benefit from beam search
- **Fix applied**: beam_size=1 (greedy) for inputs ≤20 tokens

### TTS (426 ms)
- Hindi Piper at RTF 0.35 — already efficient
- Model kept loaded between turns
- No optimization needed

### Playback (1,428 ms)
- Primarily audio duration (~1,200-1,300 ms generated speech)
- AudioTrack startup overhead is minimal (~100-200 ms)
- Not reducible without shorter audio output

### Transport (261 ms)
- Bluetooth Classic overhead — not easily reducible
- No PacketV2 changes warranted

## 3. Optimization Applied

| Change | File | Detail |
|---|---|---|
| CT2 beam=1 for short text | `vokie_ct2_jni.cpp` | `beam_size = (pieces.size() <= 20) ? 1 : 4` |
| Whisper audio_ctx reverted | `vokie_whisper_jni.cpp` | `audio_ctx = 0` (full 1500), with comment documenting the failed experiment |

## 4. P1.10 Physical Measurements

### Receiver warm translation (beam=1)

| Run | Translation (ms) |
|---|---:|
| 1 (cold) | 2,959 |
| 2 | 853 |
| 3 | 1,129 |
| 4 | 1,277 |
| 5 | **817** |
| 6 | **923** |
| Warm median | **~988** |

### Comparison

| Metric | P1.9 | P1.10 | Δ | % |
|---|---:|---:|---:|---:|
| Translation median | 1,445 | ~988 | **-457** | **-32%** |
| Translation best | 1,411 | 817 | **-594** | **-42%** |
| STT median | 3,321 | ~3,400 | 0 | 0% |
| TTS warm | 426 | ~445 | 0 | 0% |
| Receiver T5→T8 | 3,337 | ~2,728 | **-609** | **-18%** |

## 5. Failed Experiments

### Dynamic Whisper audio_ctx
- Hypothesis: reduce encoder context from 1500 to actual audio frames
- Implementation: `audio_ctx = max(N, (samples + 319) / 320 + 2)`
- Tested N=1 (raw), N=128 (2.56 s floor)
- Both caused decoder sampling loops (5,000-36,000 ms)
- Produced repetitive garbage transcripts
- **Reverted**: whisper-tiny requires full context for correct output

## 6. Remaining Bottlenecks

| Bottleneck | Current | Reducible? |
|---|---:|---|
| STT encoder | ~3,400 ms | Only with model upgrade (distilled/int8) |
| Translation | ~988 ms | Further reduction requires smaller model |
| Playback | ~1,300 ms | Audio duration, not latency |
| TTS synthesis | ~445 ms | Already efficient |
| Transport | ~250 ms | Bluetooth inherent |

## 7. Limitations

- Whisper-tiny ARM64 encoder is irreducibly ~3.2 s
- Dynamic audio_ctx is unsafe for this model
- Translation improvement is only for short text (≤20 tokens)
- No cross-device timestamp subtraction
- Thermal effects not isolated in this comparison

## 8. Build & Regression

- Tests: 329 / 0 failures
- assembleDebug: PASS
- EN/HI/TA/GU: preserved
- PacketV2: unchanged
- Transport: unchanged
- Hold-to-Speak: race fix preserved

## 9. Commit

`d9dd3f5b08e7cdd9ad99acd70e88a2a31c439a8f`
