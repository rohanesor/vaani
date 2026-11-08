# SIH-L10-P1.6 Android TTS Adaptation Prototype

Date: 2026-09-04
Branch: `sih/laptop2-model-lab`
P1.5 base: `197f36d`

This phase prepares one language only. It does not modify Android production
source, APK assets, `MANIFEST.json`, or any transport/runtime behavior.

## 1. Selected Language

Selected target: **Gujarati (`guj`)**

Gujarati was selected because it is `ADAPTATION-REQUIRED`, is already present
in the current `VokieLanguage` and `TtsLanguage` routing, follows the documented
MMS character-frontend path, and had the best P1.4 desktop performance among
the candidates: 430.1 ms warm median, RTF 0.364, and 560.0 MB peak RSS.

No target was selected from the desktop-slower Bengali/Marathi group, and
Punjabi remains blocked by missing application routing.

## 2. Fastest Viable Language Set

These classifications are unchanged from P1.5.

| Language | P1.5 classification | Android blocker | Conversion required | Tokenizer/frontend requirement | Model packaging requirement | Runtime changes required | Can be prepared independently of Android source? | Priority |
|---|---|---|---|---|---|---|---|---|
| Telugu (`tel`) | `ADAPTATION-REQUIRED` | Source is safetensors, not sherpa ONNX; no physical-device evidence | Yes | MMS character map, `add_blank=true`, no espeak | `model.onnx` + `tokens.txt` | None for existing route after valid pack | Yes | P1 |
| Bengali (`ben`) | `ADAPTATION-REQUIRED` | Same format gap; P1.4 desktop RTF 2.587 needs performance research | Yes | MMS character map, no espeak | `model.onnx` + `tokens.txt` | None for existing route after valid pack | Yes | P2 research gate |
| Marathi (`mar`) | `ADAPTATION-REQUIRED` | Same format gap; P1.4 desktop RTF 2.544 needs performance research | Yes | MMS character map, no espeak | `model.onnx` + `tokens.txt` | None for existing route after valid pack | Yes | P2 research gate |
| Gujarati (`guj`) | `ADAPTATION-REQUIRED` | Source is safetensors, not sherpa ONNX; physical Android evidence pending | Yes | MMS character map, `guj`, `add_blank=true`, `phonemize=false` | `model.onnx` + `tokens.txt` | None for existing route after valid pack | Yes | P0 selected |
| Kannada (`kan`) | `ADAPTATION-REQUIRED` | Source is safetensors, not sherpa ONNX; no physical-device evidence | Yes | MMS character map, `add_blank=true`, no espeak | `model.onnx` + `tokens.txt` | None for existing route after valid pack | Yes | P1 |
| Malayalam (`mal`) | `ADAPTATION-REQUIRED` | Source is safetensors, not sherpa ONNX; no physical-device evidence | Yes | MMS character map, `add_blank=true`, no espeak | `model.onnx` + `tokens.txt` | None for existing route after valid pack | Yes | P1 |
| Punjabi (`pan`) | `BLOCKED` | No Punjabi value exists in `VokieLanguage` or `TtsLanguage` | Technically yes, but not an app integration target | MMS character map, `add_blank=true`, no espeak | Do not package for Vokie yet | Domain/routing decision required | Technical conversion yes; product integration no | Blocked |

### Immediate adaptation

- Gujarati is the selected and executed prototype.
- Telugu, Kannada, and Malayalam are the next technically viable targets after
  the Gujarati export and Android gates are reviewed.

### Additional research

- Bengali and Marathi require explicit performance/UX research because P1.4
  desktop synthesis was slower than real time. This does not change their
  P1.5 classification.

### Application-routing blocker

- Punjabi cannot be routed by the current app. Its technical artifact path is
  documented, but no Punjabi binary should be promoted into Vokie packaging.

## 3. Source Model

| Field | Value |
|---|---|
| Repository | `facebook/mms-tts-guj` |
| Revision | `b72e80a7eeca90b72e0af2e2d00b77a336ce242d` |
| Source artifact | `model.safetensors` |
| Source bytes | 145,244,408 |
| Source SHA-256 | `f1f4e01188507d3cc8526d1326a6f1c8a9b51e5fd9abe7a92b500326808a0c6a` |
| Local source directory | `C:\tts\model-lab\models\tts\mms-official\gu` |
| License | CC-BY-NC-4.0; dataset/voice-specific terms remain unknown |

