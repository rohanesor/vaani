# IndicTrans2 offline translation investigation

**Status: blocked before model acquisition and laptop benchmark.** This is a provenance and feasibility investigation only. No checkpoint, tokenizer, runtime, or dataset was downloaded; no Android or production translation integration was changed.

## Official source and candidate artifacts

Official source examined: `AI4Bharat/IndicTrans2`, Git revision
`4f08e39cc6bf13cd62e2445dc725f22bff1a9219` (`main`/`HEAD`, observed 2026-08-31).
The official repository's model table identifies these Hugging Face artifacts:

| Direction family | Official artifact | HF revision observed | Primary weights (`model.safetensors`) |
|---|---|---:|---:|
| EN -> Indic | `ai4bharat/indictrans2-en-indic-dist-200M` | `173b94239f7c38886b2747b8d4a5db771a7e1232` | 1,098,427,592 bytes |
| Indic -> EN | `ai4bharat/indictrans2-indic-en-dist-200M` | `eb9e49d81077cfc5311e82ff36d8c1fc11557b5d` | 913,353,672 bytes |
| Indic -> Indic | `ai4bharat/indictrans2-indic-indic-dist-320M` | `ffb7582b6d43791f1fb26b2153fc065f2e9ea575` | 1,283,534,352 bytes |

The direct Hindi <-> Tamil requirement selects the **Indic-Indic distilled 320M** model. It is not a 200M model. EN<->HI/TA requires the two separate 200M direction-family artifacts. Therefore all required directions require three model artifacts, approximately **3.30 GB** of safetensors weights alone, before tokenizer files, runtime, allocator overhead, or duplicate in-memory models.

## Provenance and license

- Official project license file: MIT.
- The official repository's artifact-license table states that model checkpoints are MIT.
- Hugging Face API metadata for all three named `ai4bharat` artifacts reports `license: mit` and `library_name: transformers`.
- This evidence identifies the official source and revisions, but final redistribution approval still requires preserving the exact artifact LICENSE/model-card files in the acquisition record when acquisition is authorized.

## Tokenizer and runtime

The official Hugging Face interface documents `transformers` `AutoModelForSeq2SeqLM` with `trust_remote_code=True`. It also documents migration of HF-compatible tokenizer handling to **IndicTransToolkit**. Repository metadata lists `model.SRC` and `model.TGT` tokenizer artifacts for each checkpoint.

This is not presently an Android-native runtime or an Android CTranslate2 release. No official CTranslate2-compatible or ONNX Android artifact was verified in this investigation. `trust_remote_code=True` is not acceptable as an Android production integration strategy without a separately audited, local implementation.

## Required language directions

The official three-artifact layout covers the desired families:

| Direction | Candidate |
|---|---|
| EN -> HI, EN -> TA | EN-Indic distilled 200M |
| HI -> EN, TA -> EN | Indic-EN distilled 200M |
| HI -> TA, TA -> HI | Indic-Indic distilled 320M |

The exact source/target language tags and tokenizer preprocessing must be validated from the approved local artifact and official toolkit at benchmark time. They must not be guessed or fetched remotely at inference time.

## Measurement status

No laptop model load, translation, cold latency, warm latency, peak RAM, quality result, or Android measurement exists. The current host has no `transformers`, `torch`, `sentencepiece`, `ctranslate2`, or `onnxruntime` package installed and no IndicTrans2 artifact present. No test sentence output is reported because no model ran.

## Android feasibility conclusion

**Not recommended for current Device A integration.** The smallest complete direct EN/HI/TA set is already ~3.30 GB weights. It cannot be described as small, and its runtime RAM/latency on RMX3782 alongside Whisper and future TTS is unknown. There is also no verified Android-native runtime artifact.

## Recommended next step, if acquisition is explicitly approved

1. On the designated benchmark laptop only, obtain the three exact official artifacts at the revisions above plus their local tokenizer dependencies and license files.
2. Pin checksums, sizes, source URLs, revisions, and license copies in an acquisition manifest.
3. Run entirely offline after preparation: load/tokenizer tests and the five supplied EN/HI/TA emergency sentences in every required direction.
4. Record literal outputs, cold load, first translation, five warm translations, median warm latency, and peak process RAM.
5. Stop if the three-artifact footprint, RAM, output quality, legal record, or Android runtime path is unacceptable. Do not substitute cloud translation, a different model, or a dictionary without separate approval.
