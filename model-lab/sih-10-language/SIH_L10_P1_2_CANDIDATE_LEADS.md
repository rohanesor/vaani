# Candidate Leads for Future Authoritative Verification

**Not approvals. Not evidence. Not endorsements.** These are model families known from prior public work and Vokie's own local records; every field below must be independently reconfirmed against official upstream sources before any of them can enter any Vokie approval gate. Do not integrate anything from this list.

## Families that have appeared in prior public open-source TTS work

| Family | Typical code license (must reverify) | Typical model/voice license posture (must reverify per-voice) | Runtime patterns commonly documented publicly | Known Vokie prior record |
|---|---|---|---|---|
| Piper (`rhasspy/piper-voices`) | code MIT commonly stated | voice licenses vary widely per voice; some CC0/CC-BY, some CC-BY-NC-SA (Hindi Priyamvada already in Vokie is CC-BY-NC-SA-4.0 dataset) | ONNX + espeak-ng frontend; runs under sherpa-onnx which Vokie already uses | EN/HI Piper voices already inside Vokie |
| Facebook MMS-TTS (`facebook/mms-tts-<iso6393>`) | code posture separate from weights | model cards previously observed to state CC-BY-NC-4.0 in Vokie audit | ONNX conversions have been shown to run under sherpa-onnx (Vokie’s Tamil path) | rejected for general deployment; conditional prototype only |
| AI4Bharat Indic-TTS / IndicVoices / Indic-Parler-TTS families | project code license varies | dataset/voice terms explicitly non-permissive in some records (IndicVoices-R access was blocked in Vokie prior investigation) | not established as sherpa-onnx-ready in Vokie | prior IndicVoices-R blocked record |
| Coqui TTS ecosystem | code MPL-2.0 commonly stated | voice/model terms vary; some CC-BY-NC | ONNX/PyTorch; Android inference path not established in Vokie | none |
| ESPnet-TTS ecosystem | code Apache-2.0 commonly stated | voice/dataset varies | ONNX exports possible; Android inference path not established in Vokie | none |

## Rule for the next pass

For each language (GU, MR, KN, ML, TE, OR, BN), a human/authoritative pass must produce a single evidence record with:
1. exact repository URL,
2. immutable revision/commit/tag,
3. artifact filename,
4. artifact SHA-256,
5. voice/speaker identity and terms,
6. dataset terms,
7. tokenizer identity, revision, checksum,
8. frontend identity/license,
9. Android ARM64 runtime path (preferably sherpa-onnx),
10. offline-inference evidence,
11. size / RAM,
12. published quality/benchmark if any.

Only after that record exists may a language become **FIRST_INTEGRATION_CANDIDATE**. Prior-knowledge lists are not sufficient.
