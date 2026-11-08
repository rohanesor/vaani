# SIH-L10-P1.21 — CT2 Optimization Investigation

## Final status

**DEVICES UNAVAILABLE — NO PHYSICAL EXPERIMENT RUN**

Physical Android devices were not connected during this investigation. No controlled experiment could be executed.

## Safety checkpoint

- Starting SHA: `5282c988a825a99c9e6b0e9d10f6d6c4e586428d`
- Safety branch: `sih/laptop1-before-p1-21`
- Validated APK: `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9`

## Baseline

| Metric | Value |
|---|---|
| Model | NLLB-200 distilled 600M CT2 INT8 |
| Size | 619,704,329 bytes |
| Short beam=1 median / P95 | 1122 / 1423 ms |
| Medium beam=1 median / P95 | 2014 / 2058 ms |

## Controlled test sentences

| Category | Text |
|---|---|
| SHORT | `Help me.` |
| MEDIUM | `I am trapped and need immediate assistance.` |
| LONG | `I am really stuck right now and I need help immediately. Please send the rescue team to my location.` |

## CT2 API analysis

### TranslationOptions (per-request)

| Option | Current | Testable | Analysis |
|---|---|---|---|
| `beam_size` | 1 for ≤20 pieces | ✓ | Already optimized to greedy for short text |
| `max_decoding_length` | 256 | ✓ | Upper bound; lowering risks truncation |
| `max_input_length` | 1024 | ✗ | Default; rescue text is shorter |
| `patience` | 1 | ✗ | Only affects beam > 1 |
| `length_penalty` | 1 | ✗ | Changes output scoring |
| `repetition_penalty` | 1 | ✗ | Changes output quality |
| `use_vmap` | false | ✓ | Model may lack vmap |

### ReplicaPoolConfig (at model load)

| Option | Current | Resolved | Testable | Analysis |
|---|---|---|---|---|
| `num_threads_per_replica` | 0 | min(4, hw) | ⚠️ | Prior explicit changes were unsafe |
| `cpu_core_offset` | -1 | none | ✓ | May not help on big.LITTLE |
| `max_queued_batches` | 0 | default | ✗ | Single request; irrelevant |

### ComputeType

| Current | Resolved | Testable | Analysis |
|---|---|---|---|
| DEFAULT | INT8 | ✗ | Already INT8; cannot reduce further |

## Candidate experiments

| ID | Parameter | Baseline | Candidate | Hypothesis | Risk | Status |
|---|---|---|---|---|---|---|
| A | `max_decoding_length` | 256 | 64 | Short output needs fewer decoder iterations | Truncation | NOT RUN |
| B | `use_vmap` | false | true | Vocabulary mapping may accelerate lookup | Model lacks vmap | NOT RUN |
| C | `num_threads_per_replica` | 0 | 2 | Lower threads may reduce contention | Slower; prior crashes | NOT RUN |
| D | `num_threads_per_replica` | 0 | 1 | Single-threaded for predictability | Defeats parallelism | NOT RUN |

## Thermal safety gate

Any experiment would be immediately stopped if:
- Thermal status ≥ 3
- Crash or ANR
- OOM
- Garbage/repeated/missing translation output
- Language corruption

## Promotion criteria

A candidate requires ALL of:
- ≥15% median CT2 improvement
- P95 does not regress materially
- Translation quality correct
- No crashes/ANRs
- No thermal escalation
- No memory regression
- Works on ARM64 target

## Experiments run

**0 experiments run** — devices not connected.

## Recommendation

Reconnect Realme RMX3782 when available. Run controlled experiments with fixed test sentences:

1. Experiment A: `max_decoding_length=64` for short text only
2. Experiment B: `use_vmap=true` if model contains vocabulary map
3. Thread experiments only if A/B fail and with extreme caution

If all experiments fail the 15% improvement gate, restore frozen configuration and conclude:

> P1.21 — NO SAFE CT2 OPTIMIZATION FOUND

## Production changes

**None.** Production configuration remains frozen.
