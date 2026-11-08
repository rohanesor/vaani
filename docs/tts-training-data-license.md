# Training-data license and derivative-model audit

## Primary evidence

| Dataset component | Official evidence | Declared license | Audio/transcript/metadata scope | Commercial reuse / redistribution |
|---|---|---|---|---|
| IndicVoices-R repository and Zenodo download-instructions record `11636050` | https://github.com/AI4Bharat/IndicVoices-R at `4d77d8a402b680fb739c1a6a3ec531de0cf17139`; https://zenodo.org/records/11636050 | CC-BY-4.0 | Repository and Zenodo metadata declare CC-BY-4.0; repository describes WAV audio and manifests containing normalized transcripts and speaker metadata | CC BY 4.0 allows commercial sharing/adaptation subject to attribution, license link, change indication, and no extra restrictions |
| LibriTTS OpenSLR SLR60 | https://www.openslr.org/60/ | CC-BY-4.0 | OpenSLR calls it LibriTTS corpus with audio and original/normalized text | Same CC BY 4.0 permissions/conditions |

## Important limits

This is an engineering license review, not legal advice. A dataset-level CC-BY declaration is strong evidence that the licensor permits reuse/adaptation of material it controls, but it is not by itself proof that every underlying speaker, recording, transcript, privacy, personality, or jurisdictional right is cleared for every downstream use. Neither source in the Phase 2F metadata pass provides a model-weight-specific license because no iTantra model exists yet.

A model trained solely from licensed data may be distributable only after review of: dataset terms/data cards, attribution/NOTICE obligations, speaker/consent restrictions, source version and archive integrity, training-code licenses, any teacher/output provenance, and the applicable treatment of trained weights. The Android APK, S3, and CloudFront use case must preserve required attribution and notices. Do not represent a student as unrestricted or sublicensable until counsel/product review approves the complete chain.

## Result

- **IndicVoices-R:** Conditional candidate. Official HF dataset card revision `5f4495c91d500742a58d1be2ab07d77f73c0acf8` declares `license: cc-by-4.0`, identifies WAV/48-kHz data, normalized/verbatim transcripts, speaker/gender metadata, and citation. It does not state trained-weight, APK/S3/CloudFront, consent, or speaker-metadata redistribution terms beyond the CC-BY declaration. Zenodo version record `14016558` supplies a CC-BY-4.0 repository snapshot only, not data. Individual LFS shard SHA-256 values are visible in the official HF file index, while Viewer access to per-row data requires authentication.
- **LibriTTS:** Conditional candidate. OpenSLR explicitly declares CC-BY-4.0 and 585-hour/24-kHz corpus facts, but fine-grained archive manifest/checksum/speaker metadata was not acquired without downloading archives.
- **Commercial trained-model distribution:** **BLOCKED pending legal/data-card review and exact artifact acquisition.** This is not a conclusion that CC-BY forbids training or shipment; it is a refusal to claim downstream legal certainty without the complete evidence chain.

## Prohibited teacher/data sources

Do not distill from a gated, non-commercial, proprietary, or unclear teacher. Teacher outputs and teacher weights must have an equally auditable license chain. The legally safer starting point is supervised student training from the recorded CC-BY corpus sources.
