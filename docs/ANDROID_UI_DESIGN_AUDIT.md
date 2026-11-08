# VAANI Android UI — Design & Architecture Audit

**Date:** 2026-11-06 · **Scope:** audit only (no code changed) · **Repo:** `D:\vibe\vaani` (git worktree of `D:\vibe\vokie`, branch `vaani/main`)
**Canonical reference:** `client/` (web). **Audited:** `app/` (Android).

> Method: source read of `app/` and `client/`; legacy page `app/src/main/assets/www/index.html` rendered in Chromium at 390×844 and 320×640 (no native bridge → prototype content); existing Appium screenshots in `tests/android/reports/`; contents of the built `app/build/outputs/apk/debug/app-debug.apk` (built 2026-11-06 09:08).
> Not done: no Android build or device run (this environment has no Android SDK; git history was not readable because the worktree's `gitdir` points at `D:\vibe\vokie\.git`, which isn't connected).

---

## 0. Headline findings

1. **The entire Android UI is one legacy WebView page.** `MainActivity` does `setContentView(WebView)` loading `file:///android_asset/www/index.html` (2,758 lines, 175 KB). All behaviour goes through the `ITantraNative` JavaScript bridge (`ui/bridge/ITantraNativeBridge.kt`). There is **no native screen** in the running app.
2. **The Compose UI in `ui/screens/**` is dead code.** `ChatScreen`, `SettingsScreen`, `OnboardingScreen`, `LocateScreen`, `RescuePeerListScreen`, `EmergencySheet` are referenced by no other file; `VokieTheme` is never applied by `MainActivity`. It compiles into the APK but is never shown.
3. **Icons are broken on real devices.** The page uses the *Material Symbols Rounded* ligature font from `material-symbols-rounded.woff2`, but that file is **not in `assets/www/`, nor in the debug APK**. Every icon renders as its raw word — visible in `tests/android/reports/startup.png`: “mic_off”, “chevron_right”, “person_search”, “settings”, “forum”, “groups”.
4. **It is still iTantra, not VAANI.** Wordmark “iTantra”, dark-only red/charcoal palette (`--accent #E8402B` on `#111111`), Helvetica/Arial, onboarding copy “Communicate when networks can’t… No account”. Nothing is shared with the web identity (Peacock/Iris/Marigold, Fraunces + Plus Jakarta Sans + Noto, light/dark).
5. **Some UI shows invented data.** Voice bubbles draw a waveform from `Math.random()` and derive the duration from text length (`0:0${ceil(len/20)}`). Group rows (“Team Alpha · 4 peers”, “Rescue Team”) are static markup with no backing model — the Android data model has **1:1 conversations only** (`Conversation(peerId)`). These are replaced only when the bridge delivers a non-empty `conversations` array.
6. **Existing Appium evidence is weaker than its file names.** `supported-language-selection-updates-the-real-WebView-state.png` and `transcript-navigation-opens-and-returns-from-messages.png` both show the **onboarding** screen, not language selection or a transcript.

---

## 1. Existing Android screen inventory

All screens are `<div class="screen" id="screen-…">` inside `index.html`, switched by `navTo(id, dir)`. System back is forwarded to JS (`window.handleAndroidBack` → else return to `screen-speak`).

