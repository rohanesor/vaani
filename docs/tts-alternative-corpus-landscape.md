# Alternative training-corpus landscape — Phase 2N

## Finding

No verified legally clean corpus or compatible combination currently covers `hi`, `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`, and `en` with evidence sufficient for commercial/competition training and trained-weight/APK redistribution.

This is a corpus decision, not another model shortlist.

| Candidate | Languages relevant here | Audio/transcript license evidence | Speaker/consent | Training | Derivative model / weights | Commercial/APK | Official availability | Decision |
|---|---|---|---|---|---|---|---|---|
| IndicVoices-R HF | 9 Indic | CC-BY-4.0 declared on card | consent/privacy/data-card review incomplete | BLOCKED by repository legal status | UNKNOWN | UNKNOWN | authenticated gated HF; revision pinned | TECHNICALLY ACCESSIBLE, LEGALLY BLOCKED |
| OpenSLR 78/79/63/64/65/66/37 | gu/kn/ml/mr/ta/te/bn | CC-BY-SA-4.0 declared in source audit | corpus-specific review required | REQUIRES_REVIEW | BLOCKED pending ShareAlike analysis | BLOCKED pending review | official OpenSLR archives | not an all-language route |
| AI4Bharat Rasa | selected expressive Indic languages, not all nine | CC-BY-4.0 declaration recorded | artifact/consent review incomplete | REQUIRES_REVIEW | UNKNOWN | UNKNOWN | official project artifacts/access | supplement only |
| LibriTTS SLR60 | en | CC-BY-4.0 declared by OpenSLR | archive/privacy review required | REQUIRES_REVIEW | UNKNOWN | UNKNOWN | official OpenSLR | conditional English source |
| Common Voice | possible language registry coverage | current release/audio terms not verified here | UNKNOWN | UNKNOWN | UNKNOWN | UNKNOWN | official registry, exact release not frozen | reject for now |
| AI4Bharat IndicVoices | broad Indic claims | license not identified in official repository audit | UNKNOWN | BLOCKED | UNKNOWN | BLOCKED | official project source, artifact unresolved | reject |

## Why a hybrid is not approved

A hybrid of LibriTTS, Rasa, and OpenSLR would still have:

- no verified Hindi and Odia training route in the reviewed alternatives;
- CC-BY-SA obligations for several OpenSLR sources;
- unresolved consent, speaker privacy, and trained-weight treatment;
- no complete immutable ten-language artifact chain.

Combining data is not approval. Each source, transcript, metadata field, transformation, and model output needs its own license and provenance record.

## Excluded shortcuts

Do not use gated data without explicit authorization, non-commercial sources for a commercial target, unofficial mirrors, scraped speech, unclear voice rights, or a model whose weights/frontend/vocoder distribution terms are incomplete. Do not infer training permission from repository code licenses.

## Next official route

Request a provider-issued metadata/license bundle or an explicitly permissive, ungated corpus covering missing Hindi/Odia, with written terms for training, preprocessing, derivatives, commercial use, and weight/APK redistribution. Until then, keep IndicVoices-R and all alternative corpora research candidates only.
