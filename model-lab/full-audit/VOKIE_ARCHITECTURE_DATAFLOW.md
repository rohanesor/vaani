# Vokie Architecture Data Flow

## Production entry and state
`VokieApplication.onCreate` constructs transport, Room repository, STT, CT2 receiver coordinator, TTS queue, localization and peer-session services. Compose `MainActivity`/screens consume `CommunicationViewModel` StateFlows.

## Voice message path
`ChatScreen` observes an `SttResult` → `CommunicationViewModel.enqueueWhisperResult` → `VokieApplication.enqueueWhisperTranscript` → `RoomMessageRepository.createMessage` → `OutboundMessageProcessor` → `TransportManager` → `PacketV2` framed Bluetooth/Wi-Fi Direct send.

## Receive / render / speak path
`TransportManager.decodedFrames` → `InboundPacketCoordinator` → persisted `Message` → receiver collector in `VokieApplication` → target from `UserLanguageProfilePreferences` → `ReceiverTranslationCoordinator.presentOnce` → `CodeSwitchTranslationCoordinator` when eligible → `ReceiverPresentation` → `ReceiverPresentation.ttsHandoff` → `TextToSpeechUseCase.enqueueReceived` → `TtsPlaybackQueue` → `SherpaOnnxTtsEngine` → `VokieAudioPlayer`.

## Missing production stages
No partial ASR packets, sentence splitter, turn manager, synchronized end-to-end timestamps, embedded-device adapter, or guaranteed alert non-interruption.
