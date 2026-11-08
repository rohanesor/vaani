# VAANI — Product Requirements Document

**Status:** v0.1 (MVP in development) · **Owner:** Rose · **Last updated:** 2026-11-02
**Tagline:** *Every language. One conversation.*
**Source:** the VAANI product brief. No iTantra source code was available, so VAANI was built as a new codebase (see `ARCHITECTURE.md`, ADR-001).

---

## 1. Product overview

VAANI (वाणी — voice, speech, expression) is an AI-native multilingual messaging app. People chat 1:1 and in groups by text and voice. VAANI makes language a non-issue (translation, transcription, read-aloud) and adds an AI companion *inside* the conversation: it suggests replies, rewrites drafts, explains messages, answers questions about the chat, summarises, and turns plans in messages into calendar events and tasks — always as drafts the user confirms.

## 2. Target users

| User | Need | What VAANI gives them |
|---|---|---|
| Multilingual individuals | Chat across Tamil/Hindi/Telugu/English and code-mixed text | One-tap translation into their language; original always kept |
| Students | Group projects, deadlines scattered across chats | Group summaries, decisions vs. suggestions, tasks |
| Professionals | Fast, appropriate replies | Rewrite (professional/shorter/formal), suggested replies |
| Families across languages | Grandparents and grandchildren read different scripts | Translation + read-aloud in their language |
| People in noisy environments | Can’t listen to voice notes | Voice-note transcripts |
| Users with limited connectivity | Flaky networks | Offline outbox, reconnecting realtime, idempotent sends |
| Users who need AI help | Low confidence writing in a language | AI drafts they edit before sending |

## 3. Core product principles

1. **Communication comes first.** The home screen is the chat list; AI lives inside conversations.
2. **AI assists, never interrupts.** Nothing runs in the background; every AI call is user-initiated.
3. **Users stay in control.** AI output is a draft. Sending messages, creating events/tasks requires explicit confirmation.
4. **Language is not a barrier.** Translate any message; originals are preserved.
5. **Voice and text work together.** Voice notes are transcribed into searchable, translatable text.
6. **Offline where technically possible.** Messages queue while offline and send on reconnect. Cloud AI/speech are clearly labelled as online-only.
7. **Private conversations stay private.** AI only reads the conversation the user is in, only the messages needed, with contact details/OTPs redacted; users see what was read.

## 4. MVP scope (release 0.1)

In: accounts, 1:1 + group chats, realtime delivery, read receipts, typing, presence, reactions, replies, voice notes with real waveform, transcription (Gnani), read-aloud (Gnani), translation, AI companion (reply / rewrite / explain / ask), summaries, voice-to-action (events as .ics, tasks, send-message), full-text search, settings (profile, language, theme, privacy & AI, notifications, services), onboarding.

Out (later): attachments/media, push notifications when closed, E2E encryption, phone-number contacts, email password reset, direct Google/Outlook calendar sync, Android native client, streaming (live) STT, offline on-device speech.

## 5. Features

Priority: **P0** MVP-blocking · **P1** MVP · **P2** post-MVP. Status as of this document: ✅ implemented & tested · 🟡 partial · 📄 documented only.

### 5.1 Individual messaging — P0 ✅
- **Problem:** People need a reliable 1:1 chat.
- **Story:** As a user I can find someone on my server and message them; I see when it was sent and read.
- **Functional:** create-or-open direct chat (deduplicated); send text (≤4000 chars) with idempotent `clientId`; reply-to; reactions; read receipts; typing; presence; unread counts; pagination (50/page).
- **Dependencies:** server store (SQLite), WebSocket hub.
- **Acceptance:** two users can exchange messages in real time; a retried send never duplicates; non-members get 404 on every route (tested).
- **Limitations:** no edit/delete-for-everyone; no attachments.

### 5.2 Group conversations — P0 ✅
- **Story:** As a user I create a named group with several people; the owner can add people; anyone can leave.
- **Functional:** create (≥1 other member, ≤256), rename, add members (owner), leave, system messages for membership changes, sender names + colours in the thread.
- **Acceptance:** membership changes are reflected live; only members receive events (tested).
- **Limitations:** no admin roles beyond owner; adding members after creation is API-only in the UI today.

