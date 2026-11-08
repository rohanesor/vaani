# Model Lab 2 artifact recovery report

## Recovery status

**PHASE 1.5: PARTIALLY RECOVERED, BUT BLOCKED FOR ANDROID INTEGRATION.** After fetching `origin/main`, the authoritative Model Lab commit was found: `874458fa3776acc12e059c5c7bfb786926cbf49f` (`model-lab: add offline EN/HI/TA translation + Tamil TTS research artifacts`). Its tracked documents, manifest, harness, and benchmark outputs were restored without switching branches. The approved large CT2 weight binary is deliberately excluded from Git and is not present locally, so Android integration remains blocked.

No download was performed. No production Android file was modified.

## Current checkout preserved

| Item | Value |
|---|---|
| Branch | `feat/itrantra-production-transceiver` |
| HEAD | `14ebd4639ba2e5e466740b6bbd35d94d93258201` |
| HEAD subject | `feat(location): integrate Android emergency telemetry` |
| Working tree | Dirty before recovery; extensive modified and untracked user/project work was preserved unchanged |
| Stashes | none |

## Searches performed

1. Current checkout and sibling project space: filename/path search under `/mnt/d/vibe` and `/tmp`, including the required documents, manifests, JSON results, WAV outputs, and NLLB/CTranslate2/willwade/MMS identifiers.
2. Local model caches, including the accessible Windows Hugging Face cache.
3. All reachable Git refs: `git log --all --full-history -- <each required path>`, global reachable path-name search, commit-message search, all local/remotes branches and tags.
4. Reflogs and unreachable objects: `git fsck --no-reflogs --unreachable`, unreachable commit/tree path enumeration, and text inspection of suitably sized dangling blobs for the required revision/model identifiers.
5. Local archives under `/mnt/d/vibe` and `/tmp` (no relevant model archive was present).

## Required artifact inventory

| Artifact | Found | Source | SHA-256 | Verified |
|---|---:|---|---|---:|
| `docs/model-lab-final-report.md` | Yes | `origin/main:874458f` | Git object authenticated | Yes |
| `docs/model-lab-2-language-final.md` | Yes | `origin/main:874458f` | Git object authenticated | Yes |
| `docs/model-integration-plan.md` | Yes | `origin/main:874458f` | Git object authenticated | Yes |
| `model-lab/models/MANIFEST.json` | Yes | `origin/main:874458f` | Git object authenticated | Yes |
| NLLB-200-distilled-600M CT2 INT8 model | No | deliberately excluded from Git | expected `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8` | No |
| NLLB tokenizer / SentencePiece / CT2 vocabulary/configuration | No | — | — | No |
| Tamil MMS-TTS willwade ONNX revision `709a74aa...` | Yes | existing `models/tts/vits-mms-tam/` | model + tokens match manifest | Yes |
| `model-lab/bench/out/trans_results.json` | Yes | `origin/main:874458f` | Git object authenticated | Yes |
| `model-lab/bench/out/ta_tts_results.json` | Yes | `origin/main:874458f` | Git object authenticated | Yes |
| `model-lab/bench/out/e2e_results.json` | Yes | `origin/main:874458f` | Git object authenticated | Yes |
| `model-lab/bench/out/ta_mms_*.wav` | Yes | `origin/main:874458f` | Git object authenticated | Yes |

## Git and branch evidence

- `git log --all --full-history --` for every required path produced no commit.
- `git branch -a` contains `main`, `feature/vokie-core`, `feat/branding-itancore`, and `feat/itrantra-production-transceiver` with their `origin/*` counterparts. None contains the requested paths or Model Lab 2 identifiers.
- Tags are `v0.1.0`, `v0.2.0`, `v0.2.1`, and `v0.3.0`; no Model Lab tag exists.
- There are no stashes.
- The one relevant-looking dangling commit, `934df034b210fdfe04d5d3ab5b3649c93f912ee8`, is a prior multilingual TTS architecture/docs evaluation. Its tree has no requested Model Lab path. Enumerated unreachable trees and inspected dangling text blobs contain none of `46858753`, `709a74aa`, `NLLB-200-distilled-600M`, `willwade`, or `CTranslate2`.

## Existing candidates are not authenticated Model Lab 2 artifacts

The checkout contains legacy MMS VITS files, for example:

```text
models/tts/vits-mms-tam/model.onnx
size: 114,032,312 bytes
sha256: c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88
```

`app/src/main/assets/models/manifest.json` records this same legacy path/hash along with other legacy MMS language assets. It is **not** the required `models/MANIFEST.json`, contains neither a revision nor provenance establishing the approved willwade Tamil artifact, and has no relationship evidence to Model Lab 2 benchmark results. It is therefore a **candidate only, not approved/recovered**.

The accessible Hugging Face cache contains unrelated DistilBERT, sentence-transformer, and faster-whisper tokenizer entries. It has no NLLB/CT2/willwade Model Lab artifact. No local CTranslate2 model directory was found.

## SHA-256 and provenance reconciliation

The recovered `model-lab/models/MANIFEST.json` is the source of truth. The existing Tamil model and tokens match it exactly: model `114,032,312` bytes / `c86cf0a0657d57577d937b806d7b63d638cff522b5687cb650dde24bc71c5c88`; tokens `375` bytes / `0b3f692319bb5fae8658e2f84bf252bca92450d0207bbba7273caa1a182d81b8`. It records willwade revision `709a74aad80a840eb57f767a7f5d155aaad1ac7b`.

The manifest records CT2 revision `46858753dbaf8eb5e21bb6f0037c3b90851e090a` and expected hashes, but the CT2 model binary is absent; it cannot yet be verified. No manifest was edited.

## Benchmark reconciliation

All three required JSON outputs and five `ta_mms_*.wav` outputs were restored from the authenticated commit. Their values reconcile with the Model Lab 2 report: CT2 load `0.679s`, peak RSS `728.0 MB`, translation medians around `0.212–0.228s`; Tamil 16 kHz TTS, peak RSS `256.9 MB`, RTF around `0.53–0.561`, zero clipping; E2E combined peak RSS `1049.7 MB`. These are recovered host measurements, not Android measurements.

## Files restored / changed

- Restored: three Model Lab reports, integration/transfer documentation, `model-lab/models/MANIFEST.json`, Model Lab harnesses, result JSONs, and benchmark WAVs from `origin/main:874458f`.
- Created: this report only, `docs/model-artifact-recovery.md`.
- Deleted: none.
- Production code/model files changed by this recovery operation: none.

## Exact remaining blocker and next milestone

Supply or make locally accessible the approved CT2 model directory, especially `model.bin`, from the recorded revision. Verify every CT2 file against the recovered manifest before integration. Phase 1.5 remains blocked until this occurs; do not begin Android CTranslate2 integration.