The source directory also contains the immutable revision's `config.json`,
`vocab.json`, `tokenizer_config.json`, and `special_tokens_map.json`. The
source checkpoint is verified before loading and is never written by the
prototype.

## 4. Adaptation Steps

The committed script is:

`model-lab/tools/prepare_mms_guj_sherpa.py`

It performs these steps:

1. Require all five source files and reject a missing source directory.
2. Verify the source checkpoint byte hash and expected Gujarati revision data.
3. Verify VITS architecture, 16 kHz sampling rate, one speaker, vocabulary
   size 60, contiguous IDs 0..59, Gujarati language, `add_blank=true`, and
   `phonemize=false`.
4. Load the local tokenizer and safetensors checkpoint with network access
   disabled after acquisition.
5. Export a new ONNX inference graph at opset 13 to a temporary directory.
6. Generate `tokens.txt` from the exact source vocabulary sorted by integer ID.
7. Add sherpa metadata without altering source weights.
8. Validate ONNX graph structure, ONNX Runtime execution, sherpa configuration,
   and one native Gujarati synthesis.
9. Write `adaptation_metadata.json` with source/output hashes and tool versions.

The output directory must be new and outside the source directory. This avoids
overwriting source artifacts or silently reusing a stale converted package.

## 5. Required Runtime Configuration

The generated package uses the existing runtime contract:

```text
OfflineTtsConfig(
    model = OfflineTtsModelConfig(
        vits = OfflineTtsVitsModelConfig(
            model = ".../model.onnx",
            tokens = ".../tokens.txt",
            lexicon = "",
            data_dir = "",
            dict_dir = "",
        ),
        num_threads = 1,
        provider = "cpu",
    ),
    max_num_sentences = 1,
)
```

The application engine later uses the same model paths with sherpa-onnx AAR
version 1.13.6, CPU provider, up to four threads, `sid=0`, and a model-reported
sample rate. No Android source change is part of this phase.

## 6. Tokenizer and Frontend Configuration

The Gujarati source configuration is:

- tokenizer class: `VitsTokenizer`
- language: `guj`
- vocabulary size: 60
- `add_blank`: true; blank ID is 0
- `pad_token`: `|`, ID 0
- `unk_token`: `<unk>`
- `phonemize`: false
- `is_uroman`: false
- frontend: Unicode Gujarati character map
- espeak-ng: not required

The validation sentence tokenized to 35 IDs, including interspersed blank IDs.
The source period is not in the character vocabulary and sherpa reports it as
an ignored unknown character, consistent with the P1.5 character-frontend
finding. This is not a model-load failure, but punctuation behavior needs
human listening review before release use.

## 7. Output Artifact Format and Checksums

Generated outside Git at:

`C:\tts\model-lab\models\tts\mms-guj-p1-6-final2`

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `model.onnx` | 114,433,041 | `5042d4f1144ca97a324357850184d5924ea6268d55758898d2706119368a4414` |
| `tokens.txt` | 402 | `2d855f2affb7586cc6be095a4382eb0bee2a22242deda32c86aab6b1a810d8c4` |
| `adaptation_metadata.json` | lightweight metadata | machine-local; not a model payload |

The ONNX graph has:

```text
opset: 13
x: INT64[N,L]
x_length: INT64[N]
noise_scale: FLOAT[1]
length_scale: FLOAT[1]
noise_scale_w: FLOAT[1]
y: FLOAT[N,1,L]
```

Metadata records `model_type=vits`, `language=guj`, `add_blank=1`,
`frontend=characters`, `n_speakers=1`, and `sample_rate=16000`.

The same export produced identical model and token hashes in repeated runs.

## 8. Local Validation

Environment used by the reproducible script:

| Tool | Version |
|---|---|
| Python | 3.11.16 |
| PyTorch | 2.14.0+cpu |
| Transformers | 5.16.1 |
| ONNX | 1.22.0 |
| ONNX Runtime | 1.29.0 |
| sherpa-onnx | 1.13.6 |

