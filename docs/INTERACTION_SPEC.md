# VAANI — Interaction Specification (v2)

Surfaces: inline annotations (translate / explain / plans), the composer proposal (reply / rewrite / translate-draft), and the Context pane (Brief / Ask). All share one streaming hook (`useAssist`).

## AI request states (`useAssist`)
```text
idle ──(request)──► processing ──(first delta)──► streaming ──(done)──► completed
  ▲                     │  │                          │                     │
  │                     │  └──(error event)──► failed ◄┘                     │
  └──(Ask chip/input)───┴──(Stop / close / navigate)──► cancelled ───────────┘ Regenerate/Retry ─► processing
```
| State | UI |
|---|---|
| processing | Iris dots + “Reading the conversation…” → “Writing…” once the context chip arrives. Stop visible. |
| streaming | Output card with blinking caret; Stop visible. |
| completed | Proposal: Use & edit · Regenerate · Copy · Discard (“You send it — VAANI never does”). Annotations: Copy, close. |
| failed | Error card per code: not configured (no retry), disabled (link to Privacy), rate limited / timeout / invalid response / network (Try again), budget exceeded (no retry). |
| cancelled | Partial text kept, “Stopped.”, Copy + Regenerate. Server aborts the model call when the stream closes. |

## Message send
`compose → Sending (clock) → Sent (✓) → Read (✓✓)`, shown under your latest message. Network failure keeps it **Sending** in the outbox and flushes on reconnect. Server rejection → **Not sent — Retry · Delete**. `clientId` makes retries idempotent.

## Voice
`Speak → waiting for microphone → Recording ⇄ Paused → Done → Ready to send (Listen) → Send voice note | Discard` → in the transcript: Uploading → Transcribing → Transcript · <language>. Playback speed cycles 1× / 1.5× / 2×. Auto-stop at 5:00. <0.5 s → “too short”. Errors: permission denied (warning banner, how to fix), no device, unsupported browser.
Transcript: `pending → done | failed (Retry) | unavailable | none (Transcribe)`; updates arrive by `message.updated`.

## Translation
`Translate → Translating… → interlinear gloss under the original (SOURCE → TARGET · VAANI, Listen, note) → Translate again or × to hide`. The original is never hidden. Errors inline with Retry. Cached per message+language server-side.

## Voice-to-action
`More → Find plans & tasks → inline “Plans found by VAANI” → editable cards → Add to calendar / Add task / Send → done (.ics downloaded / Added to Companion / Sent)` or `Dismiss`. Already-handled actions return 409.

## Realtime
Typing: sent at most every 2.5 s while typing; shown for 4 s. Presence from `hello`/`presence`. Reconnect back-off 0.5 s × 2ⁿ (max 15 s, jittered); offline when `navigator.onLine` is false. On reconnect: refresh list, refetch open threads, flush outbox.

## Micro-interactions
Message enter (6 px rise, 200 ms) · popovers scale-in · sheet slide (right on desktop, up on mobile) · jump-to-message 2.4 s Marigold wash · reactions scale on hover · switch thumb slide · onboarding dots stretch. All disabled with `prefers-reduced-motion`; none block input.

## Keyboard
Enter send · Shift+Enter newline · Esc closes menus, sheets and the VAANI strip · Ctrl/⌘ K opens search · Tab reaches message tools via focus-within · IME composition respected (`isComposing`).

## AI attribution
A sent message is tagged “Drafted with VAANI” only when at least half of the AI draft’s words are still in it (rewrite it fully and it’s yours).
