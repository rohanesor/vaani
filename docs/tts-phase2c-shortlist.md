# Phase 2C shortlist

## Top five candidates

The ranking is by relevance to a future iTantra architecture, not a declaration of quality or production approval.

| Rank | Candidate | HI | GU | MR | KN | ML | TA | TE | OR | BN | EN | Models | Exact size | Runtime / Android path | License / redistribution | RAM, CPU, RTF, quality | Status |
|---:|---|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|:-:|---:|---:|---|---|---|---|
| 1 | Piper Indic subset | ✓ | ✗ | ✓ | ✗ | ✓ | ✗ | ✓ | ✗ | ✓ | ✗ | 5 | 342,400,826 B ONNX | Piper native/ONNX; Android integration needs proof | repo MIT, but each selected voice has separate data terms; not cleared | Unmeasured | Rejected: 5 missing languages and rights incomplete |
| 2 | Kokoro-82M | ✓ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✗ | ✓ | 1 | 327,212,226 B weight | official PyTorch only; no official Android/ONNX package | Apache-2.0 card | Unmeasured | Rejected: insufficient coverage/runtime |
| 3 | MMS VITS ONNX baseline | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 10 | 1,140,400,586 B packs | sherpa-onnx 1.13.6 exists | CC-BY-NC upstream; conversion rights unapproved | Android unmeasured; English host RTF 0.559 only | Rejected: license/offline/device gates |
| 4 | AI4Bharat Indic Parler | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | 1 | 3,763,425,107 B | gated Transformers/Parler; no native package | Apache card, gated artifact terms unverified | Unmeasured | Rejected: size/access/runtime |
| 5 | AI4Bharat `vits_rasa_13` | ✗ | ✗ | ✓ | ✓ | ✓ | ✓ | ✓ | ✗ | ✓ | ✗ | 1 | 160,822,916 B metadata inventory | gated custom Transformers; no ONNX package | CC-BY card, assets/gate unverified | Unmeasured | Rejected: coverage/access/runtime |

`✓` means an authoritative source explicitly lists the language. It does not mean quality validation.

## Top three architecture options

| Rank | Architecture | Coverage state | Installed payload | Why it is considered | Blocking condition |
|---:|---|---|---:|---|---|
| 1 | **Approved-artifact hybrid behind `UnifiedTtsEngine`**: future native Indic backends + a separate English backend | No approved mapping yet | Unknown | Preserves automatic routing, lazy one-session lifecycle, and lets legally cleared compact voices replace individual routes | No approved all-ten artifact set exists |
| 2 | Piper subset + compact approved fallback voices | 5/10 from Piper only | 342,400,826 B plus unknown fallback | Piper is already ONNX/native and materially smaller than MMS for its covered subset | Missing five languages and unresolved per-voice licenses |
| 3 | Existing MMS VITS packages behind the locked abstraction | 10/10 research coverage | 1,140,400,586 B | Only complete current technical baseline with sherpa-onnx support | Non-commercial upstream license, conversion provenance, and single-install/device failures |

No option is production-ready. A one-model solution is not preferred over a hybrid unless it passes the same legal, Android CPU, and all-language gates.

## Scoring disposition

The Phase 2B weighted rubric remains in effect: quality 30%, coverage 15%, Android 15%, latency/RTF 15%, RAM/CPU 10%, size 5%, license 5%, maintainability 5%. Final scores are intentionally withheld because all five candidates fail at least one hard gate and four lack comparable target-device measurement. A numerical rank would imply evidence that does not exist.
