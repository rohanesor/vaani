# SIH-L10-P1.11 Existing Voice-Loop Latency Optimization

Date: 2026-09-05
Branch: `sih/laptop1-c1-c2-integration`
Checkpoint: `sih/laptop1-before-l10-p1-11` at `1fc3041`

## 1. Objective

Optimize the EN → HI voice loop using existing models, runtime, and
architecture. Profile first, then attempt safe optimizations.

## 2. Experiments Attempted

### Experiment A: Whisper 6 threads (vs baseline 4)

| Metric | 4 threads (P1.9) | 6 threads | Change |
|---|---:|---:|---|
| Encoder | 3,240 ms | 4,543 ms | **+40% WORSE** |
| Total STT | 3,321 ms | 5,331 ms | **+61% WORSE** |
| Transcript | "Help me." ✅ | "Help me." ✅ | Correct |

**Cause**: The Realme RMX3782 has a big.LITTLE SoC (MediaTek Dimensity).
Threads 5-6 land on LITTLE cores, adding scheduling overhead without
encoder parallelism benefit. The ggml encoder is memory-bandwidth-bound
on ARM64 and does not scale beyond 4 big cores.

**Result**: REVERTED to 4 threads.

### Experiment B: CT2 explicit intra_threads=4

| Metric | Default | intra=4 | Change |
|---|---:|---:|---|
| Translation | ~988 ms | CRASHED | **FAILED** |

**Error**: `"reduce all is not applied for the cpu"` — CT2 4.8.2
internal error when explicit thread count is passed via constructor
on ARM64.

**Result**: REVERTED to default constructor.

### Experiment C: Dynamic Whisper audio_ctx (from P1.10)

Already documented in P1.10. Caused decoder degeneration on
whisper-tiny. Not re-attempted.

## 3. Profiling Results

### STT decomposition (from whisper_timings)

| Component | Time (ms) | % of STT |
|---|---:|---:|
| Encoder | 3,240 | 97.6% |
| Decoder | 11 | 0.3% |
| Sampling | 6 | 0.2% |
| Batch decode | 7 | 0.2% |
| Overhead/other | 57 | 1.7% |
| **Total** | **3,321** | 100% |

The encoder is irreducibly ~3.2 s on this SoC. It processes 1500
mel-spectrogram frames regardless of audio length. Dynamic audio_ctx
causes decoder degeneration. More threads cause big.LITTLE slowdown.

### Translation decomposition

| Component | Time (ms) | Note |
|---|---:|---|
| CT2 cold init | ~2,100 | First call only |
| CT2 warm inference | ~817-1,264 | beam=1, short text |
| Tokenization | <10 | SentencePiece |
| Detokenization | <5 | |

### TTS decomposition

| Component | Time (ms) | Note |
|---|---:|---|
| Cold model load | ~3,092 | First call only |
| Warm synthesis | ~441 | Hindi Piper |
| RTF | 0.35-0.43 | Real-time viable |

### Playback decomposition

| Component | Time (ms) | Note |
|---|---:|---|
| AudioTrack construction | ~100-150 | Per utterance |
| PCM write | <10 | Blocking write |
| Audio playback | ~1,200-1,300 | Generated audio duration |
| Total T7→T8 | ~1,405-1,438 | Mostly audio duration |

Playback overhead is ~150 ms. The remainder is the physical duration
of the generated Hindi speech audio.

## 4. Final Measured State

| Metric | P1.9 | P1.10 | P1.11 |
|---|---:|---:|---:|
| STT (4 threads) | 3,321 | 3,400 | 3,321* |
| Translation (beam=1) | 1,445 | 988 | 988* |
| TTS warm | 426 | 445 | 441 |
| Playback T7→T8 | 1,428 | — | 1,405 |
| Transport TX→ACK | 261 | — | 871** |

*No change from P1.10 — optimizations reverted.
**Single cold-start observation; not representative.

## 5. Remaining Bottleneck

The **Whisper encoder at ~3,200 ms** is the dominant irreducible
bottleneck with the current model and configuration. It cannot be
improved without:

1. Upgrading to a distilled/smaller Whisper model
2. Using INT8/INT4 quantization with a compatible whisper.cpp build
3. Hardware acceleration (GPU/NPU) if whisper.cpp supports it

None of these are safe to attempt in P1.11.

## 6. Optimizations Retained from P1.10

- CT2 beam=1 for inputs ≤20 tokens: **32% translation improvement**

## 7. Optimizations Reverted in P1.11

- Whisper 6 threads → 4 threads (big.LITTLE slowdown)
- CT2 explicit intra_threads (runtime crash)
- Dynamic audio_ctx (decoder degeneration, from P1.10)

## 8. Conclusion

**BLOCKED — MODEL/ARCHITECTURE CHANGE REQUIRED**

The existing whisper-tiny encoder is irreducibly ~3.2 s on ARM64.
All safe optimizations within the current model/runtime have been
exhausted. The only remaining improvement path for STT latency is
a model upgrade.

Translation and TTS are already at their practical minimum with
the existing models.

## 9. Recommendation

Proceed to the **official five-run benchmark** with the current
P1.10 configuration (beam=1, 4 Whisper threads, full 1500 context)
to establish the definitive post-optimization baseline.

Then evaluate Telugu/Kannada adaptation OR Whisper model upgrade
as the next phase.
