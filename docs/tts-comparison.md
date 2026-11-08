# Phase 2B TTS comparison and benchmark record

## Hard selection gates

A candidate is not eligible unless all ten languages, offline-after-install operation, redistributable artifacts, and a realistic native Android CPU path are verified. No weighted score can override a failed gate.

| Candidate | Coverage | Offline artifact | Redistribution | Android CPU path | Hard-gate result |
|---|---|---|---|---|---|
| `vits_rasa_13` | 6/10 | not acquired; gated | unverified | unproven | Fail |
| AI4Bharat Indic Parler | 10/10 card tags | gated; 3.76 GB | Apache card metadata only | no native route; no measurement | Fail |
| AI4Bharat Indic-TTS | 10/10 release packs | published archives | code MIT, checkpoint terms/digests unverified | PyTorch/CUDA reference only | Fail |
| Current MMS ONNX | 10/10 | full packs exist only in local research/archive; app bundles English and relies on nine runtime packs | CC-BY-NC upstream / conversion provenance unapproved | sherpa-onnx exists | Fail |

## Size and quantization evidence

| Candidate | FP32/original bytes | FP16 bytes | INT8 bytes | Finding |
|---|---:|---:|---:|---|
| `vits_rasa_13` | 160,822,916 metadata inventory | Not measured | Not measured | gate prevents tensor inspection |
| AI4Bharat Indic Parler | 3,763,425,107 metadata inventory | Not measured | Not measured | no conversion attempted; cannot fit target envelope |
| Indic-TTS checkpoints | 1,513,473,097–1,535,189,785 per pack | Not measured | Not measured | no Android conversion attempted |
| Current MMS ONNX, all 10 | 1,140,396,076 | Not created | 379,959,630 aggregate candidate bytes | all ten individually measured candidates; not approved |

The MMS INT8 aggregate is the exact sum of the ten individual experimental candidate files recorded in `docs/production-benchmark.md`; it is not a production payload. The candidates failed behavior/performance checks. FP16 was not generated for any candidate.

## Measured performance evidence

| Candidate | Environment | Init | First/warm synthesis | RTF | Peak RAM | CPU | Result |
|---|---|---|---|---:|---:|---:|---|
| Current MMS English ONNX | development Linux host, official sherpa binary | included in 2.57 s process wall time | 315 ms for `Help me` | 0.559 | 171,072 KiB max RSS process | not captured | host smoke only |
| Current MMS all languages | 2 GB Android CPU | Not measured | Not measured | Not measured | Not measured | Not measured | required before approval |
| All AI4Bharat candidates | reference/Android | Not run | Not run | Not measured | Not measured | Not measured | artifact/runtime gate blocks test |

These values must not be generalized to Android. The MMS host measurement generated 564 ms of mono 16 kHz audio from seven English characters. It is neither a quality score nor an end-to-end receiver latency.

## Quality benchmark protocol

No candidate passed source and runtime gates, so no quality winner is claimed. Generated audio is not committed. Before a candidate advances, an approved native-speaker corpus must contain, for each language:

1. emergency sentence;
2. normal sentence;
3. location sentence;
4. numeric sentence;
5. longer sentence.

For every synthesis, retain outside Git under `.research/`: source/revision/checksum, exact input, speaker/style, runtime/build/device details, sample rate, output duration, initialization/first/warm latency, RTF, PSS/RSS, CPU utilization, and listener ratings for intelligibility, pronunciation, naturalness, prosody, artifacts, and failure. At least two qualified native listeners per language must review blinded samples before an architecture can receive a quality score.

## Weighted scoring rubric

| Criterion | Weight | Scoring rule |
|---|---:|---|
| Quality | 30% | blinded native-listener results |
| Language coverage | 15% | verified required languages |
| Android deployment | 15% | reproducible native ARM CPU package |
| Latency / RTF | 15% | target-device measurements |
| RAM / CPU | 10% | target-device PSS/RSS and CPU measurements |
| Model size | 5% | verified installed payload |
| License | 5% | artifact-specific redistribution approval |
| Maintainability | 5% | pinned source, reproducible build, stable runtime |

No final numerical score is assigned because every candidate fails a hard gate and lacks target-device quality/performance evidence. Assigning numbers would falsely suggest comparability.
