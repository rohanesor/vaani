# Controlled IndicVoices-R / LibriTTS acquisition plan

## Status

**Planning only. No corpus audio has been acquired and training remains BLOCKED.**

Primary Indic source: `ai4bharat/indicvoices_r`, Hugging Face revision `5f4495c91d500742a58d1be2ab07d77f73c0acf8`.

English source: LibriTTS, official OpenSLR SLR60.

The IndicVoices-R HF Parquet representation contains an `audio` structure. Phase 2J proved that the current remote column-projection path transferred bytes overlapping audio chunks, so this plan does not use it for metadata-only acquisition. Acquisition requires an explicitly approved, bounded audio download after the legal and data gates pass.

## Candidate budgets

These are planning targets, not measured corpus availability. They cover nine Indic languages plus English.

| Target | Indic hours | English hours | Total hours | 22.05-kHz mono 16-bit PCM bound | 48-kHz mono 16-bit PCM bound | Use |
|---:|---:|---:|---:|---:|---:|---|
| 10 h/language | 90 | 10 | 100 | 15.876 GB | 34.560 GB | minimum feasibility experiment |
| 25 h/language | 225 | 25 | 250 | 39.690 GB | 86.400 GB | recommended initial target, conditional |
| 50 h/language | 450 | 50 | 500 | 79.380 GB | 172.800 GB | stronger student experiment |
| 100 h/language | 900 | 100 | 1,000 | 158.760 GB | 345.600 GB | larger run; not first acquisition |

Bounds are uncompressed PCM only (`hours × sample_rate × 2`); container, metadata, indexes, temporary files, validation copies, and any encoding savings are excluded. Reserve at least 2x the selected bound for safe staging until measured storage is available.

## Recommendation

Use **25 hours per language as a target**, with a **10-hour-per-language fallback** for the first controlled feasibility run. Do not promise that Gujarati can meet either target until the approved acquisition produces exact durations and speaker counts. If Gujarati is below the target, Gujarati's measured available hours become the cap and other languages are downsampled to that same measured target for the balanced experiment.

Why: 10 hours is useful for pipeline smoke testing but is likely too small for speaker and scenario diversity. 25 hours is a practical first multilingual student target while remaining materially below the full corpus. 50/100 hours should wait for GPU, quality, and listener evidence.

## Acquisition pipeline

```text
official HF / LibriTTS source
  -> authenticated bounded download of approved files/rows
  -> immutable source checksum and acquisition log
  -> safe audio extraction into private ignored storage
  -> metadata manifest (no speaker identifiers in Git)
  -> deterministic quality filters
  -> duplicate and transcript checks
  -> speaker-disjoint train/validation/test split
  -> training manifest
```

Every item manifest must retain dataset, pinned revision/version, source file and row/record identity, language, speaker ID, original and normalized text, duration, scenario, task name, gender, regional fields, quality values, source checksum when available, acquisition timestamp, license, attribution, and provenance notes. Private manifests must remain outside Git unless privacy review approves redaction.

## Eventual controlled process

No command is authorized yet. After legal approval and an official manifest/checksum bundle are obtained, the operator will run an acquisition wrapper in a private workspace with explicit language, split, row/shard allowlists, byte budget, checksum verification, and `--no-training`/`--no-release` controls. It must fail closed when a source checksum, language, row identity, or byte budget is missing.

Illustrative future invocation (not to be run now):

```text
scripts/tts-data/acquire_controlled.py \
  --dataset ai4bharat/indicvoices_r \
  --revision 5f4495c91d500742a58d1be2ab07d77f73c0acf8 \
  --languages hi,gu,mr,kn,ml,ta,te,or,bn \
  --splits train,test \
  --manifest /private/approved/indicvoices-r-manifest.json \
  --max-hours-per-language 25 \
  --require-speaker-disjoint-splits \
  --output /private/tts-corpus/indicvoices-r \
  --no-training
```

The command is a specification, not an existing or authorized acquisition command. It must not be implemented by bypassing HF access controls or by downloading complete language archives.

## Split policy

Use the published train/test split where trustworthy and preserve it as an immutable evaluation boundary. Create validation from the training portion by speaker, not by utterance. If the source split has speaker overlap, remove overlapping speakers from training and validation or document the resulting limitation. Select test speakers first, then validation speakers, then training speakers; no speaker may occur in more than one split.

## Legal gate

IndicVoices-R and LibriTTS are currently conditional CC-BY-4.0 sources. Preserve attribution, license links, change notices, citations, source revision, checksums, consent/data-card evidence, and speaker/privacy handling. CC-BY-4.0 alone is not approval to ship trained weights. Product/legal review must approve downstream trained-model redistribution, APK inclusion, notices, and speaker metadata treatment before acquisition for training.

## Architecture constraint

This plan does not alter the locked runtime boundary:

```text
UnifiedTtsEngine -> LanguageRouter -> TtsModelRegistry -> language frontend
  -> shared multilingual acoustic model -> shared vocoder -> PCM -> AudioTrack
```

GPU size, training duration, Android CPU/RAM, RTF, and listener quality are all **REQUIRES MEASUREMENT**. No performance claim is made here.
