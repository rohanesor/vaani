# Phase 2B multilingual TTS language matrix

Status date: 2026-08-29. A check means the language is explicitly listed by the candidate's official source; it does **not** mean Android, license, quality, or performance approval.

| Candidate | HI | GU | MR | KN | ML | TA | TE | OR | BN | EN | Models | Runtime / format | License evidence | Android feasibility | Gate result |
|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---:|---|---|---|---|
| AI4Bharat `vits_rasa_13` | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✗ | 1 | gated custom Transformers/PyTorch safetensors | card: CC-BY-4.0; individual assets/gate unverified | no ONNX/sherpa package | **FAILED** coverage, access, runtime/legal gates |
| AI4Bharat `indic-parler-tts` | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 1 | gated Parler-TTS/Transformers safetensors | card: Apache-2.0 | no audited Android-native route; 3.76 GB package | **FAILED** access, size, Android/performance gates |
| AI4Bharat Indic-TTS v1 checkpoints | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | at least 10 packs | PyTorch FastPitch + HiFi-GAN | repository code: MIT; release-weight terms/checksums not published in release API | no Android/ONNX package | **FAILED** size, artifact-license, Android gates |
| Current MMS/VITS ONNX archive | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 10 | sherpa-onnx 1.13.6 VITS ONNX | upstream MMS cards: CC-BY-NC-4.0; conversion provenance/weight redistribution unapproved | Android integration exists; 2 GB-device evidence absent | **FAILED** offline packaging, redistribution, and device-validation gates |
| Piper official voice catalogue | — | — | — | — | — | — | — | — | — | — | varies | Piper/ONNX | voice-specific | no official all-ten Indic coverage identified in this audit | **EXCLUDED**: cannot satisfy required matrix |

## Source pins used

| Candidate | Official source | Revision / release | Measured published payload |
|---|---|---|---:|
| `vits_rasa_13` | `https://huggingface.co/ai4bharat/vits_rasa_13` | `00b1590501b55708d5d66be51bae336b51bce1d2` | 160,822,916 bytes metadata inventory; gated |
| `indic-parler-tts` | `https://huggingface.co/ai4bharat/indic-parler-tts` | `7b527af5ee8ed1f9a28d80b19703ed9bb8ba10ca` | 3,763,425,107 bytes metadata inventory; gated |
| Indic-TTS | `https://github.com/AI4Bharat/Indic-TTS` | code `ad2461c22c373f89a140c5d9fb617b101219bdad`; release `v1-checkpoints-release` | 1,513,473,097–1,535,189,785 bytes per release zip |
| MMS reference | `https://huggingface.co/facebook/mms-tts-<iso6393>` | per-language revisions; for example Hindi `1d83b223ec78e30b944f7d96bd117eb3d7023303` | current converted ONNX + token packs: 1,140,400,586 bytes |

The AI4Bharat Parler card explicitly lists all ten required codes. It is not selected: its complete artifact is 3.76 GB before Android runtime/storage overhead and is inaccessible through the official gate in this environment. The Indic-TTS release contains `en.zip`, `hi.zip`, `gu.zip`, `mr.zip`, `kn.zip`, `ml.zip`, `or.zip`, `bn.zip`, `ta.zip`, and `te.zip`, but each is approximately 1.5 GB and the GitHub release API supplies no asset digest.
