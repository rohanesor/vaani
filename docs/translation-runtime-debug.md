# Translation runtime debug

**Status: TRANSLATION_RUNTIME_BLOCKED**

This is a real-runtime audit. No demo engine, hardcoded translation, cloud service, or fallback was used.

## 1. UI failure location and complete path

```text
Inbound PacketV2 message
→ InboundPacketCoordinator.acceptDecoded()
→ RoomMessageRepository.persistIncoming()
→ InboundPacketCoordinator.messages
→ VokieApplication inbound collector
→ ReceiverTranslationCoordinator.presentOnce()
→ Ctranslate2TranslationEngine.translate()
→ TranslationResult(UNAVAILABLE)
→ ReceiverPresentationState.TRANSLATION_UNAVAILABLE
→ TranslationCard()
→ "Translation unavailable offline"
```

| Item | Location | Finding |
|---|---|---|
| UI string | `app/src/main/java/com/vokie/ui/components/TranslationCard.kt` | The literal is rendered only for `TRANSLATION_UNAVAILABLE` or `TRANSLATION_FAILED` (lines 96–102). |
| State creation | `app/src/main/java/com/vokie/translation/ReceiverTranslationCoordinator.kt` | `presentOnce()` maps `TranslationStatus.UNAVAILABLE` to `TRANSLATION_UNAVAILABLE` (line 41). |
| Actual unavailable result | `app/src/main/java/com/vokie/translation/Ctranslate2TranslationEngine.kt` | `translate()` returns `UNAVAILABLE`, error `Approved local CT2 model is not staged.`, when model size or one of three companion files is absent. |

The displayed phrase is therefore a real model-availability result, not a UI-only placeholder.

## 2. Production runtime wiring

**TRANSLATION_RUNTIME = `Ctranslate2TranslationEngine`**.

`VokieApplication.onCreate()` constructs:

```kotlin
receiverTranslation = ReceiverTranslationCoordinator(
    Ctranslate2TranslationEngine(applicationContext)
)
```

`UnavailableTranslationEngine` and `EmergencyPhraseDemoTranslationEngine` exist only as non-production/test code. Neither is instantiated in the production application graph. No ONNX translation, IndicTrans, M2M100, network, Google, or cloud translation code is wired to this path.

The real runtime is CTranslate2 4.8.2 through `Ctranslate2Native` and `libvokie_ct2.so`. Native code (`app/src/main/cpp/vokie_ct2_jni.cpp`) loads SentencePiece, encodes `[source NLLB token] + pieces + </s>`, invokes CT2 CPU translation with a direct target prefix, and decodes locally. It maps `EN → eng_Latn`, `HI → hin_Deva`, and `TA → tam_Taml`; it does not use AUTO-LID or an English pivot.

## 3. APK inventory

Inspected artifact: `app/build/outputs/apk/debug/app-debug.apk`, SHA-256 `97aae6ecfc52c15d4e20f25a2c0d71c62913bfb0462a99541bf6742e192efc18` (inspection performed before diagnostic-only source edits).

| APK artifact | Present | Size | SHA-256 |
|---|---:|---:|---|
| NLLB/CT2 model | No | — | — |
| NLLB SentencePiece/tokenizer | No | — | — |
| NLLB config/vocabulary | No | — | — |
| `lib/arm64-v8a/libvokie_ct2.so` | Yes | 3,920,672 | `34e1b0c1a8dce4941fbe61a0086d85959bfd57bb3a3435efa8d9f6bf74c082d6` |
| `lib/arm64-v8a/libonnxruntime.so` | Yes, TTS dependency only | 21,684,872 | `892bde5701ea47edffb3f1cc070f5bab690fccfca40e11baaed7b252084af477` |

`assets/models/manifest.json` has STT/TTS entries only. `BundledModelStore.BUNDLED_FILES` likewise extracts only STT/TTS. Thus there is no APK translation-asset extraction or APK-side CT2 checksum path by design; the approved CT2 model is a separately staged app-private artifact.

## 4. Actual receiver evidence and root cause

The physical message `31a70aea-aa1b-4a69-a3c8-16e8481ca8b5` was created from a valid RMX3782 Whisper result and persisted/ACKed on I2221 at `09-02 14:03:09`:

```text
I2221: MESSAGE_REASSEMBLED …
I2221: Message persisted …
I2221: MESSAGE_RECEIVED …
```

I2221's DataStore profile is complete and decodes to:

```text
preferred_input_language=TA
preferred_output_language=TA
```

The incoming packet was English, so the actual request is **EN → TA**. I2221 initially had no `files/ct2/nllb600m/` directory. This exactly satisfies the unavailable condition in `Ctranslate2TranslationEngine.translate()`.

RMX3782 did have the complete CT2 directory; I2221 did not. Therefore the exact root cause was:

