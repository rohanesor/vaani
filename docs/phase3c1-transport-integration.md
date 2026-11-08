# Phase 3C.1 transport integration

## Status: PARTIAL

`TransportManager` now owns both the existing Bluetooth transport and the byte-oriented Wi-Fi Direct transport. It exposes packet transport selection with actual lifecycle priority:

```text
Wi-Fi Direct CONNECTED -> primary
otherwise Bluetooth CONNECTED -> fallback
otherwise no active transport
```

It exposes packet sending, Wi-Fi state, Bluetooth state, discovery/connect/disconnect methods, and Wi-Fi incoming packet bytes. The application constructs both transports. Packet serialization remains in `PacketV2`.

The existing Room outbound worker still uses the legacy `Transport.send(Message)` path and therefore does not yet route outgoing messages through `TransportManager.sendPacket`. End-to-end queue fallback is consequently **NOT COMPLETE** and is explicitly not claimed.
