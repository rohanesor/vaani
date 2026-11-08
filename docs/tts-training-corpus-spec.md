# Controlled TTS training corpus specification

## Status

**Specification only. No audio has been downloaded and no training is authorized.**

Sources are limited to:

- IndicVoices-R: `ai4bharat/indicvoices_r` at HF revision `5f4495c91d500742a58d1be2ab07d77f73c0acf8` for `hi`, `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`.
- LibriTTS: official OpenSLR SLR60 for `en`.

## Required record

Each retained record must contain:

```text
dataset, dataset_revision, source_file, source_row_or_record_id,
language, speaker_id, gender, original_text, normalized_text,
duration_seconds, scenario, task_name,
age_group, job_type, qualification, area, district, state,
utterance_pitch_mean, utterance_pitch_std, snr, c50, speaking_rate, cer,
source_checksum, acquired_at_utc, license, attribution, provenance_note
```

Speaker IDs and personal/regional metadata are private audit data until privacy review approves any publication. Public manifests should use stable pseudonymous record IDs and never include credentials.

## Deterministic quality policy

Thresholds are configuration values and must be frozen with the manifest, not silently changed:

- corrupted/unreadable audio: reject;
- sample rate: require the declared source rate; resampling is a separate recorded transform;
- duration: reject non-finite, `<= 0`, or outside configurable `[min_duration, max_duration]`;
- silence: reject when measured leading/trailing/total silence exceeds the configured fraction;
- SNR/C50/CER: apply configured bounds only after confirming field units and data-card meaning; missing values are reported and are not silently treated as good;
- transcript: require non-empty original and normalized text, valid Unicode, and language-appropriate script review;
- duplicate: reject identical normalized text within the same speaker where policy requires, and duplicate audio hashes globally;
- speaker ID: reject missing/empty IDs for speaker-disjoint splitting;
- quality outliers: quarantine for manual review rather than delete without a reason code.

No numeric threshold is approved by this document because source distributions and metric semantics have not been fully audited. The first acquired private sample must establish distribution-based, linguist-reviewed thresholds; configuration, counts, and rejection reasons become part of the provenance record.

## Split requirements

Use source test only if speaker overlap has been verified. Otherwise reserve speakers deterministically:

1. reserve test speakers;
2. reserve validation speakers;
3. assign remaining speakers to training;
4. assert zero speaker intersection between splits.

The split seed, speaker lists, source identities, and counts must be recorded privately. Equal utterance counts do not imply equal hours or speaker balance.

## Metadata and provenance

Record the official dataset URL, revision, source file, source checksum/ETag where available, acquisition time, tool version, command arguments, license text and citation, data-card/consent evidence, transformations, filter configuration, and all rejection counts. Preserve an immutable raw-to-processed mapping. Do not commit audio, private identifiers, tokens, or unreviewed manifests.

## Emergency evaluation separation

Emergency sentences belong to a separately reviewed evaluation set. They must not be inserted into training merely to repair vocabulary coverage. Maintain stable IDs, native-speaker/linguist review, translation provenance, Unicode-normalized text, and zero overlap with training text.

## Training architecture

The approved training design remains:

```text
language frontend -> shared multilingual acoustic student -> shared vocoder
```

The Android boundary remains:

```text
UnifiedTtsEngine -> LanguageRouter -> TtsModelRegistry -> lazy backend/session
  -> PCM -> AudioTrack
```

No Android integration or model export is part of this phase.

## Legal gate

Both sources are conditional CC-BY-4.0 candidates. Preserve attribution, license links, change notices, citations, source revision, checksums, consent/data-card evidence, and speaker/privacy restrictions. A CC-BY-4.0 dataset declaration does not automatically approve distribution of trained iTantra weights. Legal/product review must approve the complete source, training-code, derivative-model, APK, and notice chain before training or release.
