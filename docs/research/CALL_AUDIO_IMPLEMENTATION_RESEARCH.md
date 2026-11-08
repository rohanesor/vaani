# Call Audio Implementation Research

**Date:** 2025-01-14  
**Status:** Research Complete  
**Target:** Full-duplex VoIP calling over offline P2P (Bluetooth RFCOMM / Wi-Fi Direct)

---

## Executive Summary

This document analyzes the feasibility, architecture, and implementation approach for adding real-time voice calling to iTantra's existing offline peer-to-peer infrastructure. The goal is full-duplex audio streaming where both parties can speak and hear simultaneously, like a regular phone call.

### Key Findings

| Aspect | Finding |
|--------|---------|
| **Feasibility** | ✅ Achievable with current infrastructure |
| **Bandwidth** | ✅ 32 KB/s fits easily in Bluetooth RFCOMM (100+ KB/s) |
| **Latency** | ✅ Target <200ms achievable |
| **Echo Cancellation** | ⚠️ Hardware AEC available on ~90% of devices |
| **Complexity** | Medium (~400-500 lines new code) |
| **Risk** | Low - can fallback to half-duplex if AEC fails |

---

## 1. Current Infrastructure Analysis

### 1.1 Existing Audio Components

| Component | File | Purpose | Reusable? |
|-----------|------|---------|-----------|
| `MicrophoneAudioRecorder` | `stt/MicrophoneAudioRecorder.kt` | 16kHz PCM capture for STT | Partial |
| `VokieAudioPlayer` | `tts/VokieAudioPlayer.kt` | Float PCM playback for TTS | Partial |
| `EnergyVadEngine` | `stt/VadEngine.kt` | Voice activity detection | ✅ Yes |
| `BluetoothTransport` | `communication/BluetoothTransport.kt` | RFCOMM socket I/O | ✅ Yes |
| `PacketV2` | `communication/PacketV2.kt` | Frame encoding/decoding | Extend |
| `CallManager` | `communication/CallManager.kt` | Call signaling state machine | Extend |

### 1.2 Current Audio Configuration

**MicrophoneAudioRecorder:**
```kotlin
AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)  // Good for STT
    .setAudioFormat(...)
    .build()
```

**For VoIP, we need:**
```kotlin
AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)  // Required for AEC
    .setAudioFormat(...)
    .build()
```

### 1.3 Transport Layer

**BluetoothTransport frame format:**
```
[4 bytes: frame length] [N bytes: PacketV2 payload]
```

**Read loop (line 223-238):**
```kotlin
while (connectionState.value == TransportConnectionState.CONNECTED) {
    val size = input?.readInt() ?: break
    if (size <= 0 || size > 64 * 1024) throw IOException("Invalid frame size")
    val bytes = ByteArray(size)
    input!!.readFully(bytes)
    receivedPackets.tryEmit(bytes)
    // ... decode and dispatch
}
```

**Observation:** The existing read loop handles arbitrary byte arrays. Audio frames can be sent through the same path with a different type prefix.

---

## 2. Audio Streaming Architecture

### 2.1 Data Flow

```
┌─────────────────────────────────────────────────────────────────────┐
│                           PHONE A                                   │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌──────────────┐    ┌──────────────┐    ┌────────────────────┐    │
│  │ AudioRecord  │───▶│ CallAudio    │───▶│ BluetoothTransport │    │
│  │ 16kHz/16-bit │    │ Engine       │    │ RFCOMM Socket      │    │
│  │ VOICE_COMM   │    │              │    │                    │    │
│  └──────────────┘    │ ┌──────────┐ │    └─────────┬──────────┘    │
│                      │ │ AEC+NS   │ │              │               │
│  ┌──────────────┐    │ └──────────┘ │    ┌─────────▼──────────┐    │
│  │ AudioTrack   │◀───│              │◀───│ Frame Dispatch     │    │
│  │ VOICE_CALL   │    │ ┌──────────┐ │    │ (type=CALL_AUDIO)  │    │
│  │ Same Session │    │ │ Jitter   │ │    └────────────────────┘    │
│  └──────────────┘    │ │ Buffer   │ │                              │
│                      │ └──────────┘ │                              │
│                      └──────────────┘                              │
└─────────────────────────────────────────────────────────────────────┘
                               │
                               │ RFCOMM / Wi-Fi Direct TCP
                               │ ~32 KB/s bidirectional
                               │
┌──────────────────────────────▼──────────────────────────────────────┐
│                           PHONE B                                   │
│                        (Mirror of A)                                │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 Audio Frame Format

**Option A: Minimal binary header (recommended)**
```
Byte 0:      0xCA (CALL_AUDIO magic)
Bytes 1-16:  callId (UUID as 16 bytes)
Bytes 17-20: sequence (uint32, little-endian)
Bytes 21-28: timestamp (int64, elapsedRealtimeNanos)
Bytes 29-N:  PCM samples (16-bit signed, little-endian)

