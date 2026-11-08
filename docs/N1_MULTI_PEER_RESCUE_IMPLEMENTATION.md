# N1: Multi-Peer Offline Communication Implementation

## Overview

This document describes the implementation of robust multi-peer offline communication for the iTantra/Vokie Android app. The implementation ensures that multiple simultaneous communication sessions can coexist without:

- Message mixing between peers
- Wrong-peer replies
- Incorrect ACK association
- TTS/translation context leaks

## Architecture

### Core Components

1. **PeerSessionManager** (`app/src/main/java/com/vokie/communication/PeerSessionManager.kt`)
   - Central authority for per-peer session state
   - Maintains isolation of all peer-scoped data
   - Thread-safe synchronization with `@Synchronized`

2. **PeerSessionState** (data class)
   ```kotlin
   data class PeerSessionState(
       val peerId: String,
       val displayName: String? = null,
       val connectionState: TransportConnectionState = TransportConnectionState.IDLE,
       val transport: TransportType? = null,
       val lastSeen: Long? = null,
       val lastMessageTimestamp: Long? = null,
       val lastMessagePreview: String? = null,
       val pendingMessageCount: Int = 0,
       val unacknowledgedMessageCount: Int = 0,
       val unreadCount: Int = 0,
       val sourceLanguage: String? = null,
       val targetLanguage: String? = null,
       val priority: Int = 0,
       val sessionStatus: SessionStatus = SessionStatus.ACTIVE,
   )
   ```

3. **CommunicationViewModel** (`app/src/main/java/com/vokie/ui/communication/CommunicationViewModel.kt`)
   - Exposes `selectedPeerId` for explicit peer selection
   - Provides `effectivePeerId` (explicit selection or transport-level connected peer)
   - Filters messages via `selectedPeerMessages` StateFlow
   - Exposes `totalUnreadCount` and per-peer unread counts
   - Marks messages as read when selecting a peer

4. **RescuePeerListScreen** (`app/src/main/java/com/vokie/ui/screens/rescue/RescuePeerListScreen.kt`)
   - Displays all peer sessions with connection state, priority, unread badges
   - Shows last message preview per peer
   - Supports simulated peers for testing (DEBUG builds)

5. **ReceiverTranslationCoordinator** (`app/src/main/java/com/vokie/translation/ReceiverTranslationCoordinator.kt`)
   - Translation cache is keyed by `(messageId, targetLanguage)`
   - No cross-message or cross-peer translation leakage
   - TTS handoff is per-presentation, not per-session

## Key Features

### 1. Message Isolation

Each message belongs to exactly one peer conversation:
- Incoming messages are routed via `senderId`
- Outgoing messages are routed via `receiverId`
- `selectedPeerMessages` filters to show only the selected peer's conversation

### 2. Unread Count Tracking

```kotlin
// Increment on incoming message
fun recordIncomingMessage(peerId: String, message: Message, incrementUnread: Boolean = true)

// Clear when viewing conversation
fun markAsRead(peerId: String)

// Aggregate for badges
fun totalUnreadCount(): Int
```

### 3. ACK Correlation

ACKs are strictly associated with the correct peer:
```kotlin
fun recordAck(peerId: String, messageId: String, sequenceNumber: Long): Boolean {
    // Only accepts ACK if:
    // 1. Peer session exists
    // 2. Message ID was pending for THIS peer
    // 3. Sequence number matches
}
```

### 4. Priority Isolation

Each peer has independent priority tracking:
- Priority is updated from incoming message metadata
- SOS messages set priority = 200 (EMERGENCY)
- UI shows priority badge: EMERGENCY (≥200), URGENT (≥100), IMPORTANT (≥50)

### 5. Connection State Independence

Each peer maintains its own connection lifecycle:
- Connection state is per-peer, not global
- Disconnect/reconnect preserves session state
- Pending messages survive reconnection

### 6. Translation/TTS Context Isolation