| Legacy screen | Purpose | Live data via bridge | Notes |
|---|---|---|---|
| `screen-a1…a4` | Onboarding: intro, “stay connected nearby” (Wi-Fi/Bluetooth), language, setup | partial | language list here is static (Hindi, Tamil, Bengali) |
| `screen-speak` (home) | Push-to-talk to the selected peer; peer selector; language row | yes (`pttState`, `selectedPeer`, `language`) | real STT turn states: idle → listening → understanding → sending → delivered → speaking / failed |
| `screen-messages` | Conversation list | yes (`conversations`, unread badge) | prototype rows until state arrives; hardcoded badge “2” in markup |
| `screen-chat-rohan` | **The** chat screen for any peer (`openNativeChat` always opens this id) | yes (`chatMessages`) | bubble layout (out = right, in = left); delivery icons; urgent badge; AI “draft reply” status line |
| `screen-chat-sarav` | Second prototype chat | no | unreachable from live data; dead prototype |
| `screen-peers`, `screen-peers-searching` | Nearby/trusted peers, Wi-Fi Direct scan, discovery | yes | |
| `screen-pairing` | Incoming pair request accept/decline | yes (`incomingPairRequest`) | |
| `screen-call` | Voice call (mute, speaker, end) | yes (`call`) | |
| `screen-settings` | Device name, **cloud account (email/password sign-in)**, **cloud sync toggle / sync now**, language, language packs, speech (PTT toggle, TTS speed) | yes (`identity`, `cloud`, `ttsSpeed`) | |
| language picker sheet | 6 languages | yes (`onSelectLanguage`) | see §3 |
| speak-message reminder banner | In-app alert for new/urgent messages | yes | |

**Navigation:** bottom bar on Speak / Messages / Peers (3 tabs: `mic` SPEAK, `forum` MESSAGES, `groups` PEERS); Settings via a header icon. Fixed-height prototype frame (`#phone 360×740`) overridden to full-screen by CSS; fake status bar (`.sbar 9:41`) hidden by CSS on device.

**Bridge contract (must be preserved by any redesign):** 34 `@JavascriptInterface` methods incl. `onPttDown/Up`, `onSendMessage`, `onRequestAi(op, prompt, lang)`, `onCancelAiRequest`, peer discovery/pairing (`startDiscovery`, `connectPeer`, `sendPairRequest`, `acceptPair`, `declinePair`, Wi-Fi Direct), calls (`onStartCall`, `onAcceptCall`, `onRejectCall`, `onCancelCall`, `onEndCall`, mute/speaker), `playMessageAudio/stopMessageAudio`, `onUpdateDisplayName`, `onSelectLanguage`, `onSetTtsSpeed`, `onMarkAsRead`, `onDismissNotification`, `cloudLogin/cloudLogout`, `setCloudSyncEnabled`, `syncCloudNow`, `linkCloudConversation`, `getInitialState`. State arrives as one JSON snapshot via `window.onAppStateUpdate(state)` (keys: `identity, cloud{signedIn,email,syncEnabled,syncState}, ai{loading,error,text,actionsRequireConfirmation}, language, ttsSpeed, call, stripDot/stripText, selectedPeer, pttState/pttLabel, activePeers, discoveredPeers, trustedPeers, wifiDirectPeers, incomingPairRequest, conversations[], chatMessages[]`). Notification taps call `window.openNativeChat(peerId)`.
**Unused by the page today:** `onCancelAiRequest`, `stopDiscovery`, `onCancelCall`.

## 2. Canonical web screen inventory (`client/`)

Visual layer on disk is identical to the v2 design (hashes match for `tokens.css`, `app.css`, all components/screens). Only `lib/types.ts`, `state/chat.ts`, `state/session.tsx` differ from my last delivered version (logic, not visuals).

| Web screen | Component |
|---|---|
| Sign in / create account / reset; onboarding (3 steps + preferences) | `screens/Entry.tsx` |
| Index: wordmark, Companion, New, avatar → search (Ctrl/⌘K) → filters All·People·Groups·Unread → conversations | `App.tsx`, `ConversationList.tsx` |
| Today (empty stage) | `Today.tsx` |
| Conversation (transcript turns, interlinear translation, explain, plans) | `ConversationView.tsx`, `Transcript.tsx`, `Message.tsx` |
| Composer (VAANI strip, draft proposal, Speak → voice stage) | `Composer.tsx`, `AI.tsx`, `Voice.tsx` |
| Context pane (Brief / Ask) | `AI.tsx` |
| Search, New conversation / group | `Screens.tsx` |
| Companion (plans, AI activity, scope) | `Screens.tsx` |
| Settings: profile, language, appearance, privacy & AI, notifications, connected services | `Screens.tsx` |

