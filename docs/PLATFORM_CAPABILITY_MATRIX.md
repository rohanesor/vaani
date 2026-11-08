# VAANI — Platform Capability Matrix

**One product, different platform capabilities.** Android is the full communication app: online, offline, nearby and native device features. Web is an online client with a focused feature set. They share identity, design language and communication concepts — not feature sets.

Every row below was checked against the code in `D:\vibe\vaani` on 2026-11-07. The evidence column names the file. Each row uses these status values:
- **Native**: implemented on the device.
- **Supported**: works through the VAANI server.
- **Partial**: works with a limitation that is stated in the row.
- **Not supported**
- **Gap**: the capability exists in code but isn't reachable from the UI.

"Requires config" means the build or server needs credentials or URLs that aren't set by default.

## 1. Matrix

| Capability | Web (`client/` + `server/`) | Android (`app/`) |
|---|---|---|
| **Account / cloud authentication** | Supported: `server/src/routes/core.ts` `/auth/login`, `/register`, `/logout` | Supported, requires config: `cloud/AuthClient.kt` calls `/api/auth/*` on `BuildConfig.AI_BACKEND_BASE_URL`. That URL is empty by default, so sign-in fails until it is set. |
| **Use without an account** | Not supported. Every route needs a session. | Native. Identity is a local device ID (`SettingsRepository.identity`). Nearby features need no account. |
| **Online messaging** | Supported: `POST /conversations/:id/messages` | Partial. There is no direct send to the server. Messages in a *linked* local conversation are queued (`CloudSyncRepository.queueForSync`, called from `VokieApplication` when a local message is created) and uploaded at the next `/api/sync`. |
| **Realtime updates** | Supported: WebSocket hub `server/src/realtime.ts` and `client/src/lib/realtime.ts` | Not supported. Android has no socket client. Cloud changes arrive through sync, either on demand or through periodic WorkManager jobs (`cloud/SyncCoordinator.kt`). |
| **Cloud synchronisation** | Not applicable. The web app is live against the server. | Supported, requires config and opt-in. `POST /api/sync` (`server/src/routes/sync.ts`) runs a cursor-based upload and download, opt-in through `setCloudSyncEnabled`. It is gated on `NetworkType.CONNECTED`. |
| **Cloud conversations on device** | Not applicable | Gap → fixed in this work. They were stored in `cloud_conversations` and `cloud_messages` but not exported to the UI. They are now exported read-only. |
| **Sending into a cloud-only conversation** | Supported | **Not supported.** Upload requires a *local* message in a conversation that is linked to the cloud one. Recorded as native follow-up work. |
| **Group conversations** | Supported: `kind: 'group'` | Partial. Cloud groups can be stored and viewed (`CloudConversationEntity.kind`). Local nearby conversations are 1:1 only (`ConversationEntity.peerId`). |
| **Offline queue** | Partial. Text composed while disconnected is kept in `localStorage` (`state/chat.ts`, `vaani.outbox.v1.<user>`) and sent when the server is reachable again. There is no peer-to-peer path. | Native. Messages persist in Room with `DeliveryState` (QUEUED, RETRYING…) and retry over Wi-Fi or Bluetooth (`MessageDao.observeOutboundQueue`). |
| **Offline (no internet) messaging** | Not supported | Native: Bluetooth and Wi-Fi Direct transports |
| **Nearby discovery (Bluetooth)** | Not supported | Native: `BluetoothTransport`, `startDiscovery` |
| **Wi-Fi Direct** | Not supported | Native: `onDiscoverWifiDirect`, `onConnectWifiDirect` |
| **Pairing / trusted peers** | Not supported | Native: `pair_requests`, `trusted_peers`, `sendPairRequest`, `acceptPair`, `declinePair` |
| **Nearby voice calls** | Not supported | Native: `onStartCall` … `onEndCall`, with 11 CallStates |
| **Push-to-talk** | Partial. Hold-to-record voice notes through `getUserMedia` and AudioWorklet (`client/src/lib/recorder.ts`), uploaded to `/conversations/:id/voice`. Not a live channel. | Native. `onPttDown`/`onPttUp` → on-device STT → send → TTS on the receiver. |
| **Speech recognition** | Supported, requires config: server-side `POST /messages/:id/transcribe` (Gnani, `server/src/speech/gnani.ts`) | Native, offline: on-device models for 10 languages (`stt/SttModels.kt`). Status is exported as `speech.sttState`. |
| **Text-to-speech** | Supported, requires config: server `POST /tts` (Gnani) | Native, offline: MMS/VITS for 10 languages (`tts/TtsModels.kt`), with per-message state and voices downloadable per language |
| **Translation** | Supported, requires config: `POST /ai/translate` (Claude) | Native: receiver-local translation stored on `Message.translatedText` |
| **AI assistance** | Supported, requires config: `/ai/assist` (reply, rewrite, explain, ask), `/ai/summarize`, `/ai/actions/*` | Partial, requires config: `AiOperation` covers REPLY, REWRITE, TRANSLATE and SUMMARIZE (plus SEARCH, EXTRACT_ACTIONS and VOICE_TO_ACTION, which return structured output). It is `UnconfiguredAIProvider` unless `AI_BACKEND_BASE_URL` is set, and the token provider returns null. |
| **AI drafts never auto-send** | Yes: `Composer.tsx` proposal → "Use & edit" | Yes: the draft goes to the composer; tests assert no `onSendMessage` call |
| **Search** | Supported: `/search` across all messages | Partial: names and last-message previews only |
| **Reactions, edit, delete** | Supported: `/messages/:id/reactions`, `PATCH`, `DELETE` | Not supported. Sync request edits and deletions are `TODO` in `CloudSyncRepository.sync`. |
| **Read state** | Supported: `/conversations/:id/read`, live | Partial. Local `unreadCount` only; cloud read cursors are `TODO`. |
| **Internet status shown to user** | Partial: `navigator.onLine` plus WebSocket state (`realtime.ts`) | Gap → fixed in this work. Only the model downloader used `ConnectivityManager` before. A validated-connectivity monitor is now added (§2). |
| **Notifications** | Partial: in-app only | Native: `AppNotificationManager`, notification tap opens the chat |
| **Theme** | Light/dark/system setting | Follows the system |

