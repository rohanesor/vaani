# Phase 3 transport protocol status

## Current status

Packet v2 is now implemented in `PacketV2.kt`. The compatibility facade `VokieProtocol` preserves existing callers while emitting version-2 deterministic binary frames. It validates magic, version, language, IDs, payload size, TTL, CRC, and ACK frames. Bluetooth uses length-prefixed protocol frames, so packet bytes are independent of the underlying stream.

## Remaining Packet v2 work

Packet v2 now includes message ID, sequence number, language code, payload length, payload, CRC32, fragmentation, reassembly, TTL, priority, and ACK framing. Persistent replay inbox state and complete transport interoperability still require device validation and a Room inbox migration.

The packet must remain transport-independent:

```text
Packet v2 -> Wi-Fi Direct TCP or Bluetooth RFCOMM framing
```

No cloud relay or internet dependency is permitted. A message must remain queued in Room when a transport fails, and `Delivered` must only be emitted after an actual acknowledgement.
