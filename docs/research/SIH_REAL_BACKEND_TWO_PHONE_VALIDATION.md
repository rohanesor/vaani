# SIH REAL BACKEND TWO-PHONE VALIDATION

## Candidate

- APK: `artifacts/runtime-validation/iTantra-test-integration-fixed-1-6.apk`
- SHA-256: `a994679db7fc955039a0d4b60b7ad91710f78a39f1a8452841df3e1bd395c9de`
- Size: `2,055,805,035` bytes
- Package/version: `com.vokie`, `1.0.0` / versionCode `1`
- ABI: arm64-v8a
- Canonical artifact was not modified or installed over.

## Packaging audit

The candidate APK contains the following verified assets:

- SenseVoice: `assets/models/stt/sensevoice/model.int8.onnx`, `239,233,841` bytes
- SenseVoice tokens: `assets/models/stt/sensevoice/tokens.txt`
- CT2 NLLB: `assets/models/ct2/nllb600m/model.bin`, `619,704,329` bytes
- SentencePiece model, config, and shared vocabulary
- Existing approved TTS models and native runtime libraries
- No corpus WAV/recording assets detected

The candidate extracts CT2 under the bundled model directory used by the native translator; no manual app-private model staging is intended.

## Implemented physical-test fixes

- Room conversations are created and activity-updated for outbound and inbound messages.
- Inbound messages receive the correct remote-peer conversation ID.
- Conversation previews group messages by remote peer rather than by the local receiver ID.
- Active connected peers are synthesized into the Messages list before the first message.
- Conversation peer names resolve from discovered or trusted Room state.
- Bluetooth inquiry no longer exposes ambient audio, wearable, peripheral, or health devices; bonded phone/computer candidates and iTantra SDP/HELLO validation remain authoritative.
- Prototype `Meera` identity is migrated to `Build.MODEL`; explicit user names remain preserved.
- Identity is exported through the native state bridge and reflected by the UI.
- A direct call action is available on the active Speak peer card; chat call signaling remains native-backed.

## Automated evidence

- `:app:testDebugUnitTest`: PASS, 463 tests, 0 failures.
- Integration APK build: PASS.
- APK contents inspected independently with `unzip` and `aapt`.

## Physical devices

The following devices and results are supplied by the physical-test record:

- Phone 1: Realme Narzo 60x 5G, serial `MJPVXCSG9HYL65YL`
- Phone 2: Samsung Galaxy M35, serial `RZCX41PAVLB`

The current shell did not expose `adb`, so no new install/logcat capture was performed in this implementation pass.

## Strict evidence table

| Gate | Result | Evidence |
|---|---|---|
| BUILD | PASS | Candidate assembled successfully |
| APK_SELF_CONTAINED | PASS | Required STT/CT2/TTS assets present in APK |
| FRONTEND_WIRED | PASS | Dynamic conversation, identity, and call actions wired |
| IDENTITY | PARTIAL | Default is now device model; two-phone regression pending |
| DISCOVERY | PASS (prior record) | Two active phones reported; filtering fix pending physical regression |
| HANDSHAKE | PASS (prior record) | Active Bluetooth sessions reported |
| PAIRING | PARTIAL | Prior connection evidence; fixed candidate not re-tested |
| TEXT_MESSAGING | PARTIAL | Prior wire delivery; blank-screen fix not re-tested physically |
| ACK | PASS (prior record) | Prior MESSAGE_OUT → MESSAGE_IN → ACK logs |
| STORE_FORWARD | BLOCKED | Requires physical regression through repaired Messages UI |
| PTT | PASS (prior record) | Real 16 kHz microphone and SenseVoice transcription |
| STT | PASS (prior record) | On-device SenseVoice transcript evidence |
| TRANSLATION | PASS (prior record) | Offline NLLB evidence |
| TTS | PASS (prior record) | Sherpa-ONNX playback evidence |
| SINGLE_PHONE_LOOP | PARTIAL | Individual pipeline components tested; complete loop not newly captured |
| TWO_PHONE_LOOP | PARTIAL | Discovery/wire/PTT/translation/TTS prior evidence, repaired build not re-tested |
| CALL_SIGNALING | BLOCKED | Call access fix built but no new physical signaling run |
| CALL_MEDIA | NOT_IMPLEMENTED | Intentionally out of scope |
| PERFORMANCE | BLOCKED | No new measurements |
| THERMAL | BLOCKED | No new measurements |
| READY_FOR_REAL_HUMAN_VOICE | NO | Required repaired-build physical regression remains |

## Exact remaining blockers

1. Install the fixed candidate on both phones without replacing the protected canonical installation.
2. Capture `adb devices`, package path, dumpsys, and logcat from both devices.
3. Re-run Tests 1–6 on the fixed candidate, especially Messages, store-forward, and call signaling.
4. Confirm model extraction and first-run initialization on both devices.
5. Capture latency, PSS/CPU, audio playback, and thermal evidence.
6. Commit the integration-checkout source changes and this report in a versioned integration checkout.
