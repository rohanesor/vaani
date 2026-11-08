# TTS system engineering comparison

| Approach | Quality | Coverage | Size / RAM / RTF | Android/offline | License | Development complexity | Decision |
|---|---|---|---|---|---|---|---|
| A. Current MMS | Existing English host smoke only; all-language quality unknown | 10 routes in research archive | 1.14 GB packs; Android RAM/RTF unknown | sherpa path exists; full offline package not current behavior | CC-BY-NC upstream and conversion provenance fail | Low code change, high legal/deployment risk | Baseline only |
| B. Off-the-shelf hybrid | Candidate-specific, unvalidated | No legal complete set found | ranges from hundreds of MB to multi-GB | partial ONNX components only | incomplete per-artifact rights | Medium integration, unresolved supply chain | Reject |
| C. Distilled compact multilingual model | Unknown; must be measured | Design covers all 10 | target only; no measured size/RAM/RTF | ONNX Runtime Mobile planned | viable only with approved data and self-trained teacher | High | Conditional fallback if supervised student fails |
| D. Compact language-specific models | Potentially strong per-language pronunciation; unknown here | 10 models required | aggregate unknown; potentially duplicates vocoders | feasible only after artifact/training work | must audit each model/data chain | High packaging/maintenance | Fallback, not default |
| E. Shared acoustic model + language-specific components | Unknown; targeted for intelligibility | Design covers all 10 | target shared weights + small frontends/adapters; unmeasured | ONNX Runtime Mobile planned | viable only with approved data chain | High training, lower long-term package duplication | **Recommended** |

## Why E is recommended

It is the only option that addresses the actual unresolved constraints together: a single automatic registry-facing engine, all-ten coverage from a defined CC-BY training basis, no dependency on unlicensed third-party weights, local CPU-native deployment path, and avoidance of ten full vocoder copies. It does not yet beat MMS in measured quality, RAM, latency, or footprint; those remain prototype gates.

## Risk and mitigation

| Risk | Mitigation |
|---|---|
| IndicVoices-R per-language hours/quality imbalance | Audit manifests and enforce language-balanced splits; add only legally compatible data after review |
| Grapheme pronunciation failures for numbers/names/locations | Versioned language frontends and native-speaker emergency corpus |
| Student quality below baseline | Train own larger teacher from approved data and distill; retain teacher offline only |
| ONNX export/operator incompatibility | Select/export architecture incrementally in a lab; inspect graphs before Android work |
| 2 GB device memory/latency failure | one-session lifecycle, separate acoustic/vocoder profiling, precision experiments only after FP32 success |
| Corpus-to-weight legal ambiguity | preserve data versions/licenses/attribution; legal review before distributing student weights |
