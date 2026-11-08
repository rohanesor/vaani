# Gnani Integration Architecture

## Verified scope

Gnani is a **server-side, opt-in cloud speech provider** in VAANI. It is not the Android offline Whisper or CT2 path. The server creates `GnaniSpeechRecognitionProvider` (`gnani-prisma`) and `GnaniSpeechSynthesisProvider` (`gnani-timbre`) from environment-only configuration.

```text
Voice-note WAV (web client)
  -> POST /api/conversations/:id/voice
  -> VAANI server validates WAV (300 ms–5 min), stores note, emits message
  -> if voice transcription + GNANI_API_KEY are enabled:
       split WAV into <=29-second chunks
       POST multipart /stt/v3 to Gnani Prisma
       X-API-Key-ID: server-only GNANI_API_KEY
  -> transcript + detected language stored on message and emitted to members

Text selected for read-aloud (web client)
  -> POST /api/tts { text <= 2000 chars, supported language }
  -> POST JSON /api/v1/tts/inference to Gnani Timbre
       X-API-Key-ID: server-only GNANI_API_KEY
  -> binary audio response returned to authenticated client
```

## Responsibilities

| Component | Responsibility |
|---|---|
| Web client | Captures/plays audio and presents transcript/provider availability. |
| VAANI server | Authentication, conversation authorization, WAV validation, chunking, retries/errors, storage and realtime delivery. |
| Gnani Prisma | Cloud speech recognition and optional detected language code. |
| Gnani Timbre | Cloud speech synthesis; returns audio bytes. |
| Android app | Separate local/offline speech and translation paths; it does not contain the Gnani API key. |

## Supported application language inputs

The server accepts `en-IN`, `hi-IN`, `ta-IN`, `te-IN`, `kn-IN`, `ml-IN`, `bn-IN`, `gu-IN`, and `mr-IN`. This is VAANI input validation, **not a claim that every configured Gnani model/voice supports every combination**. Recording must use a language/model/voice combination confirmed in the configured Gnani account.

## Failure behavior

- Missing `GNANI_API_KEY`: status endpoint says unavailable; voice notes remain usable and transcript status is `unavailable`.
- Invalid WAV or provider rejection: a clear request error; no fabricated transcript.
- Timeout: 30 seconds by default, reported as retryable upstream failure.
- Rate limit: mapped from provider HTTP 429.
- Provider/network failure: stored transcript failure/error and realtime message update.
- Local Android backends unavailable: visible explicit unavailable state; no cloud substitution.

Translation is handled by VAANI's Claude orchestration when enabled, not by the Gnani adapter. It must not be represented as a Gnani translation feature.
