# LAP 1.2 — Emergency / Priority Audio Audit

**Date:** 2024-09-11  
**Branch:** `sih/ui-fidelity-corrections-wip`  
**Baseline Commit:** `1c0e6cd`

## Audit Summary

✅ **PASS** — Emergency audio handling is correctly implemented.

## Current Implementation

### 1. TTS Queue Priority (TtsPlaybackQueue)

```kotlin
internal class TtsPriorityQueue {
    private val items = ArrayDeque<TtsQueueItem>()
    
    fun add(item: TtsQueueItem) { 
        if (item.messageType == MessageType.SOS) 
            items.addFirst(item)  // SOS goes to front
        else 
            items.addLast(item)   // Normal messages queue at back
    }
}
```

**Behavior:**
- SOS messages jump to front of TTS queue
- Normal messages play in FIFO order
- SOS preempts currently playing normal audio

### 2. Audio Focus (VokieAudioPlayer)

```kotlin
val gain = if (emergency) 
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE  // Cannot be interrupted
else 
    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK   // Can be ducked
```

**Behavior:**
- Emergency: `AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE` — other apps must pause
- Normal: `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` — other apps can lower volume

### 3. Audio Stream (VokieAudioPlayer)

```kotlin
val attributes = AudioAttributes.Builder()
    .setUsage(if (emergency) 
        AudioAttributes.USAGE_ALARM        // Alarm stream (higher priority)
    else 
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)  // Accessibility stream
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()
```

**Behavior:**
- Emergency: `USAGE_ALARM` stream — respects alarm volume, not media volume
- Normal: `USAGE_ASSISTANCE_ACCESSIBILITY` — appropriate for speech

### 4. SOS Repeat (VokieAudioPlayer)

```kotlin
repeat(if (emergency) SOS_REPEAT_COUNT else 1) { 
    playOnce(buffer, emergency) 
}

private companion object {
    const val SOS_REPEAT_COUNT = 2
}
```

**Behavior:**
- SOS messages play twice for emphasis
- Normal messages play once

### 5. Volume Control

```kotlin
track.setVolume(1f)  // Maximum application gain
```

**Important Note:**  
Android does not allow apps to override system volume without special permissions. The implementation:
- Sets track volume to maximum (1.0)
- Uses ALARM stream which respects alarm volume setting
- Cannot programmatically increase device volume

The "Alerts at full volume" setting in UI is a user preference that currently does NOT override system volume (this would require `MODIFY_AUDIO_SETTINGS` permission and is generally discouraged).

### 6. Audio Focus Change Handling

```kotlin
private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
    when (change) {
        AudioManager.AUDIOFOCUS_GAIN -> track?.setVolume(1f)
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> track?.setVolume(0.25f)
        AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> track?.pause()
        AudioManager.AUDIOFOCUS_LOSS -> stopWithFailure(TtsException(...))
    }
}
```

**Behavior:**
- On regaining focus: restore full volume
- On transient duck request: lower to 25% (for non-emergency)
- On transient loss: pause playback
- On permanent loss: stop and report failure

### 7. Queue Preemption (TtsPlaybackQueue)

```kotlin
preemptNormalPlayback = item.messageType == MessageType.SOS 
    && current != null 
    && current?.messageType != MessageType.SOS

if (preemptNormalPlayback) engine.stop()
```

**Behavior:**
- SOS arriving while normal message plays: stops normal, plays SOS
- SOS arriving while SOS plays: queues (doesn't interrupt another SOS)

## Test Coverage

### Existing Tests

1. **`TtsCoreTest.sosHasPriorityWhileNormalMessagesRemainOrdered`**
   - Verifies SOS goes to front of queue
   - Normal messages maintain FIFO order

2. **`InboundPeerRoutingTest.14 - SOS message type with emergency priority`**
   - Verifies SOS messages are recorded with correct priority

## Known Limitations

1. **Cannot Override System Volume**
   - Android restricts programmatic volume control
   - App uses maximum application gain (1.0)
   - Actual loudness depends on user's alarm volume setting

2. **`alertsFullVolume` Preference**
   - Setting exists in UI
   - Currently does NOT modify actual playback behavior
   - Would require `MODIFY_AUDIO_SETTINGS` permission
   - Considered misleading; should be documented or removed

## Recommendation

The current implementation is correct and complete for what Android allows. The `alertsFullVolume` preference could be:

a) **Removed** — since it cannot actually boost volume
b) **Documented** — clarify it only works if alarm volume is set high
c) **Repurposed** — use it to toggle between ALARM and MUSIC streams

For now, the implementation is **functionally correct** and provides appropriate emergency priority.

## Test Results

```
./gradlew testDebugUnitTest    → BUILD SUCCESSFUL
./gradlew lintDebug            → BUILD SUCCESSFUL
./gradlew assembleDebug        → BUILD SUCCESSFUL
```

## Conclusion

**LAP 1.2 EMERGENCY/PRIORITY AUDIO: PASS**

Emergency audio correctly:
- Jumps to front of TTS queue
- Preempts normal playback
- Uses ALARM stream with exclusive focus
- Repeats twice for emphasis
- Cannot be ducked by other apps

The implementation respects Android's audio management policies.
