# Verified TTS artifact matrix

## Interpretation

`PASS` requires an official downloadable artifact, explicit terms permitting commercial redistribution, and a realistic Android-native CPU route. No row currently passes all gates. “No candidate” means no artifact satisfying the phase filters was found, not that no model exists anywhere.

| Language | Candidate / exact artifact | Artifact URL and revision | License evidence | Commercial / redistribution | Size | Runtime | Android / ONNX / CPU | Status |
|---|---|---|---|---|---:|---|---|---|
| Hindi | Kokoro `kokoro-v1_0.pth` | `https://huggingface.co/hexgrad/Kokoro-82M`, `f3ff3571791e39611d31c381e3a41a3af07b4987` | Apache-2.0 card and weights | Yes under Apache-2.0 | 327,212,226 B | PyTorch official | no official Android or ONNX artifact | Fail runtime |
| Gujarati | No verified artifact | — | — | — | — | — | — | Blocked |
| Marathi | Piper `mr_IN-google-medium.onnx` | `https://huggingface.co/rhasspy/piper-voices`, `39ab474be869e9181350af6a65e4953eef67aaa0` | card identifies CC-BY-SA 4.0 training dataset; no explicit model-weight grant | not established for resulting model | 76,768,179 B | Piper ONNX | ONNX/native possible; Android not measured | Fail rights |
| Kannada | No verified artifact | — | — | — | — | — | — | Blocked |
| Malayalam | Piper `ml_IN-arjun-medium.onnx` | Piper revision above | card points to external Kaggle URL; no license text | not established | 62,950,044 B | Piper ONNX | ONNX/native possible; Android not measured | Fail rights |
| Tamil | No verified artifact | — | — | — | — | — | — | Blocked |
| Telugu | Piper `te_IN-maya-medium.onnx` | Piper revision above | card points to external IndicTTS license PDF; not verified here | not established | 62,950,044 B | Piper ONNX | ONNX/native possible; Android not measured | Fail rights |
| Odia | No verified artifact | — | — | — | — | — | — | Blocked |
| Bengali | Piper `bn_BD-google-medium.onnx` | Piper revision above | card identifies OpenSLR CC-BY-SA 4.0 and CMU terms; no explicit model-weight grant | not established for resulting model | 76,782,515 B | Piper ONNX | ONNX/native possible; Android not measured | Fail rights |
| English | Kokoro `kokoro-v1_0.pth` | Kokoro revision above | Apache-2.0 card and weights | Yes under Apache-2.0 | 327,212,226 B | PyTorch official | no official Android or ONNX artifact | Fail runtime |

## Architecture footprint comparison

| Architecture | Verified model payload | Frontend/vocoder/runtime | Final installed size | Legal / Android result |
|---|---:|---|---:|---|
| Current MMS ten packs | 1,140,400,586 B incl. tokens | sherpa AAR is separately pinned; extraction duplicates storage | Not measured | Fail legal/provenance/device gates |
| Piper five-language subset | 342,400,826 B ONNX, plus small JSON files | Piper native runtime not integrated or measured | Not measured | Fail five missing routes and per-voice rights |
| Kokoro Hindi + English | 327,212,226 B weight, plus selected voice data not inventoried | official Python runtime; no approved Android runtime | Not measured | Fail missing eight routes/runtime |
| Future legal hybrid | Unknown | Must include audited frontend/vocoder/native runtime | Unknown | No artifact set selected |

No APK contribution, RAM, CPU, latency, RTF, or quality figure is supplied because no legal complete artifact architecture can be installed or tested. File size must not be used as a RAM estimate.
