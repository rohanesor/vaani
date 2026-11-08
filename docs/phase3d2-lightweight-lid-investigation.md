# Phase 3D.2 — Lightweight offline LID investigation

## Scope and decision

This is a design/research record only. No LID model was downloaded, converted, packaged, or invoked. The repository contains no approved lightweight audio-LID artifact with verified provenance, Android runtime compatibility, model size, ten-language coverage, accuracy evidence, and redistribution terms.

**NO APPROVED CANDIDATE.**

## Required acceptance gate for any future candidate

A candidate must provide all of the following before integration investigation can begin:

| Requirement | Required evidence |
|---|---|
| Target coverage | Explicit support/evaluation for HI, GU, MR, KN, ML, TA, TE, OR, BN, and EN |
| Offline Android runtime | Supported TFLite, ONNX Runtime Mobile, or maintained native Android route; no server/API dependency |
| Packaged artifact | Immutable model URL/revision, SHA-256, exact model size, and no runtime download |
| License | License for weights and redistribution reviewed and approved for this product |
| Accuracy | Language-labelled held-out audio evaluation, including confusable Indic languages |
| Performance | Device-A same-audio measurement for feature extraction + inference, RAM, and repeated-run stability |
| Integration | Deterministic audio requirements (sample rate, channels, window length), frontend provenance, and conversion steps |

No candidate has supplied this complete evidence in the current project. Therefore model size, runtime, CPU/RAM cost, and accuracy figures are **not claimed**.

## Smart AUTO architecture — future investigation only

```text
Audio
  -> approved lightweight audio LID
  -> confidence / supported-language validation
       -> explicit Whisper detected language
       -> otherwise Whisper AUTO
```

With a Tamil profile, a confidence-qualified Tamil result could select explicit `ta`; another confidence-qualified supported result could select its own explicit code. Low confidence, unsupported output, and LID failure must take the existing Whisper AUTO path—not silently select Tamil or English.

This could remove Whisper's Auto-LID encoder pass only if a future Device-A benchmark proves the frontend plus explicit Whisper is faster and preserves all-language accuracy. It is not currently a performance claim.

## Alternative already available: user-selected FAST

A future explicit **FAST** mode can use the locally stored preferred language directly. Phase 3D.1 measured EN explicit at 3102 ms versus AUTO at 6462 ms on one 1520 ms English PCM. That result does not establish correctness for other languages or for speech that differs from the preferred language. AUTO remains the safe default.
