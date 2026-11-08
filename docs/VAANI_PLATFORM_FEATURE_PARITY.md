# VAANI Platform Feature Parity

| Capability | Web | Android | Shared service | Status |
|---|---|---|---|---|
| Authentication | Canonical UI/API | Cloud bridge/UI | Server auth | Web PASS; Android cloud E2E BLOCKED |
| Messaging | Server-backed transcript | Local/nearby + optional cloud mappings | Server message contract | Web PASS; physical Android BLOCKED |
| AI assistance | Server UI/API | Native provider abstraction | Claude server path | Web MOCK-BACKED |
| Language selection | Server-supported metadata | Local language settings/models | Shared codes only | Web PASS metadata; no live speech claim |
| Voice notes | Browser recorder → server | Native local STT/TTS | Optional Gnani server STT/TTS | Web permission/error PASS; live BLOCKED |
| Nearby/peer discovery | Not exposed | Bluetooth/Wi-Fi Direct | Android-native | NOT APPLICABLE to web |
| Calls | Not exposed | Android-native | Android-native | NOT APPLICABLE to web |
| Cloud sync | Standard web REST/realtime message state, no sync UI | Explicit Android `/api/sync` | Server Store/sync route | Web N/A; Android runtime contract PASS, E2E BLOCKED |

Parity is intentionally capability-based, not forced UI equivalence. The web client must not imply native nearby/offline features it does not implement.
