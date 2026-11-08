# VAANI Android — Mobile Design System

Android is the full VAANI communication app: online, offline, nearby, and native device features. It shares the web client's identity, which covers tokens, type, icons, colour roles and the transcript model. It does **not** share the web client's feature set or information architecture. Feature boundaries are in [`PLATFORM_CAPABILITY_MATRIX.md`](PLATFORM_CAPABILITY_MATRIX.md).

![Android screens, light, 390px](android-ui/overview-light-390.png)
![Android screens, dark, 390px](android-ui/overview-dark-390.png)

## 1. Source of truth

| Layer | File | Rule |
|---|---|---|
| Tokens (colour, type, radius, motion, shadow) | `app/src/main/assets/www/tokens.css` | Verbatim copy of `client/src/styles/tokens.css`. Copy it with `node scripts/sync-android-webview-tokens.mjs`, and use `--check` in CI. |
| Mobile layout | `app/src/main/assets/www/vaani-mobile.css` | Uses tokens only. It adds the phone variables `--tap`, `--gutter`, `--tabbar-h`, `--app-h` and the safe areas, and it adds Noto Sans Oriya to the font stack. |
| Type | `fonts/`, `fonts.css` | Self-hosted OFL WOFF2 for all 10 scripts, loaded per script through `unicode-range`. |
| Icons | Inline SVG sprite in `index.html` | Lucide 1.70.0 (ISC), plus the VAANI `i-ai` and `i-voice` marks. No icon font. |
| Native chrome | `res/values*/colors.xml` and `themes.xml` | `vaani_window_bg` mirrors `--color-bg` in light and dark |

## 2. Information architecture (Android's own)

| Destination | Purpose | Web equivalent |
|---|---|---|
| **Chats** (home) | Every conversation: Nearby, Cloud linked and Cloud. Separate Internet and Nearby indicators, filters, search, and a **Speak** button. | The web's conversation list, minus groups-as-local and plus origin badges |
| **Nearby** | Discovery, connection, trust, pairing, Wi-Fi Direct | None (Android only) |
| **Settings** | Profile, language, connectivity, offline speech, optional cloud account, notifications | Web Settings, plus connectivity and on-device speech |
| Speak (overlay) | Push-to-talk to the selected nearby person | Web voice notes (a different mechanism) |
| Conversation | Transcript, delivery states, translation, VAANI composer, Context sheet | Web conversation pane |
| Cloud conversation | Read-only copy of a cloud conversation stored on the phone | Web conversation (read/write there) |

System back moves through this stack in order:
1. Sheet
2. Speak
3. AI draft
4. Cloud conversation
5. Conversation
6. A tab other than Chats
7. Chats
8. Exit

## 3. Status vocabulary

These rules are not negotiable.

- **Internet and Nearby are two indicators, always shown side by side on Chats.** Losing internet never hides or disables nearby features.
- **Internet** shows exactly what native reports from Android's validated-connectivity callback:

  | Native state | Pill | Tone |
  |---|---|---|
  | `online` (validated) | "Online · Wi-Fi / Mobile data" | success |
  | `connecting` (connected, not validated) | "No internet yet" | warning |
  | `limited` (captive portal) | "Sign-in needed" | warning |
  | `none` | "No internet" | offline |
  | field absent | "Internet: unknown" | offline |

- **Nearby** uses four separate counts (Connected, Nearby, Trusted, Pairing) on the Nearby tab. The Chats pill shows connected, or else discovered. The Nearby tab badge counts incoming pairing requests only.
- **Origin badges** on every conversation:

  | Badge | Colour | Meaning |
  |---|---|---|
  | Nearby | Peacock | Phone to phone |
  | Cloud linked | Iris | A local conversation mapped to a cloud conversation for this account |
  | Cloud | Info blue | Stored from the account |

- **Sync line** (Cloud linked only), from persistent counts:
  - "N failed to sync" (error)
  - "N waiting to sync" (warning), plus "· needs internet" when internet isn't validated
  - otherwise nothing in the list and "N synced · time" in the conversation

## 4. Components (new or changed in this round)

| Component | Class | Notes |
|---|---|---|
| Status pill | `.status-pill.ok/warn/off` | Icon and words; colour is never the only signal. Minimum height 36px. |
| Filter tabs | `.filters [aria-selected]` | All · Nearby · Cloud · Unread, with counts. Web-style underline. Minimum 44×44. |
| Origin chip | `.origin.nearby/linked/cloud` | Next to the conversation name |
| Sync line | `.sync.warn/err/ok` | Under the preview, or in `.convo-meta` |
| Speak button | `.fab` | Marigold (voice role), bottom right of Chats |
| Context button | `.ctx-btn` | Iris outline, the same as the web "Context" |
| VAANI key | `.vaani-key.wide` | Iris "VAANI" label in the composer, the same as the web |
| Context sheet | `.ctx-action` | Four supported operations. Disabled with a reason when AI is unconfigured or there's no internet. |
| Read-only note | `.readonly-note` | On cloud conversations; explains why there's no composer |
| Link strip | `.link-strip` | Offered only to a Nearby conversation when signed in, sync is on and cloud conversations exist |
| Nearby stats | `.stats .stat` | Four counts |

The components from the first round are unchanged: transcript turns (no bubbles), translation gloss, voice strip (no fake waveform or duration), delivery words, proposal card, call layer, sheets and dialogs.

## 5. Colour roles (same as web)

| Role | Used for |
|---|---|
| Peacock (brand) | Connection, Nearby, primary actions, your own turns |
| Marigold (voice) | Speak button, push-to-talk, voice strips, speech settings |
| Iris (AI) | VAANI, Context, drafts, translation glosses, Cloud-linked badge |
| Status colours | Internet, sync and delivery state |

## 6. Responsive and accessibility

- Fluid from 320 to 430px with 16px gutters. Tested at 320, 360, 375, 390, 412 and 430, in light and dark.
- Tap targets are at least 32px and usually 44–56px. The harness checks every screen.
- `role="status"` on the connectivity rows and `role="log"` on transcripts.
- Language rows carry `lang` and are radios.
- Pinch zoom is not disabled.

![Chats at every width](android-ui/overview-widths-chats.png)
