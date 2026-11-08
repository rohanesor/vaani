# iTantra native design adaptation

Source: local handoff `iTantra emergency communication app-handoff/itantra-emergency-communication-app/project/iTantra.dc.html` and its generated `support.js`. Claude Design MCP was not available. The exported browser runtime is not embedded in the Android app (it loads React from a CDN).

Checkpoint: `sih/before-itantra-design-implementation`. Existing source edits from the first design pass are included; no model or APK is committed or installed.

## Screen mapping

| Handoff | Native implementation |
|---|---|
| 01 Splash | Existing Android system splash retained; no artificial load progress, placeholder photo or blocking model initialization added. |
| 02–04 Introduction | Three introduction pages, Skip, Next, Get started, back navigation, local wordmark and illustrations. Copy reflects supported capabilities, not ten-language availability. |
| 05 Attribution | Optional selection persisted in local setup preferences on the IO dispatcher; Continue and Skip. No analytics transmission. |
| 06 Language setup | Searchable supported-language list, independent input/output selectors, swap and summary retained. No Telugu/Kannada enablement. |
| 07–08 Pack preparation/retry | Installed-pack dialog with asynchronous inspection plus explicit confirmation, real download progress/completion/error and retry wired to the existing ModelDownloadManager/CDN configuration. No downloads were triggered during tests. No invented resume guarantee. |
| 09 Setup complete | Actual chosen input/output languages, model-inventory link and Open iTantra. Does not falsely declare every model ready. |
| 10 Home | Speak tab with 168dp circular microphone, real peer status, language settings, settings and SOS access. |
| 11 Listening | Real STT status in the same microphone surface; no fake partial transcript or random audio waveform. Hold/release or tap mode follows the saved preference. |
| 12 Incoming | Home shows actual selected-conversation PLAYING state and translated/source text. No fabricated elapsed audio clock. Replay/stop remains in message controls; SOS acknowledgement behavior retained. |
| 13 Channel | Messages tab retains delivery states, retry, source/translation, replay/stop and SOS acknowledgement; real timestamps, amber incoming/vermilion outgoing treatment, 88dp PTT bar. |
| 14 Settings | Language, installed packs, speech mode/speed, device and About groups. Locate and prior maps/diagnostics remain accessible. No new emergency-volume override. |

This is a native functional adaptation, not a pixel-identical reproduction of all fourteen mockups. In particular, uninterrupted full-volume alerts, partial streaming transcripts, measured waveforms and audio seek/progress are not invented or added to the production architecture.

## Safety

No changes to PacketV2, transport, model files, translation routing, native STT/TTS engines, or queue ownership. Speak and Messages share the existing ChatScreen owner. UI only renders results; it does not submit transcripts or enqueue incoming playback. A debug UI build was installed and launched on Realme; no latency or production promotion is claimed. See ITANTRA_UI_DEVICE_RESULT.md.

Microphone pointer recognition remains independent of STT state so READY→LISTENING cannot cancel a held gesture. Tap mode invokes existing start/stop callbacks. Accessibility click actions are provided. Microphone permission is requested once per touch rather than in both onPress and onTap.

## Validation

- `testDebugUnitTest`: passed.
- `assembleDebug`: passed, including the final UI-test host dependency.
- `compileDebugAndroidTestKotlin`: passed.
- Added instrumentation tests for intro/attribution/search, tap-mode start/stop, and single permission request.
- All three instrumentation tests passed on Realme. Home screenshot inspected; TalkBack, remaining screen-by-screen visual comparison and full physical voice-loop regression remain pending. Thermal status 3 stopped further testing.
