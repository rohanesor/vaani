# SIH-L9-P1 Artifact + Evidence Gate

## Method
Only official sources, original papers, model cards, repository provenance, and current Vokie source/project records may advance an artifact. This offline audit did not download candidates or claim current Web/repository availability beyond documented project evidence.

## Current immutable Vokie artifacts

| Component | Identity | Integrity/runtime | Status |
|---|---|---|---|
| STT | Whisper tiny multilingual Q5_1 | `ggml-tiny-q5_1.bin`, 33,554,432 bytes, SHA256 `818710568da3ca15689e31a743197b520007872ff9576237bda97bd1b469c3d7`; whisper.cpp ARM64 JNI | explicit EN/HI/TA only |
| Translation | `osa911/nllb-200-distilled-600M-ct2-int8` revision `46858753dbaf8eb5e21bb6f0037c3b90851e090a` | 619,704,329 bytes, SHA256 `ca3362e6e81906c0cf9c33bd6917674222c71d69617d0afb18507ce0b6c2e2e8`; CT2 4.8.2 ARM64 JNI | EN/HI/TA JNI mapping only |
| TTS | sherpa-onnx VITS route | engine path exists; exact approved redistributable EN/HI/TA voice set remains unresolved in project records | blocked artifact gate |

## Evidence result

Whisper multilingual is the only STT family already proven to compile/run in Vokie. Upstream language-code availability for all ten does not establish WER, memory, Android latency, or rescue quality. sherpa-onnx/Vosk are runtime candidates, not selected ten-language models. MMS/Piper/VITS public artifacts require separate weight and voice/dataset-rights review. Prior project research identified MMS TTS cards as CC-BY-NC-4.0, therefore non-compliant for intended deployment until ownership/use terms explicitly change.

## Strict per-language decision

HI/TA/EN: **REQUIRES DEVICE VALIDATION** for STT and **BLOCKED** for final TTS artifact approval. GU/MR/KN/ML/TE/OR/BN: **REQUIRES RESEARCH** for both model artifacts. All ten have NLLB tags, but only EN/HI/TA current CT2 JNI mapping.

## Recommendation

Keep Whisper baseline. Do not add a second STT runtime before a candidate has: exact revision/checksum, model/code license, Android ARM64 evidence, weight size, benchmark citation, and same-device evaluation plan. Prefer one multilingual STT loaded once; use language-specific TTS voices loaded only on demand. This avoids ten concurrent voice models while allowing per-language quality selection.

## Required evidence record before integration

For each STT/TTS language artifact: source URL/revision, SHA256, code/weight/dataset license, commercial and redistribution determination, compressed/unpacked size, parameter count/quantization, ARM64 runtime path, min API/native dependencies, published language-specific WER/MOS if available, and low/mid-device WER/intelligibility/PSS/RTF plan.
