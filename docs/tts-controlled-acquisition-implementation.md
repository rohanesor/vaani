# Phase 2L controlled acquisition implementation

## Status

**PHASE 2L IMPLEMENTED — ACQUISITION NOT YET APPROVED**

Implemented:

- `scripts/tts-data/acquire_controlled.py`
- `scripts/tts-data/test_acquire_controlled.py`
- `data/manifests/indicvoices-r/acquisition-plan.json`

The tool is fail-closed. It requires `HF_TOKEN` from the environment, pins the dataset and revision, validates language/split allowlists, requires an explicit output and selection manifest, and requires both `--approve-acquisition` and a positive `--max-bytes` before an approved transfer path could run.

## Dry-run result

Executed for all nine Indic languages, both `train` and `test`, using the 10-hour feasibility target. Authenticated `/info` requests resolved successfully for every selected language. The run made no `/rows` request, did not dereference a Parquet/audio URL, and acquired zero audio bytes.

Published train/test record counts reported by the official metadata endpoint:

| Language | Train | Test |
|---|---:|---:|
| hi | 26,318 | 376 |
| gu | 1,129 | 662 |
| mr | 18,776 | 365 |
| kn | 16,986 | 342 |
| ml | 31,106 | 397 |
| ta | 39,292 | 293 |
| te | 47,208 | 339 |
| or | 25,202 | 363 |
| bn | 39,904 | 288 |

The source reports configuration-level published download bytes, but exact bounded 10-hour source bytes cannot be established from `/info`; duration and speaker distributions are unavailable without an approved record acquisition. The tool therefore reports estimates/unknowns and does not claim that any language reaches 10 hours.

## Safety controls

- Dataset must equal `ai4bharat/indicvoices_r`.
- Revision must equal `5f4495c91d500742a58d1be2ab07d77f73c0acf8`.
- Languages must be the nine-language allowlist.
- Splits must be `train` and/or `test`.
- `--no-training` is mandatory.
- Without `--approve-acquisition`, the tool performs dry-run metadata resolution only.
- Approval requires an explicit positive `--max-bytes`.
- An approved manifest must be pinned to the same source and contain unique source records, speaker IDs, durations, provenance fields, and source byte sizes.
- Hour and byte budgets are enforced before transfer.
- Disk space is checked with a staging multiplier before transfer.
- Speaker intersections across selected splits fail closed when requested.
- The tool never recursively downloads a repository, archive, language directory, or complete Parquet dataset.
- Errors never include environment variables or request headers.

## Selection policy

The approved selection manifest is produced only after a private metadata/quality audit. It must select speakers before utterances, reserve test speakers first, reserve validation speakers second, apply per-speaker caps, and use a recorded deterministic seed. It must not duplicate Gujarati or any other audio to fill a target. Gujarati's 1,791 known utterances remain a hard candidate-count ceiling before filtering and split reservation.

The first real acquisition target is 10 hours per language, not 25. The common target is the minimum verified usable hours across all nine Indic languages, capped by the configured target. English/LibriTTS is explicitly out of scope for Phase 2L.

## Quality and processing boundary

The selection manifest must retain original text, verbatim text, normalized source text, and a separate iTantra normalization history. The post-acquisition processing stage must verify decoding, sample rate, channels, duration, silence, SNR, C50, CER, speaking rate, transcript validity, duplicate audio/transcripts, and speaker IDs. Thresholds remain configuration values and are not finalized until source distributions and metric semantics are reviewed.

## Eventual command

Dry-run (executed):

```text
HF_TOKEN=... python3 scripts/tts-data/acquire_controlled.py \
  --dataset ai4bharat/indicvoices_r \
  --revision 5f4495c91d500742a58d1be2ab07d77f73c0acf8 \
  --languages hi,gu,mr,kn,ml,ta,te,or,bn \
  --splits train,test \
  --max-hours-per-language 10 \
  --output /private/itrantra/indicvoices-r \
  --manifest /private/approved/indicvoices-r-selection.json \
  --require-speaker-disjoint-splits \
  --no-training \
  --dry-run
```

A future approved invocation must add `--max-bytes` and `--approve-acquisition`. The selection manifest, checksums, legal approval, and private output path must be reviewed before that invocation. The command must fail closed if any source identity, checksum, record identity, speaker assignment, or budget is missing.

## Legal status

The HF card declares CC-BY-4.0. Preserve attribution, citation, source revision, checksums, consent/data-card evidence, transformations, and speaker/privacy controls. CC-BY-4.0 is not automatic approval for trained-weight redistribution; legal/product review remains required.