Total: 29 + 640 = 669 bytes per 20ms frame
```

**Option B: Reuse PacketV2 with new MessageType**
```kotlin
enum class MessageType {
    TEXT, VOICE, VOICE_TEXT, LOCATION, DISTRESS, SOS, FILE, SYSTEM,
    CALL_AUDIO,  // NEW
}
```

**Recommendation:** Option A. Audio frames are fire-and-forget (no ACK), and PacketV2's CRC/validation overhead is unnecessary for real-time audio that will be discarded if late.

### 2.3 Frame Size Selection

| Duration | Samples | Bytes | Frames/sec | Latency | Overhead |
|----------|---------|-------|------------|---------|----------|
| 10ms | 160 | 320 | 100 | Low | High |
| **20ms** | **320** | **640** | **50** | **Good** | **Good** |
| 40ms | 640 | 1280 | 25 | High | Low |
| 60ms | 960 | 1920 | 17 | High | Low |

**Selection: 20ms frames (640 bytes)**
- Standard VoIP frame size (matches Opus, G.711)
- 50 packets/second is manageable
- Good balance between latency and per-packet overhead

---

## 3. Bandwidth Analysis

### 3.1 Uncompressed PCM

```
Sample rate:     16,000 Hz
Bit depth:       16 bits
Channels:        1 (mono)
─────────────────────────────
Bitrate:         256 kbps (32 KB/s)
With headers:    ~33.5 KB/s (669 bytes × 50 fps)
```

### 3.2 Transport Capacity

| Transport | Typical Throughput | Margin |
|-----------|-------------------|--------|
| Bluetooth RFCOMM | 100-300 KB/s | 3-9x |
| Wi-Fi Direct TCP | 10+ MB/s | 300x |

**Conclusion:** Raw PCM is viable. No compression needed for V1.

### 3.3 Future Compression Options

| Codec | Bitrate | Complexity | Notes |
|-------|---------|------------|-------|
| Raw PCM | 256 kbps | None | Current approach |
| Opus | 16-64 kbps | Native lib | Best quality/size |
| G.711 µ-law | 64 kbps | Trivial | Telephony standard |
| AMR-NB | 4.75-12 kbps | Android HW | Speech-optimized |

---

## 4. Latency Budget

### 4.1 Component Breakdown

```
┌────────────────────────┬────────────┬────────────┐
│ Component              │ Min (ms)   │ Max (ms)   │
├────────────────────────┼────────────┼────────────┤
│ Audio capture          │ 20         │ 40         │
│ VAD/Processing         │ 1          │ 5          │
│ Packetization          │ <1         │ <1         │
│ Transport (Bluetooth)  │ 20         │ 80         │
│ Jitter buffer          │ 40         │ 80         │
│ Audio playback         │ 20         │ 40         │
├────────────────────────┼────────────┼────────────┤
│ TOTAL ONE-WAY          │ 101        │ 245        │
└────────────────────────┴────────────┴────────────┘
```

### 4.2 Latency Perception

| Latency | Perception |
|---------|------------|
| < 150ms | Excellent (feels real-time) |
| 150-300ms | Good (slight delay, usable) |
| 300-500ms | Fair (noticeable delay) |
| > 500ms | Poor (walkie-talkie feel) |

**Target:** < 200ms one-way latency (achievable)

---

## 5. Echo Cancellation

### 5.1 Android AEC Availability

```kotlin
// Check at runtime
val aecAvailable = AcousticEchoCanceler.isAvailable()
val nsAvailable = NoiseSuppressor.isAvailable()
```

**Device coverage:**
- ~90%+ of modern Android phones (API 16+)
- Hardware DSP acceleration on Qualcomm, MediaTek, Samsung Exynos
- May not be available on cheap tablets or emulators

### 5.2 AEC Requirements

```kotlin
// 1. Use VOICE_COMMUNICATION audio source (not VOICE_RECOGNITION)
AudioRecord.Builder()
    .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
    .build()

// 2. Use STREAM_VOICE_CALL or USAGE_VOICE_COMMUNICATION
AudioTrack.Builder()
    .setAudioAttributes(AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build())
    .build()

// 3. Share audio session ID
val sessionId = audioRecord.audioSessionId
val audioTrack = AudioTrack.Builder()
    .setSessionId(sessionId)  // CRITICAL
    .build()

// 4. Attach AEC to the session
val aec = AcousticEchoCanceler.create(sessionId)
aec.enabled = true

