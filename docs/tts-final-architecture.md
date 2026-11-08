# Final TTS architecture decision — Phase 2D

## Classification: BLOCKED

Phase 2N legal decision: IndicVoices-R is **TECHNICALLY ACCESSIBLE BUT LEGALLY BLOCKED**. No alternative corpus combination has sufficient verified rights for all ten required languages and trained-weight/APK distribution. See `docs/tts-legal-decision.md`, `docs/tts-alternative-corpus-landscape.md`, and `docs/tts-final-training-options.md`. No corpus is hard-coded into the runtime.

The product abstraction is locked and remains model-neutral:

```text
UnifiedTtsEngine
  → LanguageRouter
  → TtsModelRegistry
  → lazily loaded TtsBackend session
  → PCM
  → AudioTrack
```

A final `TtsModelRegistry` cannot be implemented honestly yet because there is no immutable, legally cleared, Android-validated artifact for every required language. A registry must contain only explicit routes with source URL, revision, artifact SHA-256, license record, frontend/vocoder record, backend ID, and no download URL. It must verify bundled files before availability and report model corruption rather than continue.

## Production readiness gate

| Requirement | Result |
|---|---|
| Ten actual language routes | Fail: no legally cleared all-ten set |
| Commercial redistribution | Fail: MMS is CC-BY-NC; Piper weight terms incomplete |
| Offline packaged assets | Fail: no selected complete set |
| Android CPU inference | Not tested: no ARM device and no legal complete set |
| 2 GB RAM / low-memory behavior | Not measured |
| RTF, cold/warm latency, time-to-first-audio | Not measured |
| Native-listener emergency clarity | Not measured |
| Stable repeated synthesis | Not measured |

## Final architecture

**No concrete production TTS model architecture is selected.** The recommended deliverable remains the locked hybrid-capable abstraction, not an unverified MMS, Piper, Kokoro, or AI4Bharat deployment. The research training direction remains a shared acoustic student with language-specific frontends/adapters, but it cannot proceed until the corpus/legal gate passes.

Current training decision: **D — no legally clean training corpus; stop training and retain existing candidates as benchmarks only.** This is not authorization to ship MMS or any other candidate.

## Conditions to leave BLOCKED

1. Acquire official, ungated artifacts with explicit model-weight/frontend/vocoder commercial redistribution terms for every route.
2. Pin revision and SHA-256 for each artifact and review any conversion chain.
3. Build a test-only registry and lab APK with all assets included.
4. Run the complete benchmark and listening protocol on a real 2 GB ARM Android device in airplane mode.
5. Select only after all metrics and all language routes pass.
