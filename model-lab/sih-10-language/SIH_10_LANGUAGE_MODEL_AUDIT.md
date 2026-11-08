# SIH 10-Language Capability and Model Readiness Audit

**Scope:** research/audit only. No model was downloaded, replaced, benchmarked, or integrated. Candidate availability is not Vokie validation.

## 1. SIH interpretation

SIH requires all ten languages to work locally on low/mid-range Android, including sentence endpointing, local STT, Bluetooth/Wi-Fi text transfer, intelligible local TTS, PTT/phone-like operation, and measurable accuracy/efficiency/latency. EN/HI/TA prototype paths do **not** satisfy the ten-language requirement.

## 2. Evidence methodology

- **Published evidence:** Whisper (Radford et al., 2022), NLLB (Costa-jussà et al., 2022), official whisper.cpp/sherpa-onnx/Vosk/Piper project documentation, and exact model cards/licenses where available.
- **Vokie evidence:** source audit and existing project records only.
- **Engineering inference:** ARM64/model suitability is not claimed until exact artifacts run offline on target phones.
- **Proposed validation:** immutable artifact, license, offline ARM64, WER/intelligibility, PSS, CPU, RTF, and two-phone timing gates.

## 3. Ten-language matrix

The machine-readable per-language matrix is `sih_10_language_capability_matrix.json`.

| Language | Script / NLLB tag | Current STT | Current TTS | Key blocker |
|---|---|---|---|---|
| HI | Devanagari / `hin_Deva` | explicit Whisper implemented | artifact gated | HI/MR script overlap |
| GU | Gujarati / `guj_Gujr` | Whisper candidate, not integrated | no selected artifact | all validation missing |
| MR | Devanagari / `mar_Deva` | Whisper candidate, not integrated | no selected artifact | cannot identify by Devanagari alone |
| KN | Kannada / `kan_Knda` | Whisper candidate, not integrated | no selected artifact | validation missing |
| ML | Malayalam / `mal_Mlym` | Whisper candidate, not integrated | no selected artifact | validation missing |
| TA | Tamil / `tam_Taml` | explicit Whisper implemented | artifact gated | approved TTS artifact |
| TE | Telugu / `tel_Telu` | Whisper candidate, not integrated | no selected artifact | validation missing |
| OR | Odia / `ory_Orya` | Whisper candidate, not integrated | no selected artifact | validation missing |
| BN | Bengali / `ben_Beng` | Whisper candidate, not integrated | no selected artifact | validation missing |
| EN | Latin / `eng_Latn` | explicit Whisper implemented | artifact gated | Latin ambiguity/TTS artifact |

Status terminology: **implemented** means a source path exists; **available/not integrated** means upstream candidate coverage only; **blocked** means a required artifact/right/runtime gate is absent.

## 4. STT comparison

| Family | Offline Android/ARM64 | Streaming | Ten-language readiness | License/status |
|---|---|---|---|---|
| Whisper multilingual + whisper.cpp | Current Vokie CPU JNI route | current Vokie is final-utterance, not partial streaming | upstream language codes cover the ten; Vokie enum/config/corpus/device evidence covers only EN/HI/TA | Whisper code MIT; exact model artifact record still required |
| sherpa-onnx ASR | official Android runtime | candidate/model dependent | no approved ten-language bundle identified | runtime Apache-2.0; model rights independent |
| Vosk/Kaldi | Android-capable | designed for streaming | Indic quality/coverage must be proven per model | code Apache-2.0; model rights independent |
| MMS ASR | candidate only | candidate dependent | broad research coverage is not deployment readiness | exact model/runtime/license gate required |

**Recommendation:** retain Whisper as the baseline, extend explicit-language evaluation one language at a time, and benchmark a streaming sherpa/Vosk candidate only after it passes provenance and Android gates. Do not replace Whisper based on desktop claims.

Current Whisper tiny Q5_1 is 32 MiB on disk with a code-declared approximate 273 MiB RAM estimate. It is final-utterance transcription, capped at 30 seconds; it is not evidence of low-end ten-language accuracy.

## 5. TTS comparison and license audit

| Family | Android route | Ten-language readiness | Rights conclusion |
|---|---|---|---|
| Current sherpa-onnx VITS route | implemented CPU/ARM64 engine | no approved complete ten-language artifact set | blocked pending exact artifact provenance/license |
| MMS-TTS | possible conversion/runtime investigation | broad language availability does not establish deployability | previously audited EN/HI/TA MMS cards are CC-BY-NC-4.0; unsuitable until intended use is approved |
| Piper | candidate dependent | no verified complete SIH-ten voice set | voice/model/dataset rights and Android export require review |
| VITS/Matcha | architecture, not a model choice | candidate dependent | exact weights, frontend, speaker, and redistribution rights required |

A public repository is not automatically usable. Record code license, weight license, dataset/voice license, commercial restriction, redistribution terms, revision, checksum, Android export path, and exact footprint separately. No current language is honestly “production-ready TTS” under the project’s approved-artifact gate.

## 6. Translation

NLLB-200 has direct tags for all ten listed in the JSON matrix. Its multilingual model supports direct source/target tag conditioning; English pivot is unnecessary in principle. Current Vokie CT2 JNI has only EN/HI/TA mappings, so the current app cannot call the other seven tags despite the model family supporting them. This audit does not change that mapping. Tokenizer/script behavior must be tested per pair.

