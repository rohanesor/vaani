# VAANI — Android and Web: Shared Identity, Different Capabilities

**One product, different platform capabilities.** Android and Web look and speak like the same product. Android is the full communication app with online, offline, nearby and native capabilities. Web is a focused online client.

Android is not reduced to match Web, and Web is not given Android features it can't support. The capability boundaries, with code evidence, are in [`PLATFORM_CAPABILITY_MATRIX.md`](PLATFORM_CAPABILITY_MATRIX.md).

![Web and Android, conversation list and transcript](android-ui/parity-web-vs-android-chats.png)
![Web and Android, settings; Nearby is Android only](android-ui/parity-web-vs-android-settings.png)

## 1. What is shared (identity)

| Aspect | Status |
|---|---|
| Tokens | Identical file (SHA-256 match, CI check `--check`) |
| Display and UI type | Same font files (Fraunces, Plus Jakarta Sans, Noto scripts). Android adds Odia. |
| Icons | Same Lucide glyphs and the same VAANI AI and voice marks |
| Colour roles | Peacock = brand/connection, Marigold = voice, Iris = AI |
| Conversation hierarchy | Speaker turns with a coloured rule; your turns tinted; transcript labels; translation under the original. No chat bubbles. |
| Composer | "VAANI · Message … · Speak" pill, Context button in the header |
| AI principle | Draft → you edit → you send. Neither platform auto-sends. |

## 2. What differs on purpose (information architecture)

| Area | Web | Android | Why |
|---|---|---|---|
| Navigation | Two panes: list on the left, conversation on the right | Three tabs: Chats · Nearby · Settings. The conversation is a full-screen push. | Phone, and Nearby needs its own destination |
| Home | Conversation list | Chats with separate **Internet** and **Nearby** indicators | Android works offline, and users must see both states |
| Filters | All · People · Groups · Unread | All · Nearby · Cloud · Unread | Android conversations differ by **origin**, not just by type |
| Voice | Voice notes recorded in the browser and uploaded | Push-to-talk to a nearby person, on-device STT and TTS | Native capability |
| Cloud conversations | Live, read/write | Copies stored on the phone: read-only, and syncable when linked | Android has no direct send to the server and no socket (see matrix) |
| Groups | Supported | Visible as Cloud (read-only). Local nearby chats are 1:1. | Native data model |
| Connectivity | WebSocket state | Validated internet plus nearby counts | Different transports |

## 3. Android-only capabilities (first-class in the UI)

| Capability | Where in the UI |
|---|---|
| Nearby discovery, Wi-Fi Direct, pairing, trusted peers | Nearby tab (four separate counts) |
| Offline phone-to-phone messaging with delivery states | Conversation |
| Push-to-talk | Speak overlay and the composer Speak key |
| Nearby voice calls | Call layer |
| On-device speech recognition and voices, per-language availability | Settings and the language sheet |
| Receiver-local translation | Translation gloss in the transcript |
| Local and cloud identity separation, explicit linking, per-conversation sync status | Origin badges, sync lines, Link sheet |

## 4. Web-only capabilities (not copied into Android)

None of these is simulated in the Android UI:
- Realtime updates
- Reactions, edits and deletes
- Search across all messages
- AI Explain and Ask
- Sending into any cloud conversation
- Manual theme choice

## 5. Gaps that need native work (not UI work)

1. Send into a cloud-only conversation.
2. Realtime cloud updates on Android.
3. Cloud edits, deletions and read cursors in sync (marked `TODO` in `CloudSyncRepository.sync`).
4. AI token provider (currently returns null).
5. Mute and speaker state exported from the call manager.