**K — receiver-local approved CT2 model missing on the actual receiver (I2221).**

It was not a source-language mismatch, unavailable/demo-engine wiring, tokenizer routing failure, or failed inference.

## 5. Approved artifact staging and checksum

The exact approved model was staged to the receiver's production runtime path, `I2221/files/ct2/nllb600m/`, then checked on-device:

| File | Size (bytes) | SHA-256 |
|---|---:|---|
| `model.bin` | 619,704,329 | `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` |
| `config.json` | 223 | `8f6496adfc930cbfecbe8281112197705c488fab47d34b4829b06d7f478909af` |
| `shared_vocabulary.json` | 5,921,176 | `af53bfd0e6f726209e7325e45b87ab3b14e5856f7d42d7b9be91de3287c45267` |
| `sentencepiece.bpe.model` | 4,852,054 | `14bb8dfb35c0ffdea7bc01e56cea38b9e3d5efcdcb9c251d6b40538e1aab555a` |

Provenance: `osa911/nllb-200-distilled-600M-ct2-int8`, revision `46858753dbaf8eb5e21bb6f0037c3b90851e090a`; see `docs/ct2-reproduction-report.md`. The artifact is CC-BY-NC-4.0 and is acceptable only under this non-commercial prototype constraint.

## 6. Tokenizer, routing, and inference evidence

**TOKENIZER = PASS** for the approved CT2 native path. The tokenizer is SentencePiece 0.2.1 and is loaded in `nativeLoadModel`; native source explicitly handles special source/target NLLB tokens and EOS.

Previously recorded physical RMX3782 debug validation proves real offline CT2 inference, including direct HI↔TA (not English pivot):

| Direction | Input | Output | Latency |
|---|---|---|---:|
| EN→HI | Help me. | मेरी मदद करो. | 1796 ms |
| EN→TA | Help me. | எனக்கு உதவுங்கள். | 2029 ms |
| HI→EN | मुझे मदद चाहिए। | I need help. | 2019 ms |
| HI→TA | मुझे मदद चाहिए। | எனக்கு உதவி தேவை. | 1955 ms |
| TA→EN | எனக்கு உதவி தேவை. | I need help. | 2041 ms |
| TA→HI | எனக்கு உதவி தேவை. | मुझे मदद की जरूरत है. | 1708 ms |

The coordinator unit test now covers same-language EN→EN, HI→HI, and TA→TA bypass semantics; source equals target returns the original text without calling the engine. The previous physical CT2 test also verified those bypasses.

## 7. Memory and offline evidence

On RMX3782, previously measured CT2 process PSS was 177,896 KB before load, 860,077 KB after load, and 1,115,245 KB peak during inference. The CT2 Android validation disabled Wi-Fi and mobile data and completed all six directions locally.

No I2221 CT2 memory/inference measurement is claimed yet. A debug smoke activity was tried after staging but it incorrectly looks in the obsolete `files/models/ct2/nllb600m` path; production uses the correct `files/ct2/nllb600m` path. That debug-only path mismatch must not be used to judge the production runtime.

## 8. Diagnostic logging added

Diagnostic-only `VOKIE][TRANSLATION` logs were added around the production request/result and real CT2 engine:

```text
TRANSLATION_RECEIVER_REQUEST source=EN target=TA
TRANSLATION_REQUEST source=EN target=TA
TRANSLATION_ASSET_FOUND | TRANSLATION_ASSET_MISSING
TRANSLATION_RUNTIME_INIT | TRANSLATION_RUNTIME_READY
TRANSLATION_INFER_START | TRANSLATION_INFER_SUCCESS | TRANSLATION_INFER_FAILURE
TRANSLATION_RECEIVER_RESULT
```

No message text is logged by these additions.

## 9. Remaining physical A→B verification

The immediately preceding UI state on RMX3782 showed `Connection failed`; a new post-staging Bluetooth physical message was therefore not sent during this audit. The requested live A→B result and fresh I2221 RAM measure remain blocked on restoring the already-existing Bluetooth connection and installing a build that contains the diagnostic-only logs. The staging blocker itself has been removed and verified by on-device checksums.

## 10. Recommended fix and safety

1. Make CT2 staging a supported, verified deployment step for **each receiving device**, retaining the current production path `files/ct2/nllb600m` and all four checksums.
2. Correct `Ct2SmokeActivity`'s obsolete debug-only path before using it for I2221 validation; do not change production's path.
3. Reconnect Bluetooth, send a newly created EN message from RMX3782 to I2221 (target TA), and capture the new production diagnostic sequence plus PSS.
4. Do not package the 630 MB model in the APK, and do not substitute any unapproved model.

Integration is safe for the non-commercial prototype only after the fresh I2221 inference/RAM and physical A→B check succeed. The minimal legitimate correction is receiver model staging, not a translation-engine replacement.