### 5.3 Voice messaging — P0 ✅
- **Story:** As a user I record, pause/resume, review and send a voice note; recipients play it with a waveform and progress.
- **Functional:** in-browser capture → 16 kHz mono PCM WAV (≤5 min); server validates format, computes the waveform from samples, stores audio; playback with seek; live recording waveform from mic levels.
- **Acceptance:** WAV upload creates a voice message with a 48-bar envelope derived from the audio (tested); invalid/short audio rejected (tested).
- **Limitations:** WAV is ~1.9 MB/min (no Opus yet).

### 5.4 Multilingual communication — P0 ✅
- **Supported:** English, Hindi, Tamil, Telugu, Kannada, Malayalam, Bengali, Gujarati, Marathi (UI fonts: Noto per script).
- **Functional:** per-user preferred language; translate any text or transcribed voice note into it; read aloud via TTS.
- **Limitations:** UI chrome is English-only (i18n of the UI is P2).

### 5.5 AI communication companion — P1 ✅
- **Story:** From a conversation I open VAANI and ask for help without copying anything.
- **Functional:** operations *Suggest reply*, *Rewrite* (professional / shorter / friendlier / clearer / formal), *Explain message*, *Ask about this chat*; streaming output; Stop; Retry/Regenerate; Copy; context chip (messages read, details hidden, provider).
- **Acceptance:** context is built server-side from the authorised conversation; redaction applied; output is never auto-sent (tested).

### 5.6 AI-generated replies — P1 ✅
Draft inserted into the composer, editable, marked *AI-assisted* when sent. Prompt forbids inventing facts/dates; uses `[placeholder]` instead.

### 5.7 Contextual translation — P1 ✅
Claude translation with surrounding messages as context, structured output (`source_language`, `translation`, `note`), cached per message+language. Original preserved, toggle *Show original*.

### 5.8 Conversation summaries — P1 ✅
Structured: summary, key points, decisions (`confirmed` vs `proposed`), tasks (owner/due only if stated), open questions; each item links to source messages. Server drops any message reference not in the context (tested).

### 5.9 Conversation search — P1 ✅
SQLite FTS5 (`unicode61`) over text and voice transcripts, prefix matching, scoped to the user’s memberships; chat-title matches; result → jump to message with highlight; recent searches.
- **Limitation:** lexical, not semantic; no transliteration (searching “vanakkam” won’t match “வணக்கம்”).

### 5.10 Voice-to-action — P1 ✅
Pipeline: voice note → Gnani STT → Claude structured extraction (with reference date + user timezone) → validated actions → user edits/confirms → executor.
Executors: **create_event** → standards-compliant `.ics` download; **create_task** → VAANI Tasks; **send_message** → posts to the chat. All require confirmation; invalid dates/times rejected; actions are per-user (others get 404).
- **Limitation:** no direct calendar API (CalendarProvider seam exists).

### 5.11 AI conversation spaces (group intelligence) — P1 🟡
Ask-about-chat uses retrieval (FTS ranked hits + neighbours + recent tail, bounded by the user’s context budget) and cites `[#id]`. Not yet: persistent “space” memory across sessions, semantic embeddings, cross-conversation questions (deliberately excluded for privacy).

### 5.12 Existing iTantra capabilities — 📄 Blocked
No iTantra source was provided, so mesh/offline-radio features could not be evaluated or preserved. The `SpeechRecognitionProvider`/`SpeechSynthesisProvider` seams allow an on-device engine to be added later.

## 6. Non-functional requirements
- **Security:** provider keys only in server env; JWT (HS256) sessions; scrypt passwords; per-IP auth throttling; membership check on every conversation route; security headers; CORS allow-list.
- **Cost controls:** per-user requests/minute + daily token budget; fast model tier for translate/rewrite/explain; context capped by user setting (5–100).
- **Reliability:** idempotent sends; offline outbox; WS back-off reconnect + heartbeat; AI timeout (45 s) with SDK retries.
- **Accessibility:** WCAG 2.1 AA target — keyboard, labels, focus, contrast, reduced motion.

## 7. Success metrics (post-launch)
Translation usage per active user; % AI drafts edited before send; summary “jump to message” clicks; send failure rate; time-to-first-message after signup.
