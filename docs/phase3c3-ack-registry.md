# ACK registry

## Status: IMPLEMENTED IN CODE / INTEGRATION PARTIAL

`PendingAckRegistry` is manager-owned and keyed by `messageId + sequenceNumber`. It records the originating `PacketTransport`, creation/deadline, completion, timeout cleanup, cancellation, and transport cleanup. `TransportManager` decodes incoming ACK packets and resolves matching entries.

Full Room ACK state transitions and physical transport validation remain outstanding.

Bytes written to a socket are not treated as acknowledgement. ACK remains receipt-only, not PLAYED or HEARD.
