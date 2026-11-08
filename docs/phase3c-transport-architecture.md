# Phase 3C transport architecture

## Status: PARTIAL / NOT DEVICE-VALIDATED

Added a byte-oriented `PacketTransport` boundary and native Android `WifiDirectTransport`. Packet serialization remains in `PacketV2`; transports carry opaque bytes only. Existing Bluetooth message transport remains compatible through its Packet v2 integration.

The current application queue still uses the existing `Transport`/`Message` facade. Full TransportManager priority wiring and Room-authoritative transport switching remain required before production approval.

```text
PacketV2 bytes -> primary Wi-Fi Direct / fallback Bluetooth -> PacketV2 decode
```

Application identity (`sourceDeviceId`) is distinct from Wi-Fi/Bluetooth transport identity. No encryption or authentication is claimed.
