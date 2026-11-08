# Phase 2B TTS candidate landscape

## Scope and method

Candidates were admitted only when an official publisher source exposed enough information to assess the required language matrix, artifact format, and license declaration. Search-result mirrors are not authoritative. A candidate fails selection if it fails coverage, offline redistribution, Android CPU feasibility, or reproducibility, regardless of any numerical score.

## AI4Bharat ecosystem

### `ai4bharat/vits_rasa_13`

Phase 2A result remains **BLOCKED**. Its official card lists six required iTantra languages only (Bengali, Kannada, Malayalam, Marathi, Tamil, Telugu); Hindi, Gujarati, Odia, and English are absent. The pinned repository is gated and publishes no Android/ONNX package. See [`tts-vits-rasa13-evaluation.md`](tts-vits-rasa13-evaluation.md).

### `ai4bharat/indic-parler-tts`

- Official source: https://huggingface.co/ai4bharat/indic-parler-tts
- Pin: `7b527af5ee8ed1f9a28d80b19703ed9bb8ba10ca`
- Public card metadata: Apache-2.0; language tags include all ten required codes.
- Official inventory: a 3,751,321,772-byte `model.safetensors`, 10,272,460-byte tokenizer JSON, 1,795,391-byte tokenizer model, and small configs; total **3,763,425,107 bytes**.
- Packaging: gated `transformers` / `parler_tts` safetensors package, not ONNX or sherpa-onnx.

It is **not viable** for a 2 GB RAM CPU-only Android target: model payload alone exceeds the device-memory target and available artifact inspection/inference is blocked by the gate. Apache-2.0 card metadata does not compensate for the size/runtime failure.

### AI4Bharat Indic-TTS repository and checkpoint release

- Official source: https://github.com/AI4Bharat/Indic-TTS
- Audited code commit: `ad2461c22c373f89a140c5d9fb617b101219bdad`
- Code license: MIT.
- Official checkpoint release: `v1-checkpoints-release`, published 2023-01-18.
- Architecture declared by official README: monolingual FastPitch acoustic model plus HiFi-GAN V1; reference setup requires PyTorch and CUDA configuration.
- Coverage: nine required Indic languages plus a separate English checkpoint release.
- Size: each official language zip is **1,512,955,815–1,535,189,785 bytes**; the ten required packs total **15,183,608,993 bytes** (14.14 GiB) before extraction/runtime overhead.

The repository license covers code, but the GitHub release API provides no per-asset digest and no separately verified checkpoint/data/vocoder redistribution statement. There is no Android package, ONNX model, or CPU-device benchmark. **Not viable.**

## MMS-TTS baseline

The local research archive contains ten VITS ONNX models plus tokens. The installed app currently bundles only English and obtains the other nine packs through the existing runtime-pack path, so it does not meet the required single-install offline rule. It is technically the most mature research baseline in this repository:

- all ten target languages exist;
- exact local checksums and per-language sizes are in `app/src/main/assets/models/manifest.json`;
- all graphs are ONNX opset 13 with FP32 initializers;
- sherpa-onnx 1.13.6 has an existing lazy single-session Android integration;
- host smoke evidence exists only for English: 315 ms synthesis for 564 ms generated audio, RTF 0.559, with 171,072 KiB maximum host RSS including process startup.

However, this is **not an approved production selection**. The official Facebook MMS model cards declare CC-BY-NC-4.0, which does not permit commercial use. The local ONNX conversion source/revision and conversion authorization are also not recorded. The ten ONNX files total **1,140,396,076 bytes**; with token files, the complete packs total **1,140,400,586 bytes**; the app must further prove final APK/install footprint, peak PSS/RSS, latency, and quality on a 2 GB Android device. Existing dynamic INT8 experiments reduce individual model files to about 38 MB but are slower in host testing and six output waveform lengths differ, so they are rejected for now.

## Other systems

No other official open candidate found in this audit provided all ten required languages plus a pinned, redistributable, Android-native CPU package. Piper is a native/ONNX ecosystem, but no official all-ten Indic voice set was identified; it cannot fill the mandatory matrix. Cloud, proprietary, Python-at-runtime, CUDA-only, and unpinned community conversions are excluded by product requirements.

## Conclusion

There is **no eligible production artifact architecture** yet. The desired user experience remains one automatic engine; the missing work is legal/model acquisition and target-device evidence, not a user-facing language selector or runtime downloads.
