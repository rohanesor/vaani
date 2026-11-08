# `vits_rasa_13` architecture evidence record

## Scope and confidence

This document separates verified public evidence from facts that cannot be responsibly inferred while the official candidate snapshot is gated. It is not a generic VITS architecture description and does not claim that the candidate has a standard VITS implementation.

Pinned official source: [`ai4bharat/vits_rasa_13`](https://huggingface.co/ai4bharat/vits_rasa_13) revision `00b1590501b55708d5d66be51bae336b51bce1d2`.

## Verified public facts

| Topic | Evidence |
|---|---|
| Architecture family | Official README calls the model “VITS-based.” |
| Packaging | `model.safetensors` plus Hugging Face Transformers custom code/config/tokenizer files. |
| Reference runtime | README installs `transformers` and `torch`, loads model/tokenizer with `trust_remote_code=True`, and invokes `model(input_ids, speaker_id=..., emotion_id=...)`. |
| Output API | README example writes `outputs.waveform.squeeze()` using `model.config.sampling_rate`. |
| Speaker conditioning | Explicit integer `speaker_id` argument; 20 speaker profiles declared in model card. |
| Style/emotion conditioning | Explicit integer `emotion_id` argument; 14 styles declared in model card. |
| Language conditioning | The public card lists 13 languages, but the language-ID tensor/mapping implementation is not accessible. |
| Weight format | Safetensors; no ONNX artifact is listed by official inventory. |

## Unverified architecture facts

The official `config.json`, `configuration_vits.py`, `modeling_vits.py`, tokenizer code, vocabulary, and weights are gated. These required details are therefore unknown:

| Required fact | Status |
|---|---|
| Text encoder topology and dimensions | Unknown |
| Posterior encoder topology | Unknown |
| Normalizing-flow topology | Unknown |
| Decoder/generator topology | Unknown |
| Whether a HiFi-GAN-like vocoder is integrated, separate, or modified | Unknown |
| Parameter count | Unknown |
| Individual tensor dtypes | Unknown |
| Sample rate and output PCM characteristics | Unknown |
| Language embedding mechanism and IDs | Unknown |
| Speaker embedding table/conditioning mechanism | Unknown beyond integer `speaker_id` API |
| Style/emotion embedding mechanism | Unknown beyond integer `emotion_id` API |
| Text normalization, grapheme/token behavior, and phonemization | Unknown |
| Dynamic shapes, custom operators, and export blockers | Unknown |
| CPU/GPU assumptions within model code | Unknown |

A 160,708,568-byte safetensors file is not enough evidence to derive parameter count or FP32/FP16 composition. No estimate is recorded.

## Public speaker and style mapping

The official card exposes these speaker IDs:

| ID | Speaker |
|---:|---|
| 0 | ASM_F |
| 1 | ASM_M |
| 2 | BEN_F |
| 3 | BEN_M |
| 4 | BRX_F |
| 5 | BRX_M |
| 6 | DOI_F |
| 7 | DOI_M |
| 8 | KAN_F |
| 9 | KAN_M |
| 10 | MAI_M |
| 11 | MAL_F |
| 12 | MAR_F |
| 13 | MAR_M |
| 14 | NEP_F |
| 15 | PAN_F |
| 16 | PAN_M |
| 17 | SAN_M |
| 18 | TAM_F |
| 19 | TEL_F |

Declared style IDs are ALEXA 0, ANGER 1, BB 2, BOOK 3, CONV 4, DIGI 5, DISGUST 6, FEAR 7, HAPPY 8, NEWS 10, SAD 12, SURPRISE 14, UMANG 15, and WIKI 16.

## Required post-access forensic procedure

After the official gate is approved, run the pinned acquisition script and inspect only the downloaded snapshot. The audit must:

1. Parse `config.json` and custom configuration/model source to enumerate model blocks, dimensions, sample rate, dtypes, inputs, outputs, and dependencies.
2. Use safetensors metadata/tensor enumeration to calculate parameter count, dtype counts, and exact weight footprint without executing arbitrary code.
3. Inspect tokenizer and vocabulary source/configuration to determine input normalization, language routing, special tokens, and external phonemizer requirements.
4. Trace the official forward path to establish waveform/vocoder behavior and speaker/style/language conditioning.
5. Run the official reference inference in a disposable virtual environment only after source review. `trust_remote_code=True` must never be used against an unpinned or unreviewed revision.
6. Produce an operator/dynamic-shape inventory before attempting ONNX export.

## Android implications

The candidate is currently a Python/Transformers custom-code package, not an Android inference package. Android cannot use the reference `trust_remote_code=True` path. Before any Android prototype, the project needs an audited non-Python frontend plus a native runtime compatible with the complete acoustic/vocoder graph and its conditioning inputs.

Neither ONNX Runtime Mobile nor sherpa-onnx compatibility can be concluded from “VITS-based.” The candidate must first pass source/configuration and reference-inference gates.