Canonical tokens (source of truth `client/src/styles/tokens.css`): Peacock `#0B6E63` / dark `#4CC1B2`; Iris `#5646C8` / `#ACA0FF`; Marigold `#965D00` / `#F0A63E`; Ink `#1F2A5C`; paper neutrals (`#F3F1EB` bg … `#1A1915` text) and teal-charcoal dark (`#0E1413` … `#EAE8E1`); semantic success/warning/error/info/pending/offline/reconnecting. Type: Fraunces (editorial), Plus Jakarta Sans (UI), Noto Sans per Indic script. Radius 6/10/14/20/22; 4-px spacing; 44-px targets; motion 120/200/320 ms. Icons: Lucide + custom `AIMark` (orbit) and `VoiceMark` (three bars).

## 3. Visual inconsistencies (Android vs canonical web)

| Area | Android today | Canonical web |
|---|---|---|
| Brand | “iTantra” wordmark, red `#E8402B` accent | “vaani” wordmark + speech-bubble mark, Peacock |
| Theme | Dark only, `#111111` | Light + designed dark, follows system |
| Type | Helvetica/Arial (159 inline uses), no Indic web fonts → system fallback | Fraunces / Plus Jakarta Sans / self-hosted Noto per script |
| Icons | Material Symbols ligatures — **font missing → words on screen** | Lucide + AIMark/VoiceMark |
| Messages | Left/right bubbles, “WhatsApp-style” (comment in code) | Transcript turns, your turns on Peacock rule |
| Translation | Second line inside the bubble (“Spoken by iTantra in X’s language”) | Interlinear gloss with `SOURCE → TARGET · VAANI` label; original kept |
| AI | One “draft reply” action; result as a status line; not insertable | VAANI strip, proposal with Use & edit / Regenerate / Copy, Context pane |
| Voice | PTT orb; voice bubbles with random waveform | Marigold voice strip with real waveform, transcript label |
| Colour semantics | Red = brand *and* error *and* urgent; green = connected | Peacock / Iris / Marigold each mean one thing; states carry words |
| Styling method | 565 inline `style=""` attributes; tokens only partly used | Semantic tokens only |

## 4. Navigation differences

- Android: 3-tab bottom bar (Speak · Messages · Peers) + Settings in header; Speak (push-to-talk) is home.
- Web: single index column (no tab bar) with Companion / New / Profile; conversation-first; Today as empty state.
- Android has destinations web lacks: **Speak (PTT), Peers/pairing, Call, Wi-Fi Direct** — these are core offline features and must stay first-class.
- Web has destinations Android lacks: **Companion, Search, Context (Brief/Ask), Groups, New group** — not backed by Android data/bridge today (groups don’t exist in the Android model; Search, Brief/Ask and Companion need bridge + backend support).
- Recommendation: a bottom bar *is* justified on Android (unlike web) because there are 3–4 real, peer destinations; keep it, restyle it, and never add tabs for unsupported features.

## 5. Missing mobile interactions

- Keyboard-aware chat layout is only CSS (`flex:1` + safe-area padding); no `visualViewport` handling — composer may be covered on some keyboards (to verify on device).
- No light theme; no response to system dark mode or font scale (`user-scalable=no`, `maximum-scale=1` block pinch zoom — accessibility issue).
- AI: no rewrite/translate/explain/summarise entry points, no Stop (bridge has `onCancelAiRequest`), no “use draft” into the composer.
- Language: picker offers **6** languages (EN, HI, TA, TE, KN, BN) while the native model supports **10** (adds GU, MR, ML, OR); onboarding lists only 3; no search.
- Conversation search: none.
- Calls: no cancel for an outgoing call (bridge `onCancelCall` unused).
- Touch: many controls are `div onclick` (not focusable, no accessible names); icons have no labels.

## 6. Existing native functionality that must be preserved

