# N5 Validation Report

## Status matrix

| Area | Status | Evidence |
|---|---|---|
| Architecture integration | PASS | Transcript remains `Message.text`; metadata is optional supplemental field. |
| Semantic extraction | PASS | Deterministic event/urgency/severity/hazard/victim rule tests. |
| Negation handling | PASS | Explicit no-fire/no-injury false-positive tests. |
| Multilingual/code-switch support | PASS (bounded) | Hindi+English and Tamil+English doctor/fire tests; N3 suite retained. |
| PacketV2 compatibility | PARTIAL | New decoder accepts legacy text-only packets; older deployed decoder cannot parse enriched tail. |
| N1 regression | PASS | Full suite includes peer/session routing tests. |
| N2 regression | PASS | Full suite includes retry/requeue/ACK/dedup tests; metadata persists with message. |
| N3 regression | PASS | Full suite includes code-switch analyzer/coordinator tests. |
| Translation regression | PASS | Receiver translation tests pass; text remains source. |
| TTS regression | PASS | Receiver handoff tests pass; text remains TTS source. |
| Frontend | PASS | Minimal optional compact chip only; no unknown/debug UI. |
| Performance | PARTIAL | JVM extraction/serialization/size measured; no Android CPU/RAM profile. |
| Automated tests | PASS | 421/421 unit tests passed. |
| Physical validation | PARTIAL | One device only; no two-device enriched-packet validation. |
| Documentation | PASS | N5 implementation and validation documents added. |

## Tests added

- `SemanticExtractorTest`: positive events, urgency, hazards, victim count, negation, unknown/empty/long, Hindi+English, Tamil+English, malformed metadata.
- `SemanticPacketV2Test`: full text + metadata round trip, legacy no-metadata packet, fragmented reassembly, fixed size overhead.
- `SemanticPerformanceTest`: deterministic JVM report.
- `RoomMessageRepositoryTest`: semantic persistence with text.

## Exact targeted commands executed

```text
./gradlew :app:testDebugUnitTest --tests com.vokie.semantic.* --tests com.vokie.communication.SemanticPacketV2Test --tests com.vokie.data.RoomMessageRepositoryTest --no-daemon
./gradlew :app:testDebugUnitTest --tests com.vokie.semantic.SemanticPerformanceTest --no-daemon
./gradlew :app:testDebugUnitTest --no-daemon
./gradlew :app:assembleDebug --no-daemon
```

## Full result and APK

- Unit tests: **422 passed, 0 failed**
- Debug build: **PASS**
- APK size: 502,991,514 bytes
- APK SHA-256: `60d0d8b2e0cce12762cd968aeb2d2f54072f224517a8c0fa8465da1ec0da54ec`

## Physical validation

No physical semantic communication PASS is claimed. Only one Android device is available; two-device transport validation remains deferred. No autonomous actions are present.

## Git commit

Included in this N5 implementation commit (`feat: add bounded offline semantic message metadata`).
