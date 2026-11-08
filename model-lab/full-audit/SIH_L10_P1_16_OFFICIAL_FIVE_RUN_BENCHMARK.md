# SIH-L10-P1.16 — Official Five-Run Physical Voice-Loop Benchmark

## Decision

**P1.16 OFFICIAL FIVE-RUN BENCHMARK: FAIL**

Five physical EN→HI loops were run through the intended pipeline, but only **4/5** satisfy the stated validity gate. Run 5 transcribed the controlled phrase “Help me.” as **“helled me”**. The run was retained, not silently replaced, and the benchmark stopped after the five requested attempts.

This is a quality-gate failure, not a duplicate-send, transport, translation, TTS-queue, process, timing-order, or crash failure. Do not use these measurements to claim an official five-valid-run improvement.

## Configuration and devices

| Item | Value |
|---|---|
| Pre-benchmark commit | `7ef8509f4f7d8e2f8a912c317a17a4dfafc07f5d` |
| Validated APK SHA-256 | `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9` |
| Benchmark phrase | `Help me.` |
| Direction | EN→HI |
| Sender | vivo V2205, Android 14, arm64-v8a, PID 30131 |
| Receiver | Realme RMX3782, Android 15, arm64-v8a, PID 9578 |
| STT | SenseVoice-Small int8 |
| Translation | local CT2 NLLB; existing short-input beam=1 behavior retained |
| TTS | local sherpa-onnx 1.13.7 Hindi MMS/VITS; two threads |

Both installed APK hashes matched the host hash before logs were cleared. No production code, routing, model selection, PacketV2 semantics, CT2 configuration, or frontend behavior was changed for this benchmark.

## Per-run timing and validity

All intervals are calculated only inside their originating device’s `elapsedRealtimeNanos()` clock domain. No cross-device monotonic timestamps were subtracted.

| Run | Message | Transcript | Valid | STT T1→T2 | T2→T3 | T3→T4 | TX→ACK | RX→T6 start | Translation | TTS synthesis | Playback | T5→T8 |
|---:|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | `64834df9` | help me | yes | 350.1 | 26.6 | 7.6 | 127.6 | 65.3 | 3303.4 | 2360.4 | 1051.0 | 6781.5 |
| 2 | `22ef14f7` | help me | yes | 273.2 | 35.0 | 3.3 | 362.0 | 48.9 | 753.3 | 181.5 | 964.6 | 1949.9 |
| 3 | `276a29f4` | help me | yes | 269.4 | 15.2 | 5.6 | 79.5 | 52.4 | 717.9 | 193.0 | 1067.8 | 2032.0 |
| 4 | `d7fda153` | help me | yes | 278.7 | 17.1 | 2.8 | 89.4 | 67.1 | 733.8 | 189.8 | 992.6 | 1986.7 |
| 5 | `6a176a0e` | **helled me** | **no** | 314.1 | 12.2 | 4.5 | 350.5 | 45.9 | 741.8 | 197.9 | 962.0 | 1952.2 |

Milliseconds. Run 1 includes first-use cold costs: Hindi CT2/TTS model initialization is reflected in its translation and TTS intervals.

### Required-event ordering

For all five observed runs:

- Sender: `T0 < T1 < T2 < T3 < T4 < ACK`
- Receiver: `T5 < T6 start < T6 complete < T7 < T8`

The queue completed each receiver turn before the next tracked turn: `queue_depth=0`, `active_jobs=0`.

## Observed five-attempt statistics

These are provided for diagnostic transparency only; they are **not official five-valid-run results**.

| Metric | Median | P95 | Minimum | Maximum |
|---|---:|---:|---:|---:|
| SenseVoice STT T1→T2 | 278.7 ms | 350.1 ms | 269.4 ms | 350.1 ms |
| Transport T4→ACK | 127.6 ms | 362.0 ms | 79.5 ms | 362.0 ms |
| Translation | 741.8 ms | 3303.4 ms | 717.9 ms | 3303.4 ms |
| Hindi TTS synthesis | 193.0 ms | 2360.4 ms | 181.5 ms | 2360.4 ms |
| Playback | 992.6 ms | 1067.8 ms | 962.0 ms | 1067.8 ms |
| Receiver T5→T8 | 1986.7 ms | 6781.5 ms | 1949.9 ms | 6781.5 ms |

SenseVoice audio durations were 1040, 840, 760, 1060, and 1000 ms. Its observed RTFs were approximately 0.336, 0.324, 0.353, 0.262, and 0.313.

Hindi TTS RTFs were 0.384 (cold), then 0.197, 0.190, 0.197, and 0.215. Playback duration is reported independently and was not counted as synthesis latency.

## Pipeline invariants

| Invariant | Observed |
|---|---:|
| Turn submissions | 5 |
| PacketV2 creations/TX | 5 |
| Receiver RX/persisted messages | 5 |
| TTS enqueues/starts/completions | 5 / 5 / 5 |
| Queue drains | 5 |
| Duplicate jobs | 0 |
| Process restarts | 0 |
| MainActivity count | 1 per device |
| Fatal errors / ANRs | 0 |
| Valid transcripts | 4 / 5 |

## Memory and thermal

| Device | Fresh PSS / native KB | Final PSS / native KB | Thermal |
|---|---:|---:|---:|
| vivo sender | 643,707 / 356,992 | 649,963 / 363,764 | 0 |
| Realme receiver | 712,029 / 338,660 | 1,459,681 / 1,081,216 | 1 |

The receiver’s Hindi MMS model loaded on run 1 and remained resident. After that load, its native allocation and PSS stabilized across runs 2–5 rather than increasing monotonically. The large fresh-to-final difference is model residency, not queue accumulation: every queue drained and active jobs returned to zero. Thermal state was not throttling-critical in the captured output, but Realme status 1 must remain documented.

## Comparison context

- P1.9 Whisper STT median: 3321 ms; P1.16 observed-attempt STT median: 278.7 ms. This indicates a strong observed speed difference, but cannot be claimed as an official quality-passing five-run comparison because run 5 failed transcript correctness.
- P1.9 translation median: 1445 ms; P1.10 approximately 988 ms. P1.16 warm observed translation was 718–753 ms, while run 1 cold was 3303 ms.
- P1.15A English-TTS characterization is not comparable to this Hindi target output. P1.16 warm Hindi synthesis was 181–198 ms; run 1 includes model load/first-use cost.

## Limitations and next action

1. The controlled phrase was not transcribed correctly on run 5, invalidating the required 5/5 quality gate.
2. Speech capture duration varied between attempts despite the same requested phrase.
3. Run 1 is a valid cold measurement and must not be discarded, but it contains Hindi model first-use cost.
4. The official benchmark must be rerun from a clean, verified state until five valid transcripts are collected. The invalid run must remain in this record; it must not be relabeled or substituted.
5. Telugu/Kannada adaptation remains out of scope.

## Build verification

No source changes were made during the benchmark. The validated pre-benchmark build was already P1.15A-tested: `testDebugUnitTest` 331 passed / 0 failed and `assembleDebug` passed, with APK SHA stated above.
