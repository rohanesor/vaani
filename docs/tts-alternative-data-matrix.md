# Alternative multilingual TTS training-data matrix

`✓` is explicit official coverage; `?` is a language registration or public claim without a verified downloadable corpus release/manifest in this phase; `✗` is not covered. No row is approved for trained-weight distribution merely because data is publicly downloadable.

| Dataset / official source | HI | GU | MR | KN | ML | TA | TE | OR | BN | EN | License | Redistribution / trained weights | Hours / speakers | Quality / TTS suitability | Status |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---|---|---|---|---|
| IndicVoices-R | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | CC-BY-4.0 declared | Conditional; full data-card/derivative review pending | 1,704h / 10,496 aggregate; per-language unknown | Designed/enhanced for TTS; per-language audit blocked | **USER ACTION REQUIRED** |
| Mozilla Common Voice language registry | ? | ? | ? | ? | ? | ? | ? | ? | ? | ? | Current exact corpus release/license metadata not acquired | Unknown for current downloadable release/weights | Unknown | Crowdsourced ASR-style speech; TTS quality unknown | Blocked metadata/access review |
| OpenSLR 78 Gujarati | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | CC-BY-SA-4.0 | Commercial reuse possible under ShareAlike; trained-weight treatment requires review | Unknown | high-quality multi-speaker TTS corpus | Conditional |
| OpenSLR 79 Kannada | ✗ | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | CC-BY-SA-4.0 | Same | Unknown | high-quality multi-speaker TTS corpus | Conditional |
| OpenSLR 63 Malayalam | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | CC-BY-SA-4.0 | Same | Unknown | high-quality multi-speaker TTS corpus | Conditional |
| OpenSLR 64 Marathi | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | CC-BY-SA-4.0 | Same | Unknown | high-quality multi-speaker TTS corpus | Conditional |
| OpenSLR 65 Tamil | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | ✗ | CC-BY-SA-4.0 | Same | Unknown | high-quality multi-speaker TTS corpus | Conditional |
| OpenSLR 66 Telugu | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | ✗ | ✗ | CC-BY-SA-4.0 | Same | Unknown | high-quality multi-speaker TTS corpus | Conditional |
| OpenSLR 37 Bengali | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | CC-BY-SA-4.0 | Same | archive sizes published; hours/speakers unknown | high-quality multi-speaker TTS corpus | Conditional |
| AI4Bharat Rasa | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | ✗ | ✗ | ✓ | ✗ | CC-BY-4.0 | Conditional trained-weight review | 10h neutral + 1–3h per emotion for each of 3 languages; speaker count unknown | expressive TTS supplement | Conditional |
| LibriTTS SLR60 | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | CC-BY-4.0 | Conditional trained-weight review | ~585h / speakers unknown | read English TTS corpus | Conditional |
| AI4Bharat IndicVoices | ? | ? | ? | ? | ? | ? | ? | ? | ? | ✗ | License not identified in official repository | Unknown | 12,000h total / 3,200h transcribed; per-language unknown | ASR/conversational mix, not validated for TTS | Blocked license/access |

## Finding

No alternative legally cleared corpus combination currently proves all nine Indic languages. The OpenSLR/Rasa combination is a potentially useful **supplement** for Gujarati, Kannada, Malayalam, Marathi, Tamil, Telugu, and Bengali, but leaves Hindi and Odia without a verified permissible TTS corpus and introduces CC-BY-SA derivative-model review. LibriTTS remains the clear English candidate.
