# VAANI — UI/UX Specification (v2 redesign)

Companion docs: `DESIGN_SYSTEM.md` · `SCREEN_INVENTORY.md` · `INTERACTION_SPEC.md` · `RESPONSIVE_SPEC.md`.

## The idea: a conversation is a transcript
VAANI means speech. So a conversation reads like an **annotated transcript**, not a column of left/right bubbles:

- **One reading column** (max 720 px). Messages are grouped into **speaker turns**: avatar in a gutter, speaker name in their own hue, time, and a script glyph when they read a different language than you.
- **Your turns** sit on a pale Peacock surface with a Peacock rule — clearly yours, still in the same reading line.
- **The original is never replaced.** Everything VAANI adds is an *annotation beneath* the message, on an Iris hairline with a small-caps label:
  - **Translation** — interlinear gloss: `TELUGU → ENGLISH · VAANI`, with Listen.
  - **Explanation** — streamed inline: `EXPLAINED BY VAANI`.
  - **Plans** — `PLANS FOUND BY VAANI` with editable event/task/message cards and explicit confirm.
- **Voice notes** are a Marigold *voice strip* (real waveform, seek, 1×/1.5×/2×) followed by `TRANSCRIPT · <language>`.
- **System events** are a quiet rule-line; **days** are an italic editorial heading.

What this is not: green-right/white-left bubbles (WhatsApp), channel sidebars (Discord), or a chatbot window.

## Navigation: the index
One left **index** column replaces both an icon rail and a bottom tab bar, on every size:
`vaani` wordmark · Companion (AI mark) · New · your avatar (Settings) → search (Ctrl/⌘ K) → filters **All · People · Groups · Unread** (with counts) → conversations.
Conversation rows: name, language glyph, group size/presence, time; preview shows sender, **voice notes with duration and the start of the transcript**, or live “typing” with an animated voice mark; unread count in Peacock.
On phones the index *is* the home screen and a conversation takes the full screen with a back button — no tab bar.

## The composer: one instrument
A single “stage” holds **VAANI** (left), the text field, and **Speak** (right; becomes Send when there’s text).
- **VAANI** opens an action strip in place: *Draft a reply · Professional · Shorter · Warmer · Clearer · Formal · In <their language> · Brief me · Ask…*
- Drafts appear above the composer as a **proposal** (Iris rule, original draft struck through, streaming text, what was read): **Use & edit · Regenerate · Copy · Discard**. VAANI never sends. A message is labelled “Drafted with VAANI” only if ≥ 50% of the AI draft’s words survive your edits.
- **Speak** turns the stage into the **voice stage**: state label (Recording / Paused / Ready to send), large timer, live waveform from the microphone, Discard · Pause/Resume · Done → Listen · Send voice note.

## Context pane
The header’s **Context** button opens Brief and Ask for *this* conversation: Brief = summary with Agreed vs Suggested decisions, to-dos, open questions, each linked to source messages; Ask = questions with cited answers. A permanent line states scope: only this conversation, contact details hidden.

## Home & Companion
- **Today** (wide screens, nothing open): greeting, *Waiting for you* (real unread), *Open plans from your chats* (real tasks), three starts.
- **Companion**: open/done plans, AI activity by feature over 30 days with today’s budget meter, and a plain-language “What AI can see” reflecting your actual settings.

## States (all implemented)
Messages: Sending → Sent → Read (✓✓) · Not sent (Retry/Delete) · queued while offline. AI: idle → reading → writing (caret) → done | failed (per-code copy, Retry where sensible) | stopped. Voice: waiting for mic → recording ⇄ paused → ready → uploading → transcribing → transcript | failed (Retry) | not set up. Connection: connecting / reconnecting / offline (index banner + pill on the conversation). Empty: first-run, no matches, caught up, no plans. Errors: load failures with Try again.
Not shown because not supported: “delivered”, attachments, images, profile photos.

## Accessibility
Native controls; skip link; transcript is a polite `role="log"`; every icon-only control is labelled; toolbars appear on hover *and* focus; `aria-pressed` on toggles (Translate, reactions, VAANI, Context); tabs use `role="tab"`; focus rings on `--color-focus`; reduced motion honoured; no meaning by colour alone.
