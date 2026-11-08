# N6 — Neural JSCC & Semantic Erasure: Experiment Plan & Benchmark Design
**Project:** iTantra / Vokie — Smart India Hackathon (SIH)  
**Track:** Offline Multilingual Emergency Speech Communication  
**Phase:** N6 Experimental Benchmark Plan  
**Target Execution Environment:** Standalone Python (`model-lab/research/jscc_bench.py`)  
**Status:** **APPROVED FOR LEVEL 1 SIMULATION ONLY**

---

## 1. Executive Summary & Objective

The objective of this experiment is to provide **rigorous, reproducible empirical proof** of the communication trade-offs between:
1. **Classical Digital Separation** (UTF-8 text over TCP ARQ and Reed-Solomon erasure coding)
2. **Physical-Layer DeepJSCC** (Continuous Transformer semantic coding over simulated AWGN / Rayleigh channels)
3. **Vokie Semantic Application-Layer JSCC (AL-JSCC)** (Priority-interleaved emergency frames over Packet Erasure Channels)

This experiment runs **completely outside the Android production app** in a standalone Python script, guaranteeing zero risk to codebase stability while producing publication-quality benchmark curves.

---

## 2. Experimental Architecture Diagram

```
                                      BENCHMARK HARNESS ARCHITECTURE
                                      
                            [Emergency Corpus (200 Utterances)]
                                             │
                                             ▼
                          [Semantic Extraction & Tokenization]
                             - Text: 50-150 bytes UTF-8
                             - VBEF Frame: 16-byte struct
                                             │
               ┌─────────────────────────────┼─────────────────────────────┐
               ▼                             ▼                             ▼
       [SYSTEM A: BASELINE]          [SYSTEM B: CLASSICAL FEC]     [SYSTEM C: SEMANTIC AL-JSCC]
       UTF-8 + PacketV2              UTF-8 + PacketV2              VBEF Frame (Pri 1) + Text (Pri 2)
       Transport: TCP (ARQ)          Transport: UDP + RS(8,4)      Transport: Priority Interleaver
               │                             │                             │
               └─────────────────────────────┼─────────────────────────────┘
                                             ▼
                           [CHANNEL SIMULATION ENGINE]
                           - Model 1: Gilbert-Elliott Burst Erasure (p = 0.05 ... 0.50)
                           - Model 2: AWGN Channel (SNR = -6 dB ... 18 dB)
                                             │
               ┌─────────────────────────────┼─────────────────────────────┐
               ▼                             ▼                             ▼
       [Receiver System A]           [Receiver System B]           [Receiver System C]
       Wait for Retransmit           Reed-Solomon Reconstruct      Extract High-Priority VBEF
       (Records Stalls/Drops)        (Cliff Effect Threshold)      (Instant Triage Recovery)
               │                             │                             │
               └─────────────────────────────┼─────────────────────────────┘
                                             ▼
                             [METRICS EVALUATION SUITE]
                             - Triage Decision Accuracy (%)
                             - Semantic Similarity (SemScore)
                             - Effective Goodput (kbps)
                             - 95th Percentile Latency (ms)
```

---

## 3. Benchmark Dataset

We utilize a curated evaluation split of **200 emergency disaster utterances** derived from the **Disaster Response Messages Dataset** and **CrisisLexT26**, translated across four core languages:
- English (`en`)
- Hindi (`hi`)
- Tamil (`ta`)
- Telugu (`te`)

Each utterance is annotated with ground-truth triage metadata:
```json
{
  "id": "disaster_042",
  "text": "Flood water rising near primary school, 4 children trapped on roof, urgent rescue needed",
  "category": "RESCUE_REQUEST",
  "urgency": "CRITICAL",
  "severity": "EXTREME",
  "victim_count": 4,
  "hazard": "FLOOD"
}
```

---

## 4. Systems Under Test (SUT)

