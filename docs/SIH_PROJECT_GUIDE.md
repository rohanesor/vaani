# iTantra — SIH Project Guide

> This document is the governing product brief for iTantra development. Where a future feature conflicts with this guide, preserve the guide’s offline, open-source, low-power, inclusive rescue-communication requirements unless the project owner explicitly changes them.

## Background

Vocal audio information is data-intensive, making it difficult to transmit through low-data-rate links. In alert and distress scenarios, transmitting audio information is critical rather than relying only on written messages: it is more inclusive and supports people regardless of literacy.

## Problem Statement

Build an Android application with lightweight, highly accurate local STT and TTS models for ten Indian languages:

- Hindi
- Gujarati
- Marathi
- Kannada
- Malayalam
- Tamil
- Telugu
- Odia
- Bengali
- English

The application must run locally on a low-power device.

### Required communication loop

1. Local STT is activated after pause/stoppage detection.
2. STT forms detected sentences.
3. Text is sent efficiently with minimal latency through Wi-Fi/Bluetooth to an embedded device or another phone running iTantra.
4. Receiver-local TTS converts received text into intelligible speech and plays it as a voice-note style message.
5. Alert messages must be announced at maximum allowed volume and be non-interruptible according to platform-safe audio rules.
6. Two phones running the app must support a push-to-talk walkie-talkie workflow over Wi-Fi or Bluetooth.
7. When push-to-talk is disabled, the system should support a phone-like communication workflow.

## Evaluation Metrics

### Efficiency — 20%

Measure and optimize:

- model size;
- app size;
- RAM and flash footprint;
- CPU usage during idle listening.

### Accuracy — 40%

Measure and optimize:

- low STT Word Error Rate (WER);
- high human intelligibility, legibility, and natural flow for TTS.

### Latency — 20%

Measure:

- speech end/pause to STT completion;
- text receipt to TTS synthesis and playback;
- TTS Real-Time Factor (RTF);
- end-to-end delay from speech on sender to audio start on receiver.

## Non-Negotiable Technical Boundaries

- **Fully offline:** no cloud STT, cloud TTS, hosted inference, or Internet requirement.
- **Open source only:** do not use proprietary, closed-source, or commercial voice-activation SDKs.
- **Approved framework direction:** open-source ML/TinyML frameworks such as TensorFlow Lite, TensorFlow Lite for Microcontrollers, PyTorch Mobile, or comparable open-source local runtimes.
- **Android deployment:** run smoothly on low- and mid-range Android phones.
- **Transport:** use local Wi-Fi and/or Bluetooth for device-to-device communication.

## iTantra Engineering Principles

- Keep audio on-device; transmit compact text rather than raw audio where possible.
- Preserve an offline-first, resilient rescue workflow.
- Prefer bounded CPU, RAM, storage, and latency over unnecessary model complexity.
- Do not claim accuracy, intelligibility, latency, battery, or two-device behavior without measured evidence.
- Maintain truthful user-facing states when models, translation, TTS assets, permissions, or transport are unavailable.
- Preserve PacketV2 compatibility and existing reliable Bluetooth/Wi-Fi Direct behavior unless an explicitly approved protocol revision is required.
- Treat ten-language coverage as a delivery requirement: do not imply all ten languages are validated until STT, TTS, runtime, and physical-device validation exists for each.

## Validation Expectations

For every candidate STT/TTS artifact or pipeline change, record:

- source, revision, checksum, and license/redistribution status;
- offline Android compatibility;
- model and app footprint;
- cold/warm load behavior;
- RAM/PSS and CPU where measurable;
- STT WER or documented transcript evaluation method;
- TTS playback/intelligibility evaluation method;
- sentence-level and end-to-end latency;
- RTF for TTS;
- one-phone and two-phone validation status, clearly separated.

## Current Scope Reminder

This guide defines the target product. Existing partial implementations, tests, debug harnesses, and model investigations must be represented honestly as implemented, simulated, software-tested, one-device-tested, or physically two-device-validated as applicable.