Bluetooth + Wi-Fi Direct transport, peer discovery/pairing, ACK/retry delivery (`QUEUED → TRANSMITTING → RETRYING → RECEIVED_BY_PEER/RELAYED → DELIVERED / FAILED`), Room persistence (local-first), on-device STT (SenseVoice/Whisper + VAD), on-device translation (CTranslate2), on-device TTS (sherpa-onnx), voice calls, location/proximity guidance, semantic urgency labels, notifications → `openNativeChat`, cloud sign-in with secure credential store, **opt-in** cloud sync (`syncEnabled`, `syncState`), backend AI via `AiBackendProvider` (draft-before-send; `actionsRequireConfirmation`), permissions flow, system back.
None of these depend on the HTML; all are reached through the bridge — so the UI layer can be replaced without touching them.

## 7. Recommended migration approach

**Keep the WebView + `ITantraNative` bridge; replace the page.** Rationale:
- It is the architecture that runs today; every native capability is already exposed through it. A Compose rewrite would be a second UI architecture and a near-total rewrite of 2,758 lines of behaviour, with the existing Compose screens being unmaintained dead code.
- It lets Android reuse the canonical web design language directly (same tokens file, same fonts, same icon marks) while keeping native logic untouched.

Steps (one screen at a time, each verified):
1. **Fix the icon failure first** (bundle the icon font or switch to inline SVG icons) — it affects every screen today.
2. Introduce `assets/www/` as a small mobile shell with the canonical `tokens.css` (copied/generated from `client/`) and self-hosted fonts; add light theme + `prefers-color-scheme`.
3. Rebuild screens on the same bridge contract, in this order: Messages list → Conversation (transcript layout, real delivery states) → Speak/PTT → Settings (incl. cloud account + sync, unchanged semantics) → Language picker (all 10 languages, native script, search) → Peers/Pairing → Call → Onboarding.
4. Expose only supported AI actions (REPLY today; REWRITE/TRANSLATE/SUMMARIZE exist in `AiOperation` and can be surfaced through `onRequestAi`) with draft-before-send and Stop via `onCancelAiRequest`.
5. Remove prototype-only content (`screen-chat-sarav`, static group rows, random waveforms, hardcoded badge) or show honest empty states.
6. Decide separately whether to delete or revive the dead Compose screens; don’t maintain both.
7. Verify with the existing Appium runner (`tests/android/run.mjs`, UiAutomator2) — fix the two mis-captured screenshots — plus a browser harness that drives `onAppStateUpdate` with recorded states at 320–430 px.

## 8. Proposed mobile design system (summary)

- **Tokens:** reuse `client/src/styles/tokens.css` verbatim (same names, same light/dark values) so web and Android share one source; mobile overrides only for layout (`--tap: 48px`, bottom bar height, safe-area insets).
- **Type:** Plus Jakarta Sans UI, Fraunces for screen titles only, bundled Noto Sans for HI/MR (Devanagari), TA, TE, KN, ML, BN, GU, and **Odia** (missing from web today). Base 16 px message text; respect system font scale.
- **Identity mapping:** Peacock = brand/own turns/primary; Marigold = voice — the PTT control, recording, playback (fits Android’s voice-first home); Iris = AI only. Error red is no longer the brand colour.
- **Components:** app bar, bottom bar (3–4 tabs, labelled), conversation row (with transport/presence + unread), transcript turn, voice strip, delivery-state line (words + icon), PTT “voice key”, AI draft proposal, language row (native script + English), settings row/toggle, sheet, banner, empty/error/offline states.
- **Layout:** mobile-first single column, 16-px gutters, 320–430 px verified, keyboard-aware composer, bottom sheets for pickers.

## 9. Evidence

- `docs/android-ui-audit/legacy-screens-390.png` — Speak, Messages, Chat, Settings, Peers, Onboarding rendered from `index.html` (no bridge; icon words visible).
- `tests/android/reports/startup.png` — real device: icon ligatures shown as text.
