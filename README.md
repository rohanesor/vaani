# VAANI

**Every language. One conversation.**

VAANI is an AI-native multilingual messaging app: 1:1 and group chat, voice notes with transcription, one-tap translation into your language, and an AI companion that suggests replies, rewrites drafts, explains messages, answers questions about the chat, summarises, and turns plans into calendar events and tasks. It never sends anything for you.

| | |
|---|---|
| Client | React 19 + Vite + TypeScript (`client/`) — responsive web app, phone to desktop, light/dark |
| Server | Node ≥ 22.13 + Express 5 + WebSocket + `node:sqlite` with FTS5 (`server/`) |
| AI | Claude via the Anthropic SDK, server-side only (`server/src/ai/`) |
| Speech | Gnani Prisma (STT) and Timbre (TTS), server-side only (`server/src/speech/`) |

## Quick start
```bash
npm run install:all
cp server/.env.example server/.env      # add ANTHROPIC_API_KEY / GNANI_API_KEY if you have them
npm --prefix server run seed            # optional demo accounts, password: vaani-demo
npm run dev:server                      # :8787
npm run dev:client                      # :5173 → open this
```
Without keys, messaging, voice notes, search and settings work; AI and transcription show “not configured” states.

## Verify
```bash
npm test           # 33 server tests (API, authz, realtime, AI orchestration, Gnani contracts, WAV)
npm run typecheck
npm run build
npm run ui:harness # real app + scripted AI on :8790 for UI walkthroughs (not for production)
```

## Docs
`docs/PRD.md` · `docs/ARCHITECTURE.md` · `docs/IMPLEMENTATION_PLAN.md` · `docs/UI_UX_SPEC.md` · `docs/DESIGN_SYSTEM.md` · `docs/SCREEN_INVENTORY.md` · `docs/INTERACTION_SPEC.md` · `docs/RESPONSIVE_SPEC.md`

## Security
API keys live only in `server/.env` (git-ignored). The browser holds a VAANI session token, never a provider key. AI only reads the conversation you’re in, only when you ask, with phone numbers/emails/OTPs redacted by default.
