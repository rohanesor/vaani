# Engineered multilingual TTS roadmap

## Phase 1 — data acquisition

Create a private, checksummed corpus manifest for IndicVoices-R and LibriTTS; record exact archive URLs, licenses, attribution, normalized-text policy, per-language hours/speakers/sample rates, and train/dev/test splits. Stop if data-card/consent/license review fails.

## Phase 2 — teacher/model preparation

Implement reproducible preprocessing and language frontends. Train no model until corpus audit passes. Select an Apache/MIT-compatible training implementation for a shared non-autoregressive acoustic model and HiFi-GAN-class vocoder.

## Phase 3 — supervised training and optional distillation

Train the compact student on ground truth. Only if native-language evaluation fails, train an in-house teacher solely from cleared data and distill into the student. Record code/container/GPU/data/checkpoint provenance. Do not use non-commercial or unclear external teacher outputs.

## Phase 4 — export

Export separately pinned acoustic and vocoder ONNX graphs. Inspect inputs, dynamic shapes, operators, model bytes, sample rate, and generated PCM parity before Android work.

## Phase 5 — quantization

Establish FP32 baseline, then separately test FP16 and INT8 candidates. Reject any precision that regresses emergency intelligibility, stability, or target-device performance.

## Phase 6 — Android lab integration

Implement a test-only `TtsModelRegistry` and ONNX Runtime Mobile backend behind the locked `UnifiedTtsEngine`. Bundle only checked, local test artifacts; verify SHA-256 before model availability; do not alter production MMS playback.

## Phase 7 — 2 GB device benchmark

Use a real ARM Android phone in airplane mode. Measure APK/install footprint, PSS/native heap, CPU, cold/warm/first-audio latency, RTF, repeated synthesis, and session release across language switches.

## Phase 8 — language coverage validation

Run all ten languages through short, long, location, number, name, and emergency messages. Native listeners evaluate intelligibility, pronunciation, emergency clarity, naturalness, prosody, and failures.

## Phase 9 — transceiver integration

Only after all gates pass, migrate `TextToSpeechUseCase` from the MMS-specific path to an implementation of `UnifiedTtsEngine`; retain packet language metadata as the routing source. Then separately review model packaging, offline launch, integrity failure UX, release, and deployment.

## First implementation task

**Acquire and audit corpus manifests, not model weights:** produce a reproducible private manifest for the ten IndicVoices-R/LibriTTS language slices and establish language-specific frontend normalization tests. This is the first dependency for a legally defensible training prototype.
