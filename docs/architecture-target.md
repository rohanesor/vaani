# Target production architecture

## Objective

> Indian Multilingual TTS & STT Aided Neural Transceiver Radio Access for Low-Bitrate Links

The target iTantra pipeline is fully offline after installation:

```text
microphone -> VAD -> multilingual STT -> text + language
           -> versioned compact packet -> persistent queue -> local transport
           -> reassembly + integrity -> text + language -> language router
           -> multilingual TTS -> AudioTrack
```

Text input follows the same packet, queue, transport, language-routing, and TTS path. Audio is never transmitted.

## Architectural invariants

1. **Language belongs to each message.** The receiver routes the packet language to TTS automatically. A global UI language preference must not determine received-message speech.
2. **Single offline distribution.** All production inference resources are bundled, checksummed, atomically extracted when required, and available in airplane mode. No language-pack, model, cloud inference, or remote language-detection path exists at runtime.
3. **Measured selection.** Whisper, IndicConformer, vits_rasa_13, MMS-TTS, and any conversion/quantization candidate are selected only after reproducible benchmark and license evidence.
4. **Transport-independent packets.** The same packet codec is used by Wi-Fi Direct, RFCOMM, and future acoustic transport.
5. **Truthful state.** ACKNOWLEDGED, location, delivery, diagnostics, and benchmark claims require actual evidence.
6. **Low-memory operation.** Keep bounded recording/queue buffers and one active TTS inference session where practical. Do not infer RAM suitability from model size.

## Target component boundaries

```text
ui
  -> TransceiverCoordinator
      -> SpeechCapture (Silero VAD + bounded recorder)
      -> SttEngine (production engine selected by benchmark)
      -> LanguageRouter
      -> PacketCodec / Fragmenter / Reassembler
      -> MessageRepository (Room state machine)
      -> TransportManager
          -> WiFiDirectTransport
          -> BluetoothRfcommTransport
          -> future AcousticTransport
      -> TtsEngine (production engine selected by benchmark)
      -> AudioOutput
```

### LanguageRouter

`LanguageRouter` is the sole conversion point between STT, protocol, and TTS codes. It will:

- normalize incoming codes to lowercase BCP-47/ISO 639-1 message codes: `en`, `hi`, `gu`, `mr`, `kn`, `ml`, `ta`, `te`, `or`, `bn`;
- map STT output to protocol code;
- map protocol code to the selected TTS engine's required language/speaker ID;
- expose the supported-language inventory;
- return an explicit unsupported-language result and safe readable-text fallback.

No transport, UI, or TTS queue should duplicate language-specific `if`/`when` mappings.

### STT contract

The target engine contract is automatic by default:

```kotlin
data class SpeechResult(
    val text: String,
    val language: String,
    val languageConfidence: Float?,
    val confidence: Float?,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val processingTimeMs: Long,
)
```

Whisper multilingual remains the baseline candidate. The normal UI will not select a Whisper language; the engine must run language detection and the result must be normalized by `LanguageRouter`. An exceptional manual override may be an advanced accessibility/test feature, not default product state.

IndicConformer is an experimental benchmark candidate. It must be evaluated using identical recordings and language-specific ground truth before it replaces Whisper. The production APK must not contain both complete STT engines without a documented product rationale.

### TTS contract

The target `TtsEngine` accepts text plus a router-resolved language/voice descriptor and returns local PCM. It must load no language via the network. A multilingual model is preferred only if its Android runtime, frontend/tokenizer, quality, memory, latency, licensing, and redistribution conditions are proven.

AI4Bharat `vits_rasa_13` is the primary evaluation candidate. MMS-TTS remains the operational baseline until a documented model decision exists. The target does not presume sherpa-onnx, ONNX Runtime Mobile, or a custom C++ runtime is compatible before a pinned source artifact and Android inference test prove it.

## Packet protocol v2 target

The existing v1 frame is not extended in place until interoperability/migration is designed. The target new codec has an explicit version and separates packet metadata from transport framing.

Required logical fields:

```text
magic | protocol version | packet type | flags
message ID | sender ID | timestamp | language ID
priority | sequence number | fragment index/count | payload length
payload UTF-8 text | integrity field (CRC or stronger defined checksum)
```

SOS packets additionally support explicitly authorized location data, location accuracy/time, and priority. Missing location is represented as unavailable; coordinates are never fabricated.

The codec must provide bounded parsing, integrity verification before persistence, fragmentation/reassembly limits and expiry, duplicate/replay detection, ACK correlation, ordering policy, and test vectors. Authentication/device-identity design must be specified before claiming secure peer authentication.

## Transport target

`TransportManager` selects an active `Transport` based on real availability and policy. All transports accept and emit packet bytes, not domain `Message` objects.

1. **Primary: Wi-Fi Direct.** Implement peer discovery, connection/group lifecycle, peer validation, local socket creation, framed packet I/O, disconnect recovery, ACK/retry integration, and no-router operation.
2. **Secondary: Bluetooth Classic RFCOMM.** Preserve tested discovery/listener/connector behavior while moving its payload to the shared packet codec.
3. **Future: acoustic/ultrasonic.** Implement only behind the same byte-packet interface.

Transport status must include which transport is active; “delivered” is never shown without an application-level ACK.

## Persistence target

Room retains messages, packet metadata, reassembly state where needed, peer identity/trust metadata, and transport diagnostics. The planned lifecycle is:

```text
CREATED -> QUEUED -> TRANSMITTING -> ACKNOWLEDGED
                    -> RETRYING -> FAILED | EXPIRED
```

Incoming persistence, duplicate recording, ACK generation, and state transitions require explicit transaction boundaries. The migration must preserve existing messages or document a destructive database migration before release.

## VAD, location, and diagnostics

- Replace or retain the current energy VAD only after a Silero VAD evaluation proves endpointing and low-end CPU/memory suitability. STT remains event-driven; it never runs continuously while idle.
- GNSS acquisition is explicit and bounded. SOS includes a location only after deliberate authorization; normal use does not poll continuously.
- A development/testing diagnostics surface, hidden from normal users, reports model identity, VAD state, active transport, queue state, RTF, measured stage latencies, and measured process memory/CPU.

## Model and benchmark gates

The benchmark suite must retain source/revision/checksum, recordings/ground truth authorization, device/build information, commands, raw metrics, and results for every candidate.

- STT: WER, CER, language accuracy, latency, RTF, initialization, model size, PSS/RSS, CPU.
- TTS: intelligibility/pronunciation/naturalness emergency clarity, cold/warm latency, RTF, language switch/repeat behavior, PSS/RSS, CPU, payload size.
- Network: packet sizes, throughput, loss, retransmissions, reassembly failures.
- End-to-end: capture-finalized through audio-start time.

The final model selection is recorded in `docs/model-selection.md` only after all target languages and target device classes have evidence.

## Incremental migration order

1. Establish current architecture/model evidence and candidate provenance (this execution).
2. Define code-level language domain types and `LanguageRouter`; remove normal-user STT/TTS model-management flows only after all models are bundled and verified.
3. Build packet v2 alongside v1 with codec vectors, fragment/reassembly tests, and Room migration plan.
4. Convert RFCOMM to shared packet bytes while retaining ACK/retry behavior.
5. Implement and validate Wi-Fi Direct against the same protocol.
6. Add GNSS/SOS packet schema, diagnostics, benchmark collection, and physical-device validation.
7. Select/quantize/freeze production STT and TTS only after benchmark/license gates pass.

No release, AWS deployment, model replacement, or claim of target-architecture completion is authorized by this document.
