# Phase 2C: next-generation offline TTS discovery

## Method and hard gates

This discovery used official project repositories, official Hugging Face publisher pages, official release inventories, and per-voice model cards. A public ONNX file is not sufficient: every production artifact needs a known weight/frontend/vocoder license, all-ten routing coverage, offline bundling, and a native Android CPU path. RAM, RTF, quality, and Android claims are marked unmeasured unless a recorded run exists.

## New deployment-oriented findings

### Piper voice catalogue

Official source: https://huggingface.co/rhasspy/piper-voices, revision `39ab474be869e9181350af6a65e4953eef67aaa0`; repository card license is MIT. Piper publishes ready-to-run ONNX voices and configuration files, which makes it the strongest newly discovered Android-runtime *format* candidate. The official catalogue has these target-language voices:

| Language | Selected compact official voice | ONNX bytes | Per-voice license evidence | Result |
|---|---|---:|---|---|
| Bengali | `bn_BD-google-medium` | 76,782,515 | OpenSLR CC-BY-SA 4.0 plus CMU terms | requires artifact legal review |
| Hindi | `hi_IN-rohan-medium` | 62,950,044 | IndicTTS external license URL | requires artifact legal review |
| Malayalam | `ml_IN-arjun-medium` | 62,950,044 | external Kaggle URL; no verified license text in card | blocked |
| Marathi | `mr_IN-google-medium` | 76,768,179 | CC-BY-SA 4.0 | requires artifact legal review |
| Telugu | `te_IN-maya-medium` | 62,950,044 | IndicTTS external license URL | requires artifact legal review |

The five selected ONNX files total **342,400,826 bytes**, before five JSON configurations. Gujarati, Kannada, Tamil, Odia, and English are absent from the official Piper catalogue, so Piper cannot form the required final system. Piper project MIT licensing does not override per-voice training-data terms.

### Kokoro-82M

- Official source: https://huggingface.co/hexgrad/Kokoro-82M
- Revision: `f3ff3571791e39611d31c381e3a41a3af07b4987`
- Official weight: `kokoro-v1_0.pth`, **327,212,226 bytes**, SHA-256 `496dba118d1a58f5f3db2efc88dbdc216e0483fc89fe6e47ee1f2c53f18ad1e4`
- Official card: Apache-2.0, 82 million parameters.
- Official voice documentation explicitly lists Hindi and English, but not Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia, or Bengali.
- Runtime: official PyTorch path. No official ONNX, sherpa-onnx, or Android artifact was found. Community ONNX conversions are not authoritative production sources.

Kokoro is a legally promising English/Hindi component, but it cannot be selected without a native Android runtime and a complete legally approved Indic complement.

## Previously audited candidates retained in discovery

| Candidate | Official source / pin | Coverage | Exact published payload | Format | License evidence | Hard result |
|---|---|---|---:|---|---|---|
| AI4Bharat `vits_rasa_13` | HF `ai4bharat/vits_rasa_13` @ `00b1590501b55708d5d66be51bae336b51bce1d2` | 6/10 | 160,822,916 bytes | gated custom Transformers | card CC-BY-4.0; assets/gate unverified | Fail |
| AI4Bharat Indic Parler | HF `ai4bharat/indic-parler-tts` @ `7b527af5ee8ed1f9a28d80b19703ed9bb8ba10ca` | 10/10 | 3,763,425,107 bytes | gated Parler-TTS | card Apache-2.0 | Fail: gate, size, no Android runtime |
| AI4Bharat Indic-TTS | GitHub `AI4Bharat/Indic-TTS` @ `ad2461c22c373f89a140c5d9fb617b101219bdad`, release `v1-checkpoints-release` | 10/10 | 15,183,608,993 bytes | FastPitch + HiFi-GAN, PyTorch/CUDA reference | code MIT; asset terms/digests unverified | Fail |
| Facebook MMS / local ONNX baseline | HF `facebook/mms-tts-<iso6393>`; e.g. Hindi @ `1d83b223ec78e30b944f7d96bd117eb3d7023303` | 10/10 | 1,140,400,586 bytes including tokens | VITS ONNX / sherpa-onnx | upstream cards CC-BY-NC-4.0; local conversion provenance unapproved | Fail |

## Excluded modern systems

Modern large multilingual/zero-shot systems were not converted merely because they are popular. Systems requiring cloud inference, GPU/CUDA, Python at runtime, unpinned community conversions, or more than the low-end mobile envelope are excluded. No official model discovered in this pass combines all ten required languages, approved redistribution, compact native Android deployment, and measured CPU performance.