## 7. Size, RAM, CPU, and accuracy

Keep these quantities separate:

- **STT model:** current Whisper tiny Q5_1: 32 MiB file; approximate code-declared RAM 273 MiB.
- **Translation model:** current CT2 NLLB `model.bin`: 619,704,329 bytes; prior debug-device CT2 PSS observations must not be generalized to whole-app ten-language operation.
- **TTS:** no approved artifact means no defensible final size/RAM/RTF figure.
- **APK/native runtime:** must be measured separately from model files.

Published WER/CER and MOS/intelligibility values are benchmark-, language-, model-size-, and dataset-specific. No Vokie ten-language WER, TTS intelligibility, low-end CPU, idle CPU, or Android RTF result exists. Rescue vocabulary, noise, code-switching, and conversational speech are domain mismatches requiring a consented evaluation corpus.

## 8. Latency measurement plan

Record monotonic timestamps:

`T0` speech begins, `T1` endpoint, `T2` final STT, `T3` packet created, `T4` packet received, `T5` translation complete, `T6` TTS synthesis start, `T7` audio ready, `T8` playback start.

Derived metrics: STT `T2-T0`; transport `T4-T3`; translation `T5-T4`; TTS processing `T7-T6`; end-to-end `T8-T0`; TTS RTF = processing time/audio duration. Current logs expose parts of STT/translation/TTS timing, but do not yet provide a complete synchronized two-phone timeline.

## 9. Current Vokie pause / sentence gap

Implemented: 16 kHz mono recording, 100 ms frames, energy VAD, 200 ms minimum speech, 750 ms final silence, maximum 30-second capture, final Whisper transcription, PTT release finalization.

Missing/not validated: linguistic sentence segmentation, streaming partial transcription, multiple sentence emission in a capture, endpoint accuracy under rescue noise, and complete endpoint-to-receiver-audio latency measurement.

## 10. Code-switching gap

Current analyzer is explicitly EN/HI/TA only. Tamil and Devanagari script rules cannot scale directly: Hindi and Marathi share Devanagari; Latin cannot distinguish English from Romanized Indic; script identity is not acoustic language identification. Do not add ten-language labels without a language design, corpus, and validation plan. A separate LID model is not justified before this evidence exists.

## 11. Walkie-talkie and alert gap

| SIH need | Current status |
|---|---|
| PTT recording | implemented UI/STT finalization path |
| Wi-Fi Direct/Bluetooth text transport | implemented architecture; two-phone validation remains limited/blocked by hardware availability |
| continuous phone-like STT mode | partial: VAD finalization exists, continuous turn loop/streamed partials not established |
| embedded-device protocol | not implemented/validated |
| voice-note playback | architecture implemented; artifact-gated actual TTS |
| SOS priority | queue prioritizes SOS and playback uses alarm usage/repetition |
| highest volume/non-interruptible | partial only: app gain is 1.0 and exclusive/alarm focus is requested; Android/user volume/focus policy cannot guarantee maximum volume or non-interruption |

## 12. Low/mid-range strategy

1. Keep one quantized multilingual STT baseline initially to avoid ten simultaneous STT packages.
2. Load only the selected language’s TTS voice; never preload all ten.
3. Require a per-language quality gate before claiming coverage.
4. If Whisper tiny misses quality targets, compare language-specific or streaming candidates against the same device/corpus, not against desktop results.
5. Select TTS per language only after legal/runtime review; a single multilingual voice is attractive only if it meets all ten language and intelligibility gates.

Accuracy is 40% of SIH weighting; do not select merely by smallest file. TTS artifact readiness and ten-language STT evidence are the highest blockers, followed by end-to-end two-phone timing.

## 13. Vokie versus SIH gap matrix

| Requirement | Current status | Priority | Next phase |
|---|---|---|---|
| 10-language STT | EN/HI/TA code path only | Critical | explicit-language artifact/device audit |
| 10-language TTS | no approved ten-language set | Critical | artifact/provenance selection |
| NLLB ten-language mapping | model tags exist; JNI maps 3 | High | separate approved mapping phase |
| pause endpointing | energy VAD present | High | noisy endpoint evaluation |
| sentence streaming | final utterance only | High | streaming/turn-loop design |
| STT WER | not measured | Critical | consented corpus + device test |
| TTS intelligibility/RTF | not measured | Critical | approved voices + human/device test |
| Wi-Fi/Bluetooth | implemented architecture | Medium | physical two-phone protocol |
| PTT | present | Medium | physical loop validation |
| phone-like continuous mode | partial | High | turn-loop design |
| alert audio | partial | High | Android-safe alert policy test |
| low/mid-range RAM/CPU | not measured across full stack | Critical | PSS/CPU/thermal plan |
| offline/open source | architecture supports it | Maintain | artifact license gate |

## 14. Recommended next step

**SIH-L9-P1: 10-language artifact and evidence gate.** For each language, create an immutable candidate record before code integration: exact STT/TTS artifact/revision/checksum/license; Android ARM64 runtime path; model/APK/RAM estimate; public benchmark citation; consented test-script plan; and accept/reject decision. Start with a TTS artifact gate because it is the clearest current ten-language blocker.