- Translation cache key includes `messageId` → no cross-message leakage
- TTS handoff is derived from individual `ReceiverPresentation`
- Source language is stored per-peer in session state

## Test Coverage

### Unit Tests

1. **MultiPeerRescueTest** (28 tests)
   - Registration and independence
   - Message routing and unread counts
   - ACK correlation per peer
   - Priority isolation
   - Language context isolation
   - Connection lifecycle
   - Complex interleaved scenarios

2. **MultiPeerTtsIsolationTest** (12 tests)
   - Translation isolation between peers
   - Same text from different peers
   - TTS handoff language correctness
   - Cache isolation and hits
   - Edge cases (blank/null inputs)

3. **MultiPeerSimulationTest** (20 tests)
   - End-to-end deterministic simulation
   - Three-peer interleaved events
   - Simulated peer lifecycle

4. **PeerSessionManagerTest** (existing, extended)
5. **PeerMessageRoutingTest** (existing, extended)

## UI Integration

### Peer List Screen
- Shows all peer sessions grouped by connection state
- Unread badge on avatar (shows count, "9+" if >9)
- Last message preview
- Priority indicators
- Connection status and timestamp

### Chat Screen
- `selectedPeerMessages` provides filtered message list
- Selecting a peer marks all messages as read
- Outgoing messages automatically include `receiverId`

## Backward Compatibility

The implementation maintains full backward compatibility with:
- Existing two-device communication flow
- PacketV2 protocol unchanged
- SenseVoice STT, NLLB translation, Sherpa-ONNX TTS unchanged
- Bluetooth and Wi-Fi Direct transports unchanged
- Room persistence unchanged

## Debug Features

- `ADD SIM` / `REMOVE SIM` button in peer list (DEBUG builds)
- Simulated peers: `SIM-CIV-001`, `SIM-CIV-002`, `SIM-CIV-003`
- VokieLog rescue tags for session events

## Files Modified

### Core Session Management
- `app/src/main/java/com/vokie/communication/PeerSessionManager.kt`
  - Added `unreadCount`, `lastMessagePreview`, `sessionStatus` to PeerSessionState
  - Added `markAsRead()`, `totalUnreadCount()` methods
  - Enhanced `recordIncomingMessage()` to track unread and preview

### ViewModel
- `app/src/main/java/com/vokie/ui/communication/CommunicationViewModel.kt`
  - Added `totalUnreadCount` StateFlow
  - Added `unreadCountFor()` method
  - `selectPeer()` now marks messages as read

### UI
- `app/src/main/java/com/vokie/ui/screens/rescue/RescuePeerListScreen.kt`
  - Added unread badge on peer avatar
  - Added last message preview
  - Enhanced metadata display

### Tests
- `app/src/test/java/com/vokie/communication/MultiPeerRescueTest.kt` (new)
- `app/src/test/java/com/vokie/communication/MultiPeerTtsIsolationTest.kt` (new)

## Usage Example

```kotlin
// 1. View all peer sessions
val sessions = viewModel.peerSessions.collectAsState()

// 2. Select a peer to view conversation
viewModel.selectPeer("CIV-001")

// 3. Get filtered messages
val messages = viewModel.selectedPeerMessages.collectAsState()

// 4. Send message to selected peer (handled automatically)
viewModel.sendMessage("Help is coming")

// 5. Check total unread
val unread = viewModel.totalUnreadCount.collectAsState()
```

## Verification Checklist

- [x] Messages from Civ-A appear only in Civ-A conversation
- [x] ACK from Civ-A completes only messages sent to Civ-A
- [x] Selecting Civ-B shows only Civ-B messages
- [x] Unread count increments only for receiving peer
- [x] Mark-as-read clears only selected peer's count
- [x] Priority is isolated per peer
- [x] Translation cache is keyed by messageId
- [x] TTS handoff contains correct target language
- [x] Reconnection preserves session state
- [x] Simulated peers can be added/removed (DEBUG)
