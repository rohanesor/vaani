# Phase 3C.3 replay protection

## Status: PARTIAL

Room schema version 3 contains `received_packets` with primary key:

```text
sourceDeviceId + messageId + sequenceNumber
```

`InboundPacketCoordinator` now decodes both transport streams, reassembles complete messages, inserts the replay identity with conflict-ignore semantics, persists accepted messages, and ACKs through the transport that delivered the packet. Expired replay rows have a DAO cleanup operation.

The coordinator is wired into the application inbound stream. Persistent cleanup scheduling and a complete restart/incomplete-reassembly test on a real database remain outstanding. No cryptographic replay protection is claimed.
