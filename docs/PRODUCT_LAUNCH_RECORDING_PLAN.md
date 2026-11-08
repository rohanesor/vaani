# VAANI Product Launch Recording Plan

**Build to record:** next signed release built from this checkpoint (debug runtime validation used `com.vaani.app`, version `1.0.0` code `1`). No clips have been recorded in this repository.

## Clean demo setup

Use fictional accounts only: Rohit, Sachaya, Rohan, and Joshna. Start from a new database/recording account, remove test conversations, disable developer overlays/notifications, and verify provider configuration in the server health/status UI without exposing secrets. Use only a language/model/voice combination confirmed live in the Gnani account.

| Clip | Target | Start/action | Expected real response | Status |
|---|---:|---|---|---|
| Product introduction | 5–8 s | Open VAANI, land on transcript communication UI | Branding, conversation list, established transcript visual identity | PLANNED |
| Communication problem | 5–8 s | Rohit sees a message in a different preferred language | Brief contrast only; do not fake an untranslated failure | PLANNED |
| Gnani STT (hero) | 10–15 s | Rohit records/sends a real WAV voice note | Pending state then actual Gnani transcript/detected language | BLOCKED pending live configured provider |
| Multilingual interaction | 10–15 s | Recipient opens the processed message and replies in a verified second language | Actual transcript/message/translation behavior only | BLOCKED pending live provider and verified translation configuration |
| Gnani TTS | 8–12 s | Tap read aloud for short message | Actual returned audio and playback control | BLOCKED pending live configured provider |
| Feature montage | 10–15 s | Show messaging, transcript, cloud-sync settings, nearby UI, AI assist | Only stable/verified surfaces; label offline/nearby capability accurately | PLANNED |
| Closing | 5 s | Return to working conversation/brand | VAANI branding/tagline actually present in app | PLANNED |

## Recording checklist

- [ ] Record device/app/version and server commit in shot notes.
- [ ] Confirm no personal data, keys, tokens, email addresses, or debug errors are visible.
- [ ] Confirm every shown Gnani result came from the configured live provider; retain timestamped request/response-safe logs separately.
- [ ] Capture each clip independently as MP4/screen recording; store files outside git or in an approved media location.
- [ ] Capture a fallback/error take only if it explicitly labels provider unavailability.
- [ ] Verify audio playback in the captured file before edit.
- [ ] Assemble externally with the repository's existing video tooling if present; no new video framework is required.

## Narration direction

Problem → multilingual communication friction → server-side Gnani STT/TTS engineering flow → real VAANI interaction → verified product features → closing. Do not call a mock, test-double, offline Whisper, or Claude translation response a live Gnani result.
