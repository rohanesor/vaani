# Multilingual training-corpus options

## Option A — IndicVoices-R primary corpus

Nine Indic languages from IndicVoices-R plus LibriTTS English. This remains the preferred data architecture because it is explicitly TTS-oriented, multilingual, and CC-BY-4.0 at the published dataset level. It cannot start until the official provider restores/document access and supplies immutable archive/manifests/data-card evidence.

## Option B — alternative supplement corpus

Use OpenSLR Gujarati, Kannada, Malayalam, Marathi, Tamil, Telugu, and Bengali datasets, optionally adding AI4Bharat Rasa for Bengali/Tamil expressiveness, plus LibriTTS English. This is insufficient as a full path: Hindi and Odia remain without verified corpus routes, all OpenSLR datasets are CC-BY-SA-4.0, and per-language metadata has not been audited. It is a future supplement, not a training authorization.

## Option C — hybrid corpus

If IndicVoices-R access is restored, combine it with only reviewed supplemental sources where per-language quality, vocabulary, sample rate, speaker balance, and license obligations justify inclusion. Keep dataset identity in every record and balance batches by language/speaker. This is the strongest prospective path, but remains blocked pending Option A access and compatibility review.

## Language/speaker conditioning strategy

Keep the locked shared acoustic student plus shared vocoder. Use an explicit language embedding for every routed language. Do not include raw speaker identity in the production API. During training, speaker IDs can condition multi-speaker data or support balancing, but the initial deployment voice should use a selected stable speaker/style embedding or language adapter only after held-out native-listener evaluation. Small language adapters are preferred over separate full acoustic/vocoder copies when data shows consistent language-specific pronunciation failures.

## Data quantities

There is no reliable universal minimum-hour threshold. Minimum/good/excellent hours per language are **UNKNOWN / REQUIRE PROTOTYPE** because quality depends on speaker diversity, transcript accuracy, recording consistency, architecture, and target intelligibility. Per-language figures will be computed only from official manifests. Do not set curriculum or sampling weights from aggregate published hours.

## Emergency vocabulary strategy

Corpus transcripts may not contain emergency vocabulary. Maintain a separately licensed, native-reviewed evaluation set for emergency prompts, numbers, names, locations, medical terms, directions, warnings, and code-switching. Do not add supplemental recordings until their consent/license and text provenance are explicitly documented.

## Decision tree

- If official IndicVoices-R access is restored with immutable metadata: **Option C**, hybrid corpus after compatibility review.
- If access remains unavailable but an official CC-BY/CC0 corpus path is verified for Hindi and Odia: evaluate an alternative full corpus path.
- With current evidence: **Option D — stop training.**
