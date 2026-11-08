# VAANI — Screen Inventory (v2)

| # | Screen | Where | Component | Notes |
|---|---|---|---|---|
| 1 | Main home | stage, wide screens | `Today` | real unread + open plans |
| 2 | Conversation list | index | `ConversationList` | filters, language glyphs, voice previews, typing |
| 3 | Individual conversation | stage `#/chats/:id` | `ConversationView` + `Transcript` | |
| 4 | Group conversation | same | same + `Details` sheet | rename, members, languages, leave |
| 5 | Composer | conversation | `Composer` | VAANI strip, proposal, Speak/Send |
| 6 | Voice recording | composer | `VoiceStage` | live mic waveform, pause/resume, review |
| 7 | Voice playback | transcript | `VoicePlayer` | seek, speed, transcript states |
| 8 | AI companion | conversation | `ContextPane`, inline annotations, strip | no separate chatbot |
| 9 | AI reply preview | composer | `DraftProposal` | |
| 10 | Translation | under message | `Message` gloss | |
| 11 | Conversation summary | Context → Brief | `ContextPane` | |
| 12 | Search | index `#/search` | `SearchScreen` | jumps to `#/chats/:id?m=` |
| 13 | New conversation | index `#/new` | `NewConversation` | |
| 14 | Group creation | index `#/new/group` | `NewConversation` | shows the group’s languages |
| 15 | Profile | `#/settings/profile` | `ProfileSettings` | photos not supported |
| 16 | Settings | `#/settings` | `SettingsScreen` | |
| 17 | Language preferences | `#/settings/language` | `LanguageSettings` | native-script grid |
| 18 | AI privacy | `#/settings/privacy` | `PrivacySettings` | |
| 19 | Connection status | index + conversation | `ConnectionIndicator`, per-message status | |
| 20 | Onboarding | first login | `Onboarding` | |
| — | Companion | `#/companion` | `CompanionScreen` | plans, AI activity, scope |
| — | Sign in / up / reset | anon | `AuthScreen` | |

```text
Auth ─► Onboarding ─► Today ─┬─ index: conversations / search / new / new group
                             ├─ Conversation ─┬─ VAANI strip ─► Proposal ─► composer
                             │                ├─ Speak ─► Voice stage ─► voice note
                             │                ├─ message ▸ Translate | Explain | Find plans | Read aloud
                             │                └─ Context ─► Brief | Ask ─► jump to message
                             ├─ Companion
                             └─ Settings ─► section
```
