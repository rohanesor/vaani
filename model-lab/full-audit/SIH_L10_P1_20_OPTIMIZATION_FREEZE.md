# SIH-L10-P1.20 — Optimization Freeze and Evidence Consolidation

## Final status

**P1.20 — OPTIMIZATION FROZEN**

| Item | Status |
|---|---|
| Current production configuration | PRESERVED |
| Safe CT2 optimization | NONE FOUND |
| Production behavior | UNCHANGED |
| New physical benchmark | NOT RUN |
| Telugu/Kannada | NOT STARTED |
| Future optimization | MODEL/RUNTIME LEVEL ONLY |

## Task 1 — Clean state verification

| Check | Result |
|---|---|
| Branch | `sih/laptop1-c1-c2-integration` |
| Local HEAD | `8a855e5b462edf6439e0605a1a50ff284f9c6c70` |
| Remote HEAD | `8a855e5b462edf6439e0605a1a50ff284f9c6c70` (verified identical) |
| Tracked working tree | clean |
| Untracked artifacts | preserved; not committed |

No unintended production changes were made. The only committed code changes since P1.15A are:

- Duplicate-message prevention (`singleTask`, `TurnSubmissionGate`)
- TTS/CT2 profiling log instrumentation (debug diagnostics only)
- Evidence/documentation files

## Task 2 — Optimization boundary

### CT2 translation receiver

| Attribute | Value |
|---|---|
| Model | `facebook/nllb-200-distilled-600M` |
| CT2 artifact | `osa911/nllb-200-distilled-600M-ct2-int8@46858753dbaf8eb5e21bb6f0037c3b90851e090a` |
| Model size | 619,704,329 bytes |
| Runtime | CTranslate2 4.8.2, Android ARM64 JNI, CPU Ruy backend, OpenMP disabled |
| Beam policy | beam=1 when input ≤20 pieces; beam=4 otherwise |
| Session reuse | verified; one model load, mutex-serialized |

### Measured physical receiver latency

| Metric | Value | Source |
|---|---:|---|
| Short beam=1 median / P95 | 1122 / 1423 ms | P1.17 receiver profile |
| Medium beam=1 median / P95 | 2014 / 2058 ms | P1.17 receiver profile |
| Long beam=4 first sample | 9600 ms | P1.17 (stopped at thermal 3) |
| P1.16 valid-run CT2 warm | 718–754 ms | P1.16 four valid runs |

### Other validated components

| Component | Warm measurement | Source |
|---|---:|---|
| SenseVoice STT T1→T2 | 176–278 ms median | P1.15A/P1.16 |
| Hindi TTS synthesis | 182–198 ms warm | P1.16 valid runs |
| Transport TX→ACK | 79–362 ms | P1.16 |
| Receiver T5→T8 (incl. playback) | 1950–2032 ms warm | P1.16 valid runs |

### Conclusion

CT2 native `translate_batch` inference is the dominant receiver compute bottleneck. SentencePiece tokenization, transport dispatch, TTS synthesis, and AudioTrack playback are negligible or proportional to generated audio duration. No safe configuration-level optimization was found.

## Task 3 — Rollback point

| Reference | SHA |
|---|---|
| Last validated production APK SHA-256 | `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9` |
| Commit generating that APK | `7ef8509f4f7d8e2f8a912c317a17a4dfafc07f5d` |
| Safety checkpoint branch | `sih/laptop1-before-l10-p1-17` |
| P1.19 checkpoint | `sih/laptop1-before-l10-p1-19` at `038e58b4902566c89d9563bc610f797bd4e8f830` |

The validated APK and commit are recoverable by checking out the safety branch and rebuilding.

## Task 4 — Future optimization roadmap

The following options may reduce receiver latency. **None is claimed to improve latency until physically measured.**

| Option | Requirement | Risk |
|---|---|---|
| Smaller/faster multilingual translation model | Android ARM64 + CT2 compatible; covers EN/HI/TA direct routing; materially smaller than 600M | Quality degradation; conversion/provenance; integration |
| Alternative translation runtime | Verified Android ARM64 support; NLLB or equivalent coverage | Engineering effort; thermal/memory; quality |
| Model quantization/compression | Quality validation; runtime compatibility; thermal/memory profile | Output quality; thermal; latency regression |
| Architectural strategy (e.g., cascade, distillation) | Future research phase; not a configuration change | Scope; timeline; correctness |

P1.18 evaluated available candidates and rejected all due to provenance, compatibility, footprint, or quality gates. No model download or integration was performed.

## Task 5 — Benchmark handoff

Laptop 1 is prepared to consume future multilingual benchmark results from Laptop 2.

- Do not run Telugu/Kannada benchmarks on Laptop 1.
- Do not perform the official five-run EN→HI benchmark until devices are cooled and reconnected.
- Future benchmark artifacts should be placed under `model-lab/bench/out/` with phase-tagged filenames.

## Verification

| Check | Result |
|---|---|
| Local HEAD matches remote | YES |
| Tracked worktree clean | YES |
| Unit tests (last build) | 331 passed |
| APKs/WAVs/models committed | NO |