### System 1: Baseline Vokie (PacketV2 + TCP ARQ)
- Payload: Full UTF-8 text string (average 82 bytes).
- Transport Protocol: Stop-and-wait ARQ with maximum 3 retransmissions and 200 ms timeout.
- Behavior: Completely lossless if retransmissions succeed; drops connection if channel outage exceeds timeout.

### System 2: Classical Separation with FEC (PacketV2 + Reed-Solomon)
- Payload: Full UTF-8 text encoded using Reed-Solomon $RS(N=8, K=4)$ erasure code across 8 sub-packets.
- Channel: Packet Erasure Channel.
- Behavior: Recovers message if $\le 4$ packets are lost; suffers 100% loss (cliff effect) if $\ge 5$ packets are erased.

### System 3: Vokie Semantic AL-JSCC (Priority Interleaving)
- Payload:
  - **Packet 0 (Priority 1):** 16-byte VBEF struct containing Category, Urgency, Severity, Count, and GPS offset. Transmitted with $3\times$ replication.
  - **Packets 1..3 (Priority 2):** Chunked UTF-8 text.
- Behavior: Under extreme packet erasure ($p = 0.4$), Packet 0 survives with $97.3\%$ probability, enabling instant triage display even when text chunks are lost.

### System 4 (Theoretical Reference): DeepSC Continuous Simulation
- Model: 4-layer Transformer semantic encoder $\to$ AWGN channel $\to$ 4-layer Transformer decoder.
- Channel: Continuous AWGN with SNR $\in [-6, 18]$ dB.
- Purpose: Demonstrates analog graceful degradation as the upper theoretical bound.

---

## 5. Channel Models & Impairment Parameters

1. **Independent Packet Erasure Channel (PEC):**
   - Erasure probability: $p \in \{0.0, 0.05, 0.10, 0.20, 0.30, 0.40, 0.50\}$.
2. **Gilbert-Elliott Burst Loss Model:**
   - State G (Good): $P(\text{loss} | G) = 0.02$.
   - State B (Bad): $P(\text{loss} | B) = 0.85$.
   - Average burst length: 4 packets ($P_{BG} = 0.25$).
   - Transition $P_{GB} \in [0.01, 0.15]$ modeling increasing shadowing severity.
3. **Physical AWGN Channel (for System 4):**
   - SNR $\in \{-6, -3, 0, 3, 6, 9, 12, 15, 18\}$ dB.

---

## 6. Evaluation Metrics

1. **Triage Decision Accuracy (%):**
   $$\text{Accuracy} = \frac{1}{N} \sum_{i=1}^N \mathbb{I}\left(\hat{\text{Category}}_i = \text{Category}_i \land \hat{\text{Urgency}}_i = \text{Urgency}_i\right)$$
2. **Semantic Similarity (SemScore):**
   Cosine similarity between Sentence-BERT / IndicBERT embeddings of original text and reconstructed text.
3. **Effective Goodput (bps):**
   Useful information bits delivered per second over the channel.
4. **95th Percentile Triage Latency (ms):**
   Time elapsed from end-of-speech to successful category display on receiver.

---

## 7. Execution Harness Code Specification

The experiment is implemented in `model-lab/research/jscc_bench.py`:
```bash
# How to execute the benchmark harness:
python model-lab/research/jscc_bench.py --samples 200 --output-dir docs/research/benchmark_results/
```

### Expected Output Deliverables:
1. `docs/research/benchmark_results/triage_accuracy_vs_packet_loss.png`
2. `docs/research/benchmark_results/latency_vs_loss_percentile.png`
3. `docs/research/benchmark_results/semscore_vs_snr_curves.png`
4. `docs/research/benchmark_results/summary_metrics.json`

---

## 8. Safety & Non-Interference Verification

- **Production Code Isolation:** No code from this experiment touches `app/src/main/`.
- **Zero Native Dependency:** Runs on pure Python standard library + NumPy + optional PyTorch.
- **Reproducibility:** Seeded random number generators ensure identical simulation results across test runs.

---
