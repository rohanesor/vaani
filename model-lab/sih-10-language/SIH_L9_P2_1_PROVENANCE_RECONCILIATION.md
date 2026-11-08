# SIH-L9-P2.1 Provenance Reconciliation

## English conclusion

**ENGLISH_PROVENANCE_STATUS = MISMATCH / REJECTED.** The actual staged file is 114,016,948 bytes with SHA-256 `e3a198...`; it is not the earlier documented ~63 MB Piper English ONNX. Local `mms-provenance-audit.md` identifies an official candidate `facebook/mms-tts-eng@c71de0fe...`, but explicitly states that local ONNX conversion command, converter revision, input checkpoint checksum linkage, tokenizer acquisition, and redistribution authorization are unknown. The candidate official MMS card license is CC-BY-NC-4.0. Therefore this is a different MMS-family-derived artifact, not a packaging variant of Piper, and cannot be approved.

## Seven asset conclusion

GU/MR/KN/ML/TE/OR/BN have immutable manifest hash/size entries and official candidate MMS revisions recorded in `docs/mms-provenance-audit.md`, but their ONNX/token files are not present under the current project assets. The official candidate sources have CC-BY-NC-4.0 and conversion/token provenance is unknown. Each is individually **UNVERIFIED / REJECTED** for deployment; no language is omitted from classification.

## Actual byte reconciliation

Actual local files EN, HI, and TA exactly match current manifest SHA-256 and bytes. Shared `espeak-ng-data` is 17,991,651 bytes. No APK extraction copy was available for byte comparison; source asset versus APK copy is therefore **NOT MEASURED**. No SHA-512 was needed because SHA-256 manifest verification is the current integrity mechanism.

## Runtime/deployment

The 114 MB family is documented as VITS ONNX opset-13 FP32 in the MMS audit. Sherpa-onnx is a plausible runtime but does not establish asset authorization. Only HI/TA have Android runtime evidence. GU/MR/KN/ML/TE/OR/BN have no staged file, no selectable `TtsLanguage`, and no Android test.

## Integrator decision

No new model may proceed to integration. Hindi is the only conditionally documented prototype voice; its non-commercial dataset condition requires an explicit project policy. Tamil has physical runtime evidence but CC-BY-NC restriction. English and the seven MMS manifest candidates are rejected pending a permissive, reproducible artifact chain.
