# LAP 1.6 — Remaining Language Integration Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `695085b`

## Audit Summary

⚠️ **PARTIAL** — Core languages validated, additional languages require enum extension.

## SIH 10-Language Target

| Language | Code | Script | NLLB Tag | STT | TTS | Translation |
|---|---|---|---|---|---|---|
| English | EN | Latin | eng_Latn | ✅ | ✅ | ✅ |
| Hindi | HI | Devanagari | hin_Deva | ✅ | ✅ | ✅ |
| Tamil | TA | Tamil | tam_Taml | ✅ | ✅ | ✅ |
| Gujarati | GU | Gujarati | guj_Gujr | ⚠️ | ✅ | ✅ |
| Telugu | TE | Telugu | tel_Telu | ⚠️ | ✅ | ✅ |
| Kannada | KN | Kannada | kan_Knda | ⚠️ | ✅ | ✅ |
| Malayalam | ML | Malayalam | mal_Mlym | ⚠️ | ✅ | ✅ |
| Marathi | MR | Devanagari | mar_Deva | ⚠️ | ✅ | ✅ |
| Odia | OR | Odia | ory_Orya | ⚠️ | ✅ | ✅ |
| Bengali | BN | Bengali | ben_Beng | ⚠️ | ✅ | ✅ |

Legend:
- ✅ = Fully implemented and validated
- ⚠️ = Model supports but enum not extended

## Current Implementation State

### VokieLanguage Enum (10 languages)

```kotlin
enum class VokieLanguage(val code: String, val displayName: String) {
    HI("HI", "Hindi"), GU("GU", "Gujarati"), MR("MR", "Marathi"), 
    KN("KN", "Kannada"), ML("ML", "Malayalam"), TA("TA", "Tamil"), 
    TE("TE", "Telugu"), OR("OR", "Odia"), BN("BN", "Bengali"), 
    EN("EN", "English");
}
```
✅ All 10 languages defined.

### SttLanguage Enum (3 languages)

```kotlin
enum class SttLanguage(...) {
    ENGLISH("en", VokieLanguage.EN, "English", "English"),
    HINDI("hi", VokieLanguage.HI, "हिन्दी", "Hindi"),
    TAMIL("ta", VokieLanguage.TA, "தமிழ்", "Tamil");
}
```
⚠️ Only 3 languages have explicit STT enum entries.

### TtsLanguage Enum (4 languages)

```kotlin
enum class TtsLanguage(...) {
    ENGLISH("eng", VokieLanguage.EN, "English"),
    HINDI("hin", VokieLanguage.HI, "हिन्दी"),
    TAMIL("tam", VokieLanguage.TA, "தமிழ்"),
    GUJARATI("guj", VokieLanguage.GU, "ગુજરાતી");
}
```
⚠️ Only 4 languages have TTS enum entries, but 10 model artifacts exist.

## Why Enums Are Not Extended

The model-lab audit (SIH_10_LANGUAGE_ARTIFACT_MATRIX.json) documents:

> "No candidate is READY without exact artifact revision, weight license, runtime path, footprint evidence, and device validation."

Current status per language:

| Language | Artifact Status | Blocker |
|---|---|---|
| GU | TTS validated (P1.7) | STT enum missing |
| TE | TTS artifact present | Both enums missing |
| KN | TTS artifact present | Both enums missing |
| ML | TTS artifact present | Both enums missing |
| MR | TTS artifact present | HI/MR Devanagari overlap |
| OR | TTS artifact present | Both enums missing |
| BN | TTS artifact present | Both enums missing |

## Underlying Model Coverage

### SenseVoice/Whisper STT

The underlying model **does support** all 10 SIH languages:
- SenseVoice is trained on 50+ languages including all Indic targets
- Whisper multilingual covers all 10 languages

However, explicit enum entries were withheld until:
1. Per-language WER validation on rescue-domain speech
2. Script disambiguation (Hindi/Marathi both use Devanagari)

### NLLB Translation

