# SIH-L10-P1.15A — Final Benchmark Readiness Gate

## Final status

**READY FOR OFFICIAL FIVE-RUN BENCHMARK**

This phase was a memory/readiness gate only. It did **not** perform the official P1.9/P1.10-style latency benchmark. Telugu/Kannada work was not started. SenseVoice, PacketV2, transport semantics, translation models, and TTS model artifacts were not changed.

## Safety checkpoint

- Working branch: `sih/laptop1-c1-c2-integration`
- Pre-gate HEAD: `6055ef366317e73543c5a3a0ca36a594db8a5922`
- Checkpoint branch: `sih/laptop1-before-l10-p1-15a`
- Verified remote checkpoint SHA: `6055ef366317e73543c5a3a0ca36a594db8a5922`

Pre-existing untracked WAVs, model artifacts, logs, and machine-local files were preserved and excluded from Git.

## Physical configuration

| Role | Device | OS | ABI | Stable PID |
|---|---|---|---|---:|
| Sender | Realme RMX3782 (`MJPVXCSG9HYL65YL`) | Android 15 | arm64-v8a | 2891 |
| Receiver | vivo V2205 (`10BCAC2HM5000CR`) | Android 14 | arm64-v8a | 30131 |

Runtime configuration:

- Primary STT: SenseVoice-Small int8
- TTS runtime: sherpa-onnx 1.13.7
- English TTS: existing MMS/VITS artifact
- TTS threads: 2
- TTS engine objects: 1
- Native TTS sessions loaded: 1
- TTS queue workers: 1
- MainActivity instances: 1 per device

## Duplicate-message validation

The earlier duplicate originated before transport: two `MainActivity` instances owned two `CommunicationViewModel` collectors over the application-scoped turn flow. Each collector submitted the same `TurnEvent.Sentence`, producing two independent message IDs.

The cleanup uses `singleTask` activity ownership plus a defensive, stable `turn_id + sentence_index` claim at the submission boundary. It does not deduplicate by transcript.

During this gate:

| Event | Count |
|---|---:|
| Spoken turns/T0 | 5 |
| `TURN_SUBMIT` | 5 |
| Packet creation/TX | 5 |
| Correlated ACK | 5 |
| Receiver PacketV2 delivery | 5 |
| TTS enqueue | 5 |
| TTS start | 5 |
| TTS completion | 5 |
| Queue drained | 5 |
| Duplicate jobs | 0 |

## Five-turn queue and job state

| Turn | Message ID | Queue depth after playback | Active jobs | Model instances |
|---:|---|---:|---:|---:|
| 1 | `da6891a5-195c-402d-9045-d23719e58c10` | 0 | 0 | 1 |
| 2 | `0ec6f878-ec2b-4240-9214-090e53458651` | 0 | 0 | 1 |
| 3 | `ddccdee7-09b4-41f3-a092-787fce603df3` | 0 | 0 | 1 |
| 4 | `afed9575-d3d0-4bbb-8164-a95385d4f9e9` | 0 | 0 | 1 |
| 5 | `6ccdc2ec-26b6-473f-ab01-f76cee514e3a` | 0 | 0 | 1 |

No TTS failure, fatal exception, ANR, process restart, duplicate queue insertion, or accumulated playback job was observed.

## Five-turn settled memory table

Values are Android `dumpsys meminfo` KB. Retained model/runtime allocations are allowed; the gate tests progressive accumulation rather than requiring a return to the pre-load baseline.

### Realme sender

| Point | PSS KB | Native heap KB | Java heap KB | Thermal status |
|---|---:|---:|---:|---:|
| Fresh idle | 711,587 | 567,172 | 11,640 | 1 |
| Turn 1 settled | 448,517 | 223,220 | 12,264 | 1 |
| Turn 2 settled | 444,590 | 222,512 | 15,260 | 1 |
| Turn 3 settled | 446,435 | 220,888 | 11,560 | 1 |
| Turn 4 settled | 445,980 | 220,844 | 11,064 | 1 |
| Turn 5 settled | 445,581 | 220,836 | 11,088 | 1 |
| Final settled | 444,373 | 220,864 | 9,852 | 1 |

### vivo receiver

