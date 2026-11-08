# N6 Level-1: Application-Layer Semantic Erasure Benchmark

## Decision and scope

**Level-2 Android implementation recommendation: DEFER.**

This is a deterministic offline packet-erasure simulation, not DeepJSCC, PHY-JSCC, a Wi-Fi/Bluetooth PHY experiment, or an Android production validation. It evaluates whether a compact emergency hint can remain available when complete UTF-8 text is erased. The result is promising for semantic availability, but it does not yet justify protocol complexity, capability negotiation, production regression risk, or physical multi-device validation.

## Research question

Under independent and burst packet loss, does protected, semantic-first N5 metadata improve emergency-information availability and packet-unit triage latency relative to text alone and a classical packet FEC baseline?

## N5 representation audited

The benchmark was derived from `SemanticMetadata.kt` and `SemanticExtractor.kt` at starting commit `4a654c833a527799c0dc2ec2745569e2c100c858`.

Actual N5 v1 metadata is exactly **7 bytes**: version, event-category ordinal, urgency ordinal, severity ordinal, an eight-hazard bitset, victim count (`0` = unspecified; otherwise 1–254), and certainty ordinal. `UNKNOWN` is ordinal zero; an all-unknown, empty-hazard, unspecified-count object is not available. The extractor is a conservative, offline, explicit-keyword rule system for a bounded English/Hindi/Tamil/common-romanized vocabulary with local negation handling. It does not currently support Telugu terms.

The production PacketV2 N5 extension is an optional flag plus one length byte and the seven metadata bytes appended to each fragment. **This benchmark does not import Kotlin, serialize PacketV2, or demonstrate byte-level compatibility.** Its benchmark-equivalent model instead transmits the actual seven-byte semantic payload as a separate semantic-first packet so unequal protection can be measured. This is an experiment boundary, not a proposed production packet format.

## Dataset

`model-lab/research/n6_synthetic_emergency_v1.json` is a CC0-1.0 hand-authored synthetic benchmark dataset, not a licensed field corpus and not human translation ground truth.

- 24 utterances: English 6, Hindi 6, Tamil 6, Hindi+English 3, Tamil+English 3.
- Telugu: **NOT EVALUATED**. There are no Telugu examples because actual N5 extraction has no Telugu terms.
- Labels were hand mapped to actual N5 v1 slots; they are not clinical triage labels.
- Dataset SHA-256: `3d77aea9faf612fea8b99bafc919a377978eeb153f29bf9b85354bb064133fc2`.

## Systems and fair accounting

All systems use the same deterministic message selection and packet-erasure seed for a trial. Packet counts differ by declared redundancy, so a common loss probability/seed is used rather than pretending different-length transmissions have identical erasure vectors.

| System | Mechanism | Packet/byte accounting |
|---|---|---|
| A — text only | UTF-8 transcript split into 32-byte application payload chunks | transcript bytes; mean 2.50 packets / 63.2 bytes per message |
| B — classical FEC | Systematic K=4 data + one XOR parity packet, N=5 per block | mean 5.00 packets / 160.0 padded bytes; 100% packet overhead for this short-message dataset |
| C — semantic priority | Actual N5-equivalent 7-byte semantic payload first, replicated R=1/2/3; unprotected text chunks follow | A plus R packets and R×7 payload bytes; R=3 mean 5.50 packets / 84.2 bytes (+33.2% bytes vs A) |

The XOR code is transparent, not Reed-Solomon: a block recovers when all data arrive, or when exactly one data packet is missing and parity arrives. Its parity overhead is one packet per four data packets. The padded short-message behavior is a deliberate cost visible in the results, not an inferred production FEC result.

For A/B, semantic information is counted as available only if the complete authoritative transcript is recovered. For C, it is available when at least one entire semantic replica arrives. A recovered C frame matches its source slots exactly by construction, so **conditional field/frame accuracy is 100%**; the relevant end-to-end metric is field/frame availability, not classifier accuracy.

Useful-goodput is reported as `(complete text availability + independently usable semantic-frame availability) / transmitted packets`; it is an explicitly defined availability utility, not a bitrate or a mix of text bytes and metadata bytes.

## Channel models and reproducibility

Independent packet erasure was tested at 0%, 5%, 10%, 20%, 30%, 40%, and 50%. Each system/condition has 1,000 Monte Carlo messages (35,000 independent-channel trials). Burst tests add 15,000 Gilbert–Elliott trials.

Gilbert–Elliott trials start in the stationary state. Good loss is 0.01, Good→Bad is 0.08, Bad→Good is 0.12, stationary Bad probability is 0.40, and expected Bad run length is 8.33 packet units. Bad loss is 0.40/0.70/0.95 for mild/moderate/severe, giving stationary expected losses 0.166/0.286/0.386. Per-result observed loss, erased count, and transmitted count are in `summary_metrics.json` and `raw_results.csv`.

Latency is deliberately reported in **packet transmission units**: `t0` is message ready; C semantic latency is the first surviving priority replica; text latency is the completion/recovery point. No Android milliseconds or RTT were fabricated.

Run exactly:

```bash
python3 model-lab/research/jscc_bench.py --samples 1000 --seed 42 --output-dir docs/research/benchmark_results
```

The standard-library-only script records Python/platform, seed, dataset hash, configuration, timestamp, and git commit. Re-running with the same environment, seed, arguments, and starting commit produces identical numerical results (the timestamp is expected to differ).

