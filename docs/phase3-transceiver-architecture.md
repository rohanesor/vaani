# Phase 3 iTantra transceiver architecture

## Status

Production pipeline work is model-neutral. No new TTS model was trained or acquired. MMS remains **BENCHMARK_ONLY** and is no longer selected as the application production engine. The current production TTS route is **BLOCKED** and returns an explicit unsupported-language failure until an approved bundled artifact exists.

## End-to-end design

```text
Microphone -> VAD -> on-device Whisper multilingual STT -> detected language
  -> immutable message -> Packet v2 -> Wi-Fi Direct preferred / Bluetooth fallback
  -> packet validation -> Room persistence -> LanguageRouter
  -> approved TtsBackend -> PCM -> AudioTrack
```

The receiver uses the packet language code; it does not ask the operator to select the sender language. The transport carries text and language metadata, never audio.

## Implemented boundaries

- Whisper tiny multilingual Q5_1 remains the selected local STT model.
- RMS VAD remains the measured existing implementation; a Silero runtime has not been acquired or validated, so it is not falsely represented as production-ready.
- `LanguageRouter`, `TtsModelRegistry`, and `UnavailableTtsEngine` provide explicit model-neutral routing.
- `UnavailableTtsEngine` is production-safe: it returns `UNSUPPORTED_LANGUAGE`/`MODEL_MISSING`; it never substitutes another language.
- Existing Room repository and Bluetooth RFCOMM path remain offline-capable code paths.

## Status labels

- `PRODUCTION_APPROVED`: only a verified bundled artifact after legal, device, and quality gates.
- `BENCHMARK_ONLY`: MMS and other unapproved candidates.
- `MOCK_TEST_ONLY`: test doubles; never presented as speech capability.
- `BLOCKED`: current multilingual production TTS route.

## Not yet claimed

Automatic Whisper language extraction, full TransportManager Wi-Fi priority/fallback wiring, persistent replay inbox state, and physical-device latency/RAM/CPU metrics require implementation and real-device validation. The native Wi-Fi Direct transport and length-prefixed framing now exist, but are not device-validated. No measurements are fabricated.
