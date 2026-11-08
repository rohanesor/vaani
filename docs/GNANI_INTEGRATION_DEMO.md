# Gnani Integration Demo Evidence

## Evidence status

| Item | Status | Evidence |
|---|---|---|
| Server adapter implementation | PASS | `server/src/speech/gnani.ts` |
| Authenticated voice-note/transcription lifecycle | PASS (contract) | `server/src/routes/ai.ts`, server tests |
| WAV segmentation | PASS (contract) | <=29-second chunking before `/stt/v3` |
| TTS request/response handling | PASS (contract) | `/api/tts`, audio response validation |
| Error/timeout/rate-limit mapping | PASS (contract) | `SpeechError` and adapter tests |
| Live Gnani STT | BLOCKED | M18 live smoke command requires controlled `GNANI_API_KEY` and external WAV fixture; neither was supplied |
| Live Gnani TTS | BLOCKED | Same controlled live smoke prerequisite; no provider request was made |
| Latency measurement | NOT RUN | Live provider was deliberately not invoked |
| Screenshot/video evidence | NOT PRODUCED | No recording clip exists yet |

## End-to-end behavior to demonstrate

1. Sign in to the recording account; confirm `/api/ai/status` reports both Gnani providers configured.
2. Record a supported-language WAV voice note of at least 300 ms.
3. Send it to a demo conversation. VAANI stores it immediately and marks transcription pending.
4. Observe the actual transcript/status update delivered by the server.
5. Use read-aloud for a short supported-language text and verify returned audio plays.
6. Repeat with an intentionally unavailable configuration only if showing fallback: the UI must say unavailable/failed, never display invented text.

## Configuration and data handling

- `GNANI_API_KEY` is read on the server only and sent as `X-API-Key-ID`.
- STT sends WAV chunks with model and language code; automatic language uses `indic-auto` only for `prisma-v3*` models.
- TTS sends text, configured model/voice/language, bounded speed (0.85–1.15), and MP3 64k audio configuration.
- VAANI does not log keys. Do not show developer tools, `.env`, request headers, or raw provider responses in recordings.

## Limits

M19 ran the live-smoke prerequisite check: it is **BLOCKED** because neither the controlled credential nor WAV fixture was supplied. No provider request, transcript, audio result, timing, or verified Gnani language was produced. Do not claim accuracy, latency, language coverage, streaming recognition, or translation from Gnani without a newly captured live measurement. Current STT is bounded/chunked REST processing, not a verified streaming session.
