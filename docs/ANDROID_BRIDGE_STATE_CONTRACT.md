# Android Bridge State Contract

This document is the contract between the native app and the WebView UI. It was written **before** the native changes, as the approved plan requires.

The rule for this work: only state exports and one read-only selection method are added. No change is made to messaging, transport, pairing, synchronisation, authentication or delivery behaviour.

## 1. Unchanged: all 34 `@JavascriptInterface` methods

Existing methods are not renamed, re-signatured, removed, or changed in behaviour.

| Method | Group |
|---|---|
| `onPttDown()`, `onPttUp()` | Push-to-talk |
| `onSendMessage(peerId, text)` | Messaging |
| `onRequestAi(operation, prompt, targetLanguage?)`, `onCancelAiRequest()` | AI |
| `onSelectPeer(peerId)` | Selection |
| `startDiscovery()`, `stopDiscovery()`, `connectPeer(peerId)`, `onDiscoverWifiDirect()`, `onConnectWifiDirect(address)` | Nearby |
| `sendPairRequest(peerId)`, `acceptPair(requestId)`, `declinePair(requestId)` | Pairing |
| `onStartCall(peerId)`, `onAcceptCall(callId)`, `onRejectCall(callId)`, `onCancelCall(callId)`, `onEndCall(callId)`, `onSetCallMuted(Boolean)`, `onSetCallSpeakerphone(Boolean)` | Calls |
| `playMessageAudio(messageId, text, languageCode)`, `stopMessageAudio(messageId)` | TTS |
| `onUpdateDisplayName(name)`, `onSelectLanguage(code)`, `onSetTtsSpeed(Float)` | Settings |
| `onMarkAsRead(peerId)`, `onDismissNotification(peerId)` | Notifications |
| `cloudLogin(email, password)`, `cloudLogout()`, `setCloudSyncEnabled(Boolean)`, `syncCloudNow()`, `linkCloudConversation(localConversationId, cloudConversationId: Long)` | Cloud |
| `getInitialState(): String` | State |

The native → JS callbacks are also unchanged:
- `window.onAppStateUpdate(json)`
- `window.openNativeChat(peerId)`
- `window.onCloudAuthResult(ok, message)`
- `window.handleAndroidBack()`

## 2. Unchanged: existing state fields

These fields keep the same names, types and meanings:
- `identity{peerId,name,initial}`
- `cloud{signedIn,displayName,email,syncEnabled,syncState}`
- `ai{loading,error,text,actionsRequireConfirmation}`
- `language`
- `ttsSpeed`
- `call{callId,state,direction,peerId,peerName,peerInitial,failureReason}`
- `stripDot`, `stripText`
- `selectedPeer{…}`
- `pttState`, `pttLabel`
- `activePeers[]`, `discoveredPeers[]`, `trustedPeers[]`, `wifiDirectPeers[]`
- `incomingPairRequest`
- `conversations[{id,peerId,name,initial,lastMessage,time,unreadCount}]`
- `chatMessages[{id,text,senderId,isOutgoing,time,status,type,language,urgent,semanticLabel}]`

These were added earlier in this redesign and are still present:
- `aiConfigured`
- `speech{sttState,ttsInstalled[]}`
- `messageAudio{id:state}`
- `chatMessages[].translatedText`, `chatMessages[].translatedLanguage`

## 3. Additions (minimal)

| Field | Shape | Source (read-only) | Why |
|---|---|---|---|
| `network` | `{state:"none"\|"connecting"\|"limited"\|"online", transport:"wifi"\|"cellular"\|"ethernet"\|"other"\|null, metered:bool}` | New `NetworkStatusSource`: `ConnectivityManager.registerDefaultNetworkCallback`. `online` **only** with `NET_CAPABILITY_VALIDATED`. `limited` = `NET_CAPABILITY_CAPTIVE_PORTAL`. `connecting` = a network with `NET_CAPABILITY_INTERNET` that isn't validated. | Separates internet from Nearby. A Wi-Fi or mobile connection on its own is not reported as online. |
| `nearby` | `{discovered:int, connected:int, trusted:int, pairingIncoming:int, pairingOutgoing:int}` | `peersUiState.discoveredPeers` (ACTIVE vs not), `trustedPeers`, `pendingRequests` split by `receiverPeerId`/`senderPeerId == deviceId` | Four separate counts, never combined into one |
| `outgoingPairRequests` | `[{requestId, peerId, name}]` | `pendingRequests` where `senderPeerId == deviceId` | Shows "Request sent" against a peer |
| `cloud.configured` | bool | `BuildConfig.AI_BACKEND_BASE_URL.isNotBlank()` (the URL `AuthClient` and `SyncApiClient` use) | Explains why sign-in can't work in an unconfigured build |
| `cloud.lastSyncedAt` | epoch ms \| null | `cloud_user.lastSyncedAt` | "Synced 5 min ago" |
| `conversations[].origin` | `"nearby"` \| `"cloud-linked"` | `cloud_id_mapping` (current account, `localType='conversation'`, `cloudId` not null) | Nearby vs linked. See `PLATFORM_CAPABILITY_MATRIX.md` §3 |
| `conversations[].sync` | `{cloudId, pending, failed, synced}` \| null | New read-only DAO query: `cloud_id_mapping` ⨝ `messages` grouped by `messages.conversationId` and `syncStatus`, for the current account | Per-conversation sync indicators from persistent records |
| `cloudConversations` | `[{cloudId, kind, title, members[], updatedAt, lastMessage{body,senderName,kind,createdAt}\|null}]` | `cloud_conversations` + `cloud_members` + `cloud_messages`. **Only** rows not mapped to a local conversation for the current account. | Shows Cloud conversations (including groups) that are stored on the phone |
| `cloudChat` | `{cloudId, messages[{cloudId,senderName,isMine,kind,body,sourceLanguage,createdAt,deleted}]}` \| null | `cloud_messages.observeRecent(cloudId, 100)` for the conversation the UI opened | Read-only view of a cloud conversation |

**New methods:** one pair, and it only changes which cached cloud conversation's messages are exported.

| Method | Effect |
|---|---|
| `onOpenCloudConversation(cloudConversationId: String)` | Sets the selected cloud conversation in the export source. No network call, no write. |
| `onCloseCloudConversation()` | Clears that selection |

**One read-only DAO query** is added to `CloudIdMappingDao`:
- `observeMessageSyncCounts(accountId)`: `SELECT messages.conversationId, cloud_id_mapping.syncStatus, COUNT(*) … GROUP BY …`
- It doesn't change the database schema, so no migration is needed.

**Re-push triggers** in `MainActivity` are added for the new flows: network status, cloud snapshot, and `pairingUiState`, which was already collected.

## 4. Explicitly not changed

- `CloudSyncRepository.sync`, `queueForSync`, `linkLocalConversation`, `logout`
- `SyncCoordinator` scheduling
- `SyncApiClient`, `AuthClient`
- `MessageRepository`, delivery states, retry
- Transports: Bluetooth, Wi-Fi Direct
- Pairing repository, trusted peers
- Call manager
- STT/TTS engines
- Database schema (version 10)
- Message delivery semantics

## 5. Gaps recorded, not implemented

- Sending into a cloud-only conversation: there is no native path.
- Realtime cloud updates: no socket.
- Cloud edits, deletions and read cursors: `TODO` in sync.
- AI token provider: returns null.
