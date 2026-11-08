# SIH physical-device validation

This report records only observed results. Do not mark an item passed without the stated test on a physical device.

## Candidate

- Build: `cc12704` or later
- APK: `app/build/outputs/apk/debug/app-debug.apk`
- Device: realme narzo 60x 5G (Android SDK 35)
- Offline model payload: 1,172,553,259 bytes

## Capture commands

```bash
ADB=/tmp/android-sdk/platform-tools/adb scripts/collect-device-metrics.sh idle
# Start STT, then while it is processing:
ADB=/tmp/android-sdk/platform-tools/adb scripts/collect-device-metrics.sh stt-processing
# Start TTS playback:
ADB=/tmp/android-sdk/platform-tools/adb scripts/collect-device-metrics.sh tts-playing
```

iTantra writes measured STT processing time and RTF to the `VOKIE][STT` log tag. Capture one snapshot after each utterance.

## Results

| Test | Result | Measurement / notes |
|---|---|---|
| Airplane-mode first launch and bundled extraction | Pending | |
| STT English: “Hey, can you help me?” | Pending | RTF: |
| STT Hindi: “क्या आप मेरी मदद कर सकते हैं?” | Pending | RTF: |
| STT Tamil: “நீங்கள் எனக்கு உதவ முடியுமா?” | Pending | RTF: |
| STT 10 consecutive recordings | Pending | Success / graceful no-speech count: |
| STT 30-second timeout/abort | Pending | |
| STT navigation during processing | Pending | No invalid state transition: |
| TTS English intelligibility | Pending | |
| TTS Hindi intelligibility | Pending | |
| TTS Gujarati intelligibility | Pending | |
| TTS Marathi intelligibility | Pending | |
| TTS Kannada intelligibility | Pending | |
| TTS Malayalam intelligibility | Pending | |
| TTS Tamil intelligibility | Pending | |
| TTS Telugu intelligibility | Pending | |
| TTS Odia intelligibility | Pending | |
| TTS Bengali intelligibility | Pending | |
| Idle PSS / CPU | Pending | |
| STT processing PSS / CPU | Pending | |
| TTS playback PSS / CPU | Pending | |
| Bluetooth discovery and connection | Pending | |
| Phone A → Phone B text/TTS delay | Pending | |
| Phone B → Phone A text/TTS delay | Pending | |
| Bluetooth disconnect, retry, and persisted queue | Pending | |
| SOS playback behavior | Pending | |

## Acceptance

Keep Bluetooth enabled in airplane mode. Do not claim a complete SIH demonstration until all language, two-phone transport, and physical-device entries above have observed results.
