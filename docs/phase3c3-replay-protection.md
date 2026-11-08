# Persistent replay protection

## Status: PARTIAL

The `received_packets` Room table and DAO are connected to `InboundPacketCoordinator`. Complete messages are inserted under `sourceDeviceId + messageId + sequenceNumber` with conflict-ignore semantics before application emission.

Scheduled cleanup, restart/incomplete-reassembly tests, and a complete transport integration test remain outstanding. This is not cryptographic authentication.
