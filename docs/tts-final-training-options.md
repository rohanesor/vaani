# Final TTS training options — Phase 2N

## Status

**No training option is approved.** The legal and deployment gates are independent: even a corpus with a permissive data license needs provenance, consent/privacy, derivative-model, Android, and distribution review.

## Option comparison

| Option | Size / RAM / CPU | Coverage | ONNX/offline path | Redistribution | Decision |
|---|---|---|---|---|---|
| A. One shared multilingual model | potentially one shared footprint; actual size/RTF unknown until trained and benchmarked | best parameter sharing if data exists | feasible in principle with static ONNX and ONNX Runtime Mobile | depends on every source and training artifact | preferred research architecture, not approved |
| B. Shared acoustic model plus language frontends/adapters | shared core plus small route-specific assets; actual RAM/RTF unknown | good compromise for Indic phonology and normalization | feasible in principle; frontends must be bundled | frontend/data/model rights all require review | preferred engineering direction, not approved |
| C. Compact language-specific models plus shared vocoder | more model files and possible cold-load cost; actual APK/RAM unknown | isolates language quality and licensing | feasible in principle if each artifact passes | each model and vocoder requires review | fallback architecture, not approved |

No Android CPU, RAM, latency, RTF, APK size, or quality numbers are claimed. Real ARM airplane-mode measurements are mandatory.

## Decision

Continue with the model-neutral architecture and do not train. If no legally clean corpus is approved, use **Decision D operationally: stop training and retain an existing candidate only as a benchmark**, not as a production recommendation. Current MMS is not an acceptable replacement because its upstream licensing and production gates are unresolved. No all-ten-language redistributable pretrained model has been verified.

Thus the current state is:

```text
D — no legally clean training corpus; training stopped
```

This does not authorize shipping MMS or any other model.

## Requirements to reopen training

1. Approve a source or source combination for all ten languages.
2. Resolve data acquisition, preprocessing, transcript, speaker/consent, derivatives, and commercial/APK/AWS rights.
3. Pin every release and checksum.
4. Freeze speaker-disjoint splits and quality manifests.
5. Train a small research student outside production.
6. Export static ONNX and measure correctness.
7. Benchmark on a real low-end/mid-range Android device in airplane mode.
8. Complete native-language emergency evaluation.
9. Obtain explicit trained-weight and APK distribution approval.

## Runtime architecture remains locked

```text
UnifiedTtsEngine -> LanguageRouter -> TtsModelRegistry
  -> language frontend -> shared acoustic model -> shared vocoder
  -> PCM -> AudioTrack
```

The incoming packet's language code selects the route automatically. The receiver does not choose or download a language package at runtime. Assets must eventually be bundled or installed through an explicitly approved offline package; no runtime model download is permitted.