Results:

| Check | Result |
|---|---|
| Source hash verification | PASS |
| Source tokenizer/config validation | PASS |
| ONNX checker | PASS |
| ONNX interface and opset | PASS |
| ONNX Runtime inference | PASS; non-empty output |
| sherpa config validation | PASS |
| sherpa model load | PASS |
| tokenizer/frontend load | PASS |
| deterministic token input | PASS; fixed 35-ID sequence |
| one synthesis | PASS |
| sample rate | PASS; 16,000 Hz |
| audio | PASS; 24,805 samples, 1.550312 s |
| finite waveform | PASS |
| clipping | PASS; peak 0.193864, clip fraction 0 |

The VITS model contains stochastic inference components. “Deterministic” here
means the source, tokenizer output, export settings, and validation input are
fixed; the generated waveform is not claimed to be bit-identical across every
native runtime execution.

## 9. Known Android Risks

- No physical ARM64 Android device test was performed.
- APK extraction, manifest verification, native heap/PSS, thermal behavior,
  audio playback, and interrupted extraction remain unverified.
- The exporter emitted legacy TorchScript tracer warnings for shape-dependent
  Transformer branches. Dynamic axes are present and the tested Gujarati
  package loads, but multiple text lengths and batch behavior still require
  validation.
- The prototype preserves sherpa control inputs in the graph interface through
  zero-valued dependencies; it uses the Transformers checkpoint defaults for
  noise and duration. `length_scale`/app speech-speed semantics are therefore
  not proven by this prototype and must not be called production-compatible.
- The source period is skipped as an unknown character by the character
  frontend.
- Desktop peak RSS was 560.0 MB; Android memory and thermal behavior are
  unknown.
- CC-BY-NC-4.0 permits only the approved non-commercial context, pending legal
  review of dataset and voice terms.

## 10. Exact Handoff Instructions for Laptop 1

1. Obtain the two output files through the protected artifact channel, not Git:
   `model.onnx` and `tokens.txt` from the output directory above.
2. Obtain `adaptation_metadata.json` with the files so the source lineage and
   output hashes can be independently checked.
3. Verify the two SHA-256 values in section 7 before staging anything.
4. Place the verified files in a protected model archive under
   `tts/guj/model.onnx` and `tts/guj/tokens.txt` only after the Android device
   gate is scheduled.
5. Run the existing staging and APK verification procedures in the protected
   release environment. Do not edit the production manifest by hand.
6. Validate the pack on the ARM64 device using sherpa-onnx AAR 1.13.6 before
   any release promotion.

To reproduce the package rather than transfer it, Laptop 1 needs the committed
script plus the immutable Gujarati source files and the pinned `uv` dependency
set listed in section 8. Reproduction output must be compared to the recorded
hashes before use.

## 11. Files Laptop 1 Must Obtain

For Android integration:

- `model.onnx` with the section 7 hash
- `tokens.txt` with the section 7 hash
- `adaptation_metadata.json`
- This adaptation report and the committed conversion script

For independent reproduction only:

- Gujarati source files from the exact P1.4 revision
- `config.json`
- `model.safetensors`
- `special_tokens_map.json`
- `tokenizer_config.json`
- `vocab.json`

Source files are not Android runtime inputs and should remain in the protected
conversion environment.

## 12. Files Laptop 1 Must Not Obtain for Android Runtime

- The source `model.safetensors` checkpoint as an APK/runtime asset
- Hugging Face tokenizer/config files as Android assets
- `pytorch_model.bin` or any alternate checkpoint
- WAV samples or temporary benchmark audio
- Python, PyTorch, Transformers, ONNX Runtime, or sherpa development wheels
- `.venv`, `build`, Gradle intermediates, native binaries, or generated APKs
- Any unverified ONNX variant or quantized candidate
- Punjabi artifacts until application routing is explicitly resolved

## Final Status

Gujarati adaptation is **host-validated and structurally sherpa-compatible**,
not Android-approved. The next gate is Laptop 1's protected ARM64 device
validation with the exact two-file package and pinned 1.13.6 AAR.
