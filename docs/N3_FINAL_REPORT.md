# N3 — Offline Code-Switching

## 1. Existing architecture audited
**PASS**

Retained the existing offline path:
`SenseVoice/Whisper transcript → CodeSwitchAnalyzer → CodeSwitchTranslationCoordinator → CT2/NLLB TranslationEngine → ReceiverPresentation → Sherpa-ONNX TTS handoff`.

Audited `CodeSwitchAnalyzer`, `CodeSwitchTranslationCoordinator`, `ReceiverTranslationCoordinator`, CT2 mutex protection, receiver presentation cache, TranslationCard UI, and versioned offline corpus replay.

## 2. CodeSwitchAnalyzer changes
**PASS**

- Kept the conservative script-first design.
- Expanded the deliberately small contextual English vocabulary for Indic-script utterances: communication/device (`phone`, `message`, `call`, `device`, `battery`, `charger`, `signal`, `network`), emergency/medical (`emergency`, `assistance`, `oxygen`, `ambulance`, `hospital`, `medical`, `rescue`, `team`), and existing common terms (`help`, `water`, `location`).
- This is not general English detection and does not identify arbitrary Romanized Indian languages.

Primary outcome: Hindi/Tamil utterances containing these English terms retain Hindi/Tamil source spans and only classify the supported contextual English token/phrase as English.

## 3. Coordinator changes
**PASS**

`ReceiverTranslationCoordinator.presentOnce()` is now guarded by a receiver-local `Mutex`. Concurrent arrivals cannot race cache publication or translate/enqueue the same `(messageId, targetLanguage)` presentation twice. This complements the existing CT2 mutex; translation engine/model behavior was not changed.

## 4. STT integration behavior
**PARTIAL**

No STT model or SenseVoice behavior was changed. N3 operates on the transcript produced by the existing STT pipeline. Deterministic text corpus tests separate script/span routing from ASR quality. Mixed-speech SenseVoice behavior requires two-device/voice physical validation and is not claimed.

## 5. Translation behavior
**PASS**

High/medium-confidence Hindi/Tamil/English spans use direct source-to-receiver-target CT2 calls. No English pivot is introduced. Low-confidence Latin and unknown-script spans use the configured source language and existing whole-message path. Segment failures preserve original span text; all-segment failure takes the existing whole-message fallback.

## 6. TTS behavior
**PASS**

No TTS engine/model change. One final merged `ReceiverPresentation` produces one target-language `ReceiverTtsHandoff`; per-span TTS is not used. Duplicate packet/retry cache hits do not create another presentation handoff.

## 7. Frontend changes
**PASS**

Existing `TranslationCard` shows only subtle production-safe text such as `Mixed language normalized`, `Partially translated`, or `Using fallback translation`. No span IDs, language diagnostics, or dashboard UI is exposed.

## 8. Supported language combinations
**PASS — bounded scope**

Primary validated routing combinations:
- Hindi + contextual English → EN/HI/TA receiver target
- Tamil + contextual English → EN/HI/TA receiver target
- English + Tamil native-script span → configured target

Hindi + Tamil + English remains architecturally handled by script spans but is not a general multilingual-capability claim. Gujarati and other languages were not added or claimed for complete N3 pipeline support.

## 9. Fallback behavior
**PASS**

- Latin-only `mujhe help chahiye` / `enakku help venum`: configured Hindi/Tamil, LOW confidence, whole-message translation.
- Unknown scripts: configured language, LOW confidence.
- Non-allowlisted Latin in Indic text: configured language, LOW confidence.
- Excessive span count: bounded whole-message fallback.

## 10. Test corpus
**PASS**

`model-lab/code-switch/corpus/v1.jsonl` expanded from 22 to 38 deterministic transcript items. It includes Hindi/Tamil/English single language, Hindi+English, Tamil+English, phone/emergency/assistance terms, repeated switching, punctuation, numbers, long inputs, Romanized Hindi/Tamil, unknown scripts, and bounded three-language architectural cases. It is a conformance corpus, not an ASR or translation-accuracy benchmark.

## 11. Tests added
**PASS**

- `CodeSwitchSessionIsolationTest`: two peer-like mixed presentations retain independent source/TTS targets; concurrent duplicate delivery produces one new presentation; stable N2 retry identity reuses presentation/TTS handoff.
- Expanded analyzer tests for Hindi `phone` and Tamil `emergency assistance` context.
- Corpus replay updated for 38 items and primary Hindi+English/Tamil+English cases.

## 12. Total test result
**PASS**

`./gradlew :app:testDebugUnitTest --no-daemon` → **395 passed, 0 failed**.

## 13. Build result
**PASS**

`./gradlew :app:assembleDebug --no-daemon` succeeded.

APK SHA-256: `7e67716ff4b1e8d10ca48e358419b0b1e7a023237c3cafe69ae987c7c4e87c7f`

## 14. Physical validation result
**PARTIAL**

No mixed-speech two-phone validation was performed: only one device is attached and prior thermal status was elevated. No claim is made about natural mixed-speech ASR accuracy, multilingual pronunciation quality, or arbitrary-language detection.

## 15. Known limitations
- Latin-only text remains fundamentally ambiguous; configured language wins.
- English contextual vocabulary is intentionally small and not a general LID dictionary.
- Devanagari is treated as Hindi in the currently integrated EN/HI/TA route; it is not Marathi identification.
- Corpus replay validates deterministic routing only, not SenseVoice recognition or CT2 semantic quality.

## 16. Files changed
- `app/src/main/java/com/vokie/translation/CodeSwitchAnalyzer.kt`
- `app/src/main/java/com/vokie/translation/ReceiverTranslationCoordinator.kt`
- `app/src/test/java/com/vokie/translation/CodeSwitchAnalyzerTest.kt`
- `app/src/test/java/com/vokie/translation/CodeSwitchCorpusReplayTest.kt`
- `app/src/test/java/com/vokie/translation/CodeSwitchSessionIsolationTest.kt`
- `model-lab/code-switch/corpus/v1.jsonl`
- `docs/N3_FINAL_REPORT.md`

## 17. Git commit hash
**PASS** — implementation commit `c5aee21`
