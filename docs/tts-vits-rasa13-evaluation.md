# AI4Bharat `vits_rasa_13` Phase 2A forensic evaluation

## Decision: B. BLOCKED

`ai4bharat/vits_rasa_13` is an official AI4Bharat Hugging Face candidate, but it cannot proceed to Android prototyping in the current environment. The exact upstream revision is identified, but the model repository is gated: all non-card artifacts return `401 GatedRepo` without an authorized Hugging Face token. In addition, the public official model card verifies only six of iTantra's ten required languages.

This is not a judgment about audio quality. No reference inference, conversion, or Android integration has been attempted.

## Official source and immutable identity

| Field | Evidence |
|---|---|
| Official source | [Hugging Face: `ai4bharat/vits_rasa_13`](https://huggingface.co/ai4bharat/vits_rasa_13) |
| Publisher identity | Hugging Face API model ID is `ai4bharat/vits_rasa_13`; the `ai4bharat` publisher is the official AI4Bharat organization used for this evaluation |
| Immutable revision | `00b1590501b55708d5d66be51bae336b51bce1d2` |
| API `lastModified` | `2024-12-31T10:49:31Z` |
| Metadata retrieval date | `2026-08-29T18:32:41Z` UTC |
| Runtime declaration | `transformers`, `custom_code`, `safetensors`, `text-to-speech` |
| Access result | Public model-card metadata is readable; `config.json`, custom Python files, tokenizer files, and weights require approved gated-repository access |

The pinned revision must be used by all future research commands. `main`, `latest`, or an unpinned `from_pretrained()` call is not acceptable.

## License and redistribution audit

The public model-card metadata and its front matter declare repository license `cc-by-4.0` ([Creative Commons Attribution 4.0 International](https://creativecommons.org/licenses/by/4.0/legalcode)). CC BY 4.0 permits sharing, commercial use, and adaptation provided that appropriate attribution, a license link, and change indication are supplied; it does not grant trademark, patent, privacy, or other rights outside the license.

| Asset group | Public evidence | Redistribution conclusion |
|---|---|---|
| Repository-level package | Model-card metadata: `license: cc-by-4.0` | Conditional: CC BY attribution/notice obligations apply |
| `model.safetensors` weights | Listed by official API; individual file inaccessible behind gate | **Not approved for distribution yet**: verify gated terms and weight-specific licensing after access |
| Config/custom model/tokenizer code | Listed by official API; inaccessible behind gate | **Not approved yet**: inspect source headers/LICENSE files after access |
| Text frontend/vocabulary | Listed by official API; inaccessible behind gate | **Not approved yet**: inspect dependencies and their licenses after access |
| Vocoder | Not separately listed by official inventory | Unknown until architecture/configuration inspection |

Therefore bundling model assets in an APK, distributing them through S3/CloudFront, or republishing them in an open-source repository is **blocked pending**: (1) approval of the model gate using an authorized organizational account, (2) capture of the gate terms, and (3) legal review confirming that the declared repository license covers every weight and dependency artifact. Do not interpret the public card as evidence that all individual assets have identical terms.

Future redistribution package requirements, if approved, include a preserved upstream attribution/NOTICE record, the CC BY 4.0 license link/text as required by legal review, source/revision/checksum records, and indication of conversion/quantization changes.

## Official artifact inventory

The following is the complete inventory exposed by the official Hugging Face API for the pinned revision. `model.safetensors` has an official LFS SHA-256 in the model API. Git blob IDs for small files are **not** SHA-256 values and are not substituted for a downloaded file checksum.

| File | Type | Bytes | SHA-256 | Purpose | License evidence | Access |
|---|---|---:|---|---|---|---|
| `.gitattributes` | Git LFS metadata | 1,519 | Pending download | LFS rules | Repository card CC BY 4.0 only | Gated |
| `README.md` | model card | 4,130 | `a109a7f040cece79316b0e0249b2f8d68f1be55e1a4242fabbccab44b1790c5e` | usage, language/speaker/style declarations | Card declares CC BY 4.0 | Readable |
| `config.json` | model config | 1,918 | Pending download | architecture/runtime/sample-rate configuration | Pending inspection | Gated |
| `configuration_vits.py` | custom Transformers config | 13,394 | Pending download | custom config class | Pending inspection | Gated |
| `model.safetensors` | weights | 160,708,568 | `0596e963e176aa71b4f581ea3e69d9deceff4ae20caa2752b6ffa970e721fc91` | neural weights | Pending gate/license verification | Gated |
| `modeling_vits.py` | custom Transformers model | 67,963 | Pending download | inference graph, likely includes waveform generation path | Pending inspection | Gated |
| `special_tokens_map.json` | tokenizer metadata | 275 | Pending download | special-token mapping | Pending inspection | Gated |
| `tokenization_vits.py` | custom tokenizer | 7,176 | Pending download | text frontend/tokenization | Pending inspection | Gated |
| `tokenizer_config.json` | tokenizer config | 795 | Pending download | tokenizer settings | Pending inspection | Gated |
| `vocab.json` | vocabulary | 17,178 | Pending download | vocabulary/token IDs | Pending inspection | Gated |

The public metadata total is **160,822,916 bytes** (153.37 MiB), including all listed files. The weights are **160,708,568 bytes** (153.26 MiB). This is metadata-reported artifact size, not a downloaded and locally recomputed package checksum. It is smaller than the current all-language MMS-TTS payload (1,140,400,586 bytes), but no footprint decision may be made before Android runtime, frontend, quality, and memory evidence exists.

## Verified language and speaker coverage

The official README and API card list exactly these languages:

`as`, `bn`, `brx`, `doi`, `kn`, `mai`, `ml`, `mr`, `ne`, `pa`, `sa`, `ta`, `te`.

The public card does **not** list English (`en`), Hindi (`hi`), Gujarati (`gu`), or Odia (`or`). The model cannot replace iTantra's ten-language production TTS inventory as currently documented.

| iTantra language | ISO | Officially listed | Speaker IDs declared by official card | Tested | Notes |
|---|---|---:|---|---:|---|
| Hindi | `hi` | No | — | No | Absent from official list |
| Gujarati | `gu` | No | — | No | Absent from official list |
| Marathi | `mr` | Yes | `12` MAR_F, `13` MAR_M | No | Gated package prevents inference |
| Kannada | `kn` | Yes | `8` KAN_F, `9` KAN_M | No | Gated package prevents inference |
| Malayalam | `ml` | Yes | `11` MAL_F | No | Gated package prevents inference |
| Tamil | `ta` | Yes | `18` TAM_F | No | Gated package prevents inference |
| Telugu | `te` | Yes | `19` TEL_F | No | Gated package prevents inference |
| Odia | `or` | No | — | No | Absent from official list |
| Bengali | `bn` | Yes | `2` BEN_F, `3` BEN_M | No | Gated package prevents inference |
| English | `en` | No | — | No | Absent from official list |

The card also declares style IDs: ALEXA `0`, ANGER `1`, BB `2`, BOOK `3`, CONV `4`, DIGI `5`, DISGUST `6`, FEAR `7`, HAPPY `8`, NEWS `10`, SAD `12`, SURPRISE `14`, UMANG `15`, and WIKI `16`. Default speaker/style policy cannot be selected until audio is actually synthesized and evaluated.

## Reference inference and quality baseline

**Not run.** The official card's reference path requires `transformers`, `torch`, `trust_remote_code=True`, the custom code files, and the gated model snapshot. The provided example moves model and tokenizer to CUDA; it is not CPU or Android evidence.

No emergency phrases, short/long/numeric/name/location/punctuation samples, generated audio, intelligibility observations, sample rate, initialization time, latency, RTF, RAM, or CPU figures exist for this candidate. Generated files must remain under `.research/`, which is ignored by Git.

## Architecture, frontend, vocoder, and parameter count

The official public README calls the candidate “VITS-based” and exposes custom Transformers files (`configuration_vits.py`, `modeling_vits.py`, and `tokenization_vits.py`). It does **not** expose enough readable source/configuration to determine, without speculation:

- encoder, posterior encoder, flow, decoder, or vocoder topology;
- parameter count and tensor dtypes;
- sampling rate, PCM format, dynamic shapes, or operators;
- language/speaker embedding layout;
- phonemization, text normalization, vocabulary semantics, or external frontend dependencies;
- whether waveform generation/vocoder weights are integrated into `model.safetensors` or external.

These facts are recorded as unverified in [`tts-vits-rasa13-architecture.md`](tts-vits-rasa13-architecture.md), not inferred from the generic VITS label.

## Android, ONNX, and sherpa-onnx feasibility

| Route | Status | Evidence |
|---|---|---|
| sherpa-onnx 1.13.6 | **UNSUPPORTED / unproven** | Current sherpa-onnx integration expects its VITS ONNX model/token files. The candidate publishes a custom PyTorch/Transformers package, not a sherpa-onnx package. Config/model code are gated, so format conversion cannot be assessed. |
| ONNX Runtime Mobile | **BLOCKED** | No source graph/config has been inspected; unsupported/custom operators, dynamic behavior, frontend, and waveform-generation compatibility are unknown. |
| Native runtime | **BLOCKED** | No audited architecture/frontend/vocoder implementation exists. |
| Python/PyTorch in Android | **Not acceptable** | The reference path is research-only and cannot be used in the installed app. |

FP16 and INT8 feasibility are **unknown**. No weight tensors or graph can be inspected without gate access; no quantized candidate was generated.

## Reproducible approved acquisition

`scripts/acquire-vits-rasa13.sh` pins the exact revision, requires an explicitly supplied `HF_TOKEN`, downloads only into ignored `.research/`, and writes local SHA-256/size inventory after an authorized organization account has accepted the official model gate. It does not modify production assets, AWS, CI, or Android dependencies.

## Exact blockers

1. Official repository gate requires authenticated, approved access; no authorized token or accepted gate terms were supplied.
2. Weight/config/tokenizer/custom inference code cannot be inspected or run without that access.
3. Public verified language list excludes `en`, `hi`, `gu`, and `or`, failing required iTantra coverage.
4. License metadata is repository-level only; individual asset/dependency and gated-term verification is incomplete.
5. No ONNX or sherpa-onnx package is published in the exposed official inventory.
6. No reference CPU/Android inference, quality, RAM, CPU, latency, RTF, sample-rate, or quantization evidence exists.

## Phase 2A conclusion

**B. BLOCKED.** The candidate may be revisited after authorized official access and artifact/legal inspection, but it is not ready for Android prototype work and cannot replace MMS-TTS for iTantra's required language set on current evidence.
