# TTS training-data landscape and legal acquisition strategy

## Recommended legal data basis

| Dataset | Official source | License | Coverage | Verified scale | Alignment / quality | Commercial redistribution assessment | Use decision |
|---|---|---|---|---|---|---|---|
| AI4Bharat IndicVoices-R | https://github.com/AI4Bharat/IndicVoices-R; Zenodo record `11636050` | CC-BY-4.0 (repository and Zenodo metadata) | Bengali, Gujarati, Hindi, Kannada, Malayalam, Marathi, Odia, Tamil, Telugu among 22 languages; its official `data_links.txt` lists each | 1,704 hours, 10,496 speakers, 22 languages total | WAV plus manifest with normalized text, duration, language, speaker ID, QA markers, SNR/C50/CER | CC BY 4.0 permits commercial reuse/adaptation with attribution and change indication; confirm data-card/consent obligations before training | **Primary Indic training candidate** |
| LibriTTS | https://www.openslr.org/60/ | CC-BY-4.0 | English | approximately 585 hours at 24 kHz | sentence-split audio; original and normalized text; noise-filtered | CC BY 4.0 permits commercial reuse/adaptation with attribution and change indication | **Primary English training candidate** |
| OpenSLR 37 Bengali | https://www.openslr.org/37/ | CC-BY-SA-4.0 | Bengali | archive sizes 586 MB bn-BD / 416 MB bn-IN; hours/speakers not verified here | transcribed high-quality multi-speaker data | ShareAlike obligations require legal review for a trained distributable model | Optional evaluation only |
| OpenSLR 63 Malayalam | https://www.openslr.org/63/ | CC-BY-SA-4.0 | Malayalam | female/male archives 710 MB / 635 MB; hours/speakers not verified here | transcribed high-quality volunteer recordings | ShareAlike obligations require legal review for a trained distributable model | Optional evaluation only |
| OpenSLR 64 Marathi | https://www.openslr.org/64/ | CC-BY-SA-4.0 | Marathi | per-language hours/speakers not verified here | transcribed high-quality volunteer recordings | ShareAlike obligations require legal review for a trained distributable model | Optional evaluation only |

## Coverage matrix for the proposed legal-first corpus

| Language | IndicVoices-R | LibriTTS | Training data decision |
|---|---:|---:|---|
| Hindi | Yes | — | IndicVoices-R |
| Gujarati | Yes | — | IndicVoices-R |
| Marathi | Yes | — | IndicVoices-R |
| Kannada | Yes | — | IndicVoices-R |
| Malayalam | Yes | — | IndicVoices-R |
| Tamil | Yes | — | IndicVoices-R |
| Telugu | Yes | — | IndicVoices-R |
| Odia | Yes | — | IndicVoices-R |
| Bengali | Yes | — | IndicVoices-R |
| English | — | Yes | LibriTTS |

## Acquisition and preparation rules

Do not download complete corpora in this phase. First record each archive URL, retrieval date, byte size, SHA-256, data-card version, and attribution requirements in a private training manifest. Then validate archive safety, sample rate, transcript encoding, license files, speaker consent/data-card constraints, per-language hours, speaker distribution, text normalization, and train/dev/test split leakage.

All totals above are corpus-wide. **Per-language hours, speaker counts, sampling rate for IndicVoices-R, and pronunciation/domain coverage are UNKNOWN until the official manifests are acquired and audited.** They must not be guessed from archive sizes. Emergency vocabulary, names, numbers, and locations need an explicitly authored, native-reviewed evaluation set; they are not proven by generic corpus transcripts.

## Teacher/distillation legal rule

No third-party teacher with non-commercial, gated, or unclear output/derivative terms may create a shipping student. If distillation is used, train the teacher from the approved corpus above using reviewed open-source code, retain its complete reproducibility record, and treat the student as a derivative training output requiring attribution/notice review. Ground-truth supervised student training is the legal baseline; teacher distillation is optional only after counsel confirms the corpus-to-weights chain.
