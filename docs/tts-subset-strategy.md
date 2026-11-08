# Multilingual subset strategies

## Scope and evidence limits

This is an acquisition design, not a corpus result. IndicVoices-R currently exposes exact published utterance counts, but complete duration and speaker distributions have not been safely acquired. The following values are targets or formulas, explicitly marked as estimates. No language's hours or speaker count is fabricated.

The weakest known language is Gujarati at 1,791 published utterances; Telugu has 47,547. The 26.5x utterance range shows why equal percentages are inappropriate. Gujarati is a hard upper bound of 1,791 utterances for any subset from this revision. It becomes a hard hours/speaker bound only after exact Gujarati duration and speaker metadata are acquired.

## Candidate strategies

| Strategy | Rule | Expected language balance | Expected speakers | Usefulness | Feasibility |
|---|---|---|---|---|---|
| A. Equal utterance count | Set N to the smallest verified usable language count after filtering; take N per language | Exact utterance balance; not hour balance | Unknown until metadata is acquired | Simple, but duration and speaker skew can remain | High storage predictability after audit |
| B. Equal estimated hours | Set H to the smallest verified usable language hours; sample each language to H | Equal hours, count varies | Unknown; may overuse a few speakers | Better acoustic exposure balance | Requires duration metadata and bounded selection |
| C. Speaker-balanced | Choose a common speaker quota and per-speaker hour/utterance cap; fill language target without replacement | Best speaker exposure balance | Controlled after speaker audit | Preferred for a multi-speaker student | Most complex; Gujarati may have too few speakers |
| D. Maximum under 2:1 | Maximize usable hours subject to max/min language hours <= 2 | At most 2:1 hours | Unknown; add speaker caps | Efficient but risks Gujarati underrepresentation | Requires complete exact audit |

No strategy can be selected from current metadata alone. Strategy A must not be used as a proxy for equal hours.

## Budget scenarios

All figures below are **estimates/targets**, not available corpus measurements. They include nine Indic languages and English.

| Per-language target | Indic total | English | Total | Raw PCM at 22.05 kHz mono 16-bit | Raw PCM at 48 kHz mono 16-bit |
|---:|---:|---:|---:|---:|---:|
| 10 h | 90 h | 10 h | 100 h | 15.876 GB | 34.560 GB |
| 25 h | 225 h | 25 h | 250 h | 39.690 GB | 86.400 GB |
| 50 h | 450 h | 50 h | 500 h | 79.380 GB | 172.800 GB |
| 100 h | 900 h | 100 h | 1,000 h | 158.760 GB | 345.600 GB |

Raw bound formula: `hours × sample_rate × 2 bytes`. Add metadata, indexes, temporary conversion output, validation copies, and reserve space; compressed WAV/FLAC size must be measured after legitimate acquisition and must not be assumed.

## Recommended initial strategy

Recommend **C, speaker-balanced, with a 25-hour-per-language target and a 10-hour fallback**:

1. Acquire no more than the approved bounded target after legal approval.
2. Audit exact duration, speaker, gender, scenario, task, region, transcript, and quality fields.
3. Set `H = min(25 hours, verified usable Gujarati hours, verified usable hours of every other language)`.
4. If `H < 10`, stop and report Gujarati as insufficient rather than silently changing the target.
5. Within each language, select speakers using a deterministic seeded procedure, cap each speaker's hours, retain gender/scenario/task strata where available, then select utterances to reach H.
6. Reserve speakers—not utterances—for validation and test.

This is not a claim that 25 hours or 10 hours exists for Gujarati. It is a controlled decision rule. Gujarati's 1,791 utterances remain the maximum candidate pool before filtering, deduplication, and split reservation.

## Expected training value and hardware

- 10 h/language: smoke/feasibility run; likely weak speaker and emergency coverage.
- 25 h/language: recommended first shared-student target; enough to test language conditioning, frontends, and common vocoder assumptions while limiting storage.
- 50 h/language: stronger quality experiment after successful 25-hour validation.
- 100 h/language: scale experiment only after measured GPU, storage, and quality gates.

GPU VRAM, GPU count, wall time, CPU inference, RAM, RTF, and quality remain **REQUIRES MEASUREMENT**. No GPU requirement is asserted.

## Selection and split algorithm

- Freeze source revision and source file checksums.
- Filter invalid records deterministically.
- Deduplicate normalized text/audio identity.
- Group by `speaker_id`; reserve disjoint test speakers, then validation speakers.
- Stratify selection by gender/scenario/task/region only when counts support it.
- Use a published seed and record selected source row/file identities.
- Reject a candidate if any speaker crosses splits or if its measured hours exceed the budget.
- Recompute all statistics from the final manifest.

The resulting manifest must identify exact source files and row groups eventually required; no selection has been made in this phase.
