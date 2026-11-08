# VAANI Design System (v2)

Source of truth: `client/src/styles/tokens.css`. Components use semantic `--color-*` tokens only.
Theme is resolved to `data-theme="light|dark"` before first paint (inline script in `index.html`, then `session.tsx` follows the OS when set to System).

## Identity — three hues, three meanings
| Identity | Hue | Means | Never used for |
|---|---|---|---|
| Communication | **Peacock** `#0B6E63` | brand, your own turns, primary actions, selection, unread | AI or voice |
| Intelligence | **Iris** `#5646C8` | anything AI produced or will produce: annotations, drafts, context, citations | regular actions |
| Voice | **Marigold** `#965D00` | recording, playback, waveforms, transcripts | anything not audio |
| Secondary | **Ink** `#1F2A5C` | sign-in hero only | |

Iris appears as a **hairline + small-caps label**, not as purple cards. The AI mark is a point with an orbit (`AIMark`), never sparkles. The voice mark is three bars (`VoiceMark`).

## Colour tokens
| Token | Light | Dark |
|---|---|---|
| `--color-bg` / `-bg-secondary` | #F3F1EB / #ECE9E1 | #0E1413 / #0A100F |
| `--color-surface` / `-surface-elevated` | #FAF9F5 / #FFFEFA | #141B1A / #1B2423 |
| `--color-surface-conversation` | #FBFAF6 | #111817 |
| `--color-surface-input` / `-hover` / `-selected` | #F0EDE5 / #EDEAE2 / #E1EEEA | #1A2221 / #1C2625 / #163833 |
| `--color-surface-self` (your turns) | #E8F1EE | #12302C |
| `--color-text-primary` / `-secondary` / `-muted` | #1A1915 / #55524A / #6B665C | #EAE8E1 / #B4B0A6 / #A39F94 |
| `--color-border` / `-border-strong` / `-divider` | #DCD8CE / #C4BEB1 / #E5E1D8 | #283231 / #3A4645 / #1F2928 |
| `--color-disabled-bg` / `-fg` | #E8E5DD / #9A958A | #1C2423 / #646B68 |
| `--color-brand-primary` / `-hover` / `-soft` / `-text` / `on-brand` | #0B6E63 / #08594F / #DCEEEA / #0A5C53 / #FFF | #4CC1B2 / #67CEC1 / #123631 / #7FD6CB / #03211D |
| `--color-ai-primary` / `-text` / `-soft` / `-line` | #5646C8 / #4A3BB5 / #EFEDFC / #B8AFEF | #ACA0FF / #C2B9FF / #201C42 / #4C438F |
| `--color-voice-primary` / `-text` / `-soft` / `-line` / `-live` | #965D00 / #8A5500 / #FAEEDA / #E2B46A / #C47A06 | #F0A63E / #F4B967 / #33250E / #75521D / #F0A63E |
| `--color-success` / `-warning` / `-error` / `-info` | #1A7240 / #875800 / #B42323 / #1F5FB0 | #5FCB8D / #EAB54E / #F3807A / #82B3F2 |
| `--color-pending` / `-offline` / `-reconnecting` | #6B665C / #55524A / #875800 | #A39F94 / #B4B0A6 / #EAB54E |

Dark mode is a teal-tinted charcoal (not an inversion) so Peacock still feels at home; identity hues are lifted for contrast.

**Contrast (computed, WCAG 2.1):** every text token (primary, secondary, muted, brand-text, ai-text, voice-text, success, warning, error, info) was checked against every surface it can sit on in both themes; the minimum is **4.74:1** (light) and **4.82:1** (dark). White on Peacock 6.1:1. States always carry a word or icon (Sending / Sent / Read / Not sent; Agreed / Suggested; Offline / Reconnecting).

## Typography
- **Editorial:** Fraunces (variable, optical size) — conversation titles, page headlines, the Brief lead, day separators (italic), the voice timer.
- **UI & messages:** Plus Jakarta Sans.
- **Indic scripts:** Noto Sans Devanagari (Hindi, Marathi), Tamil, Telugu, Kannada, Malayalam, Bengali, Gujarati — **self-hosted** via `@fontsource` (works offline; each face has a `unicode-range`, so only scripts on screen download). Verified rendering of Tamil, Telugu and Devanagari conjuncts in the browser.
- Message text 16/1.6 for vowel signs and conjuncts; `dir="auto"` on all user text; `overflow-wrap: anywhere` for long Tanglish/Hinglish tokens.
- Small-caps labels (`.label`, 11px/700/0.08em) mark every annotation: `TELUGU → ENGLISH · VAANI`, `TRANSCRIPT · HINDI`, `EXPLAINED BY VAANI`.

## Space, shape, depth, motion
4-px spacing scale. Radius: 6 / 10 / 14 (turn) / 20 / 22 (composer stage). Depth is mostly hairlines; shadows only on floating things (toolbars, proposals, sheets). Motion 120/200/320 ms, `cubic-bezier(.2,.8,.2,1)`; disabled under reduced motion (spinners keep turning).

## Components
`Logo`, `AIMark`, `VoiceMark`, `LangGlyph` (script glyph, e.g. த), `Avatar`, `Button` (primary · secondary · ghost · danger · ai · voice), `IconButton`, `Label`, `Banner`, `EmptyState`, `Sheet`, `Toast`, `Segmented`, `Toggle`, `Spinner`;
`ConversationList` · `Today` · `ConversationView` · `Transcript` · `Message` · `VoicePlayer` · `VoiceStage` · `Composer` · `DraftProposal` · `InlineExplain` · `InlinePlans` · `ContextPane` (Brief / Ask) · `ConnectionIndicator`.
