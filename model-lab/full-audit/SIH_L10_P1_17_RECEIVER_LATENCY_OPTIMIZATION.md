# SIH-L10-P1.17 — Receiver-Side Latency Optimization Investigation

## Decision

**No safe optimization was promoted.** The current CT2/TTS configuration remains unchanged: CPU CT2 with existing short-input beam=1 behavior, one reused translation session, and the validated Hindi MMS/VITS TTS configuration.

P1.16 remains a **4-valid-run diagnostic**, not an official five-valid-run benchmark. Its fifth transcript was invalid and is preserved in the P1.16 evidence.

## Safety and scope

- Starting SHA: `7ef8509f4f7d8e2f8a912c317a17a4dfafc07f5d`
- Starting branch: `sih/laptop1-c1-c2-integration`
- Safety branch pushed: `sih/laptop1-before-l10-p1-17` at the same SHA.
- Existing untracked models, WAVs, local logs, and environment files were preserved.
- PacketV2, transport, language routing, SenseVoice, validated TTS artifacts, timing semantics, and frontend behavior were not changed.
- The only code added is diagnostic: a debug-only CT2 fixed-input profiler and native stage logging. It is not a production optimization.

## P1.16 diagnostic baseline

Four valid EN→HI turns produced warm medians:

| Stage | Warm median |
|---|---:|
| SenseVoice STT | 273.2 ms |
| Transport TX→ACK | 89.4 ms |
| CT2 translation | 733.8 ms |
| Hindi TTS synthesis | 189.8 ms |
| Playback | 992.6 ms |
| Receiver T5→T8 | 1986.7 ms |

## What is measured versus unavailable

The deployed CTranslate2 API supplies `Translator::translate_batch()`. It combines CT2 encoder and decoder execution into one opaque call. It does not provide separate encoder or decoder timing callbacks. Therefore:

| Receiver stage | Status |
|---|---|
| T5→translation start | Measured in P1.16: warm 45.9–67.1 ms |
| CT2 preprocessing / SentencePiece encode | Measured natively |
| CT2 encoder | **Unavailable separately** without changing/instrumenting CT2 runtime internals |
| CT2 decoder | **Unavailable separately** without changing/instrumenting CT2 runtime internals |
| CT2 combined inference | Measured natively |
| Translation postprocessing / SentencePiece decode | Measured natively |
| Translation→TTS enqueue | Existing receiver handoff; no queue backlog in P1.16 |
| TTS preprocessing/native inference | Existing P1.15A/P1.16 profiles |
| AudioTrack preparation/playback | Existing P1.15A profile and P1.16 timing |

This report deliberately does not invent encoder/decoder splits.

## CT2 controlled receiver-only profile

A debug-only direct JNI profiler ran on receiver Realme RMX3782 (Android 15). It used the staged production CT2 model, but a separate temporary profiling session; it did not send packets, alter routing, or inject voice-loop transcripts. One warm-up was performed before each five-sample fixed-text case. Native logs measured SentencePiece encode, `translate_batch`, and SentencePiece decode.

| Text | Chars / pieces / beam | Five warm native totals (ms) | Median | P95 | Encode/decode conclusion |
|---|---|---|---:|---:|---|
| `Help me.` | 8 / 5 / 1 | 1130.5, 1122.1, 1031.3, 1053.2, 1423.2 | 1122.1 | 1423.2 | encode 0.043–0.097 ms; decode 0.028–0.030 ms |
| Medium rescue request | 46 / 12 / 1 | 2058.2, 2014.3, 2014.1, 2003.8, 1983.3 | 2014.1 | 2058.2 | encode 0.138–3.738 ms; decode 0.080–1.657 ms |
| Long rescue request | 135 / 31 / 4 | 9600.4 (one sample) | — | — | stopped before repetitions |

The long text crossed the existing 20-piece policy boundary and correctly used beam=4. The initial long call brought the receiver thermal status to **3**. The profiler was immediately stopped; no remaining long repetitions, thread changes, affinity changes, or production changes were attempted.

## Findings

### CT2

1. **Beam policy is intact.** Short/medium test inputs used beam=1. Long input used the existing beam=4 policy. No change was made.
2. **One session/model load.** The controlled run emitted exactly one `CT2 model loaded`; tokenizer and translator belong to that session and were reused for all subsequent requests.
3. **Tokenizer is not the bottleneck.** Encode/decode together were below 6 ms even in the medium first measured sample. The native combined CT2 inference occupied effectively all 1.0–2.1 s totals.
4. **Length strongly correlates with native CT2 latency.** Short beam-1 median was 1122 ms; medium beam-1 median was 2014 ms; the one long beam-4 sample was 9600 ms. This is a controlled direct-JNI result, not a replacement for end-to-end voice-loop measurements.
5. **P1.16’s 3303 ms translation tail is compatible with cold-path/cache/thermal scheduling effects, but causality is not proven.** Run 1 included cold receiver initialization. No GC trace or CPU scheduler trace was captured, so this investigation makes no causal claim about GC or contention.
6. **No thread increase was tested.** The known runtime exposes device index `{0}` but no proven compatible `intra_threads` control in this integration. Blindly trying four threads would violate the evidence-first gate.

### Hindi TTS

P1.16 warm synthesis was 181.5–197.9 ms (median 189.8 ms). Its 2360.4 ms first-turn tail aligns with the logged 2007 ms Hindi model load plus first native call. The queue had zero depth/active jobs after every run, and P1.15A had already shown negligible Kotlin preprocessing, buffer wrapping, and AudioTrack write cost. This is **not** evidence of a warm TTS architecture bottleneck.

### Playback

P1.16 playback was 962–1068 ms because generated Hindi PCM was approximately that duration. Earlier physical profiling measured AudioTrack construction at approximately 14–22 ms and write below 0.1 ms. Playback duration is speech duration, not compute latency; truncating it is not an acceptable optimization.

## Memory and thermal

The controlled profiler reached PSS 1,357,161 KB and native heap 837,636 KB while the temporary CT2 session was resident. Thermal status reached 3 on the first long sample. The receiver was force-stopped, and the exact validated P1.15A APK was restored from vivo; its SHA was reverified as:

`072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9`

This is evidence against pursuing thread/affinity experiments while hot. It is not evidence of per-message memory accumulation; the profiler’s model session was deliberately resident during the capture.

## Experiment record

| Experiment | Result | Production action |
|---|---|---|
| Verify beam=1 for short input | PASS | none |
| Verify CT2 model/tokenizer/session reuse | PASS: one load/session | none |
| Profile fixed input lengths | completed short/medium; long stopped on thermal 3 | none |
| Alter CT2 thread count | not run | none |
| CPU affinity experiment | not run | none |
| GC/scheduler causal experiment | inconclusive; no trace | none |
| Replace Hindi TTS | not considered; warm synthesis already excellent | none |

## Recommended next step

Keep the current CT2/TTS architecture. Cool the receiver before any further physical measurement. If a further CT2 study is needed, collect thermal, scheduler, and GC traces on a cooled device with a strict thermal abort threshold; do not alter threads or model routing until such data supports a compatible change.

Do not begin Telugu/Kannada adaptation. Before claiming a P1.16 official result, repeat the complete physical benchmark until exactly five transcript-correct valid runs are obtained.

## Verification

After adding diagnostic-only code: `testDebugUnitTest` passed (331 tests, 0 failures/errors) and `assembleDebug` passed. No production optimization was promoted.