## 2. Status definitions used by the Android UI

**Internet is separate from Nearby.** A phone can have no internet and still be connected to a nearby peer.

| Indicator | Source | States |
|---|---|---|
| **Internet** | `ConnectivityManager` default-network callback (new, read-only) | `none` = no network<br>`connecting` = network present but not validated by Android<br>`limited` = captive portal<br>`online` = `NET_CAPABILITY_VALIDATED`<br>Wi-Fi or mobile data being connected is **not** enough for `online`. |
| **Nearby** | `peersUiState`, `pairingUiState` | Four separate counts: **discovered** (in range, not active), **connected** (`PeerConnectionState.ACTIVE`), **trusted** (`trusted_peers` ACTIVE), **pairing** (incoming and outgoing PENDING requests) |
| **Cloud account** | `cloud_user`, Keystore credential | signed out / signed in / not configured in this build |
| **Sync** | `SyncCoordinator.syncState` | Idle, Syncing, Synced(time), WaitingForConnection, RetryPending, AuthRequired, Error, PartialError |

## 3. Conversation origin model

Origin is derived from persistent records, never from UI state.

| Origin | Rule | Evidence |
|---|---|---|
| **Nearby** | A local `conversations` row with **no** account-scoped conversation mapping for the signed-in account. Local rows are created only by `RoomMessageRepository.ensureConversation(peerId)`, the peer-to-peer channel for one device. | `data/MessageRepository.kt:86-92` |
| **Cloud-linked** | A local `conversations` row **with** a `cloud_id_mapping` row where `accountCloudUserId = current account`, `localType = 'conversation'` and `cloudId` is not null. | `CloudSyncRepository.linkLocalConversation`, `CloudIdMappingDao` |
| **Cloud** | A `cloud_conversations` row whose `cloudId` is not mapped to a local conversation for the current account. | `CloudSyncRepository.sync` stores conversations with `localConversationId = null` |

**Per-conversation sync status for Cloud-linked conversations** counts, from `cloud_id_mapping` (current account, `localType = 'message'`, joined to `messages.conversationId`):
- **pending**: `syncStatus = 'pending'`
- **failed**: `syncStatus = 'error'`
- **synced**: `syncStatus = 'synced'`

Nearby conversations show no sync badge because they don't sync. After sign-out, `logout()` clears that account's mappings, so links disappear with the account. They are never reused by another account.

## 4. What this means for each UI

- **Web** shows online features only. It must not show nearby, pairing, Wi-Fi Direct, offline peer messaging or device speech models.
- **Android** shows everything in the Android column. Online features show their requirements honestly (sign in, configure the build, link a conversation), and nearby features never depend on them.
