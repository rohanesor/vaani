# TTS legal decision — Phase 2N

## Decision

**No legally clean, fully evidenced training corpus currently covers all ten required languages. Training remains BLOCKED.** IndicVoices-R is technically accessible but legally blocked. The potentially useful OpenSLR hybrid is not approved because it leaves Hindi/Odia unresolved and introduces CC-BY-SA trained-weight questions. No existing pretrained model has passed the complete ten-language, offline, Android, provenance, and redistribution gates.

This is a technical/provenance decision, not legal advice.

## Rights matrix

| Source / right | IndicVoices-R | OpenSLR Indic TTS sets | AI4Bharat Rasa | LibriTTS SLR60 | Common Voice current release | AI4Bharat IndicVoices |
|---|---|---|---|---|---|---|
| Dataset acquisition | TECHNICALLY ACCESSIBLE / LEGALLY BLOCKED | REQUIRES_REVIEW | REQUIRES_REVIEW | REQUIRES_REVIEW | UNKNOWN | BLOCKED |
| Research/training | BLOCKED pending approval | REQUIRES_REVIEW | REQUIRES_REVIEW | REQUIRES_REVIEW | UNKNOWN | BLOCKED |
| Audio preprocessing | BLOCKED pending approval | REQUIRES_REVIEW | REQUIRES_REVIEW | REQUIRES_REVIEW | UNKNOWN | BLOCKED |
| Derived manifest/dataset | BLOCKED pending consent/privacy review | REQUIRES_REVIEW | REQUIRES_REVIEW | REQUIRES_REVIEW | UNKNOWN | BLOCKED |
| Model training | BLOCKED | REQUIRES_REVIEW | REQUIRES_REVIEW | REQUIRES_REVIEW | UNKNOWN | BLOCKED |
| Trained-weight redistribution | BLOCKED / UNKNOWN | BLOCKED pending ShareAlike review | UNKNOWN | UNKNOWN | UNKNOWN | BLOCKED |
| Commercial use | UNKNOWN | BLOCKED pending review | UNKNOWN | UNKNOWN | UNKNOWN | BLOCKED |
| APK bundling | BLOCKED | BLOCKED pending review | UNKNOWN | UNKNOWN | UNKNOWN | BLOCKED |
| AWS/S3/CloudFront | BLOCKED | BLOCKED pending review | UNKNOWN | UNKNOWN | UNKNOWN | BLOCKED |
| Attribution | REQUIRED if approved | REQUIRED | REQUIRED | REQUIRED | UNKNOWN | UNKNOWN |

## Evidence and limits

### IndicVoices-R

- Official HF dataset: `ai4bharat/indicvoices_r`
- Revision: `5f4495c91d500742a58d1be2ab07d77f73c0acf8`
- Dataset card declares CC-BY-4.0.
- Technical access works, but repository policy currently does not authorize private audio acquisition, preprocessing, derived data, training, or distribution.
- Consent/data-card detail, speaker/privacy treatment, immutable artifact evidence, and trained-weight terms remain unresolved.

Status: **TECHNICALLY ACCESSIBLE BUT LEGALLY BLOCKED**.

### OpenSLR Indic corpora

Official OpenSLR language-specific corpora are listed as CC-BY-SA-4.0 in the existing source audit. They may be useful research inputs, but ShareAlike implications for transformed corpora, model weights, APK assets, and commercial distribution require counsel. They also do not provide the required Hindi and Odia path in the verified matrix.

Status: **not approved for this product**.

### AI4Bharat Rasa

The existing audit records a CC-BY-4.0 declaration and limited expressive coverage. It is a supplement, not a verified all-language corpus, and artifact/provenance/derivative review remains incomplete.

Status: **conditional, not approved**.

### LibriTTS

Official OpenSLR SLR60 is declared CC-BY-4.0 and is a suitable English research candidate. Archive-level manifest/checksum, consent/privacy, and trained-weight/APK review are still required.

Status: **conditional English source only**.

## Required legal record before any training

Preserve the source release/revision, archive and file checksums, attribution/NOTICE text, citation, data card, consent and privacy terms, speaker metadata policy, transcript rights, filter and normalization changes, selected record IDs, split seed, training code licenses, model checksum, and the final distribution decision.

CC-BY-4.0 does not automatically authorize trained iTantra weight redistribution, APK inclusion, or AWS distribution. No commercial or competition-use claim is made.

## Product consequence

The locked model-neutral boundary remains the only approved production design. Do not hard-code IndicVoices-R or an unapproved corpus into Android. MMS remains a benchmark/baseline and is not approved for shipping; its upstream non-commercial constraints remain documented.
