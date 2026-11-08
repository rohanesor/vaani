# Phase 3D.3 — Preferred-language FAST STT validation

## Scope and evidence

The approved retained replay PCM inventory contains only English: “How are you?”, 1,520 ms. No labelled retained PCM exists for HI, TA, TE, BN, GU, MR, KN, ML, or OR, and this phase does not request new recording or synthesized speech.

| Language | Audio | AUTO median | AUTO RTF | FAST median | FAST RTF | Gain | Accuracy |
|---|---:|---:|---:|---:|---:|---:|---|
| EN | 1520 ms | 6462 ms | 4.25 | 3102 ms (explicit EN benchmark) | 2.04 | 52.0% | “How are you?” correct in both modes |
| HI | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| TA | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| TE | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| BN | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| GU | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| MR | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| KN | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| ML | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |
| OR | NOT AVAILABLE | — | — | — | — | — | No retained labelled PCM |

Classification: **LIMITED EVIDENCE**. The English result proves the existing explicit Whisper path is materially faster on one Device-A utterance. It does not establish all-language FAST accuracy, wrong-language behavior, or a production default.

## Debug-only FAST product experiment

The Debug Communicate screen exposes two experiment controls:

- **⚡ MY LANGUAGE / FAST**: sends the locally persisted `UserLanguageProfile.sttLanguage` to Whisper explicitly.
- **🌐 AUTO DETECT**: restores the existing selected STT mode, normally `AUTO`.

The ViewModel computes the requested language only for Debug builds. Release remains governed by the selected STT mode and its fresh default remains AUTO. The native JNI `inferenceConfig` log includes `language=<code>` and is the required proof for a future physical FAST test; no new physical FAST request was made in this phase because no retained PCM remains after APK installation.

## Routing contract

```text
AUTO: Whisper detected language -> SttResult.language -> Message.language -> PacketV2 -> receiver language route
FAST: preferred explicit language -> SttResult.language -> Message.language -> PacketV2 -> receiver language route
```

AUTO keeps valid detection authoritative. FAST is explicitly selected and reports `EXPLICIT_SELECTED`; it must not represent its selected language as Auto-LID. Neither path introduces an English fallback.

## Wrong-language safety

NOT AVAILABLE: no retained labelled Tamil PCM exists, so Tamil FAST versus Hindi FAST was not run. The implementation does not inspect transcript text or auto-switch language; FAST passes precisely the user-selected preferred language to Whisper. A future test must record the actual requested JNI code, transcript, result language, and failure/mismatch behavior.

## Latency breakdown

Replay evidence measures Whisper processing only. Capture-stop and PTT-release-to-result timings are **NOT MEASURED** for FAST in this phase. No sub-second claim is made.

## Recommendation

Keep AUTO as production default. Preserve the Debug FAST experiment for controlled same-PCM multilingual validation. Production FAST is not justified until each target language has labelled same-PCM AUTO/FAST accuracy and wrong-language evidence.