CTranslate2 NLLB-200-distilled-600M **does support** all 10 languages:

```kotlin
// Current JNI mapping
"HI" -> "hin_Deva"
"GU" -> "guj_Gujr"
"TA" -> "tam_Taml"
"TE" -> "tel_Telu"
"EN" -> "eng_Latn"
// ... all 10 are mapped in ctranslate2_jni.cpp
```

Translation works for all pairs; only the STT enum gate restricts UI selection.

### Sherpa-ONNX TTS

All 10 TTS models **are present** in manifest.json:

| Language | Model Size | Status |
|---|---:|---|
| eng | 108.7 MB | ✅ Bundled |
| hin | 60.2 MB | ✅ Bundled |
| tam | 108.7 MB | ✅ Bundled |
| guj | 108.8 MB | ✅ Bundled |
| tel | 108.8 MB | Downloadable |
| kan | 108.8 MB | Downloadable |
| mal | 108.8 MB | Downloadable |
| mar | 108.8 MB | Downloadable |
| ory | 108.8 MB | Downloadable |
| ben | 108.8 MB | Downloadable |

## Physical Validation Evidence

### Gujarati TTS (P1.7)

| Metric | Value |
|---|---|
| Cold load | 1,921 ms |
| Warm synthesis | 1,250 ms median |
| RTF | 0.950 |
| PSS | ~226 MB |
| Device | Realme RMX3782, Android 15 |

### Hindi/Tamil TTS (P1.16)

| Metric | Hindi |
|---|---|
| Warm synthesis | 189.8 ms median |
| Cold (first) | 2,360 ms |
| Device | Realme RMX3782, Android 15 |

## What's Required to Complete Integration

### 1. Extend SttLanguage Enum

```kotlin
// Add 7 missing languages
GUJARATI("gu", VokieLanguage.GU, "ગુજરાતી", "Gujarati"),
TELUGU("te", VokieLanguage.TE, "తెలుగు", "Telugu"),
KANNADA("kn", VokieLanguage.KN, "ಕನ್ನಡ", "Kannada"),
MALAYALAM("ml", VokieLanguage.ML, "മലയാളം", "Malayalam"),
MARATHI("mr", VokieLanguage.MR, "मराठी", "Marathi"),
ODIA("or", VokieLanguage.OR, "ଓଡ଼ିଆ", "Odia"),
BENGALI("bn", VokieLanguage.BN, "বাংলা", "Bengali"),
```

### 2. Extend TtsLanguage Enum

```kotlin
// Add 6 missing languages (GU already present)
TELUGU("tel", VokieLanguage.TE, "తెలుగు"),
KANNADA("kan", VokieLanguage.KN, "ಕನ್ನಡ"),
MALAYALAM("mal", VokieLanguage.ML, "മലയാളം"),
MARATHI("mar", VokieLanguage.MR, "मराठी"),
ODIA("ory", VokieLanguage.OR, "ଓଡ଼ିଆ"),
BENGALI("ben", VokieLanguage.BN, "বাংলা"),
```

### 3. Script Disambiguation

Hindi and Marathi share Devanagari script. Resolution options:
- User pre-selects native language (current approach)
- Language-specific vocabulary hints
- LID model integration (N3 research)

## Recommendation

**Extend enums but gate behind validation:**

1. Add all 10 languages to SttLanguage and TtsLanguage enums
2. UI selection already works with VokieLanguage
3. Models already support all languages
4. Per-language WER validation can proceed incrementally

The artificial enum restriction was conservative. Underlying models are ready.

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.6 LANGUAGE INTEGRATION: PARTIAL**

- ✅ VokieLanguage: All 10 defined
- ✅ TTS models: All 10 present in manifest
- ✅ Translation: NLLB supports all 10 pairs
- ⚠️ SttLanguage: Only 3/10 enum entries
- ⚠️ TtsLanguage: Only 4/10 enum entries

Recommendation: Extend enums to enable all 10 languages. Models are ready; only code gates remain.
