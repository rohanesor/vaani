# LAP 1.1 — Frontend ↔ Backend Wiring Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `1c0e6cd`  

## Audit Summary

✅ **PASS** — Frontend correctly wired to backend state.

No fake state, hardcoded values, or disconnected buttons found in production paths.

## Complete Wiring Map

### 1. PTT (Push-to-Talk)

```
UI (ChatScreen / VoiceHome)
└─> pointerInput / gesture detection
└─> CommunicationViewModel.startVoice() / stopVoice()
└─> voiceCommands Channel (serializes PTT commands)
└─> ContinuousTurnManager.start() / stop()
└─> SenseVoiceSttEngine.transcribe()
└─> TurnEvent.Sentence
└─> Repository.createMessage()
└─> PacketV2 → Transport → Peer
```

**Status:** ✅ WIRED — No fake timers or animations pretending to record.

### 2. Microphone / Audio Recording

```
ContinuousTurnManager
└─> MicrophoneAudioRecorder
└─> VAD (EnergyVadEngine)
└─> AudioPreprocessor
└─> SenseVoiceSttEngine / WhisperSttEngine
```

**Status:** ✅ WIRED — AudioRecord is real; VAD detects actual speech.

### 3. STT (Speech-to-Text)

```
SpeechToTextUseCase
└─> SttEngine.transcribe()
└─> SttStatus StateFlow
└─> CommunicationViewModel.sttStatus
└─> ChatScreen observes via collectAsState()
```

**Status:** ✅ WIRED — SenseVoice is primary; Whisper is fallback.

### 4. Language Selection

```
OnboardingScreen / SettingsScreen
└─> UserLanguageProfilePreferences.select()
└─> DataStore persistence
└─> preferredLanguage StateFlow
└─> STT language routing
└─> Translation source/target routing
└─> TTS output language
```

**Status:** ✅ WIRED — Canonical language configuration flows to all engines.

### 5. Translation (Receiver)

```
VokieApplication.inboundPackets.messages
└─> ReceiverTranslationCoordinator.presentOnce()
└─> Ctranslate2TranslationEngine
└─> ReceiverPresentation stored in presentations StateFlow
└─> Message.translatedText persisted to Room
└─> MessageBubble displays translated text for incoming
```

**Status:** ✅ WIRED — Translation persisted; survives app restart.

### 6. Message Sending

```
CommunicationViewModel.send()
└─> Repository.createMessage()
└─> Room persistence (QUEUED state)
└─> OutboundMessageProcessor observes queue
└─> TransportManager.send()
└─> PacketV2.encode()
└─> BluetoothTransport.send() / WifiDirectTransport
```

**Status:** ✅ WIRED — Messages flow through actual transport.

### 7. Peer Discovery

```
ConnectionSheet / MoreScreen
└─> CommunicationViewModel.discover()
└─> TransportManager.discoverBluetooth()
└─> BluetoothTransport.discoverPeers()
└─> peers StateFlow (actual Bluetooth scan results)
└─> ConnectionSheet displays real nearby devices
```

**Status:** ✅ WIRED — No fake peer injection in production.

### 8. Peer Selection / Connection

```
ConnectionSheet
└─> CommunicationViewModel.connect(peerId)
└─> TransportManager.connectBluetooth()
└─> BluetoothSocket connection
└─> connectionState StateFlow
└─> connectedPeerId StateFlow
└─> UI reflects actual connection state
```

**Status:** ✅ WIRED — Connection state is real transport state.

### 9. Connection Status

```
TransportManager.connectionState (StateFlow)
└─> CommunicationViewModel.connectionState
└─> ChatScreen / DesignHomeHeader observe
└─> Displays: SEARCHING / CONNECTING / CONNECTED / DISCONNECTED
```

**Status:** ✅ WIRED — No hardcoded "Connected" strings.

### 10. ACK / Delivery State

```
Outbound packet sent
└─> AckTracker awaits response
└─> Inbound ACK packet received
└─> Message.deliveryState updated in Room
└─> MessageBubble displays ✓✓ for RECEIVED_BY_PEER
```

**Status:** ✅ WIRED — Delivery state reflects actual transport ACK.

### 11. Queue / Retry

```
OutboundMessageProcessor
└─> observeOutboundQueue() (Room flow)
└─> RetryPolicy governs retries
└─> ReconnectPolicy handles reconnection
└─> Message.deliveryState = QUEUED / RETRYING / FAILED
└─> UI shows PENDING / RETRYING / FAILED badges
```

**Status:** ✅ WIRED — N2 store-forward physically validated.

### 12. Semantic Metadata (N5)

```
Repository.createMessage()
└─> SemanticExtractor.extract()
└─> SemanticMetadata stored in Message
└─> PacketV2 includes 7-byte wire format
└─> Receiver displays semantic tags (e.g., "URGENT · REQUEST ASSISTANCE")
```

**Status:** ✅ WIRED — N5 physically validated.

### 13. TTS Playback

```
Incoming message received
└─> ReceiverTranslationCoordinator
└─> TTS_HANDOFF to TextToSpeechUseCase
└─> TtsPlaybackQueue.enqueue()
└─> SherpaOnnxTtsEngine.synthesize()
└─> VokieAudioPlayer.play()
└─> MessageTtsState: QUEUED → SYNTHESIZING → PLAYING → COMPLETED
└─> MessageBubble shows play/pause controls
```

**Status:** ✅ WIRED — TTS plays actual translated audio.

### 14. Model Status

```
TtsModelManager.installedLanguages (StateFlow)
└─> TextToSpeechUseCase.installedLanguages
└─> CommunicationViewModel.installedTtsLanguages
└─> OnboardingScreen checks actual file existence
```

**Status:** ✅ WIRED — "Installed" means model actually exists.

### 15. Persistence (Room)

```
VokieDatabase (Room)
├── messages table
├── peers table
├── received_packets table (deduplication)
├── transport_events table
└── app_settings table

All tables wired to DAO → Repository → ViewModel → UI
```

**Status:** ✅ WIRED — Room is canonical persistence layer.

### 16. Locate UI

```
LocateScreen
└─> CommunicationViewModel.proximityMeasurements
└─> LocationMeasurementCollector (real RSSI/ACK measurements)
└─> RelativePeerLocalizationEngine
└─> UI displays: ProximityZone.VERY_NEAR / NEAR / UNKNOWN

Note: Direction/Distance explicitly marked "Not available yet"
```

**Status:** ✅ WIRED — Displays actual measurements; does not claim GPS.

## Debug/Simulation Infrastructure

The following debug-only functionality exists and is **correctly guarded**:

1. **Simulated Peers** (`addSimulatedPeers()` / `removeSimulatedPeers()`)
   - Guarded by `BuildConfig.DEBUG`
   - Peers clearly labeled "SIMULATED —"
   - Only visible in DEBUG builds
   - "ADD SIM" button shown in RescuePeerListScreen for DEBUG only

2. **Debug Fast STT** (`debugFastSttEnabled`)
   - Debug-only experiment flag
   - Guarded by `BuildConfig.DEBUG`

3. **Reset Onboarding** (`resetOnboardingForDebug()`)
   - Guarded by `BuildConfig.DEBUG`

**Status:** ✅ SAFE — Test infrastructure does not leak to production.

## Identified Issues

### None Found

The frontend is correctly wired to canonical backend state throughout.

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.1 FRONTEND ↔ BACKEND WIRING: PASS**

All UI components observe actual backend StateFlows. No fake state, hardcoded values, or disconnected buttons were found in production paths. Debug/simulation infrastructure is correctly guarded and clearly labeled.
