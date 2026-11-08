# Phase 3C transport fallback

Desired priority:

```text
Wi-Fi Direct -> Bluetooth RFCOMM -> Room QUEUED
```

`TransportManager` now exposes lifecycle-based Wi-Fi-primary packet selection and Bluetooth fallback availability. The existing outbound worker still uses the legacy Message transport path, so automatic retry from a Wi-Fi Direct disconnect through Bluetooth is **NOT YET INTEGRATED** into the Room queue; this remains a production blocker, not a simulated capability.

A socket disconnect must leave the Room message queued/retrying rather than delete it. ACK remains transport/application receipt only and never means TTS playback or user acknowledgement.