## Analytical sanity checks

At p=0 all five systems achieved 1.000 semantic and text recovery with observed loss 0.000.

Independent semantic replication has `P(recover)=1-p^R`. The mandatory p=0.40, 100,000-trial checks were:

| R | Theory | Monte Carlo | Absolute error |
|---|---:|---:|---:|
| 1 | 0.6000 | 0.60010 | 0.00010 |
| 2 | 0.8400 | 0.83983 | 0.00017 |
| 3 | 0.9360 | 0.93719 | 0.00119 |

The complete checks for every p/R and the XOR recovery condition are machine-readable in `summary_metrics.json`.

## Independent-erasure results

Values below are end-to-end availability; 95% Wilson intervals are in the JSON output.

| Loss | System | Semantic/frame | Full text | Mean packet units to semantic/text (among successes) |
|---:|---|---:|---:|---|
| 20% | A | 0.600 | 0.600 | 2.36 / 2.36 |
| 20% | B | 0.751 | 0.751 | 5.00 / 5.00 |
| 20% | C R=1 | 0.814 | 0.583 | 1.00 / 3.33 |
| 20% | C R=2 | 0.958 | 0.578 | 1.15 / 4.35 |
| 20% | C R=3 | 0.989 | 0.577 | 1.21 / 5.37 |
| 40% | A | 0.289 | 0.289 | 2.11 / 2.11 |
| 40% | B | 0.333 | 0.333 | 5.00 / 5.00 |
| 40% | C R=1 | 0.583 | 0.307 | 1.00 / 3.18 |
| 40% | C R=2 | 0.825 | 0.324 | 1.29 / 4.28 |
| 40% | C R=3 | **0.924** | 0.315 | 1.48 / 5.16 |
| 50% | A | 0.202 | 0.202 | 2.00 / 2.00 |
| 50% | B | 0.192 | 0.192 | 5.00 / 5.00 |
| 50% | C R=3 | **0.876** | 0.215 | 1.59 / 5.11 |

At 40% loss, C R=3 makes metadata available 63.5 percentage points more often than text-only (0.924 vs 0.289), while full-text availability stays near the unprotected-text baseline (0.315 vs 0.289) and is delayed by priority transmissions. This is graceful degradation, not improved transcript recovery. C R=2 gives 0.825 metadata availability at +22.2% bytes; R=3 buys a further 9.9 points at +33.2% bytes.

B helps at 20% (0.751 vs A 0.600) but its fixed K=4,N=5 block is costly for these short messages and is not better than A at 50% (0.192 vs 0.202). It does not preserve semantic information when text is unrecoverable.

## Burst results

| Burst severity (observed loss for C R=3) | A semantic/text | B semantic/text | C R=3 semantic/text |
|---|---|---|---|
| mild (0.164) | 0.698 / 0.698 | 0.749 / 0.749 | **0.980 / 0.686** |
| moderate (0.283) | 0.591 / 0.591 | 0.589 / 0.589 | **0.900 / 0.574** |
| severe (0.379) | 0.523 / 0.523 | 0.529 / 0.529 | **0.738 / 0.523** |

The burst channel exposes a failure case: correlated loss reduces R=3 semantic availability to 0.738 in the severe configuration, substantially below its independent-loss result at a comparable loss rate. Replication is not a cure for a sufficiently long bad state.

## Outputs

`docs/research/benchmark_results/` contains configuration, summary JSON, 50,000-row raw CSV, analytical checks, plot metadata, and five dependency-free PNG plots:

- `triage_accuracy_vs_packet_loss.png`
- `semantic_recovery_vs_packet_loss.png`
- `full_text_recovery_vs_packet_loss.png`
- `latency_or_packet_cost_vs_loss.png` (normalized packet-cost visualization; exact costs are JSON values)
- `burst_loss_results.png`

The PNG renderer intentionally uses only the Python standard library. Series order/color mapping is in `plot_metadata.json`; exact values and confidence intervals should be used rather than reading pixels.

## Failure cases and limitations

- The semantic payload is atomic and error-free whenever a packet arrives; extraction errors, malformed metadata, partial-byte corruption, encryption, MTU/framing, ACK/retry timing, and scheduling contention are not simulated.
- Tiny synthetic messages make the one-parity FEC baseline unusually expensive. This is a baseline, not a claim against optimized Reed–Solomon codes.
- The hand-authored dataset is small, non-clinical, not a translation evaluation, and has no Telugu.
- Independent replicas are an optimistic model; burst results demonstrate correlated-loss degradation.
- No physical radio, Android resource cost, compatibility negotiation, translation/TTS effect, or delivery guarantee was measured.

## Interpretation and SIH relevance

The narrow result supports this defensible statement: Vokie evaluated an **offline application-layer semantic-priority erasure strategy** that can preserve a compact N5 emergency hint under simulated loss while the full transcript remains unavailable. It does **not** demonstrate DeepJSCC, PHY control, Shannon-limit behavior, guaranteed delivery, production-grade triage, or a production protocol.

The measurable simulated advantage is sufficient to retain the research direction, but not sufficient for Level 2. A future gate requires a larger licensed multilingual corpus (including Telugu if N5 supports it), a negotiated backward-compatible packet design, realistic packet scheduling/retry modeling, physical two-device loss injection, and a regression/risk review. Therefore the recommendation remains **DEFER**.
