# N5 — Full Text + Lightweight Semantic Metadata

## Architecture

N5 augments, never replaces, Vokie text communication:

`STT transcript → SemanticExtractor → Message(text + optional semanticMetadata) → PacketV2 → existing transport → normal receiver persistence → existing translation → existing TTS`.

`Message.text` remains authoritative for display, translation, TTS, deduplication, retries, ACKs, and ordering. Semantic metadata has no autonomous action path.

## Data model and ontology

`SemanticMetadata` is a bounded version-1 seven-byte hint:

- event category: UNKNOWN, MEDICAL, FIRE, ACCIDENT, TRAPPED, MISSING, THREAT, FLOOD, EARTHQUAKE, GENERAL_EMERGENCY, REQUEST_ASSISTANCE
- urgency: UNKNOWN, ROUTINE, URGENT, CRITICAL
- severity: UNKNOWN, LOW, MODERATE, HIGH, CRITICAL
- hazard bitset: FIRE, SMOKE, WATER, GAS, ELECTRICAL, STRUCTURAL, VEHICLE, CROWD
- victim count: only 1–254 when explicitly stated; otherwise unspecified
- certainty: UNKNOWN or EXPLICIT

Location reference is intentionally omitted: the current transcript rules cannot extract it safely.

## Extraction and negation

`SemanticExtractor` is a tiny offline rule/gazetteer implementation. It recognizes a bounded English/Hindi/Tamil/romanized-common-term set for communication, emergency, medical, device, and hazard phrases. It uses a conservative ±16-character negation window for `no`, `not`, `without`, Hindi negation, and Tamil negation.

Example: `There is no fire here` does not set FIRE. The extractor is not a syntax parser; multi-clause and long-distance negation are known limitations.

## Multilingual/code-switch behavior

N5 does not modify N3. It recognizes explicit common semantic words in Hindi+English and Tamil+English examples (`mujhe doctor chahiye`, `enakku doctor venum`, `fire irukku`) but returns UNKNOWN rather than guessing arbitrary language/meaning. Latin-only/romanized ambiguity remains conservative.

## PacketV2 integration and compatibility

For text-only messages PacketV2 byte layout is unchanged. For an available semantic hint, a PacketV2 flag and an 8-byte extension are appended: one length byte plus seven metadata bytes. New decoders accept legacy packets with no extension; absent/malformed semantic data does not alter text parsing.

This is **forward-compatible within N5-capable peers**, not binary backward compatibility with an older deployed PacketV2 decoder that rejects unrecognized trailing bytes. Full text-only packets remain interoperable with older peers. Capability negotiation is future work; no claim of seamless enriched-message interoperation with old app binaries is made.

## Persistence, retries, and deduplication

Room schema version 6 stores metadata as Base64 of the seven-byte hint. N2 retries retain the same message ID, text, sequence number, and semantic metadata. Received-packet deduplication remains `(sourceDeviceId, messageId, sequenceNumber)`, so retried enriched messages do not produce duplicate semantic UI/translation/TTS events.

## Frontend

Existing message cards show a compact category/urgency label only when metadata is available. UNKNOWN metadata is hidden. There are no semantic dashboards, debug labels, automatic actions, or navigation changes.

## Performance measurement

JVM engineering micro-measurement output: `app/build/reports/semantic/n5-performance.json`.

Representative run (five messages; 5,000 extraction calls):
- extraction: 400,527,700 ns total, about 80.1 µs/call on the build JVM
- wire serialization: 2,041,100 ns for 10,000 calls, about 0.20 µs/call
- metadata alone: 7 bytes
- PacketV2 enriched overhead: 8 bytes per fragment
- representative packets: 103→111 bytes (7.8%), 121→129 (6.6%), 138→146 (5.8%), 196→204 (4.1%)

These are JVM micro-measurements, not Android CPU/RAM/end-to-end latency benchmarks.

## Known limitations and future work

- Experimental bounded rules; not production-grade emergency classification.
- No capability negotiation for enriched packets with old deployed binaries.
- No location reference extraction.
- No field accuracy, Android memory/CPU, or two-device validation.
- Future work: negotiated extension support, larger evaluated multilingual corpus, carefully reviewed phrase/negation handling.