| Point | PSS KB | Native heap KB | Java heap KB | Thermal status |
|---|---:|---:|---:|---:|
| Fresh idle | 764,266 | 441,556 | 16,812 | 0 |
| Turn 1 settled | 636,486 | 405,724 | 17,700 | 0 |
| Turn 2 settled | 635,580 | 405,780 | 16,620 | 0 |
| Turn 3 settled | 634,526 | 405,776 | 15,584 | 0 |
| Turn 4 settled | 634,506 | 405,776 | 15,552 | 0 |
| Turn 5 settled | 634,410 | 405,776 | 15,556 | 0 |
| Final settled | 634,214 | 405,776 | 15,352 | 0 |

### Memory decision

**PASS.** Neither device shows `turn 1 < turn 2 < turn 3 < turn 4 < turn 5` progressive growth. Receiver native heap stabilized at approximately 405,776 KB; receiver PSS slightly declined. Sender PSS/native heap also remained stable or declined. Every queue drain reported zero queued and active jobs.

## TTS profiling conclusion

No speculative optimization was introduced. Profiling established:

- Text preprocessing: approximately 0.02–0.04 ms
- `AudioBuffer` wrapping: approximately 0.009–0.11 ms and no additional PCM copy
- AudioTrack preparation: approximately 14–22 ms
- AudioTrack write: below 0.1 ms
- The dominant operation is sherpa-onnx `OfflineTts.generate()`

The public sherpa API exposes frontend/tokenization, inference, and waveform/postprocessing as one opaque native call, so those sub-stages cannot be truthfully split without changing the runtime. Token count is likewise unavailable.

Controlled vivo length scaling:

| Text length | Native synthesis | Generated audio | RTF | Playback |
|---:|---:|---:|---:|---:|
| 8 chars | 2,158 ms | 624 ms | 3.46 | 840 ms |
| 23 chars | 3,886 ms | 1,799 ms | 2.16 | 1,967 ms |
| 48 chars | 7,456 ms | 3,370 ms | 2.21 | 3,553 ms |
| 109 chars | 15,908 ms | 7,083 ms | 2.25 | 7,264 ms |
| 160 chars | 23,785 ms | 9,898 ms | 2.40 | 10,089 ms |

The approximately linear scaling and negligible Kotlin/AudioTrack overhead indicate that native synthesis is inherently slow for this English artifact/runtime on vivo, rather than a queue, repeated model load, PCM copy, or playback-accounting defect.

Controlled repeated 12-character phrase:

- Cold model load: 2,842 ms
- First synthesis: 3,200 ms
- Warm synthesis: 3,010, 2,795, 3,307, 2,546 ms
- Conventional warm median: 2,902.5 ms
- Warm P95: 3,307 ms

Gate voice-loop TTS native calls were 2,320, 2,313, 1,965, 2,214, and 2,281 ms. Excluding the first request, the conventional warm median was 2,247.5 ms and P95 was 2,313 ms. Playback remained separately measured.

## SenseVoice summary

Gate STT inference values were 245, 298, 160, 171, and 176 ms:

- Median: 176 ms
- P95: 298 ms
- Minimum: 160 ms
- Maximum: 298 ms
- Engine tag: `stt_engine=sensevoice`

The earlier nine-run diagnostic remained median 203 ms and P95 567 ms. This gate does not replace that baseline or constitute the official benchmark.

## Source and evidence changes

- `app/src/main/AndroidManifest.xml`: single-task MainActivity ownership.
- `ContinuousTurnManager.kt`: stable turn/sentence submission gate.
- `CommunicationViewModel.kt`: owner and submission diagnostics.
- `TtsPlaybackQueue.kt`: queue/job/single-worker diagnostics.
- `SherpaOnnxTtsEngine.kt`, `TtsModels.kt`: native-pipeline and singleton diagnostics.
- `VokieAudioPlayer.kt`: AudioTrack preparation/write/playback diagnostics.
- Debug-only `TtsProfilingActivity.kt` and manifest registration.
- Unit coverage for stable turn submission claims.
- Machine-readable evidence: `model-lab/bench/out/sih_l10_p1_15a_memory_results.json`.

## Build gate

- `testDebugUnitTest`: 331 tests, 0 failures
- `assembleDebug`: PASS
- APK SHA-256: `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9`

## Readiness decision

All P1.15A criteria pass:

- one turn produces one message and one TTS job;
- queue and active jobs return to zero after every turn;
- one engine, model session, worker, and activity remain active;
- both PIDs remain unchanged;
- no progressive memory accumulation is present;
- SenseVoice remains functional;
- native TTS cost is characterized without conflating playback;
- tests and build pass.

**FINAL STATUS = READY FOR OFFICIAL FIVE-RUN BENCHMARK**
