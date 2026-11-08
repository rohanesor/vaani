# SIH-L10-P1.22 — Controlled CT2 Receiver Latency Optimization

## Final decision

**P1.22 — NO SAFE OPTIMIZATION FOUND**

Status: **BLOCKED BY THERMAL SAFETY GATE.** No candidate was promoted and no latency improvement is claimed.

## 1. Starting checkpoint

- Starting branch: `sih/laptop1-c1-c2-integration`
- Starting SHA: `28ae49c1d721eb7113a1a7feb2bb81c2138644f9`
- Checkpoint: `sih/laptop1-before-p1-22`
- Existing untracked artifacts were preserved.

## 2. Device information

- Serial: `MJPVXCSG9HYL65YL`
- Model: Realme `RMX3782`
- Android API: 35
- ABI: `arm64-v8a`
- ADB: 1.0.41, platform-tools 37.0.0-14910828
- Authorization: authorized
- Availability: connected

The validated APK SHA before the experiment attempt was:
`072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9`

## 3. Production baseline configuration

- NLLB-200 distilled 600M CT2 INT8
- `beam_size = 1` for short text; `4` otherwise
- `max_decoding_length = 256`
- `use_vmap = false`
- `num_threads_per_replica = 0`

Previously validated Android reference: short median ~1122 ms, P95 ~1423 ms; medium median ~2014 ms, P95 ~2058 ms.

## 4. Controlled sentences

- SHORT: `Help me.`
- MEDIUM: `I am trapped and need immediate assistance.`
- LONG: `I am really stuck right now and I need help immediately. Please send the rescue team to my location.`

## 5. Preflight and safety gate

The device initially reported thermal status 3. After app stop and cooling, it reached status 1. Detailed thermal data showed skin temperature approximately 45.1°C, above the status-3 threshold. On the subsequent profiler attempt the device again reported thermal status 3.

Per protocol, physical experiments were stopped. No candidate run was validly completed.

## 6. Experiments

### Experiment A — `max_decoding_length = 64`

- Baseline: 256
- Candidate: 64
- Raw measurements: none
- Median/P95: not applicable
- Quality/truncation: not evaluated
- Decision: **NOT RUN — thermal gate**

### Experiment B — `use_vmap = true`

- Baseline: false
- Candidate: true
- Raw measurements: none
- Decision: **NOT RUN — A was not independently completed**

### Experiment C — `num_threads_per_replica = 2`

- Baseline: 0
- Candidate: 2
- Raw measurements: none
- Decision: **NOT RUN — preceding gates not completed**

No cold, warm, T5→T6, T5→T8, TTS, playback, memory, or five-run latency dataset was produced. No desktop measurement was substituted.

## 7. Crash/ANR and regression results

No valid CT2 experiment ran, so no candidate crash/ANR or quality claim can be made. No production architecture, routing, transport, STT, TTS, UI, or language behavior was changed.

## 8. Promotion decision

**No promotion.** The ≥15% median improvement requirement cannot be evaluated without valid Android measurements. Production source configuration remains unchanged.

## 9. APK restoration note

The temporary debug APK used for profiler invocation was stopped. An exact validated-APK restoration and final hash verification were attempted, but the final ADB install/hash command timed out. The device APK must be rechecked and restored to the validated SHA before any future physical run. This report does not claim that restoration was verified.

## 10. Final configuration and commit

- Final source production configuration: unchanged
- Promoted candidate: none
- Final commit SHA: 3cb708d890558d32b028b9abbb99248e7c123b60

No Telugu/Kannada work was started.