// 5. Set audio mode
audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
```

### 5.3 Fallback Strategy

If AEC is unavailable:

| Option | UX Impact | Complexity |
|--------|-----------|------------|
| **Automatic VOX** | Good | Low |
| Manual PTT | Walkie-talkie | Very Low |
| Software AEC (WebRTC) | Good | High |
| Accept echo | Poor | None |

**Recommendation:** Implement automatic VOX as fallback:
- Use existing `EnergyVadEngine` for local speech detection
- Mute playback when local speech detected
- Only one direction transmits at a time
- Visual indicator: "You're speaking" / "Peer speaking"

---

## 6. Jitter Buffer Design

### 6.1 Purpose

Network jitter causes packets to arrive with variable timing. The jitter buffer:
1. Reorders out-of-sequence packets
2. Smooths playback timing
3. Generates silence for lost packets

### 6.2 Implementation

```kotlin
class CallJitterBuffer(
    private val targetDelayMs: Int = 60,  // 3 frames at 20ms
    private val maxDelayMs: Int = 200,    // 10 frames max
) {
    private data class Frame(
        val sequence: Long,
        val timestamp: Long,
        val pcm: ShortArray,
        val receivedAt: Long,
    )
    
    private val buffer = PriorityQueue<Frame>(compareBy { it.sequence })
    private var nextExpectedSequence = 0L
    private var playbackStarted = false
    
    @Synchronized
    fun add(sequence: Long, timestamp: Long, pcm: ShortArray) {
        // Drop if too old or duplicate
        if (playbackStarted && sequence < nextExpectedSequence) return
        
        // Drop if buffer full
        if (buffer.size >= maxDelayMs / 20) {
            buffer.poll()  // Remove oldest
        }
        
        buffer.add(Frame(sequence, timestamp, pcm, SystemClock.elapsedRealtime()))
    }
    
    @Synchronized
    fun poll(): ShortArray {
        // Wait for initial buffer fill
        if (!playbackStarted && buffer.size < targetDelayMs / 20) {
            return SILENCE_FRAME
        }
        playbackStarted = true
        
        val frame = buffer.poll()
        return if (frame != null && frame.sequence == nextExpectedSequence) {
            nextExpectedSequence++
            frame.pcm
        } else {
            // Packet loss - generate silence
            nextExpectedSequence++
            SILENCE_FRAME
        }
    }
    
    companion object {
        val SILENCE_FRAME = ShortArray(320)  // 20ms of silence
    }
}
```

### 6.3 Adaptive Jitter Buffer (Future)

For V2, consider adaptive buffering:
- Measure actual jitter over sliding window
- Increase buffer when jitter spikes
- Decrease buffer during stable periods
- Trade latency for smoothness dynamically

---

## 7. Call State Machine Updates

### 7.1 Current States

```kotlin
enum class CallState {
    IDLE, CALLING, RINGING, CONNECTING, CONNECTED,
    RECONNECTING, REJECTED, MISSED, FAILED, ENDED, UNAVAILABLE
}
```

### 7.2 Audio-Aware State Transitions

```
                              ┌──────────────┐
                              │    IDLE      │
                              └──────┬───────┘
                                     │ startCall()
                                     ▼
                              ┌──────────────┐
                     ┌────────│   CALLING    │────────┐
                     │        └──────┬───────┘        │
                     │               │ CALL_ACCEPT    │ CALL_REJECT
                     │               ▼                │
                     │        ┌──────────────┐        │
                     │        │  CONNECTING  │        │
                     │        │              │        │
                     │        │ startAudio() │        │
                     │        └──────┬───────┘        │
                     │               │ AUDIO_READY    │
                     │               │ (both sides)   │
                     │               ▼                │
                     │        ┌──────────────┐        │
                     │        │  CONNECTED   │        │
                     │        │              │        │
                     │        │ ═══AUDIO═══  │        │
                     │        │ bidirectional│        │
                     │        └──────┬───────┘        │
                     │               │ CALL_END       │
                     ▼               ▼                ▼
              ┌──────────────────────────────────────────────┐
              │                   ENDED                      │
              │                 stopAudio()                  │
              └──────────────────────────────────────────────┘
