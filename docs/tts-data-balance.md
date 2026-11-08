# Training-data balance and split plan

## Current result

Phase 2G verified that the Zenodo artifact is a repository snapshot only and that old object URLs return HTTP 403. Phase 2G authenticated-metadata preparation found the official HF card/file index: total utterance counts are now known, but duration, unique speakers, gender distribution, hours/speaker, average/median duration, dominant speakers, and single-speaker risks remain **UNKNOWN** until authenticated per-row metadata is queried.

| Language | Total utterances | Download bytes |
|---|---:|---:|
| Hindi | 26,694 | 47,225,888,374 |
| Gujarati | 1,791 | 2,889,359,875 |
| Marathi | 19,141 | 31,434,459,154 |
| Kannada | 17,328 | 27,604,791,540 |
| Malayalam | 31,503 | 52,034,095,360 |
| Tamil | 39,585 | 63,139,582,505 |
| Telugu | 47,547 | 86,122,150,914 |
| Odia | 25,565 | 44,466,622,775 |
| Bengali | 40,192 | 72,237,562,604 |

The utterance count range is 1,791 Gujarati to 47,547 Telugu (26.5×). This is severe known imbalance; do not choose language sampling weights until duration and speaker distribution are measured.

## Required calculation

After official manifests are acquired privately, run:

```text
scripts/tts-data/audit_manifests.py PRIVATE_MANIFEST.jsonl --output .research/tts-data/balance.json
scripts/tts-data/audit_transcripts.py PRIVATE_MANIFEST.jsonl --output .research/tts-data/transcripts.json
scripts/tts-data/audit_audio.py PRIVATE_AUDIO_ROOT --output .research/tts-data/audio.json
```

The private JSONL schema is documented in `scripts/tts-data/audit_manifests.py` and requires file, language, speaker, duration, sample rate, format, transcript, source, license, and checksum. The committed `data/manifests/` indexes contain only source metadata, never corpus audio or speaker records.

## Balance policy to evaluate, not yet apply

- Use language-balanced batches, with temperature sampling over per-language utterance counts; choose the temperature only after observed imbalance is reported.
- Cap or weight speakers by hours/utterances so a dominant speaker cannot define a language embedding.
- Preserve low-resource language examples through oversampling only in training; retain natural held-out distributions for evaluation.
- Keep language identity explicit in model inputs and prevent speaker IDs from crossing speaker-disjoint splits.
- Report gender categories exactly as published; do not infer gender.

## Train/validation/test split

Split **by speaker** where speaker IDs are available. Assign speakers deterministically using a recorded hash seed, stratified by language and available gender/category. No speaker may occur in more than one split. Target ratios are 90/5/5 only if every language has enough speakers; otherwise record a language-specific exception rather than leaking speakers. English LibriTTS must use its official split identity where available and still be checked for speaker overlap.

Raw data remains immutable. The reproducible pipeline is:

```text
raw archives → checksum/format validation → source manifest → NFC analysis
→ language normalizer (sidecar output) → audio/silence/duration reports
→ speaker filtering report → balance plan → speaker-disjoint manifests
```

No original transcript or audio is overwritten; every transformation is a versioned sidecar manifest with source record IDs.
