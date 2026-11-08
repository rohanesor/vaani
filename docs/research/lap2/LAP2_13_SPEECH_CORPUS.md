# LAP2.13 — iTantra Speech Test Corpus

## Status

**CORPUS_STATUS = PASS** for the 500-file minimum corpus.

This is **synthetic/generated test input**, not human speech, training data, or a redistributable TTS/model asset. It must not be copied into Android assets or the APK.

## Statistics

| Item | Result |
|---|---:|
| WAV files | 500 |
| Metadata records | 500 |
| Languages | 10 |
| Samples per language | 50 |
| Total duration | ~1,383.9 s / 23.1 min |
| Format | PCM S16LE WAV |
| Sample rate | 16,000 Hz |
| Channels | Mono |
| Duration range | 0.72–9.53 s |
| Audio validation | PASS: 500/500 |

| Language | Code | Samples | Voices |
|---|---|---:|---:|
| English | en | 50 | 2 |
| Hindi | hi | 50 | 2 |
| Tamil | ta | 50 | 2 |
| Gujarati | gu | 50 | 2 |
| Marathi | mr | 50 | 2 |
| Kannada | kn | 50 | 2 |
| Malayalam | ml | 50 | 2 |
| Telugu | te | 50 | 2 |
| Odia | or | 50 | 2* |
| Bengali | bn | 50 | 2 |

\*Odia audio was generated with Hindi voices using Hindi-script fallback text because the selected generator did not provide an Odia voice. It is therefore **not valid native-Odia speech evidence**.

## Categories

Each language has five samples in each category: short, question, normal, emergency, navigation, numbers, long, conversational, code-switch, and difficult.

## Generation Source

Audio was generated using Microsoft `edge-tts` neural voices and normalized with `ffmpeg`:

```bash
python3 corpus/generate_audio.py
ffmpeg -i input -ar 16000 -ac 1 -sample_fmt s16 output.wav
```

The generation step used a network TTS service. The resulting corpus is retained only as synthetic functional test input. No provider voice license is claimed or transferred to iTantra.

## Files

- `corpus/metadata.csv` — 500-row master metadata
- `corpus/text/*.txt` — source prompts
- `corpus/audio/<language>/*.wav` — generated test inputs (ignored by Git)
- `corpus/stress/stress_test.txt` — difficult prompt set (50 prompts, five per language)
- `corpus/tts_text_suite.csv` — 500 TTS prompts
- `corpus/validate_corpus.py` — local WAV/metadata validator
- `corpus/run_stt_corpus.py` — evaluates exported results from the actual Android STT harness

## Validation

Run:

```bash
python3 corpus/validate_corpus.py corpus
```

Observed result:

```text
CORPUS VALIDATION: PASS
total files: 500
metadata records: 500
languages: bn=50, en=50, gu=50, hi=50, kn=50, ml=50, mr=50, or=50, ta=50, te=50
```

The validator checks existence, readability, WAV parameters, non-empty/non-silent samples, metadata fields, duplicate IDs, and ten-language coverage.

## Android STT Evaluation

No Android transcripts, WER, CER, latency, translation, TTS, or E2E results are claimed by this corpus creation phase. The runner requires an export from an actual Android offline STT harness:

```bash
python3 corpus/run_stt_corpus.py \
  --metadata corpus/metadata.csv \
  --android-results reports/android_stt_results.csv \
  --output reports/stt_corpus_results.csv
```

The runner does not use desktop STT, cloud STT, or fabricated predictions.

## Limitations

1. Synthetic audio is not evidence of natural human speech quality.
2. Only two actual voices per language were used, below the preferred 5–10 voice target.
3. Odia samples are Hindi-voice fallback audio and must be reported separately.
4. The stress directory contains 50 prompts, not 10–20 audio samples per language.
5. Network generation provenance is recorded; offline corpus use does not imply offline generation.

## Production Safety

- No generated audio was added to `app/src/main/assets`.
- No model files or APKs were modified.
- Raw WAV files are excluded by `.gitignore`.
- Metadata, scripts, and documentation may be committed.