```

### 7.3 New Signaling Messages

```kotlin
// AUDIO_READY: Sent when local audio engine is initialized
{
    "action": "AUDIO_READY",
    "callId": "...",
    "peerId": "...",
    "sampleRate": 16000,
    "frameSize": 320
}
```

---

## 8. Implementation Plan

### 8.1 New Files

| File | Lines | Purpose |
|------|-------|---------|
| `CallAudioEngine.kt` | ~200 | AudioRecord/Track + AEC + streaming |
| `CallJitterBuffer.kt` | ~80 | Packet reordering + timing |
| `CallAudioFrame.kt` | ~40 | Binary frame encode/decode |

### 8.2 Modified Files

| File | Changes |
|------|---------|
| `CallManager.kt` | Add audio lifecycle, mute/speaker controls |
| `BluetoothTransport.kt` | Route CALL_AUDIO frames separately |
| `TransportManager.kt` | Add `sendCallAudio()` |
| `ITantraNativeBridge.kt` | Expose mute/speaker/timer to WebView |
| `index.html` | Call UI: timer, mute button, speaker button |
| `Models.kt` | Add `isMuted`, `isSpeakerphone` to `CallUiState` |

### 8.3 Implementation Order

| Step | Task | Effort |
|------|------|--------|
| 1 | `CallAudioFrame.kt` - binary encoding | 1 hr |
| 2 | `CallJitterBuffer.kt` - packet buffering | 2 hr |
| 3 | `CallAudioEngine.kt` - capture/playback | 4 hr |
| 4 | `CallManager.kt` - audio lifecycle | 2 hr |
| 5 | `BluetoothTransport.kt` - frame routing | 1 hr |
| 6 | UI: timer, mute, speaker | 2 hr |
| 7 | Unit tests | 2 hr |
| 8 | Physical two-phone testing | 2 hr |
| **Total** | | **~16 hr** |

---

## 9. Testing Strategy

### 9.1 Unit Tests

```kotlin
// CallJitterBufferTest.kt
@Test fun `reorders out-of-sequence packets`()
@Test fun `generates silence on packet loss`()
@Test fun `drops packets when buffer full`()
@Test fun `waits for initial fill before playback`()

// CallAudioFrameTest.kt
@Test fun `encode and decode round-trip`()
@Test fun `rejects invalid magic byte`()
@Test fun `handles max sequence number`()
```

### 9.2 Physical Tests

| Test | Validation |
|------|------------|
| **Latency** | Clap on A, measure time to hear on B (target <300ms RTT) |
| **Quality** | Speak phrase, verify intelligibility |
| **Echo** | Speak on A with B's speaker on, verify no feedback |
| **Mute** | Tap mute on A, verify B hears silence |
| **Speaker** | Toggle speaker, verify audio route changes |
| **Disconnect** | Walk out of range, verify call ends gracefully |
| **Timer** | Start call, verify timer increments correctly |

### 9.3 Edge Cases

- Both users speak simultaneously
- Network congestion / high jitter
- AEC unavailable → falls back to VOX
- Battery/thermal constraints during long calls
- Incoming PTT message during active call

---

## 10. Risk Analysis

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|------------|
| AEC unavailable | 10% | Medium | Implement VOX fallback |
| High latency | 20% | Medium | Tune jitter buffer, prioritize audio |
| Echo on speakerphone | 30% | High | Test MODE_IN_COMMUNICATION |
| Bluetooth bandwidth spike | 15% | Low | Buffer + drop oldest frames |
| AudioRecord conflicts with STT | 10% | Medium | Stop STT during call |

---

## 11. Alternatives Considered

### 11.1 Bluetooth SCO

**What:** Use Bluetooth's native telephony audio profile (HFP/HSP).

**Pros:**
- Built-in echo cancellation at hardware level
- Lower latency (8-16kHz SCO link)

**Cons:**
- Requires pairing as headset profile
- Not compatible with RFCOMM data connection
- Limited to 8kHz on many devices
- Complex to set up programmatically

**Decision:** Rejected. RFCOMM + software AEC is more flexible.

### 11.2 WebRTC

**What:** Use libwebrtc for audio processing.

**Pros:**
- Industry-standard AEC, NS, AGC
- Proven in billions of calls

**Cons:**
- Large library (~10 MB)
- Complex integration
- Overkill for P2P-only use case

**Decision:** Rejected for V1. Consider for V2 if software AEC needed.

### 11.3 Opus Compression

**What:** Use libopus for audio compression.

**Pros:**
- 8x bandwidth reduction
- Great quality at low bitrates

**Cons:**
- Native library dependency
- Additional complexity
- Bluetooth has enough bandwidth

**Decision:** Defer to V2. Raw PCM is sufficient for initial release.

---

## 12. Conclusion

**Recommendation:** Proceed with implementation.

The existing infrastructure (BluetoothTransport, VAD, AudioRecord/Track patterns) provides a solid foundation. The main technical challenge is echo cancellation, which is mitigated by:

1. Using `MODE_IN_COMMUNICATION` + `AcousticEchoCanceler`
2. Falling back to automatic VOX if AEC unavailable
3. Testing thoroughly on physical devices

**Estimated effort:** 16 hours  
**Risk level:** Low-Medium  
**User impact:** High (enables real-time offline calls)

---

## 13. References

- Android AudioRecord: https://developer.android.com/reference/android/media/AudioRecord
- Android AcousticEchoCanceler: https://developer.android.com/reference/android/media/audiofx/AcousticEchoCanceler
- VoIP Latency Guidelines: ITU-T G.114
- Jitter Buffer Design: RFC 3550 (RTP)
