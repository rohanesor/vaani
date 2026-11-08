# Test Coverage Matrix

| Feature | Android UI | Web Playwright | Backend | Current result |
|---|---|---|---|---|
| Startup/restart | Appium 8/8 suite | Yes | — | PASS |
| Permission prompt | Appium | N/A | — | PASS |
| WebView rendering | Appium | Yes | — | PASS |
| Authentication | Not meaningful without Android server fixture | Yes | Yes | Web/backend PASS; Android NOT RUN |
| Messaging/conversation | Not meaningful without peer fixture | Yes | Yes | Web/backend PASS; Android NOT RUN |
| Language selection | Appium (Tamil local UI) | NOT RUN | — | PASS Android local UI; web NOT RUN |
| Gnani STT/TTS deterministic | N/A | UI unavailable path | Yes | PASS (mock-backed contracts) |
| Gnani live STT/TTS | NOT RUN | NOT RUN | BLOCKED | BLOCKED: no controlled credentials plus WAV fixture |
| Cloud sync/account isolation | Instrumentation | N/A | Yes | PASS (runtime/backend); Android UI NOT RUN |
| Offline local persistence | Appium lifecycle only | N/A | — | NOT RUN |
| Nearby/radio | Physical device required | N/A | — | BLOCKED |
| Corpus pipeline | N/A | N/A | N/A | BLOCKED: protected corpus unstaged |

A cell is PASS only where a meaningful assertion executed. API-37 Espresso Compose UI tests remain blocked by framework compatibility, but Appium no longer blocks native automation. M19 confirmed live Gnani, Android cloud E2E, and physical nearby remain externally BLOCKED; browser/emulator automation is not substituted for them.
